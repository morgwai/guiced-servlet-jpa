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



	final WebsocketPingerService pingerService = new WebsocketPingerService(
			getPingIntervalSeconds(), getMaxMalformedPongCount(), shouldSynchronizePingSending());

	/**
	 * Allows subclasses to override ping interval.
	 */
	protected int getPingIntervalSeconds() { return WebsocketPingerService.DEFAULT_PING_INTERVAL; }

	/**
	 * Allows subclasses to override maximum allowed malformed pongs.
	 */
	protected int getMaxMalformedPongCount() {
		return WebsocketPingerService.DEFAULT_MAX_MALFORMED_PONG_COUNT;
	}

	/**
	 * Allows subclasses to override <code>synchronizePingSending</code> flag.
	 */
	protected boolean shouldSynchronizePingSending() {
		return false;
	}



	public JpaPingingServletContextListener() {
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
