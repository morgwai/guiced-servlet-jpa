// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.jpa;

import jakarta.servlet.ServletContextEvent;

import pl.morgwai.base.servlet.guiced.utils.PingingEndpointConfigurator;
import pl.morgwai.base.servlet.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * A {@link JpaServletContextListener} that automatically registers/deregisters endpoint instances
 * to a {@link WebsocketPingerService}. Endpoints need to be created with
 * {@link #addEndpoint(Class, String) addEndpoint(Class, String)} or annotated to use
 * {@link PingingEndpointConfigurator}.
 * <p>
 * This class is almost the same as
 * {@link pl.morgwai.base.servlet.guiced.utils.PingingServletContextListener}, the only difference
 * is that it extends {@link JpaServletContextListener}.</p>
 */
public abstract class JpaPingingServletContextListener extends JpaServletContextListener {



	final WebsocketPingerService pingerService;

	/**
	 * Allows subclasses to override pinger mode. By default {@code false}.
	 */
	protected boolean isPingerInKeepAliveOnlyMode() { return false; }

	/**
	 * Allows subclasses to override ping interval.
	 * By default {@link WebsocketPingerService#DEFAULT_INTERVAL}.
	 */
	protected int getPingIntervalSeconds() { return WebsocketPingerService.DEFAULT_INTERVAL; }

	/**
	 * Allows subclasses to override ping failure limit.
	 * By default {@link WebsocketPingerService#DEFAULT_FAILURE_LIMIT}.
	 */
	protected int getPingFailureLimit() { return WebsocketPingerService.DEFAULT_FAILURE_LIMIT; }

	/**
	 * Allows subclasses to override ping data size.
	 * By default {@link WebsocketPingerService#DEFAULT_PING_SIZE}.
	 */
	protected int getPingSize() { return WebsocketPingerService.DEFAULT_PING_SIZE; }

	/**
	 * Allows subclasses to override {@code synchronizeSending} flag. By default {@code false}.
	 */
	protected boolean shouldSynchronizePingSending() { return false; }



	public JpaPingingServletContextListener() {
		if (isPingerInKeepAliveOnlyMode()) {
			pingerService = new WebsocketPingerService(
					getPingIntervalSeconds(), shouldSynchronizePingSending());
		} else {
			pingerService = new WebsocketPingerService(
					getPingIntervalSeconds(),
					getPingFailureLimit(),
					getPingSize(),
					shouldSynchronizePingSending()
			);
		}
		PingingEndpointConfigurator.setPingerService(pingerService);
	}



	/**
	 * Overrides default configurator used by {@link #addEndpoint(Class, String)} to be a
	 * {@link PingingEndpointConfigurator}.
	 */
	@Override
	protected GuiceServerEndpointConfigurator createEndpointConfigurator() {
		return new PingingEndpointConfigurator();
	}



	/**
	 * Stops the associated {@link WebsocketPingerService}.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent destructionEvent) {
		pingerService.stop();
		super.contextDestroyed(destructionEvent);
	}
}
