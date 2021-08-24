// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_jpa.data_access;



public class ExternalServiceFake implements ExternalService {



	@Override
	public String getLink(String query) {
		try {
			Thread.sleep(ExternalService.PROCESSING_TIME_SECONDS * 1000l);
		} catch (InterruptedException e) {}
		return "result for query '" + query + "'";
	}
}
