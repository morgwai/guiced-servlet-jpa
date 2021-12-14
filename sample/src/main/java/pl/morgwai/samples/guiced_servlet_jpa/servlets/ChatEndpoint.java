// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.guiced_servlet_jpa.servlets;

import java.util.concurrent.Callable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.persistence.EntityManager;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.morgwai.base.servlet.guiced.jpa.JpaPingingServletContextListener;
import pl.morgwai.base.servlet.guiced.jpa.JpaServlet;
import pl.morgwai.base.servlet.scopes.ContextTrackingExecutor;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.ChatLogDao;
import pl.morgwai.samples.guiced_servlet_jpa.domain.ChatLogEntry;

import static pl.morgwai.samples.guiced_servlet_jpa.servlets.QueryRecordListServlet.appendFiltered;



/**
 * A simple "Chat over a WebSocket" endpoint that dispatches processing of incoming messages to
 * {@link #jpaExecutor the app wide executor associated with the persistence unit}, on which it logs
 * messages to the DB using {@link EntityManager} from injected {@link #entityManagerProvider
 * Provider} (from the same request-scoped binding as servlets).
 */
@ServerEndpoint(
		configurator = JpaPingingServletContextListener.JpaPingingEndpointConfigurator.class,
		value = ChatEndpoint.PATH)
public class ChatEndpoint {



	public static final String PATH = "/websocket/chat";

	static volatile boolean isShutdown = false;

	@Inject
	ChatLogDao dao;

	@Inject
	ContextTrackingExecutor jpaExecutor;

	@Inject
	Provider<EntityManager> entityManagerProvider;

	String nickname;
	Session connection;



	@OnOpen
	public void onOpen(Session connection) {
		this.connection = connection;
		connection.setMaxIdleTimeout(5l * 60l * 1000l);
		nickname = "user-" + connection.getId();
		connection.addMessageHandler(String.class, this::onMessage);
		synchronized (connection) {
			connection.getAsyncRemote().sendText(
					String.format("### assigned nickname: %s", nickname));
		}
		broadcast(String.format("### %s has joined", nickname));
	}



	void onMessage(String message) {
		var formattedMessageBuilder = new StringBuilder(nickname.length() + message.length() + 10)
				.append(nickname)
				.append(": ");
		appendFiltered(message, formattedMessageBuilder);
		jpaExecutor.execute(connection, () -> {
			try {
				executeWithinTx(
						() -> { dao.persist(new ChatLogEntry(nickname, message)); return null; });
				broadcast(formattedMessageBuilder.toString());
			} catch (Exception e) {
				log.warn("couldn't save message from " + connection.getId() + " into the DB", e);
				synchronized (connection) {
					connection.getAsyncRemote().sendText("### couldn't send message :(");
				}
			}
		});
	}

	protected <T> T executeWithinTx(Callable<T> operation) throws Exception {
		return JpaServlet.executeWithinTx(entityManagerProvider, operation);
	}



	@OnClose
	public void onClose() {
		broadcast(String.format("### %s has disconnected", nickname));
	}



	@OnError
	public void onError(Throwable error) {
		log.warn("error on connection " + connection.getId(), error);
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
		log.info("ChatEndpoint shutdown");
	}



	static final Logger log = LoggerFactory.getLogger(ChatEndpoint.class.getName());
}
