// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.guiced_servlet_jpa.data_access;

import java.util.List;

import pl.morgwai.samples.guiced_servlet_jpa.domain.QueryRecord;



public interface QueryRecordDao {

	List<QueryRecord> findAll() throws DaoException;
	boolean update(QueryRecord record) throws DaoException;
	void persist(QueryRecord record) throws DaoException;
}
