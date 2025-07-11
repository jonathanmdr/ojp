# Spring boot

To integrate OJP into your Quarkus project follow the steps:

## 1 Add the maven dependency to your project.

         <dependency>
             <groupId>org.openjdbcproxy</groupId>
             <artifactId>ojp-jdbc-driver</artifactId>
             <version>0.0.4-alpha</version>
         </dependency>

## 2 Disable quarkus default connection pool
 
>  #Use unpooled datasource 
> 
> quarkus.datasource.jdbc=true
> quarkus.datasource.jdbc.unpooled=true

## 3 Change your connection URL
In your application.properties(or yaml) file, update your database connection URL, and add the OJP jdbc driver class as in the following example:
>  quarkus.datasource.jdbc.url=jdbc:ojp[localhost:1059]_h2:mem:shopdb
quarkus.datasource.jdbc.driver=org.openjdbcproxy.jdbc.Driver

The example above is for h2 but it is similar to any other database, you just need to add the "ojp[host:port]_" pattern immediately after "jdbc:". "[host:port]" indicates the host and port you have your OJP proxy server running.