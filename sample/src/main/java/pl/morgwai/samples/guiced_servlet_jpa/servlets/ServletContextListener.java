// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.guiced_servlet_jpa.servlets;

import java.util.LinkedList;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebListener;

import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;

import pl.morgwai.base.servlet.guiced.jpa.JpaPingingServletContextListener;
import pl.morgwai.base.servlet.scopes.ContextTrackingExecutor;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.ChatLogDao;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.ExternalService;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.ExternalServiceFake;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.JpaChatLogDao;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.JpaQueryRecordDao;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.QueryRecordDao;



@WebListener
public class ServletContextListener extends JpaPingingServletContextListener {



	@Override
	protected String getMainPersistenceUnitName() {
		return "queryRecordDb";  // same as in persistence.xml
	}

	@Override
	protected int getMainJpaThreadPoolSize() {
		return 10; // few times connectionPool size in src/main/jetty/webapps/servlet-jpa-sample.xml
	}



	/**
	 * App wide executor associated with {@link ExternalService}.
	 * Components can obtain a reference by requesting injection of instance named
	 * {@link ExternalService#EXECUTOR_NAME}.
	 */
	ContextTrackingExecutor externalServiceExecutor;



	@Override
	protected LinkedList<Module> configureInjections() {
		var modules = new LinkedList<Module>();

		// external service module
		externalServiceExecutor = servletModule.newContextTrackingExecutor(
				ExternalService.EXECUTOR_NAME, ExternalService.THREADPOOL_SIZE);
		modules.add((binder) -> {
			binder.bind(ExternalService.class).to(ExternalServiceFake.class).in(Scopes.SINGLETON);
			binder.bind(ContextTrackingExecutor.class)
					.annotatedWith(Names.named(ExternalService.EXECUTOR_NAME))
					.toInstance(externalServiceExecutor);
		});

		// dao module
		modules.add((binder) -> {
			binder.bind(QueryRecordDao.class).to(JpaQueryRecordDao.class).in(Scopes.SINGLETON);
			binder.bind(ChatLogDao.class).to(JpaChatLogDao.class).in(Scopes.SINGLETON);
		});

		return modules;
	}



	@Override
	protected void configureServletsFiltersEndpoints() throws ServletException {
		addServlet(ChatLogServlet.class.getSimpleName(), ChatLogServlet.class,
				"/" + ChatLogServlet.URI);
		addServlet(QueryRecordListServlet.class.getSimpleName(), QueryRecordListServlet.class,
				"/" + QueryRecordListServlet.URI);
		addServlet(SaveQueryServlet.class.getSimpleName(), SaveQueryServlet.class,
				"/" + SaveQueryServlet.URI);
	}



	@Override
	public void contextDestroyed(ServletContextEvent event) {
		ChatEndpoint.shutdown();
		super.contextDestroyed(event);
	}
}
