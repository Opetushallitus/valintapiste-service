(ns valintapiste-service.test-db
    (require [clojure.java.jdbc :as j]))

(defn pg-uri
    [config]
    {:connection-timeout 2500
     :connection-uri (str "jdbc:postgresql://" 
                            (-> config :db :servername) 
                                ":" (-> config :db :port) 
                                "/" (-> config :db :databasename) 
                                "?user=" (-> config :db :username) 
                                "&password=" (-> config :db :password))})

(defn testConnection 
    "Test if connection works"
    [config]
    (if (try 
        (j/query (pg-uri config) ["SELECT 1"])
        (catch Exception e false)) {:connectionWorks true} {:connectionWorks false} ))
