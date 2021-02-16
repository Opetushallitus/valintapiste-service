(ns valintapiste-service.pool
    (:require [hikari-cp.core :refer :all]
              [clojure.java.jdbc :as jdbc]
              [clj-time.coerce :as c]
              [cheshire.core :as json])
    (:import (org.postgresql.util PGobject)
             (java.sql Date Timestamp PreparedStatement)
             (org.postgresql.jdbc PgArray)
             (org.joda.time DateTime)))

(def POSTGRES_ADAPTER "postgresql")

(extend-protocol jdbc/ISQLValue
                 clojure.lang.IPersistentCollection
                 (sql-value [value]
                   (doto (PGobject.)
                         (.setType "jsonb")
                         (.setValue (json/generate-string value)))))

(extend-protocol jdbc/IResultSetReadColumn
                 PGobject
                 (result-set-read-column [pgobj _ _]
                   (let [type  (.getType pgobj)
                         value (.getValue pgobj)]
                        (case type
                              "json" (json/parse-string value true)
                              "jsonb" (json/parse-string value true)
                              :else value))))

(extend-protocol jdbc/IResultSetReadColumn
                 Date
                 (result-set-read-column [v _ _] (c/from-sql-date v))

                 Timestamp
                 (result-set-read-column [v _ _] (c/from-sql-time v))

                 PgArray
                 (result-set-read-column [v _ _]
                   (vec (.getArray v))))

(extend-type DateTime
             jdbc/ISQLParameter
             (set-parameter [v ^PreparedStatement stmt idx]
               (.setTimestamp stmt idx (c/to-sql-time v))))

(defn datasource [config]
    (make-datasource {:username (-> config :db :username)
        :password      (-> config :db :password)
        :database-name (-> config :db :databasename)
        :server-name   (-> config :db :servername)
        :port-number   (-> config :db :port)
        :maximum-pool-size (-> config :db :maximum-pool-size)
        :adapter       POSTGRES_ADAPTER}))

(defn -main [& args]
        (jdbc/with-db-connection [conn {:datasource datasource}]
          (let [rows (jdbc/query conn "SELECT 1")]
            (println rows)))
        (close-datasource datasource))
