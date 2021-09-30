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
import pl.morgwai.base.servlet.scopes.ContextTrackingExecutor;
import pl.morgwai.base.servlet.scopes.RequestContext;

import static pl.morgwai.base.servlet.guiced.jpa.JpaServletContextListener.*;



/**
 * Base class for servlets that perform other types of time consuming operations apart from JPA.
 * Requests injection of a {@link Provider}&lt;{@link EntityManager}&gt;, its associated
 * {@link #jpaExecutor} and provides some related helper methods:
 * {@link #executeWithinTx(Callable)}, {@link #removeEntityManagerFromRequestScope()}.
 *
 * @see SimpleAsyncJpaServlet
 */
@SuppressWarnings("serial")
public abstract class JpaServlet extends HttpServlet {



	/**
	 * Provides request scoped {@link EntityManager} instances.
	 * <p>
	 * If a given app uses multiple persistence units, this provider will use the one indicated by
	 * {@link #getPersistenceUnitBindingName()}.</p>
	 */
	protected Provider<EntityManager> entityManagerProvider;

	/**
	 * Executor associated with {@link #entityManagerProvider}'s persistence unit.
	 * <p>
	 * In apps that use a single persistence unit, this is the same instance as
	 * {@link JpaServletContextListener#jpaExecutor}. Otherwise, the one indicated by
	 * {@link #getPersistenceUnitBindingName()}.</p>
	 */
	protected ContextTrackingExecutor jpaExecutor;

	/**
	 * Returns injection binding name for {@link #entityManagerProvider} and {@link #jpaExecutor}
	 * in apps that use multiple persistence units.
	 * <p>
	 * If the app uses a single persistence unit (default,
	 * {@link JpaServletContextListener#isSinglePersistenceUnitApp()} is not overridden and returns
	 * {@code true}) then this method is never used.</p>
	 * <p>
	 * By default returns {@link JpaServletContextListener#MAIN_PERSISTENCE_UNIT_BINDING_NAME}.<br/>
	 * If an app that uses multiple persistence units needs to use persistence unit other than the
	 * main in some servlet, then this method should be overridden accordingly in that servlet.</p>
	 */
	protected String getPersistenceUnitBindingName() {
		return MAIN_PERSISTENCE_UNIT_BINDING_NAME;
	}



	/**
	 * Requests instances of {@link #entityManagerProvider} and {@link #jpaExecutor} from Guice.
	 */
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
	 * Executes <code>operation</code> within the DB transaction obtained from
	 * {@link #entityManagerProvider}. If {@code operation} completes normally, commits the
	 * transaction. Otherwise the transaction is rolled back.
	 */
	protected <T> T executeWithinTx(Callable<T> operation) throws Exception {
		return executeWithinTx(entityManagerProvider, operation);
	}



	/**
	 * Executes <code>operation</code> within the DB transaction obtained from
	 * <code>entityManagerProvider</code>. If {@code operation} completes normally, commits the
	 * transaction. Otherwise the transaction is rolled back.
	 */
	public static <T> T executeWithinTx(
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
	 * Removes the stored {@link EntityManager} from the scope of the current request.
	 * <p>
	 * If a given app performs some time consuming operations (such as network communication
	 * long CPU/GPU intensive computations etc), that need to be surrounded by JPA operations that
	 * do <b>not</b> need to be a part of the same transaction, like for example:</p>
	 * <pre>
	 * List someRecords = someDbDao.getSomeDataFromDB(); // TX-1 or no TX
	 * SomeClass results = someExternaNetworkConnector.someLongOperation(someRecords);
	 * someDbDao.storeSomeStatsAboutResults(results); // TX-2</pre>
	 * <p>
	 * then it may significantly improve performance to close the {@link EntityManager} after the
	 * first (batch of) JPA operation(s) so that other requests may use the underlying connection in
	 * the mean time.<br/>
	 * In such case, for the second (batch of) JPA operation(s) a new {@link EntityManager} must be
	 * obtained. To prevent request-scoped {@link #entityManagerProvider} from reusing the old
	 * closed one, it needs to be removed from the scope.</p>
	 * <p>
	 * <b>Note:</b> If a request is handled by multiple threads, care must be taken to prevent
	 * some of them from retaining the old stale instance.</p>
	 */
	public void removeEntityManagerFromRequestScope() {
		final var entityManagerBindingKey = singlePersistenceUnitApp
				? Key.get(EntityManager.class)
				: Key.get(EntityManager.class, Names.named(getPersistenceUnitBindingName()));
		requestContextTracker.getCurrentContext().removeAttribute(entityManagerBindingKey);
	}

	@Inject protected ContextTracker<RequestContext> requestContextTracker;
}
