(ns valintapiste-service.config
    (:require [clojure.edn :as edn]
              [clj-log4j2.core :as log]
              [environ.core :refer [env]]))

(defn- readConfigurationFromPath
    [configFile]
        (log/info "Using config file: {}!" configFile)
        (edn/read-string (slurp configFile)))

(defn readConfigurationFile
    "Reads configuration file"
    ([]
    (readConfigurationFromPath (env :valintapisteservice-properties)))
    ([config-file]
    (readConfigurationFromPath config-file)))
        
