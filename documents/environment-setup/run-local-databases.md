## Run postgres on docker

### Preconditions
Have docker installed in your machine.

### Run command

> docker run --name ojp-postgres -e POSTGRES_USER=testuser -e POSTGRES_PASSWORD=testpassword -e POSTGRES_DB=defaultdb -d -p 5432:5432 postgres:17 

#### docker run
Tells Docker to run a new container.

#### --name ojp-postgres
Assigns the name ojp-postgres to the container (makes it easier to manage and reference).

#### -e POSTGRES_USER=testuser
Sets the database username to 'testuser'.

#### -e POSTGRES_PASSWORD=testpassword
Sets the database user password to 'testpassword'.

#### -e POSTGRES_DB=defaultdb
Creates a default database named 'defaultdb' on startup.

#### -d
Runs the container in detached mode (in the background).

#### -p 5432:5432
Maps port 5432 of your host to port 5432 of the container (PostgreSQL’s default port), allowing local access.

#### postgres:17
Specifies the Docker image to use (in this case, the official PostgreSQL image version 17 from Docker Hub).

---

## Run mysql on docker

### Preconditions
Have docker installed in your machine.

### Run command

> docker run --name ojp-mysql -e MYSQL_ROOT_PASSWORD=testpassword -e MYSQL_DATABASE=defaultdb -e MYSQL_USER=testuser -e MYSQL_PASSWORD=testpassword -d -p 3306:3306 mysql:8.0

#### docker run
Tells Docker to run a new container.

#### --name ojp-mysql
Assigns the name ojp-mysql to the container (makes it easier to manage and reference).

#### -e MYSQL_ROOT_PASSWORD=testpassword
Sets the root password for MySQL (required by the image).

#### -e MYSQL_DATABASE=defaultdb
Creates a default database named 'defaultdb' on startup.

#### -e MYSQL_USER=testuser
Creates a new user named 'testuser'.

#### -e MYSQL_PASSWORD=testpassword
Sets the password for 'testuser'.

#### -d
Runs the container in detached mode (in the background).

#### -p 3306:3306
Maps port 3306 of your host to port 3306 of the container (MySQL’s default port), allowing local access.

#### mysql:8.0
Specifies the Docker image to use (in this case, the official MySQL image version 8.0 from Docker Hub).
