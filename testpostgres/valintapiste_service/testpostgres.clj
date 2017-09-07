(ns valintapiste-service.testpostgres
  (:require [valintapiste-service.config :as c]
            [clj-log4j2.core :as log]))


(defn -main [] 
    (let [config (c/readConfigurationFile)
          servername (-> config :db :servername)
          username (-> config :db :username)
          password (-> config :db :password)
          databasename (-> config :db :databasename)
          port (-> config :db :port)
          postgresBinaryPath (. java.nio.file.Paths get "target" (into-array ["postgres"]))
          cachedPostgres (. ru.yandex.qatools.embed.postgresql.EmbeddedPostgres cachedRuntimeConfig postgresBinaryPath)
          additionalParams (. java.util.Collections emptyList)
          postgres (new ru.yandex.qatools.embed.postgresql.EmbeddedPostgres)]
        (.addShutdownHook (Runtime/getRuntime) (Thread. #(. postgres (stop))))
        (. postgres (start cachedPostgres servername port databasename username password additionalParams))
        (log/info "Postgres started at {}:{}/{}! Username is {} and password is {}" servername port databasename username password)
        (Thread/sleep 86400000)))
        