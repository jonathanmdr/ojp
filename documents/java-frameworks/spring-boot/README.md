# Spring boot

To integrate OJP into your Spring Boot project follow the steps:

## 1 Add the maven dependency to your project.
```xml
<dependency>
    <groupId>org.openjproxy</groupId>
    <artifactId>ojp-jdbc-driver</artifactId>
    <version>[TBD]</version>
</dependency>
```

## 2 Remove the local connection pool
Spring boot by default comes with HikariCP connection pool, as OJP replaces completely the connection pool, remove it in the pom.xml as follows.
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
    <exclusions>
        <!--When using OJP proxied connection pool the local pool needs to be removed -->
        <exclusion>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```
## 3 Change your connection URL
In your application.properties(or yaml) file, update your database connection URL, and add the OJP jdbc driver class as in the following example:

```properties
spring.datasource.url=jdbc:ojp[localhost:1059]_h2:~/test
spring.datasource.driver-class-name=org.openjproxy.jdbc.Driver
## Sets the datasource to not use hikari CP connection pool and open single connections instead
spring.datasource.type=org.springframework.jdbc.datasource.SimpleDriverDataSource
``` 

The example above is for `h2` but it is similar to any other database, you just need to add the `ojp[host:port]_` pattern immediately after `jdbc:`. `[host:port]` indicates the host and port you have your OJP proxy server running.

---

> [!IMPORTANT]
> 
> Spring Boot has used `Logback` as its default logging framework since its early versions. When using Spring Boot starters, such as spring-boot-starter-web, the spring-boot-starter-logging dependency is automatically included, which in turn transitively pulls in Logback. 
While Spring Boot's internal logging utilizes Commons Logging, it provides default configurations for various logging implementations, including Logback. Logback is given preference if found on the classpath. This design ensures that dependent libraries using other logging frameworks (like Java Util Logging or Log4J) are routed correctly through SLF4J, which Logback implements.
Therefore, Logback has been the default logging system in Spring Boot for a significant period, effectively since its inception and the introduction of its starter dependencies.
With that said, `ojp-jdbc-driver` utilizes `slf4j-api`, and there is a conflict with not finding the correct logger provider. Therefore, indicate the Logback provider as a `JVM` argument in your `JAVA_OPTS` environment variable. 
Follow an example:
```shell
JAVA_OPTS="-Dslf4j.provider=ch.qos.logback.classic.spi.LogbackServiceProvider"
```