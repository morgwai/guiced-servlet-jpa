/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.servlet_jpa.servlets;

import java.lang.reflect.InvocationHandler;
import java.util.LinkedList;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebListener;

import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;

import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.base.servlet.guiced.utils.EndpointPingerDecorator;
import pl.morgwai.base.servlet.jpa.JpaServletContextListener;
import pl.morgwai.base.servlet.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.utils.WebsocketPinger;
import pl.morgwai.samples.servlet_jpa.data_access.ChatLogDao;
import pl.morgwai.samples.servlet_jpa.data_access.ExternalService;
import pl.morgwai.samples.servlet_jpa.data_access.ExternalServiceFake;
import pl.morgwai.samples.servlet_jpa.data_access.JpaChatLogDao;
import pl.morgwai.samples.servlet_jpa.data_access.JpaQueryRecordDao;
import pl.morgwai.samples.servlet_jpa.data_access.QueryRecordDao;



@WebListener
public class ServletContextListener extends JpaServletContextListener {



	@Override
	protected String getMainPersistenceUnitName() {
		return "queryRecordDb";  // same as in persistence.xml
	}

	@Override
	protected int getMainJpaThreadPoolSize() {
		return 10;  // same as connection pool in src/main/jetty/webapps/servlet-jpa-sample.xml
	}



	/**
	 * App wide executor associated with {@link ExternalService}.
	 * Components can obtain a reference by requesting injection of instance named
	 * {@link ExternalService#EXECUTOR_NAME}.
	 */
	ContextTrackingExecutor externalServiceExecutor;



	@Override
	protected LinkedList<Module> configureInjections() {
		var modules = super.configureInjections();

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
		addEndpoint(ChatEndpoint.class, ChatEndpoint.PATH);
	}



	@Override
	protected void addEndpoint(Class<?> endpointClass, String path) throws ServletException {
		super.addEndpoint(endpointClass, path, new SimplePingingEndpointConfigurator());
	}

	public class SimplePingingEndpointConfigurator extends GuiceServerEndpointConfigurator {

		@Override
		protected InvocationHandler getAdditionalDecorator(Object endpoint) {
			return new EndpointPingerDecorator(endpoint, pinger);
		}
	}

	WebsocketPinger pinger = new WebsocketPinger();



	@Override
	public void contextDestroyed(ServletContextEvent event) {
		pinger.stop();
		ChatEndpoint.shutdown();

		// close executors in parallel to speed up the shutdown
		Thread externalServiceFinalizer = new Thread(() -> {
			externalServiceExecutor.tryShutdownGracefully(5);
		});
		externalServiceFinalizer.start();

		super.contextDestroyed(event);
		try {
			externalServiceFinalizer.join();
		} catch (InterruptedException e) {}
	}
}
