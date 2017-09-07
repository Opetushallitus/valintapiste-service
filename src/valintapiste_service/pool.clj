(ns valintapiste-service.pool
    (:require [hikari-cp.core :refer :all]
        [clojure.java.jdbc :as jdbc]))

(def POSTGRES_ADAPTER "postgresql")

(defn datasource [config]
    (make-datasource {:username (-> config :db :username)
        :password      (-> config :db :password)
        :database-name (-> config :db :databasename)
        :server-name   (-> config :db :servername)
        :port-number   (-> config :db :port)
        :adapter       POSTGRES_ADAPTER}))

(defn -main [& args]
        (jdbc/with-db-connection [conn {:datasource datasource}]
          (let [rows (jdbc/query conn "SELECT * FROM valintapiste")]
            (println rows)))
        (close-datasource datasource))
