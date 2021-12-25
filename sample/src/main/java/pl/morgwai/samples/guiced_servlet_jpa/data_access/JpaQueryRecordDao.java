// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.guiced_servlet_jpa.data_access;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import pl.morgwai.samples.guiced_servlet_jpa.domain.QueryRecord;



public class JpaQueryRecordDao implements QueryRecordDao {



	Provider<EntityManager> entityManagerProvider;



	@Inject
	public JpaQueryRecordDao(
		EntityManagerFactory persistenceUnit,
		Provider<EntityManager> entityManagerProvider
	) {
		this.entityManagerProvider = entityManagerProvider;

		// create named queries
		EntityManager initialEntityManager = persistenceUnit.createEntityManager();
		persistenceUnit.addNamedQuery(
				FIND_ALL_QUERY_NAME, initialEntityManager.createQuery(FIND_ALL_QUERY));
		persistenceUnit.addNamedQuery(
				UPDATE_QUERY_NAME, initialEntityManager.createQuery(UPDATE_QUERY));
		initialEntityManager.close();
	}



	static final String FIND_ALL_QUERY_NAME = JpaQueryRecordDao.class.getName() + ".findAll";
	static final String FIND_ALL_QUERY = "select r from "
			+ QueryRecord.class.getSimpleName() + " r";

	@Override
	public List<QueryRecord> findAll() throws DaoException {
		try {
			return entityManagerProvider.get()
					.createNamedQuery(FIND_ALL_QUERY_NAME, QueryRecord.class).getResultList();
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}



	static final String UPDATE_QUERY_NAME = JpaQueryRecordDao.class.getName() + ".update";
	static final String UPDATE_QUERY = "update " + QueryRecord.class.getSimpleName() + " r"
			+ " set " + QueryRecord.QUERY + " = :" + QueryRecord.QUERY
			+ ", " + QueryRecord.RESULT + " = :" + QueryRecord.RESULT
			+ " where r." + QueryRecord.ID + " = :" + QueryRecord.ID;

	@Override
	public boolean update(QueryRecord record) throws DaoException {
		// merge will re-add a record if it was deleted in the mean time which is not what we want
		try {
			EntityManager entityManager = entityManagerProvider.get();
			if (entityManager.contains(record)) return true;  // will be updated automatically
			int updatedCount = entityManager.createNamedQuery(UPDATE_QUERY_NAME)
				.setParameter(QueryRecord.ID, record.getId())
				.setParameter(QueryRecord.QUERY, record.getQuery())
				.setParameter(QueryRecord.RESULT, record.getResult())
				.executeUpdate();
			return updatedCount > 0;
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}



	@Override
	public void persist(QueryRecord record) throws DaoException {
		try {
			entityManagerProvider.get().persist(record);
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}
}
