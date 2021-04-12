/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.servlet_jpa.data_access;

import java.util.List;

import javax.annotation.Nonnull;

import pl.morgwai.samples.servlet_jpa.domain.QueryRecord;



public interface QueryRecordDao {

	List<QueryRecord> findAll() throws DaoException;
	boolean update(@Nonnull QueryRecord record) throws DaoException;
	void persist(@Nonnull QueryRecord record) throws DaoException;
}
