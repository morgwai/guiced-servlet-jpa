// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.jpa;

import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Base class for servlets that do not perform synchronous time consuming operations other than JPA
 * calls. Dispatches request handling to the {@link JpaServlet#jpaExecutor JPA executor} to prevent
 * requests awaiting for available JDBC connection from blocking server threads. This way the total
 * number of server's threads can remain constant and small regardless of the number of concurrent
 * requests, while the JDBC connection pool will be optimally utilized.
 */
@SuppressWarnings("serial")
public abstract class SimpleAsyncJpaServlet extends JpaServlet {



	/**
	 * Dispatches handling of incoming requests to the {@link JpaServlet#jpaExecutor JPA executor}.
	 * Closes the associated {@link javax.persistence.EntityManager} at the end.
	 * <p>
	 * If the invoked {@code doXXX} method throws, then, unless it's an
	 * {@link IOException} (indicating broken connection), it's logged at level {@code ERROR} and an
	 * attempt to send {@link HttpServletResponse#SC_INTERNAL_SERVER_ERROR} is made.<br/>
	 * {@link IOException}s are logged at level {@code DEBUG}.<br/>
	 * {@link Error}s are additionally re-thrown after being logged.</p>
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		AsyncContext asyncCtx = request.startAsync();
		AsyncHttpServletRequest asyncRequest = new AsyncHttpServletRequest(request);
		jpaExecutor.execute(response, () -> {
			try {
				super.service(asyncRequest, response);
			} catch (Throwable e) {
				if (e instanceof IOException) {
					log.debug("probably just a broken connection", e);
				} else {
					log.error("", e);
				}
				if (!response.isCommitted()) {
					try {
						response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
					} catch (IOException e1) {}  // not even worth log.finest()  ;]
				}
				if (e instanceof Error) throw (Error) e;
			} finally {
				entityManagerProvider.get().close();
				asyncCtx.complete();
			}
		});
	}



	static Logger log = LoggerFactory.getLogger(SimpleAsyncJpaServlet.class.getName());



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
}
