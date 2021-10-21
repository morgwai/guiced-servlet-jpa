#!/bin/bash
# Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
sed -E -e 's#(\t*).*<!--jakarta:(.*)-->#\1\2#' \
	-e 's#(.*)javax(.*)<!--jakarta-->#\1jakarta-experimental\2#' \
	<pom.xml >pom.jakarta.xml &&
mv pom.jakarta.xml pom.xml &&

sed -E -e 's#(\t*).*<!--jakarta:(.*)-->#\1\2#' \
	-e 's#(.*)javax(.*)<!--jakarta-->#\1jakarta\2#' \
	<src/main/resources/META-INF/persistence.xml >persistence.jakarta.xml &&
mv persistence.jakarta.xml src/main/resources/META-INF/persistence.xml &&

find src -name '*.java' | while read file; do
	if [ ! -L "${file}" ]; then
		sed -e 's#javax.servlet#jakarta.servlet#g' \
			-e 's#javax.websocket#jakarta.websocket#g' \
			-e 's#javax.persistence#jakarta.persistence#g' \
			-e 's#javax.inject#jakarta.inject#g' \
			<"${file}" >"${file}.jakarta" &&
		mv "${file}.jakarta" "${file}";
	fi;
done
