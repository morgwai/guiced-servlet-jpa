// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.guiced_servlet_jpa.data_access;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import com.google.inject.name.Named;

import pl.morgwai.samples.guiced_servlet_jpa.domain.ChatLogEntry;

import static pl.morgwai.samples.guiced_servlet_jpa.servlets.ServletContextListener.CHAT_LOG_NAME;



public class JpaChatLogDao implements ChatLogDao {



	Provider<EntityManager> entityManagerProvider;



	@Inject
	public JpaChatLogDao(
		@Named(CHAT_LOG_NAME) EntityManagerFactory persistenceUnit,
		@Named(CHAT_LOG_NAME) Provider<EntityManager> entityManagerProvider
	) {
		this.entityManagerProvider = entityManagerProvider;

		// create named queries
		EntityManager initialEntityManager = persistenceUnit.createEntityManager();
		persistenceUnit.addNamedQuery(
				FIND_ALL_QUERY_NAME, initialEntityManager.createQuery(FIND_ALL_QUERY));
		initialEntityManager.close();
	}



	static final String FIND_ALL_QUERY_NAME = JpaChatLogDao.class.getName() + ".findAll";
	static final String FIND_ALL_QUERY = "select r from "
			+ ChatLogEntry.class.getSimpleName() + " r";

	@Override
	public List<ChatLogEntry> findAll() throws DaoException {
		try {
			return entityManagerProvider.get()
					.createNamedQuery(FIND_ALL_QUERY_NAME, ChatLogEntry.class).getResultList();
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}



	@Override
	public void persist(ChatLogEntry logEntry) throws DaoException {
		try {
			entityManagerProvider.get().persist(logEntry);
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}
}
