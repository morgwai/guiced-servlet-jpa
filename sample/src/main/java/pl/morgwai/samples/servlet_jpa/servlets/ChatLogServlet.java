/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.servlet_jpa.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pl.morgwai.base.servlet.jpa.JpaServletContextListener;
import pl.morgwai.base.servlet.jpa.SimpleAsyncJpaServlet;
import pl.morgwai.samples.servlet_jpa.data_access.ChatLogDao;
import pl.morgwai.samples.servlet_jpa.data_access.DaoException;
import pl.morgwai.samples.servlet_jpa.domain.ChatLogEntry;



/**
 * A simple <i>"get data from the DB and put it in the response"</i> case servlet extending
 * {@link SimpleAsyncJpaServlet}. Here in a form of a crappy looking, early '90s style HTML page:
 * please forgive my (lack of) frontend skills ;-]<br/>
 * {@link #doGet(HttpServletRequest, HttpServletResponse) doGet(...)} is executed by a thread from
 * the pool of {@link JpaServletContextListener#jpaExecutor app wide executor associated with
 * persistence unit's JDBC connection pool}.
 * This way the total number of server's threads can remain constant and small (<font size='-2'>
 * number of CPU cores available to the process for the main request processing pool + size of the
 * JDBC connection pool for the persistence unit associated executor pool + some constant epsilon
 * for servlet container internals</font>), regardless of the number of concurrent requests, while
 * providing optimal performance.
 */
@SuppressWarnings("serial")
public class ChatLogServlet extends SimpleAsyncJpaServlet {



	public static final String URI = "chatLog";

	@Inject
	ChatLogDao dao;



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			List<ChatLogEntry> log = dao.findAll();
			streamResults(log, response);
		} catch (DaoException e) {
			throw new ServletException(e);
		}
	}



	static void streamResults(List<ChatLogEntry> log, HttpServletResponse response)
			throws IOException {
		response.setStatus(HttpServletResponse.SC_OK);
		PrintWriter writer = response.getWriter();
		writer.println("<!DOCTYPE html>");
		writer.println("<html lang='en' ><head><meta charset='utf-8' />"
				+ "<title>chat log</title></head><body><table border='1' >");
		writer.println("<tr><th>id</th><th>" + ChatLogEntry.USERNAME + "</th><th>"
				+ ChatLogEntry.MESSAGE + "</th></tr>");
		writer.flush();  // force chunked encoding
		for (ChatLogEntry logEntry: log) {
			StringBuilder recordRowBuilder = new StringBuilder(500);
			recordRowBuilder.append("<tr><td>").append(logEntry.getId()).append("</td><td>");
			QueryRecordListServlet.appendFiltered(logEntry.getUsername(), recordRowBuilder);
			recordRowBuilder.append("</td><td>");
			QueryRecordListServlet.appendFiltered(logEntry.getMessage(), recordRowBuilder);
			recordRowBuilder.append("</td></tr>");
			writer.println(recordRowBuilder.toString());
		}
		writer.println("</table></body></html>");
		writer.close();
	}
}
