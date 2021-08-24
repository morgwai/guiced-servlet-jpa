// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_jpa.data_access;

import java.util.List;

import javax.annotation.Nonnull;

import pl.morgwai.samples.servlet_jpa.domain.ChatLogEntry;



public interface ChatLogDao {

	List<ChatLogEntry> findAll() throws DaoException;
	void persist(@Nonnull ChatLogEntry logEntry) throws DaoException;
}
