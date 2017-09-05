(ns valintapiste-service.testpostgres)

(defn -main [] 
    (let [postgresBinaryPath (. java.nio.file.Paths get "target" (into-array ["postgres"]))
          cachedPostgres (. ru.yandex.qatools.embed.postgresql.EmbeddedPostgres cachedRuntimeConfig postgresBinaryPath)
          additionalParams (. java.util.Collections emptyList)
          postgres (new ru.yandex.qatools.embed.postgresql.EmbeddedPostgres)]
        (.addShutdownHook (Runtime/getRuntime) (Thread. #(. postgres (stop))))
        (. postgres (start cachedPostgres "localhost" 5432 "test" "test" "test" additionalParams))
        (prn "Postgres started at localhost:5432/test! Username is test and password is test")
        (Thread/sleep 86400000)))
        