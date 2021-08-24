// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.guiced_servlet_jpa.data_access;



public class DaoException extends Exception {

	public DaoException() {}
	public DaoException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
	public DaoException(String message, Throwable cause) {
		super(message, cause);
	}
	public DaoException(String message) {
		super(message);
	}
	public DaoException(Throwable cause) {
		super(cause);
	}
	private static final long serialVersionUID = 4560907662141748051L;
}
