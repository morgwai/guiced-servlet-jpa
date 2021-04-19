# JPA and async Servlet integration

A few base classes useful when developing asynchronous servlets performing JPA operations.<br/>
Built on top of [Servlet and Websocket Guice Scopes library](https://github.com/morgwai/servlet-scopes).<br/>
<br/>
**latest release: [1.0-alpha1](https://search.maven.org/artifact/pl.morgwai.base/servlet-jpa/1.0-alpha1/jar)**


## SUMMARY

### [SimpleAsyncJpaServlet](src/main/java/pl/morgwai/base/servlet/jpa/SimpleAsyncJpaServlet.java)

Base class for servlets that do not perform synchronous time consuming operations other than JPA calls.<br/>
Request handling is dispatched to the app wide [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java) associated with persistence unit's JDBC connection pool. This way the total number of server's threads can remain constant and small regardless of the number of concurrent requests, while providing optimal performance.


### [JpaServlet](src/main/java/pl/morgwai/base/servlet/jpa/JpaServlet.java)

Base class for servlets that do perform other type(s) of time consuming operations apart from JPA.<br/>
Mostly just provides some helper methods.


### [JpaServletContextListener](src/main/java/pl/morgwai/base/servlet/jpa/JpaServletContextListener.java)

Base class for app's `JpaServletContextListener`. Configures and creates Guice `Injector` and manages lifecycle of persistence unit and its associated [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java).



## USAGE

```java
public class LabelServlet extends SimpleAsyncJpaServlet {

    @Inject MyDao dao;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // this code will be executed on the app wide executor associated with persistent unit,
        // so threads from server's main thread pool will not be waiting on JPA operations
        // nor for JDBC connections to become available.
        try (
            PrintWriter writer = response.getWriter();
        ) {
            MyEntity myEntity = dao.find(Long.valueOf(request.getParameter("objectId")));
            for (String label: myEntity.getLabels()) writer.println(label);
            String newLabel = request.getParameter("newLabel");
            if (newLabel == null) return;
            performInTx(() -> {
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
        // return the same value as my-persistence-unit's connection pool size. This way threads
        // from the pool will never have to wait for a JDBC connection to become available
        return 10;
    }

    @Override
    protected LinkedList<Module> configureInjections() {
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


## EXAMPLES

See [sample app](sample)
