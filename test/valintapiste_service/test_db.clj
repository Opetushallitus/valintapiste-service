(ns valintapiste-service.test-db
    (require [clojure.java.jdbc :as j]))

(defn pg-uri
    [config]
    {:connection-timeout 1500
     :connection-uri (str "jdbc:postgresql://localhost:5432/test?user=test&password=test")})

(defn testConnection 
    "Test if connection works"
    [config]
    (if (try 
        (j/query (pg-uri config) ["SELECT 1"])
        (catch Exception e false)) {:connectionWorks true} {:connectionWorks false} ))
