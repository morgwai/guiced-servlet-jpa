// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.jpa;

import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Dispatches request handling to {@link JpaServlet#jpaExecutor the executor} associated with the
 * configured persistence unit.
 * This prevents requests awaiting for an available JDBC connection from blocking server threads.
 * This way the total number of server's threads can remain constant and small regardless of the
 * number of concurrent requests, while the JDBC connection pool will be optimally utilized.
 * <p>
 * Base class for servlets that do <b>not</b> perform synchronous time consuming operations other
 * than JPA related calls.</p>
 */
@SuppressWarnings("serial")
public abstract class SimpleAsyncJpaServlet extends JpaServlet {



	/**
	 * Dispatches request handling to {@link JpaServlet#jpaExecutor}.
	 * Closes the obtained {@link javax.persistence.EntityManager} at the end. By default also calls
	 * {@link AsyncContext#complete()}: if a subclass wants to dispatch processing back to the
	 * container via {@link AsyncContext#dispatch(String)} or to another executor, then
	 * {@link #shouldCallAsyncContextComplete(HttpServletRequest)} should be overridden to return
	 * {@code false}.
	 * <p>
	 * If the invoked {@code doXXX} method throws an exception, then, unless it's an
	 * {@link IOException} (indicating broken connection), it's logged at level {@code ERROR} and an
	 * attempt to send {@link HttpServletResponse#SC_INTERNAL_SERVER_ERROR} is made.<br/>
	 * {@link IOException}s are logged at level {@code DEBUG}.<br/>
	 * {@link Error}s are additionally re-thrown after being logged.</p>
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		final var asyncCtx = startAsync(request, response);
		final var timeout = getAsyncContextTimeout();
		if (timeout >= 0l) asyncCtx.setTimeout(timeout);
		final var asyncRequest = new AsyncHttpServletRequest(request);
		jpaExecutor.execute(response, () -> {
			try {
				super.service(asyncRequest, response);
			} catch (Throwable e) {
				if (e instanceof IOException) {
					log.debug("probably just a broken connection", e);
				} else {
					log.error("", e);
				}
				if ( ! response.isCommitted()) {
					try {
						response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					} catch (IOException ignored) {}  // not even worth log.finest()  ;]
				}
				if (e instanceof Error) throw (Error) e;
			} finally {
				entityManagerProvider.get().close();
				if (shouldCallAsyncContextComplete(request)) asyncCtx.complete();
			}
		});
	}

	/**
	 * Returns timeout for {@link AsyncContext#setTimeout(long)}; Negative number indicates that
	 * {@link AsyncContext#setTimeout(long)} should not be called in which case container default
	 * will take effect. By default {@code 0} (meaning no timeout).
	 * <p>
	 * <b>NOTE:</b> combining non-container threads with {@link AsyncContext} timeout
	 * mechanism requires proper synchronization between main request processing code (running on
	 * {@link #jpaExecutor} thread and {@link javax.servlet.AsyncListener#onTimeout(AsyncEvent)}
	 * (running on container thread) and may result in the main processing code throwing harmless
	 * exceptions in case of timeouts even if the processing was discontinued
	 * (for example as of Jetty 10.0.x, if response output was obtained, an interceptor at the end
	 * of a given {@code doXXX()} method will throw an exception when trying to close it for the 2nd
	 * time).</p>
	 */
	protected long getAsyncContextTimeout() { return 0l; }

	/**
	 * Whether {@link AsyncContext#complete()} should be called automatically at the end of request
	 * processing. By default {@code true}. Should be overridden if processing is dispatched back to
	 * the container via {@link AsyncContext#dispatch(String)} or to another executor,
	 */
	protected boolean shouldCallAsyncContextComplete(HttpServletRequest request) { return true; }

	/**
	 * Starts {@link AsyncContext}. By default calls {@link HttpServletRequest#startAsync()}. Can be
	 * overridden if {@link
	 * HttpServletRequest#startAsync(javax.servlet.ServletRequest, javax.servlet.ServletResponse))}
	 * needs to be used.
	 */
	protected AsyncContext startAsync(HttpServletRequest request, HttpServletResponse response) {
		return request.startAsync();
	}



	static class AsyncHttpServletRequest extends HttpServletRequestWrapper {

		@Override
		public String getContextPath() {
			return (String) getRequest().getAttribute(AsyncContext.ASYNC_CONTEXT_PATH);
		}

		@Override
		public String getPathInfo() {
			return (String) getRequest().getAttribute(AsyncContext.ASYNC_PATH_INFO);
		}

		@Override
		public String getQueryString() {
			return (String) getRequest().getAttribute(AsyncContext.ASYNC_QUERY_STRING);
		}

		@Override
		public String getRequestURI() {
			return (String) getRequest().getAttribute(AsyncContext.ASYNC_REQUEST_URI);
		}

		@Override
		public String getServletPath() {
			return (String) getRequest().getAttribute(AsyncContext.ASYNC_SERVLET_PATH);
		}

		public AsyncHttpServletRequest(HttpServletRequest request) { super(request); }
	}



	static Logger log = LoggerFactory.getLogger(SimpleAsyncJpaServlet.class.getName());
}
