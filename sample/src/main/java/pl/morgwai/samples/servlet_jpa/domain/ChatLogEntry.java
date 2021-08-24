// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_jpa.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;



@Entity
public class ChatLogEntry implements Serializable {



	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long id;
	public static final String ID = "id";
	public Long getId() { return id; }

	String username;
	public static final String USERNAME = "username";
	public String getUsername() { return username; }

	String message;
	public static final String MESSAGE = "message";
	public String getMessage() { return message; }



	@Override
	public int hashCode() {
		return (id == null ? 0 : id.hashCode())
				+ 13 * (username == null ? 0 : username.hashCode()
				+ 31 * (message == null ? 0 : message.hashCode()));
	}



	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (other == null) return false;
		if (other.getClass() != ChatLogEntry.class) return false;
		ChatLogEntry otherEntry = (ChatLogEntry) other;
		return (id == null ? otherEntry.getId() == null : id.equals(otherEntry.getId()))
			&& (username == null ?
					otherEntry.getUsername() == null : username.equals(otherEntry.getUsername()))
			&& (message == null ?
					otherEntry.getMessage() == null : message.equals(otherEntry.getMessage()));
	}



	protected ChatLogEntry() {}

	public ChatLogEntry(String username, String message) {
		this.username = username;
		this.message = message;
	}

	public ChatLogEntry(Long id, String username, String message) {
		this.id = id;
		this.username = username;
		this.message = message;
	}

	static {
		// unit-test/deploy time check if there are no typos in field names
		try {
			ChatLogEntry.class.getDeclaredField(ID);
			ChatLogEntry.class.getDeclaredField(USERNAME);
			ChatLogEntry.class.getDeclaredField(MESSAGE);
		} catch (NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private static final long serialVersionUID = 2177348762809760267L;
}
