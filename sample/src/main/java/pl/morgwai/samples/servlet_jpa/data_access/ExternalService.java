// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_jpa.data_access;



public interface ExternalService {

	/**
	 * Binding name for
	 * {@link pl.morgwai.samples.servlet_jpa.servlets.ServletContextListener#externalServiceExecutor
	 * associated executor}.
	 */
	static final String EXECUTOR_NAME = "externalServiceExecutor";

	/**
	 * How long the faked "processing" should take. SaveQueryServlet can roughly handle
	 * <code>ASYNC_CTX_TIMEOUT * THREADPOOL_SIZE / PROCESSING_TIME_SECONDS</code> concurrent
	 * requests (assuming that JPA operations are fast enough to be negligible).
	 */
	static final int PROCESSING_TIME_SECONDS = 3;

	/**
	 * threadPool size should normally be configurable and correspond to the number of
	 * concurrent connections we are allowed to establish to a given service.
	 */
	static final int THREADPOOL_SIZE = 30;

	String getLink(String query);
}
