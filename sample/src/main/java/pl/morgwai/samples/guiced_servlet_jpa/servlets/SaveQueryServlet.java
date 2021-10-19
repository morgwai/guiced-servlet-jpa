// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.guiced_servlet_jpa.servlets;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.morgwai.base.servlet.guiced.jpa.JpaServlet;
import pl.morgwai.base.servlet.scopes.ContextTrackingExecutor;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.ExternalService;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.QueryRecordDao;
import pl.morgwai.samples.guiced_servlet_jpa.domain.QueryRecord;



/**
 * A servlet that communicates with multiple slow resources that provide synchronous API only (DB
 * via JPA and {@link ExternalService}). Dispatches slow operations to injected app wide
 * {@link ContextTrackingExecutor} instances dedicated to each resource.
 * This avoids suspending threads from the server's main pool, while also passes context to threads
 * performing the slow operations and thus preserves request/session scoped objects
 * ({@link javax.persistence.EntityManager} in this case).
 */
@SuppressWarnings("serial")
public class SaveQueryServlet extends JpaServlet {



	public static final String URI = "saveQuery";

	@Inject
	QueryRecordDao dao;

	@Inject
	ExternalService externalService;

	@Inject @Named(ExternalService.EXECUTOR_NAME)
	ContextTrackingExecutor externalServiceExecutor;

	public static final int ASYNC_CTX_TIMEOUT = 100_000;



	/**
	 * Stores into DB a record containing query parameters, issues a query to the external resource
	 * and updates the DB record with the result result obtained.<br/>
	 * Note: let's assume for the demonstration purposes that it's a business requirement to save a
	 * query to the DB immediately after the request is received (before a call to the external
	 * resource) as there is no technical reason to do so: from a technical point of view both query
	 * and its result could be stored into the DB together in a single JPA operation after the call
	 * to the external resource.
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) {
		// starting on server's main threadPool
		AsyncContext asyncCtx = request.startAsync();
		asyncCtx.setTimeout(ASYNC_CTX_TIMEOUT);

		// switch to the threadPool of app wide executor associated with persistence unit's
		// JDBC connection pool to store the query.
		jpaExecutor.execute(response, () -> {
			QueryRecord record;
			String idString = request.getParameter(QueryRecord.ID);
			try {
				if (idString == null) {
					record = new QueryRecord(request.getParameter(QueryRecord.QUERY));
					executeWithinTx(() -> {dao.persist(record); return null;});
				} else {
					record = new QueryRecord(
							Long.valueOf(idString), request.getParameter(QueryRecord.QUERY));
					boolean updated = executeWithinTx(() -> dao.update(record));
					if ( ! updated) {
						// record was deleted in the mean time or its id was invalid
						response.setHeader("Location", "/" + QueryRecordListServlet.URI);
						response.setStatus(HttpServletResponse.SC_SEE_OTHER);
						asyncCtx.complete();
						return;
					}
				}
			} catch (Exception e) {
				logAndSendError(response, e);
				asyncCtx.complete();
				return;
			} finally {
				entityManagerProvider.get().close();
			}

			// remove from this request's scope the associated (closed above) EntityManager
			// before performing long lasting call to externalService, so that the JDBC connection
			// can be used for other requests during this time
			removeEntityManagerFromRequestScope();

			// switch to the threadPool associated with externalService to perform a long lasting
			// call to it
			externalServiceExecutor.execute(response, () -> {
				String link;
				try {
					link = externalService.getLink(record.getQuery());
				} catch (Exception e) {
					logAndSendError(response, e);
					asyncCtx.complete();
					return;
				}

				// switch again to the JPA threadPool to update the record with the result.
				// a new EntityManager (and an underlying JDBC connection) will be assigned
				jpaExecutor.execute(response, () -> {
					record.setResult(link);
					try {
						executeWithinTx(() -> dao.update(record));
						// SC_SEE_OTHER is sent instead of dispatching request to make browser's
						// 'reload' button always safe to use on record list page
						response.setHeader("Location", "/" + QueryRecordListServlet.URI);
						response.setStatus(HttpServletResponse.SC_SEE_OTHER);
					} catch (Exception e) {
						logAndSendError(response, e);
					} finally {
						entityManagerProvider.get().close();
						asyncCtx.complete();
					}
				});
			});
		});
	}



	static void logAndSendError(HttpServletResponse response, Exception e) {
		log.error("", e);
		try {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.toString());
		} catch (Exception ignored) {}
	}



	static final Logger log = LoggerFactory.getLogger(SaveQueryServlet.class.getName());
}
