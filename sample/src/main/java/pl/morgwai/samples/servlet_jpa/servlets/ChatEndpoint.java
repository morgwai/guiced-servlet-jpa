// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_jpa.servlets;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.base.servlet.jpa.JpaServlet;
import pl.morgwai.samples.servlet_jpa.data_access.ChatLogDao;
import pl.morgwai.samples.servlet_jpa.domain.ChatLogEntry;



/**
 * A simple "Chat over a WebSocket" endpoint that dispatches processing of incoming messages to
 * {@link #jpaExecutor the app wide executor associated with the persistence unit}, on which it logs
 * messages to the DB using <code>EntityManager</code> from injected {@link #entityManagerProvider
 * Provider} (from the same request-scoped binding as servlets).
 */
public class ChatEndpoint extends Endpoint {



	public static final String PATH = "/websocket/chat";



	static boolean isShutdown = false;

	@Inject ChatLogDao dao;
	@Inject ContextTrackingExecutor jpaExecutor;
	@Inject Provider<EntityManager> entityManagerProvider;

	String nickname;
	Session connection;



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		this.connection = connection;
		connection.setMaxIdleTimeout(5l * 60l * 1000l);
		nickname = "user-" + connection.getId();
		connection.addMessageHandler(String.class, (message) -> this.onMessage(message));
		synchronized (connection) {
			connection.getAsyncRemote().sendText(String.format(
					"### assigned nickname: %s", nickname));
		}
		broadcast(String.format("### %s has joined", nickname));
	}



	public void onMessage(String message) {
		StringBuilder formattedMessageBuilder =
			new StringBuilder(nickname.length() + message.length() + 10);
		formattedMessageBuilder.append(nickname).append(": ");
		QueryRecordListServlet.appendFiltered(message, formattedMessageBuilder);
		jpaExecutor.execute(() -> {
			try {
				performInTx(() -> {dao.persist(new ChatLogEntry(nickname, message)); return null;});
				broadcast(formattedMessageBuilder.toString());
			} catch (Exception e) {
				log.warning("couldn't save message from " + connection.getId() + " into the DB: "
						+ e);
				synchronized (connection) {
					connection.getAsyncRemote().sendText("### couldn't send message :(");
				}
			}
		});
	}

	protected <T> T performInTx(Callable<T> operation) throws Exception {
		return JpaServlet.performInTx(entityManagerProvider, operation);
	}



	@Override
	public void onClose(Session connection, CloseReason reason) {
		broadcast(String.format("### %s has disconnected", nickname));
	}



	@Override
	public void onError(Session connection, Throwable error) {
		log.warning("error on connection " + connection.getId() + ": " + error);
		error.printStackTrace();
	}



	void broadcast(String msg) {
		if (isShutdown) return;
		for (Session peerConnection: connection.getOpenSessions()) {
			if (peerConnection.isOpen()) {
				synchronized (peerConnection) {
					peerConnection.getAsyncRemote().sendText(msg);
				}
			}
		}
	}



	static void shutdown() {
		isShutdown = true;
		log.info("ChatEndpoint shutdown completed");
	}



	static final Logger log = Logger.getLogger(ChatEndpoint.class.getName());
}
