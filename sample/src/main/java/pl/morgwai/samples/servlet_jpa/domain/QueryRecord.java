// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_jpa.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;



@Entity
public class QueryRecord implements Serializable {



	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long id;
	public static final String ID = "id";
	public Long getId() { return id; }

	String query;
	public static final String QUERY = "query";
	public String getQuery() { return query; }

	String result;
	public static final String RESULT = "result";
	public String getResult() { return result; }
	public void setResult(String result) { this.result = result; }



	@Override
	public int hashCode() {
		return (id == null ? 0 : id.hashCode())
				+ 13 * (query == null ? 0 : query.hashCode()
				+ 31 * (result == null ? 0 : result.hashCode()));
	}



	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (other == null) return false;
		if (other.getClass() != QueryRecord.class) return false;
		QueryRecord otherRecord = (QueryRecord) other;
		return (id == null ? otherRecord.getId() == null : id.equals(otherRecord.getId()))
			&& (query == null ?
					otherRecord.getQuery() == null : query.equals(otherRecord.getQuery()))
			&& (result == null ?
					otherRecord.getResult() == null : result.equals(otherRecord.getResult()));
	}



	protected QueryRecord() {}

	public QueryRecord(String query) {
		this.query = query;
	}

	public QueryRecord(Long id, String query) {
		this.id = id;
		this.query = query;
	}

	public QueryRecord(Long id, String query, String result) {
		this.id = id;
		this.query = query;
		this.result = result;
	}

	static {
		// unit-test/deploy time check if there are not typos in field names
		try {
			QueryRecord.class.getDeclaredField(ID);
			QueryRecord.class.getDeclaredField(QUERY);
			QueryRecord.class.getDeclaredField(RESULT);
		} catch (NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private static final long serialVersionUID = -2214563822217485954L;
}
