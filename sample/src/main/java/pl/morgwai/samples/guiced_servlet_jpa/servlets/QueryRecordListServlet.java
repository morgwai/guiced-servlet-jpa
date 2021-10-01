// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.guiced_servlet_jpa.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pl.morgwai.base.servlet.guiced.jpa.JpaServletContextListener;
import pl.morgwai.base.servlet.guiced.jpa.SimpleAsyncJpaServlet;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.DaoException;
import pl.morgwai.samples.guiced_servlet_jpa.data_access.QueryRecordDao;
import pl.morgwai.samples.guiced_servlet_jpa.domain.QueryRecord;



/**
 * A simple <i>"get data from the DB and put it in the response"</i> case servlet extending
 * {@link SimpleAsyncJpaServlet}. Here in a form of a crappy looking, early '90s style HTML page:
 * please forgive my (lack of) frontend skills ;-]<br/>
 * {@link #doGet(HttpServletRequest, HttpServletResponse) doGet(...)} is executed by a thread from
 * the pool of {@link JpaServletContextListener#mainJpaExecutor app wide executor associated with
 * persistence unit's JDBC connection pool}.
 * This way the total number of server's threads can remain constant and small (<font size='-2'>
 * number of CPU cores available to the process for the main request processing pool + size of the
 * JDBC connection pool for the persistence unit associated executor pool + some constant epsilon
 * for servlet container internals</font>), regardless of the number of concurrent requests, while
 * providing optimal performance.
 */
@SuppressWarnings("serial")
public class QueryRecordListServlet extends SimpleAsyncJpaServlet {



	public static final String URI = "queryList";

	@Inject
	QueryRecordDao dao;



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			List<QueryRecord> records = dao.findAll();
			streamResults(records, response);
		} catch (DaoException e) {
			throw new ServletException(e);
		}
	}



	static void streamResults(List<QueryRecord> records, HttpServletResponse response)
			throws IOException {
		response.setStatus(HttpServletResponse.SC_OK);
		PrintWriter writer = response.getWriter();
		writer.println("<!DOCTYPE html>");
		writer.println("<html lang='en' ><head><meta charset='utf-8' />"
				+ "<title>query record app</title></head><body><table border='1' >");
		writer.println("<tr><th>id</th><th>" + QueryRecord.QUERY + "</th><th>" + QueryRecord.RESULT
				+ "</th><th>actions</th></tr>");
		writer.flush();  // force chunked encoding
		for (QueryRecord record: records) {
			StringBuilder recordRowBuilder = new StringBuilder(500);
			recordRowBuilder.append("<tr><form action='").append(SaveQueryServlet.URI)
				.append("' method='POST' ><td><input type='hidden' name='").append(QueryRecord.ID)
				.append("' value='").append(record.getId()).append("' />").append(record.getId())
				.append("</td><td><input type='text' name='").append(QueryRecord.QUERY)
				.append("' value='");
			appendFiltered(record.getQuery(), recordRowBuilder);
			recordRowBuilder.append("'/></td><td>");
			appendFiltered(record.getResult(), recordRowBuilder);
			recordRowBuilder.append("</td><td><input type='submit' value='save' />"
					+ "</td></form></tr>");
			writer.println(recordRowBuilder.toString());
		}
		writer.println("<tr><form action='" + SaveQueryServlet.URI + "' method='POST' >"
			+ "<td>new</td><td><input type='text' name='" + QueryRecord.QUERY
			+ "' /></td><td>-</td><td><input type='submit' value='save' /></td></form></tr>");
		writer.println("</table></body></html>");
		writer.close();
	}



	// Adapted from
	// github.com/apache/tomcat/blob/trunk/webapps/examples/WEB-INF/classes/util/HTMLFilter.java
	public static void appendFiltered(String message, StringBuilder target) {
		if (message == null) return;
		char content[] = new char[message.length()];
		message.getChars(0, message.length(), content, 0);
		for (int i = 0; i < content.length; i++) {
			switch (content[i]) {
				case '<':
					target.append("&lt;");
					break;
				case '>':
					target.append("&gt;");
					break;
				case '&':
					target.append("&amp;");
					break;
				case '"':
					target.append("&quot;");
					break;
				case '\'':
					target.append("&apos;");
					break;
				default:
					target.append(content[i]);
			}
		}
	}
}
