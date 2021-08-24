# Sample apps for guiced-servlet-jpa library

2 simple web-apps demonstrating integration of [guiced servlets](https://github.com/morgwai/servlet-scopes) with JPA and other slow resources using guiced-servlet-jpa library.<br/>
The servlet app allows to store into DB queries to some fake external network resource, performs the given queries and updates records with the results.<br/>
The websocket app is a simple chat over a websocket that also logs all messages into a DB
(based on [Tomcat example app](https://github.com/apache/tomcat/blob/trunk/webapps/examples/websocket/chat.xhtml) and adapted to use this lib).



## MAIN CLASSES

### [QueryRecordListServlet](src/main/java/pl/morgwai/samples/guiced_servlet_jpa/servlets/QueryRecordListServlet.java) and [ChatLogServlet](src/main/java/pl/morgwai/samples/guiced_servlet_jpa/servlets/ChatLogServlet.java)

Simple <i>"get data from the DB and put it in a response"</i> case servlets extending [SimpleAsyncJpaServlet](../src/main/java/pl/morgwai/base/servlet/guiced/jpa/SimpleAsyncJpaServlet.java).


### [SaveQueryServlet](src/main/java/pl/morgwai/samples/guiced_servlet_jpa/servlets/SaveQueryServlet.java)

A servlet extending [JpaServlet](../src/main/java/pl/morgwai/base/servlet/guiced/jpa/JpaServlet.java) that communicates with multiple slow resources that provide synchronous API only (DB via JPA and some external slow service). Dispatches slow operations to injected app wide [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java) instances dedicated to each resource. This avoids suspending threads from the server's main pool, while also passes context to threads performing the slow operations and thus preserves request/session scoped objects (`EntityManager` in this case).


### [ChatEndpoint](src/main/java/pl/morgwai/samples/guiced_servlet_jpa/servlets/ChatEndpoint.java)

A simple <i>"Chat over a websocket"</i> endpoint that dispatches processing of incoming messages to the app wide executor associated with the persistence unit, on which it logs messages to the DB using `EntityManager` from injected Provider (from the same request-scoped binding as servlets).


### [ServletContextListener](src/main/java/pl/morgwai/samples/guiced_servlet_jpa/servlets/ServletContextListener.java)

Creates/configures app wide [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java) instances
associated with the external service and JPA, configures injections, adds servlets and endpoints.



## BUILDING & CONFIGURING FOR DEPLOYMENT

1. Java 11 is required to build the app (newer versions will probably work too).
1. The app requires an H2 dialect JDBC datasource/connection pool named `jdbc/queryRecordDataSource` to be configured on the server. You can change the dialect in [persistence.xml file](src/main/resources/META-INF/persistence.xml).
1. Configure DB operation executor thread pool size accordingly to the size of the above connection pool in [ServletContextListener.getMainJpaThreadPoolSize()](src/main/java/pl/morgwai/samples/guiced_servlet_jpa/servlets/ServletContextListener.java).
1. After that, issue in the root folder of this sample app repo: `mvn package`

This will produce a Java war in `target` subfolder that can be deployed to any Servlet container such as Jetty or Tomcat.



## RUNNIG WITH DEMO JETTY CONFIG

The repo contains demo config for Jetty 9.4.x and 10.x.x series in [src/main/jetty folder](src/main/jetty) to try the app:
1. download Jetty from https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/ and extract it to the folder of choice
1. issue the following command in the [src/main/jetty folder](src/main/jetty) to add H2 jar (downloaded by Maven during build) to Jetty's classpath: `mkdir -p lib/ext && cd lib/ext && ln -s ~/.m2/repository/com/h2database/h2/1.4.200/h2-1.4.200.jar`
1. you can tune acceptor, selector and threadPool size numbers in [http-threadpool.ini file](src/main/jetty/start.d/http-threadpool.ini).
1. export `JETTY_HOME` env var pointing to the above folder: `export JETTY_HOME=/path/to/folder/where/jetty/was/extracted`
1. issue the following command in [src/main/jetty folder](src/main/jetty) to start Jetty: `java -server -jar ${JETTY_HOME}/start.jar`<br/>

You can now point any browser to http://localhost:8080/ to use the app.<br/>
To stop the server press `ctrl + c` on its console or issue `java -jar ${JETTY_HOME}/start.jar --stop STOP.PORT=8084 STOP.KEY=servlet-jpa-sample` from another console.

### using eclipse launchers

After importing project to Eclipse you can use provided launchers to debug/run the app on Jetty. You
just need to configure `jetty_home` eclipse var to point to Jetty's location: click in the top menu bar `Window` -> `Preferences` then in the opened window in the left tree bar choose `Run/Debug` node  -> `String Substitution` subnode, then click `New` button on the right side.
