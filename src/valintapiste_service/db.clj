(ns valintapiste-service.db
   (:require [ragtime.jdbc :as jdbc]
             [ragtime.repl :as repl]))

(defn load-config []
  {:datastore (jdbc/sql-database {:connection-uri "jdbc:postgresql://localhost:5432/test?user=test&password=test"})
   :migrations (jdbc/load-resources "migrations")})

(defn migrate []
  (repl/migrate (load-config)))
