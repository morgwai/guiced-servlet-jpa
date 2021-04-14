/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.servlet_jpa.servlets;

import java.lang.reflect.InvocationHandler;

import pl.morgwai.base.servlet.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.websocket.pinger.PingerDecorator;
import pl.morgwai.base.servlet.websocket.pinger.WebsocketPinger;



/**
 * Automatically adds websocket endpoints to pinger.<br/>
 * Note: this Configurator has only 1 pinger which may be not sufficient if there is a massive
 * number of concurrent connections. In such case several pingers should be created and load
 * distributed among them.
 */
public class PingingGuiceEndpointConfigurator extends GuiceServerEndpointConfigurator {



	static WebsocketPinger pinger = new WebsocketPinger();



	@Override
	protected <T> InvocationHandler getEndpointInvocationHandler(T endpoint) {
		return new PingerDecorator<T>(endpoint, pinger);
	}



	public static void stopPinger() {
		pinger.stop();
	}
}
