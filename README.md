# JPA and guiced servlet integration

A few base classes useful when developing [guiced servlets](https://github.com/morgwai/servlet-scopes) performing JPA operations on a dedicated executor.

Note: on 64bit architecture there's usually no need to use asynchronous style, as creating dozens of thousands of threads is not a problem.<br/>
Moreover, most JPA implementations perform read-only transactions purely in cache, without using a JDBC connection, so finding the optimal size of a threadPool for executor performing JPA operations is more complicated than just "same as JDBC connection pool" and requires some load-testing at least and preferably real usage statistics.

**latest release: 7.2**<br/>
[javax flavor](https://search.maven.org/artifact/pl.morgwai.base/guiced-servlet-jpa/7.2-javax/jar)
([javadoc](https://javadoc.io/doc/pl.morgwai.base/guiced-servlet-jpa/7.2-javax))<br/>
[jakarta flavor](https://search.maven.org/artifact/pl.morgwai.base/guiced-servlet-jpa/7.2-jakarta/jar)
(experimental: see [notes](https://github.com/morgwai/servlet-scopes#notes-on-jakarta-support))
([javadoc](https://javadoc.io/doc/pl.morgwai.base/guiced-servlet-jpa/7.2-jakarta))


## MAIN USER CLASSES

### [SimpleAsyncJpaServlet](src/main/java/pl/morgwai/base/servlet/guiced/jpa/SimpleAsyncJpaServlet.java)
Base class for servlets that do not perform synchronous time consuming operations other than JPA calls.<br/>
Request handling is dispatched to the app wide [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java) associated with persistence unit's JDBC connection pool. This prevents  requests awaiting for available JDBC connection from blocking server threads. This way the total number of server's threads can remain constant regardless of the number of concurrent requests.

### [JpaServlet](src/main/java/pl/morgwai/base/servlet/guiced/jpa/JpaServlet.java)
Base class for servlets that perform other types of time consuming operations apart from JPA.<br/>
Mostly just provides some helper methods.

### [JpaServletContextListener](src/main/java/pl/morgwai/base/servlet/guiced/jpa/JpaServletContextListener.java)
Base class for app's `ServletContextListener`. Configures and creates Guice `Injector` and manages lifecycle of persistence unit and its associated [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java).

### [JpaPingingServletContextListener](src/main/java/pl/morgwai/base/servlet/guiced/jpa/JpaPingingServletContextListener.java)
Subclass of `JpaServletContextListener` that additionally automatically registers/deregisters created endpoint instances to a [WebsocketPingerService](https://github.com/morgwai/servlet-utils#main-user-classes).


## USAGE

```java
public class LabelServlet extends SimpleAsyncJpaServlet {

    @Inject MyDao dao;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // this code will be executed on the app wide executor associated with the persistent unit,
        // so threads from server's main thread pool will not be blocked waiting for
        // JPA operations to complete nor for JDBC connections to become available.
        try (
            PrintWriter writer = response.getWriter();
        ) {
            MyEntity myEntity = dao.find(Long.valueOf(request.getParameter("objectId")));
            for (String label: myEntity.getLabels()) writer.println(label);
            String newLabel = request.getParameter("newLabel");
            if (newLabel == null) return;
            executeWithinTx(() -> {
                myEntity.addLabel();
                writer.println(newLabel);
            });
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
```

```java
@WebListener
public class ServletContextListener extends JpaServletContextListener {

    @Override
    protected String getMainPersistenceUnitName() {
        return "my-persistence-unit";  // same as in persistence.xml
    }

    @Override
    protected int getMainJpaThreadPoolSize() {
        return 10;  // value determined by load-tests
    }

    @Override
    protected LinkedList<Module> configureMoreInjections() {
        var modules = super.configureInjections();
        modules.add((binder) -> {
            binder.bind(MyDao.class).to(MyJpaDao.class).in(Scopes.SINGLETON);
            // more bindings here...
        });
        return modules;
    }

    @Override
    protected void configureServletsFiltersEndpoints() throws ServletException {
        addServlet("label", LabelServlet.class, "/label");  // will have its fields injected
        // more servlets/filters here...
    }
}
```

```java
public class MyJpaDao implements MyDao {

    @Inject Provider<EntityManager> entityManagerProvider;

    @Override
    public MyEntity find(long id) {
        return entityManagerProvider.get().find(MyEntity.class, id);
    }

    // more dao stuff here...
}
```

### Dependency management

Similarly to [servlet-scopes](https://github.com/morgwai/servlet-scopes#dependency-management), dependencies of this jar on [guice](https://search.maven.org/artifact/com.google.inject/guice) and [slf4j-api](https://search.maven.org/artifact/org.slf4j/slf4j-api) are declared as optional, so that apps can use any versions of these deps with compatible API.

There are 2 builds available:
- build with `shadedscopes` classifier includes shaded `servlet-scopes` and its dependencies: `servlet-utils`, `java-utils`, `guice-context-scopes` and relocated [byte-buddy](https://search.maven.org/artifact/net.bytebuddy/byte-buddy).
- "default" build does not include any shaded dependencies and dependencies on `servlet-scopes` and `byte-buddy` are marked as `optional`. This is useful for apps that also depend on `byte-buddy` and need to save space (`byte-buddy` is over 3MB in size). Note that the version provided by the app needs to be compatible with the version that `servlet-scopes` depends on (in regard to features used by `servlet-scopes`). If this is not the case, then `shadedscopes` build should be used.


## EXAMPLES

[Sample app](sample)<br/>
[Almost the same sample app but with multiple persistence units](sample-multi-jpa)
