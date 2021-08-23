// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.jpa;

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
 * Manages lifecycle of
 * {@link #entityManagerFactory app wide JPA <code>EntityManagerFactory</code> (persistence unit)}
 * and {@link #jpaExecutor its associated <code>ContextTrackingExecutor</code>}.
 * A single subclass of this class must be created and either annotated with
 * <code>@WebListener</code> or enlisted in <code>web.xml</code> file in <code>listener</code>
 * element.
 */
public abstract class JpaServletContextListener extends GuiceServletContextListener {



	/**
	 * Does this app use only 1 persistence unit. If not, then the value should be changed at the
	 * very beginning of {@link #configureInjections()} in subclasses before calling super.<br/>
	 * If this flag is <code>true</code> then <code>EntityManager</code> instances,
	 * {@link #entityManagerFactory} and its associated {@link #jpaExecutor} are bound for injection
	 * without names, so that user-defined components don't need to annotate injected fields/params
	 * with <code>@Named(JpaServletContextListener.MAIN_PERSISTENCE_UNIT_BINDING_NAME)</code>
	 * when there's only 1 choice.
	 */
	public static boolean isSinglePersistenceUnitApp = true;

	/**
	 * If {@link #isSinglePersistenceUnitApp} is <code>false</code>, then this constant is used as
	 * the value of <code>@Named</code> for bindings of {@link #entityManagerFactory},
	 * {@link #jpaExecutor} and <code>EntityManager Provider</code> associated with the main
	 * persistence unit (the 1 returned by {@link #getMainPersistenceUnitName()}).
	 */
	public static final String MAIN_PERSISTENCE_UNIT_BINDING_NAME =
			"pl.morgwai.base.servlet.GuiceJpaServletContextListener.mainPersistenceUnit";



	/**
	 * App wide <code>EntityManagerFactory</code> of persistence unit named as value returned
	 * by {@link #getMainPersistenceUnitName()}.
	 * Components that perform JPA operations should request injection of
	 * <code>Provider&lt;EntityManager&gt;</code> instances named
	 * {@link #MAIN_PERSISTENCE_UNIT_BINDING_NAME} (or unnamed if
	 * {@link #isSinglePersistenceUnitApp} is <code>true</code>) that will internally be using this
	 * factory and providing <code>EntityManager</code> instances scoped to a given
	 * <code>RequestContext</code>.<br/>
	 * Components that need to create named queries as a part of their initialization can obtain a
	 * reference by requesting injection of <code>EntityManagerFactory</code> instance named
	 * {@link #MAIN_PERSISTENCE_UNIT_BINDING_NAME} (or unnamed if
	 * {@link #isSinglePersistenceUnitApp} is <code>true</code>) and create 1 initial
	 * <code>EntityManager</code> instance to create <code>Query</code> instances.<br/>
	 * <b>NOTE:</b> Components <b>SHOULD NOT</b> retain this factory nor the initial
	 * <code>EntityManager</code> instance beyond named queries initialization!
	 * Further obtaining of <code>EntityManager</code> instances during normal application runtime
	 * should happen via injected <code>Provider&lt;EntityManager&gt;</code> instances that are
	 * properly scoped to a given request.
	 */
	EntityManagerFactory entityManagerFactory;

	/**
	 * @return name of the persistence unit in <code>persistence.xml</code> file to be used.
	 */
	protected abstract String getMainPersistenceUnitName();



	/**
	 * App wide executor for asynchronous JPA operations performed via <code>EntityManager</<code>
	 * objects obtained from {@link #entityManagerFactory}.
	 * Its thread pool size is set to the value returned by {@link #getMainJpaThreadPoolSize()}.
	 * Components can obtain a reference by requesting injection of instance named
	 * {@link #MAIN_PERSISTENCE_UNIT_BINDING_NAME}
	 * (or unnamed if {@link #isSinglePersistenceUnitApp} is <code>true</code>).
	 */
	ContextTrackingExecutor jpaExecutor;

	/**
	 * @return size of the thread pool to be used by {@link #jpaExecutor}. The value should be the
	 * same or slightly bigger than the size of the JDBC connection pool referenced in the
	 * definition of the persistence unit named {@link #getMainPersistenceUnitName()}
	 */
	protected abstract int getMainJpaThreadPoolSize();



	/**
	 * Creates and binds {@link #entityManagerFactory} and {@link #jpaExecutor}.
	 */
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



	/**
	 * Shuts down {@link #jpaExecutor} and {@link #entityManagerFactory}.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent event) {
		jpaExecutor.tryShutdownGracefully(getJpaExecutorShutdownSeconds());
		entityManagerFactory.close();
		log.info("entity manager factory " + getMainPersistenceUnitName() + " shutdown completed");
		super.contextDestroyed(event);
	}



	/**
	 * @return default timeout of 5 seconds for the shutdown of {@link #jpaExecutor}. Can be
	 * overridden to change the timeout if needed.
	 */
	protected int getJpaExecutorShutdownSeconds() { return 5; }
}
