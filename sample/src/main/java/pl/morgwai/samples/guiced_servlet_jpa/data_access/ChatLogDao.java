// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.guiced_servlet_jpa.data_access;

import java.util.List;

import pl.morgwai.samples.guiced_servlet_jpa.domain.ChatLogEntry;



public interface ChatLogDao {

	List<ChatLogEntry> findAll() throws DaoException;
	void persist(ChatLogEntry logEntry) throws DaoException;
}
