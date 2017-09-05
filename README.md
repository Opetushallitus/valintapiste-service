# valintapiste-service

FIXME

## Usage

### Run the application locally

`lein ring server`

### Run the tests

`lein test`

### Start test Postgres (to localhost:5432/test with username test and password test)

`lein testpostgres`

Connecting with psql:
`psql -h localhost -d test -U test`

### Packaging and running as standalone jar

```
lein do clean, ring uberjar
java -jar target/server.jar
```

### Packaging as war

`lein ring uberwar`

## License

Copyright ©  FIXME
