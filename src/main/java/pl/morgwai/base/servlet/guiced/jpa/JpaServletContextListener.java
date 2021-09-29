// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.jpa;

import java.util.LinkedList;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;

import com.google.inject.Module;
import com.google.inject.name.Names;

import pl.morgwai.base.servlet.scopes.ContextTrackingExecutor;
import pl.morgwai.base.servlet.scopes.GuiceServletContextListener;



/**
 * Manages lifecycle of the app wide {@link EntityManagerFactory} (persistence unit) and its
 * associated {@link ContextTrackingExecutor} (called {@code JPA executor} from now on).
 * <p>
 * A single subclass must be created and either annotated with
 * {@link javax.servlet.annotation.WebListener @WebListener} or enlisted in <code>web.xml</code>
 * file in <code>listener</code> element.</p>
 * <p>
 * Components that perform JPA operations should request injection of
 * {@link com.google.inject.Provider Provider}&lt;{@link EntityManager}&gt; instances.<br/>
 * If the app uses multiple persistence unit (when {@link #isSinglePersistenceUnitApp()} is
 * overridden to return {@code false}), each injection point must be additionally annotated
 * with @{@link com.google.inject.name.Named} to indicate which persistence unit should be
 * used. In such case, the main persistence unit (returned by {@link #getMainPersistenceUnitName()})
 * will be bound for injection with the name {@link #MAIN_PERSISTENCE_UNIT_BINDING_NAME}.</p>
 * <p>
 * JPA operations should be performed on the associated {@link ContextTrackingExecutor JPA executor}
 * that can be obtained by requesting injection of {@link ContextTrackingExecutor}.
 * Again, if the app uses multiple persistence units, then injection points must be annotated
 * with @{@link com.google.inject.name.Named}.</p>
 * <p>
 * Components that need to create named queries as a part of their initialization can request
 * injection of {@link EntityManagerFactory} (with @{@link com.google.inject.name.Named} if app uses
 * multiple persistence units) and create 1 initial {@link EntityManager} instance to create
 * {@link javax.persistence.Query} instances.<br/>
 * <b>NOTE:</b> this initial {@link EntityManagerFactory} and {@link EntityManager} instances
 * <b>MUST NOT</b> be retained beyond named queries initialization.
 * Subsequent JPA operations during normal application request processing should happen via properly
 * scoped {@link EntityManager} instances obtained from the injected
 * {@link com.google.inject.Provider Provider}&lt;{@link EntityManager}&gt;.</p>
 */
public abstract class JpaServletContextListener extends GuiceServletContextListener {



	/**
	 * Returns the name of the persistence unit in <code>persistence.xml</code> file to be used.
	 */
	protected abstract String getMainPersistenceUnitName();

	/**
	 * Returns the size of the thread pool to be used by the
	 * {@link ContextTrackingExecutor JPA executor}.
	 * <p>
	 * The value should be the same or slightly bigger than the size of the JDBC connection pool
	 * referenced in the definition of the persistence unit named
	 * {@link #getMainPersistenceUnitName()} in <code>persistence.xml</code> file.</p>
	 */
	protected abstract int getMainJpaThreadPoolSize();

	/**
	 * Indicates whether this app uses only 1 persistence unit. By default <code>true</code>.
	 * <p>
	 * If <code>true</code> then {@link EntityManager} instances, {@link EntityManagerFactory} and
	 * its associated {@link ContextTrackingExecutor JPA executor} injections are bound without
	 * names, so that user-defined components don't need to annotate injected fields/params with
	 * <code>@Named(JpaServletContextListener.MAIN_PERSISTENCE_UNIT_BINDING_NAME)</code> when
	 * there's only 1 choice.</p>
	 */
	protected boolean isSinglePersistenceUnitApp() { return true; }

	/**
	 * Injection binding name for the main persistence unit associated objects in apps that use
	 * multiple persistence units.
	 * <p>
	 * If the app uses a single persistence unit (default,
	 * {@link JpaServletContextListener#isSinglePersistenceUnitApp()} is not overridden and returns
	 * {@code true}) then this constant is never used.</p>
	 * <p>
	 * If {@link #isSinglePersistenceUnitApp()} returns <code>false</code>, then this constant is
	 * used as the value of {@link com.google.inject.name.Names} for injection bindings of
	 * {@link EntityManagerFactory}, {@link ContextTrackingExecutor JPA executor} and
	 * {@link EntityManager}s associated with the main persistence unit (the 1 returned by
	 * {@link #getMainPersistenceUnitName()}).
	 */
	public static final String MAIN_PERSISTENCE_UNIT_BINDING_NAME =
			"pl.morgwai.base.servlet.guiced.jpa.mainPersistenceUnit";



	EntityManagerFactory entityManagerFactory;
	ContextTrackingExecutor jpaExecutor;
	static boolean singlePersistenceUnitApp;



	/**
	 * Calls {@link #configureMoreInjections()} and binds injections of the main
	 * {@link EntityManagerFactory}, {@link ContextTrackingExecutor JPA executor} and
	 * {@link EntityManager}.
	 */
	@Override
	protected LinkedList<Module> configureInjections() throws ServletException {
		var modules = configureMoreInjections();

		singlePersistenceUnitApp = isSinglePersistenceUnitApp();
		entityManagerFactory = Persistence.createEntityManagerFactory(
				getMainPersistenceUnitName());
		jpaExecutor = servletModule.newContextTrackingExecutor(
				getMainPersistenceUnitName() + "JpaExecutor", getMainJpaThreadPoolSize());
		log.info("entity manager factory " + getMainPersistenceUnitName()
				+ " and its JPA executor created successfully");

		modules.add((binder) -> {
			if (singlePersistenceUnitApp) {
				binder.bind(EntityManager.class)
					.toProvider(() -> entityManagerFactory.createEntityManager())
					.in(servletModule.requestScope);
				binder.bind(EntityManagerFactory.class)
					.toInstance(entityManagerFactory);
				binder.bind(ContextTrackingExecutor.class)
					.toInstance(jpaExecutor);
			} else {
				binder.bind(EntityManager.class)
					.annotatedWith(Names.named(MAIN_PERSISTENCE_UNIT_BINDING_NAME))
					.toProvider(() -> entityManagerFactory.createEntityManager())
					.in(servletModule.requestScope);
				binder.bind(EntityManagerFactory.class)
					.annotatedWith(Names.named(MAIN_PERSISTENCE_UNIT_BINDING_NAME))
					.toInstance(entityManagerFactory);
				binder.bind(ContextTrackingExecutor.class)
					.annotatedWith(Names.named(MAIN_PERSISTENCE_UNIT_BINDING_NAME))
					.toInstance(jpaExecutor);
			}
		});
		return modules;
	}

	/**
	 * See {@link GuiceServletContextListener#configureInjections()}.
	 */
	protected abstract LinkedList<Module> configureMoreInjections() throws ServletException;



	/**
	 * Shuts down the main {@link EntityManagerFactory} and its
	 * {@link ContextTrackingExecutor JPA executor}.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent event) {
		jpaExecutor.tryShutdownGracefully(getJpaExecutorShutdownSeconds());
		entityManagerFactory.close();
		log.info("entity manager factory " + getMainPersistenceUnitName() + " shutdown completed");
		super.contextDestroyed(event);
	}



	/**
	 * Returns the default timeout of 5 seconds for the shutdown of the
	 * {@link ContextTrackingExecutor JPA executor}.
	 * <p>
	 * Can be overridden to change the timeout if needed.</p>
	 */
	protected int getJpaExecutorShutdownSeconds() { return 5; }
}
