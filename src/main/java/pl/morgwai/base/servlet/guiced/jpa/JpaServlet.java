// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.jpa;

import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.RollbackException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import com.google.inject.Key;
import com.google.inject.name.Names;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.base.servlet.scopes.RequestContext;

import static pl.morgwai.base.servlet.guiced.jpa.JpaServletContextListener.*;



/**
 * Base class for servlets that perform other type(s) of time consuming operations apart from JPA.
 * Requests injection of an {@link Provider}&lt;{@link EntityManager}&gt; and provides some related
 * helper methods: ({@link #performInTx(Callable)}, {@link #removeEntityManagerFromRequestScope()}).
 *
 * @see SimpleAsyncJpaServlet
 */
@SuppressWarnings("serial")
public abstract class JpaServlet extends HttpServlet {



	/**
	 * Provides request scoped {@link EntityManager} instances obtained from a persistence
	 * unit determined by {@link #getPersistenceUnitBindingName()}.
	 */
	protected Provider<EntityManager> entityManagerProvider;

	/**
	 * JPA executor associated with {@link #entityManagerProvider}.
	 */
	protected ContextTrackingExecutor jpaExecutor;

	/**
	 * @return binding name of the persistence unit to be used by {@link #entityManagerProvider}.
	 * <br/>
	 * Defaults to {@link JpaServletContextListener#MAIN_PERSISTENCE_UNIT_BINDING_NAME}. If the app
	 * uses multiple persistence units
	 * ({@link JpaServletContextListener#isSinglePersistenceUnitApp()} returns {@code false}) and
	 * subclass wants to use other than the default, then it should override this method.
	 */
	protected String getPersistenceUnitBindingName() {
		return MAIN_PERSISTENCE_UNIT_BINDING_NAME;
	}



	@Override
	public void init(ServletConfig config) throws ServletException {
		if (singlePersistenceUnitApp) {
			entityManagerProvider = INJECTOR.getProvider(EntityManager.class);
			jpaExecutor = INJECTOR.getInstance(ContextTrackingExecutor.class);
		} else {
			final var bindingName =  Names.named(getPersistenceUnitBindingName());
			entityManagerProvider = INJECTOR.getProvider(Key.get(EntityManager.class, bindingName));
			jpaExecutor = INJECTOR.getInstance(Key.get(ContextTrackingExecutor.class, bindingName));
		}
	}



	/**
	 * Performs <code>operation</code> in a DB transaction obtained from
	 * {@link #entityManagerProvider}.
	 */
	protected <T> T performInTx(Callable<T> operation) throws Exception {
		return performInTx(entityManagerProvider, operation);
	}



	/**
	 * Performs <code>operation</code> in a DB transaction obtained from
	 * <code>entityManagerProvider</code>.
	 */
	public static <T> T performInTx(
			Provider<EntityManager> entityManagerProvider, Callable<T> operation) throws Exception {
		EntityTransaction tx = entityManagerProvider.get().getTransaction();
		if ( ! tx.isActive()) tx.begin();
		try {
			T result = operation.call();
			if (tx.getRollbackOnly()) throw new RollbackException("tx marked rollbackOnly");
			tx.commit();
			return result;
		} catch (Throwable e) {
			if (tx.isActive()) tx.rollback();
			throw e;
		}
	}



	/**
	 * Removes associated {@link EntityManager} from the current request's scope.
	 * <p>
	 * If your app performs some time consuming operations (such as network communication
	 * long CPU/GPU intensive computations etc), that need to be surrounded by JPA operations that
	 * do <b>not</b> need to be a part of the same transaction, like for example:
	 * <pre>
	 * 	List someRecords = someDbDao.getSomeDataFromDB(); // TX-1 or no TX
	 * 	SomeClass results = someExternaNetworkConnector.someLongOperation(someRecords);
	 * 	someDbDao.storeSomeStatsAboutResults(results); // TX-2
	 * </pre>
	 * then it may significantly improve your app's performance to close {@link EntityManager}
	 * after the first (batch of) JPA operation(s). In such case, for the second (batch of) JPA
	 * operation(s) a new {@link EntityManager} needs to be obtained, so, to prevent
	 * request-scoped {@link #entityManagerProvider} from reusing the old (closed one), it needs to
	 * be removed from the scope.</p>
	 * <p>
	 * <b>NOTE:</b> this method is safe only if a given request is processed by a single thread
	 * (which is the most common case).</p>
	 * <p>
	 * Note: most apps don't have such complex processing: make sure you really need to
	 * use this method before doing so.</p>
	 */
	public void removeEntityManagerFromRequestScope() {
		final var entityManagerBindingKey = singlePersistenceUnitApp
				? Key.get(EntityManager.class)
				: Key.get(EntityManager.class, Names.named(getPersistenceUnitBindingName()));
		requestContextTracker.getCurrentContext().removeAttribute(entityManagerBindingKey);
	}

	@Inject
	protected ContextTracker<RequestContext> requestContextTracker;
}
