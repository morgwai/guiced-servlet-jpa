// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.jpa;

import java.util.LinkedList;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.servlet.ServletContextEvent;

import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;

import pl.morgwai.base.servlet.scopes.ContextTrackingExecutor;
import pl.morgwai.base.servlet.scopes.GuiceServletContextListener;



/**
 * Manages the lifecycle of {@link #getMainPersistenceUnitName() the main persistent unit} and its
 * associated {@link #mainJpaExecutor executor}.
 * <p>
 * A single subclass must be created and either annotated with
 * {@link jakarta.servlet.annotation.WebListener @WebListener} or enlisted in <code>web.xml</code>
 * file in <code>listener</code> element.</p>
 * <p>
 * Components that perform JPA operations (such as DAOs) should request injection of
 * {@link com.google.inject.Provider Provider}&lt;{@link EntityManager}&gt; instances.<br/>
 * If the app uses multiple persistence units (when {@link #isSinglePersistenceUnitApp()} is
 * overridden to return {@code false}), each injection point must be additionally annotated
 * with @{@link com.google.inject.name.Named} to indicate which persistence unit should be
 * used. In such case, {@link #getMainPersistenceUnitName() the main persistence unit} will be bound
 * for injection with the name {@link #MAIN_PERSISTENCE_UNIT_BINDING_NAME}.</p>
 * <p>
 * All JPA operations on the main persistence unit should be dispatched to {@link #mainJpaExecutor}
 * to avoid blocking server threads when waiting for an available JDBC connection.</p>
 * <p>
 * Components that need to create named queries as a part of their initialization can request
 * injection of {@link EntityManagerFactory} (with @{@link com.google.inject.name.Named} if app uses
 * multiple persistence units) and create 1 initial {@link EntityManager} instance to create
 * {@link jakarta.persistence.Query} instances.<br/>
 * <b>NOTE:</b> this initial {@link EntityManagerFactory} and {@link EntityManager} instances
 * <b>MUST NOT</b> be retained beyond named queries initialization.
 * Subsequent JPA operations during normal application request processing should happen via properly
 * scoped {@link EntityManager} instances obtained from the injected
 * {@link com.google.inject.Provider Provider}&lt;{@link EntityManager}&gt;.</p>
 */
public abstract class JpaServletContextListener extends GuiceServletContextListener {



	/**
	 * Returns the name of the main persistence unit in <code>persistence.xml</code> file.
	 */
	protected abstract String getMainPersistenceUnitName();

	EntityManagerFactory mainEntityManagerFactory;



	/**
	 * Executor associated with {@link #getMainPersistenceUnitName() the main persistence unit}.
	 * <p>
	 * All JPA operations on the main persistence unit should be dispatched to this executor to
	 * avoid blocking server threads when waiting for an available JDBC connection.</p>
	 * <p>
	 * ThreadPool size of this executor is determined by {@link #getMainJpaThreadPoolSize()}.</p>
	 * <p>
	 * A reference can be obtained by requesting injection of {@link ContextTrackingExecutor}.
	 * If the app uses multiple persistence units ({@link #isSinglePersistenceUnitApp()} is
	 * overridden to return {@code false}) then injection points must be annotated with
	 * {@link com.google.inject.name.Named @Named} similarly to the below:</p>
	 * <pre>
	 * &commat;Named(JpaServletContextListener.MAIN_PERSISTENCE_UNIT_BINDING_NAME)</pre>
	 */
	protected ContextTrackingExecutor mainJpaExecutor;

	/**
	 * Returns the size of the thread pool to be used by {@link #mainJpaExecutor}.
	 * <p>
	 * The value should be the same or slightly bigger than the size of the JDBC connection pool
	 * referenced in the definition of the persistence unit named
	 * {@link #getMainPersistenceUnitName()} in <code>persistence.xml</code> file.
	 * This way scheduled tasks will be obtaining JDBC connections very fast and the connection pool
	 * will be optimally utilized.</p>
	 */
	protected abstract int getMainJpaThreadPoolSize();

	/**
	 * Creates an executor to be associated with a persistence unit. Used to create
	 * {@link #mainJpaExecutor}. Subclasses may override this method to customize executor creation.
	 * By default returns
	 * <pre>
	 * servletModule.newContextTrackingExecutor(
	 *         persistenceUnitName + JPA_EXECUTOR_NAME_SUFFIX, poolSize);</pre>
	 */
	protected ContextTrackingExecutor createJpaExecutor(
			String persistenceUnitName, int poolSize) {
		return servletModule.newContextTrackingExecutor(
				persistenceUnitName + JPA_EXECUTOR_NAME_SUFFIX, poolSize);
	}

	/**
	 * Appended to persistence unit name to create associated executor name.
	 * @see #createJpaExecutor(String, int)
	 */
	public static final String JPA_EXECUTOR_NAME_SUFFIX = "JpaExecutor";



	/**
	 * Indicates whether this app uses only 1 persistence unit. By default <code>true</code>.
	 * <p>
	 * If <code>true</code> then {@link EntityManager} instances, {@link EntityManagerFactory} and
	 * its associated {@link #mainJpaExecutor} injections are bound without names, so that
	 * user-defined components don't need to annotate injected fields/params with
	 * <code>@Named(JpaServletContextListener.MAIN_PERSISTENCE_UNIT_BINDING_NAME)</code> when
	 * there's only 1 choice.</p>
	 * <p>
	 * Apps that use multiple persistence units should create a separate injection binding name
	 * constant and a separate {@link ContextTrackingExecutor} instance for each persistence
	 * unit. Each executor's threadPool size should correspond to the connection pool size of its
	 * persistence unit.<br/>
	 * After that, given persistence unit's {@link EntityManagerFactory}, executor and
	 * {@link EntityManager} should be bound with the corresponding constant as the value of
	 * {@link com.google.inject.name.Named @Named} in {@link #configureInjections()} similarly
	 * to the below:</p>
	 * <pre>
	 * &commat;Override
	 * protected boolean isSinglePersistenceUnitApp() { return false; }
	 *
	 * public static final String CHAT_LOG_NAME = "chatLogDb"; // same as in persistence.xml
	 * public static final int CHAT_LOG_POOL_SIZE = 10;
	 * EntityManagerFactory chatLogEntityManagerFactory;
	 * ContextTrackingExecutor chatLogJpaExecutor;
	 *
	 * &commat;Override
	 * protected LinkedList<Module> configureInjections() {
	 *     chatLogEntityManagerFactory = Persistence.createEntityManagerFactory(CHAT_LOG_NAME);
	 *     chatLogJpaExecutor = createJpaExecutor(CHAT_LOG_NAME, CHAT_LOG_POOL_SIZE);
	 *     var modules = new LinkedList<Module>();
	 *     modules.add((binder) -&gt; {
	 *         binder.bind(EntityManager.class)
	 *                 .annotatedWith(Names.named(CHAT_LOG_NAME))
	 *                 .toProvider(() -&gt; chatLogEntityManagerFactory.createEntityManager())
	 *                 .in(servletModule.containerCallScope);
	 *         binder.bind(EntityManagerFactory.class)
	 *                 .annotatedWith(Names.named(CHAT_LOG_NAME))
	 *                 .toInstance(chatLogEntityManagerFactory);
	 *         binder.bind(ContextTrackingExecutor.class)
	 *                 .annotatedWith(Names.named(CHAT_LOG_NAME))
	 *                 .toInstance(chatLogJpaExecutor);
	 *     });
	 *
	 *     // more modules here...
	 * }
	 *
	 * &commat;Override
	 * public void contextDestroyed(ServletContextEvent event) {
	 *     // user components shutdowns here...
	 *     super.contextDestroyed(event);
	 *     chatLogEntityManagerFactory.close();
	 * }</pre>
	 *
	 * @see <a href='https://github.com/morgwai/guiced-servlet-jpa/tree/master/sample-multi-jpa'>
	 *     example app with multiple persistence units.</a>
	 */
	protected boolean isSinglePersistenceUnitApp() { return true; }

	 // stores the result of isSinglePersistenceUnitApp() for JpaServlet to access
	static boolean singlePersistenceUnitApp;

	/**
	 * Injection binding name for {@link #getMainPersistenceUnitName() the main persistence unit}
	 * associated objects in apps that use multiple persistence units.
	 * <p>
	 * If the app uses a single persistence unit (default,
	 * {@link JpaServletContextListener#isSinglePersistenceUnitApp()} is not overridden and returns
	 * {@code true}) then this constant is never used.</p>
	 * <p>
	 * If {@link #isSinglePersistenceUnitApp()} returns <code>false</code>, then this constant is
	 * used as the value of {@link com.google.inject.name.Named} annotation for injection bindings
	 * of {@link EntityManagerFactory}, {@link #mainJpaExecutor} and {@link EntityManager}s
	 * associated with {@link #getMainPersistenceUnitName() the main persistence unit}.
	 */
	public static final String MAIN_PERSISTENCE_UNIT_BINDING_NAME =
			"pl.morgwai.base.servlet.guiced.jpa.mainPersistenceUnit";



	/**
	 * Creates injector with an additional module that binds injections of
	 * {@link EntityManagerFactory}, {@link #mainJpaExecutor} and {@link EntityManager}s of
	 * {@link #getMainPersistenceUnitName() the main persistence unit}.
	 */
	@Override
	protected Injector createInjector(LinkedList<Module> modules)  {
		singlePersistenceUnitApp = isSinglePersistenceUnitApp();
		mainEntityManagerFactory = Persistence.createEntityManagerFactory(
				getMainPersistenceUnitName());
		mainJpaExecutor = createJpaExecutor(
				getMainPersistenceUnitName(), getMainJpaThreadPoolSize());
		log.info("entity manager factory " + getMainPersistenceUnitName()
				+ " and its JPA executor created successfully");

		modules.add((binder) -> {
			if (singlePersistenceUnitApp) {
				binder.bind(EntityManager.class)
					.toProvider(() -> mainEntityManagerFactory.createEntityManager())
					.in(servletModule.containerCallScope);
				binder.bind(EntityManagerFactory.class)
					.toInstance(mainEntityManagerFactory);
				binder.bind(ContextTrackingExecutor.class)
					.toInstance(mainJpaExecutor);
			} else {
				binder.bind(EntityManager.class)
					.annotatedWith(Names.named(MAIN_PERSISTENCE_UNIT_BINDING_NAME))
					.toProvider(() -> mainEntityManagerFactory.createEntityManager())
					.in(servletModule.containerCallScope);
				binder.bind(EntityManagerFactory.class)
					.annotatedWith(Names.named(MAIN_PERSISTENCE_UNIT_BINDING_NAME))
					.toInstance(mainEntityManagerFactory);
				binder.bind(ContextTrackingExecutor.class)
					.annotatedWith(Names.named(MAIN_PERSISTENCE_UNIT_BINDING_NAME))
					.toInstance(mainJpaExecutor);
			}
		});

		return super.createInjector(modules);
	}



	/**
	 * Shuts down the main {@link EntityManagerFactory} and {@link #mainJpaExecutor}.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent event) {
		super.contextDestroyed(event);
		mainEntityManagerFactory.close();
		log.info("entity manager factory " + getMainPersistenceUnitName() + " shutdown completed");
	}
}
