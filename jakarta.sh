#!/bin/bash
# Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
for file in pom.xml sample/pom.xml sample/src/main/resources/META-INF/persistence.xml; do
	sed -E -e 's#(\t*).*<!--jakarta:(.*)-->#\1\2#' \
		-e 's#(.*)javax(.*)<!--jakarta-->#\1jakarta\2#' \
		<"${file}" >"${file}.jakarta" &&
	mv "${file}.jakarta" "${file}" ;
done

for folder in src sample/src sample-multi-jpa/src; do
	find "${folder}" -name '*.java' | while read file; do
		if [ ! -L "${file}" ]; then
			sed -e 's#javax.servlet#jakarta.servlet#g' \
				-e 's#javax.websocket#jakarta.websocket#g' \
				-e 's#javax.persistence#jakarta.persistence#g' \
				-e 's#javax.inject#jakarta.inject#g' \
				<"${file}" >"${file}.jakarta" &&
			mv "${file}.jakarta" "${file}";
		fi;
	done;
done
