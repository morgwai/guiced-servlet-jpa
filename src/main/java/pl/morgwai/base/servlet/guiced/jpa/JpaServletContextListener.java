// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.jpa;

import java.util.LinkedList;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.servlet.ServletContextEvent;

import com.google.inject.Module;
import com.google.inject.name.Names;

import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.base.servlet.scopes.GuiceServletContextListener;



/**
 * Manages lifecycle of app wide JPA {@link EntityManagerFactory} (persistence unit) and its
 * associated {@link ContextTrackingExecutor executor} (called "JPA executor" from now on).
 * <p>
 * A single subclass of this class must be created and either annotated with
 * {@link javax.servlet.annotation.WebListener @WebListener} or enlisted in <code>web.xml</code>
 * file in <code>listener</code> element.</p>
 * <p>
 * Components that perform JPA operations should request injection of
 * {@link com.google.inject.Provider Provider}&lt;{@link EntityManager}&gt; instances named
 * {@link #MAIN_PERSISTENCE_UNIT_BINDING_NAME} (or unnamed if
 * {@link #isSinglePersistenceUnitApp} is <code>true</code>) that will be scoped to a given request.
 * <br/>
 * JPA operations should be performed on the associated JPA executor that can be obtained by
 * requesting injection of {@link ContextTrackingExecutor} instance named
 * {@link #MAIN_PERSISTENCE_UNIT_BINDING_NAME} (or unnamed if {@link #isSinglePersistenceUnitApp}
 * is <code>true</code>).</p>
 * <p>
 * Components that need to create named queries as a part of their initialization can request
 * injection of {@link EntityManagerFactory} instance named
 * {@link #MAIN_PERSISTENCE_UNIT_BINDING_NAME} (or unnamed if {@link #isSinglePersistenceUnitApp} is
 * <code>true</code>) and create 1 initial {@link EntityManager} instance to create
 * {@link javax.persistence.Query} instances.<br/>
 * <b>NOTE:</b> this initial {@link EntityManagerFactory} and {@link EntityManager} instances
 * <b>MUST NOT</b> be retained beyond named queries initialization.
 * Subsequent JPA operations during normal application request processing should happen via
 * {@link EntityManager} instances properly scoped to a given request obtained from the injected
 * {@link com.google.inject.Provider Provider}&lt;{@link EntityManager}&gt;.</p>
 */
public abstract class JpaServletContextListener extends GuiceServletContextListener {



	/**
	 * @return name of the persistence unit in <code>persistence.xml</code> file to be used.
	 */
	protected abstract String getMainPersistenceUnitName();

	/**
	 * @return size of the thread pool to be used by {@link #jpaExecutor}. The value should be the
	 * same or slightly bigger than the size of the JDBC connection pool referenced in the
	 * definition of the persistence unit named {@link #getMainPersistenceUnitName()}
	 */
	protected abstract int getMainJpaThreadPoolSize();

	/**
	 * Does this app use only 1 persistence unit. If not, then the value should be changed at the
	 * very beginning of {@link #configureInjections()} in subclasses before calling super.
	 * <p>
	 * If this flag is <code>true</code> then {@link EntityManager} instances,
	 * {@link #entityManagerFactory} and its associated JPA executor are bound for injection
	 * without names, so that user-defined components don't need to annotate injected fields/params
	 * with <code>@Named(JpaServletContextListener.MAIN_PERSISTENCE_UNIT_BINDING_NAME)</code>
	 * when there's only 1 choice.</p>
	 */
	public static boolean isSinglePersistenceUnitApp = true;

	/**
	 * If {@link #isSinglePersistenceUnitApp} is <code>false</code>, then this constant is used as
	 * the value of <code>@Named</code> for bindings of {@link #entityManagerFactory},
	 * {@link #jpaExecutor} and <code>EntityManager Provider</code> associated with the main
	 * persistence unit (the 1 returned by {@link #getMainPersistenceUnitName()}).
	 */
	public static final String MAIN_PERSISTENCE_UNIT_BINDING_NAME =
			"pl.morgwai.base.servlet.guiced.jpa.mainPersistenceUnit";



	EntityManagerFactory entityManagerFactory;
	ContextTrackingExecutor jpaExecutor;



	@Override
	protected LinkedList<Module> configureInjections() {
		entityManagerFactory = Persistence.createEntityManagerFactory(
				getMainPersistenceUnitName());
		jpaExecutor = servletModule.newContextTrackingExecutor(
				getMainPersistenceUnitName() + "JpaExecutor", getMainJpaThreadPoolSize());
		log.info("entity manager factory " + getMainPersistenceUnitName()
				+ " and its JPA executor created successfully");

		var modules = new LinkedList<Module>();
		modules.add((binder) -> {
			if (isSinglePersistenceUnitApp) {
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



	@Override
	public void contextDestroyed(ServletContextEvent event) {
		jpaExecutor.tryShutdownGracefully(getJpaExecutorShutdownSeconds());
		entityManagerFactory.close();
		log.info("entity manager factory " + getMainPersistenceUnitName() + " shutdown completed");
		super.contextDestroyed(event);
	}



	/**
	 * @return default timeout of 5 seconds for the shutdown of the JPA executor.
	 * Can be overridden to change the timeout if needed.
	 */
	protected int getJpaExecutorShutdownSeconds() { return 5; }
}
