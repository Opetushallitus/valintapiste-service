(ns valintapiste-service.config
    (:require [clojure.edn :as edn]
              [clj-log4j2.core :as log]
              [environ.core :refer [env]]))

(defn readConfigurationFile
    "Reads configuration file"
    []
    (let [configFile (env :valintapisteservice-properties)]
        (log/info "Using config file: {}!" configFile)
        (edn/read-string (slurp configFile))))
