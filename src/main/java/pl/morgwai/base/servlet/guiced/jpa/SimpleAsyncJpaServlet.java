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

import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;



/**
 * Base class for servlets that do not perform synchronous time consuming operations
 * other than JPA calls.
 * <p>
 * Request handling is dispatched to the app wide {@link ContextTrackingExecutor JPA executor}
 * associated with persistence unit's JDBC connection pool.
 * This way the total number of server's threads can remain constant and small (<font size='-2'>
 * number of CPU cores available to the process for the main request processing pool + size of the
 * JDBC connection pool for the persistence unit associated executor pool + some constant epsilon
 * for servlet container internals</font>), regardless of the number of concurrent requests, while
 * providing optimal performance.</p>
 */
@SuppressWarnings("serial")
public abstract class SimpleAsyncJpaServlet extends JpaServlet {



	/**
	 * Dispatches handling of incoming requests to the app wide
	 * {@link ContextTrackingExecutor JPA executor}.
	 * Closes the associated {@link javax.persistence.EntityManager} at the end.
	 */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		AsyncContext asyncCtx = request.startAsync();
		AsyncHttpServletRequest asyncRequest = new AsyncHttpServletRequest(request);
		jpaExecutor.execute(() -> {
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
