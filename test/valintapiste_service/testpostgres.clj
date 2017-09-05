(ns valintapiste-service.testpostgres)

(defn -main [] 
    (let [postgres (new ru.yandex.qatools.embed.postgresql.EmbeddedPostgres)]
        (.addShutdownHook (Runtime/getRuntime) (Thread. #(. postgres (stop))))
        (. postgres (start "localhost" 5432 "test" "test" "test"))
        (Thread/sleep 10000)))
        