# valintapiste-service

## Usage

### Run the application locally

`lein ring server`

### Run the tests

`lein test` or `lein test-refresh` to run tests automatically every time code updates.

To run tests little bit faster:
`export LEIN_FAST_TRAMPOLINE=1`
and then:
`lein trampoline test`

`time lein test`
lein test             9.46s user 0.95s system 95% cpu 10.877 total

`time lein trampoline test`
lein trampoline test  6.32s user 0.59s system 99% cpu 6.909 total

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
