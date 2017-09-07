(ns valintapiste-service.pool
    (:require [hikari-cp.core :refer :all]
        [clojure.java.jdbc :as jdbc]))

(defn datasource-options [] {:username      "test"
    :password      "test"
    :database-name "test"
    :server-name   "localhost"
    :port-number   5432
    :adapter       "postgresql"})

(defn datasource []
    (make-datasource (datasource-options)))

(defn -main [& args]
        (jdbc/with-db-connection [conn {:datasource datasource}]
          (let [rows (jdbc/query conn "SELECT * FROM valintapiste")]
            (println rows)))
        (close-datasource datasource))