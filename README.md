# Restoo &middot; [![Build Status](https://travis-ci.org/amsayk/restoo.svg)](https://travis-ci.org/amsayk/restoo)

Restaurant stock management app.

# Getting started

## Database

I'm using an in-memory h2 database. The database will be automatically created via a migration using [Flyway](https://flywaydb.org/)

## Running the app

```sh
$ sbt run
```

Open `http://localhost:8080` in your browser to access the swagger UI page.

By default, the app will start listening on `0.0.0.0` port 8080. You can change the port by defining the ENV variable `RESTOO_SERVER_PORT`.

The following command will start the server on localhost port 9009:

```sh
$ RESTOO_SERVER_PORT=9009 sbt run
```

Swagger also defaults to `localhost:8080` when making requests. So if you change the default host or port, you should set `SWAGGER_URL` accordingly.

E. g:


```sh
$ SWAGGER_URL="localhost:9009" RESTOO_SERVER_PORT=9009 sbt run
```

Running inside docker:

```sh
$ sbt run docker:publishLocal
$ docker run -it --rm -p 8080:8080 restoo:$VERSION
```


## REST endpoints

See the swagger UI.

## Tests

To run tests:

```sh
$ sbt test
```

To run integration tests inside docker:

```sh
$ sbt dockerComposeTest
```


## Tracing


Logging and zipkin backends are supported but disabled by default.

To enable logging, execute:

`
  $ RESTOO_ENABLE_TRACING_LOGGIN=true sbt run
`

To enable zipkin, make sure there is an instance of zipkin running on localhost on port 9411 then execute:

`
  $ RESTOO_ENABLE_TRACING_ZIPKIN=true sbt run
`

If zipkin is on a remote host, you can use the ENV variable `RESTOO_ZIPKIN_URL`.

e. g:

`
  $ RESTOO_ENABLE_TRACING_ZIPKIN=true RESTOO_ZIPKIN_URL="http://zipkinhost/api/v2/spans" sbt run
`

By default, only a few requests are traced (0.01%). You can change this behavior by setting `RESTOO_TRACE_SAMPLING_PROBABILITY`

The following will trace all requests.

`
  $ RESTOO_ENABLE_TRACING_ZIPKIN=true RESTOO_TRACE_SAMPLING_PROBABILITY=1.0 sbt run
`

## Monitoring

Monitoring is on by default via the builtin `http4s-server-prometheus` module. All metrics are exported by the `/metrics` endpoint.

To configure prometheus to scrape for metrics, add the following to your prometheus.yml `scrape_configs` config section assuming you are running on localhost port 8080:

```yaml

  - job_name: 'restoo'
    scrape_interval: 5s
    static_configs:
      - targets: ['localhost:8080']

```

Execute some requests and you should see some metrics variables in prometheus.

