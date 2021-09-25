// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.jpa;

import java.lang.reflect.InvocationHandler;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;

import pl.morgwai.base.servlet.guiced.utils.EndpointPingerDecorator;
import pl.morgwai.base.servlet.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * A {@link JpaServletContextListener} that automatically registers/deregisters endpoint instances
 * to a {@link WebsocketPingerService}. Endpoints need to be created with
 * {@link #addEndpoint(Class, String) addEndpoint(Class, String)} or annotated to use
 * {@link SimplePingingEndpointConfigurator}.
 * <p>
 * <b>NOTE:</b> This listener creates only 1 instance of {@link WebsocketPingerService} and
 * registers all endpoints to it. In case of a huge number of websocket connections, it may be
 * insufficient. A more complex strategy that creates more pingers should be implemented in such
 * case.</p>
 */
public abstract class SimplePingingEndpointJpaServletContextListener
		extends JpaServletContextListener {



	final WebsocketPingerService pingerService =
			new WebsocketPingerService(getPingIntervalSeconds(), getMaxMalformedPongCount());

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
	 * Stops the associated {@link WebsocketPingerService}.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent destructionEvent) {
		pingerService.stop();
		super.contextDestroyed(destructionEvent);
	}



	/**
	 * Adds an endpoint using a {@link SimplePingingEndpointConfigurator}.
	 */
	@Override
	protected void addEndpoint(Class<?> endpointClass, String path) throws ServletException {
		super.addEndpoint(endpointClass, path, new SimplePingingEndpointConfigurator());
	}



	/**
	 * Automatically registers and deregisters created endpoints to the
	 * {@link WebsocketPingerService} of the {@link SimplePingingEndpointJpaServletContextListener}.
	 */
	public class SimplePingingEndpointConfigurator extends GuiceServerEndpointConfigurator {

		@Override
		protected InvocationHandler getAdditionalDecorator(Object endpoint) {
			return new EndpointPingerDecorator(endpoint, pingerService);
		}
	}
}
