Storage service
=================================

This service provides a data store and an API for data plane.

Data model
=================================

![Image of ERD](https://github.com/hortonworks/dataplane/blob/master/services/db-service/db/erd.png)


Run the service
===========

Install flyway through homebrew - `brew install flyway`

Change the DB parameters in `db/flyway.conf` and then run `flyway clean migrate`

Change DB connection parameters in `application.conf`

`activator run` or `sbt ~run`

