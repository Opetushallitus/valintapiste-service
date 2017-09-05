(ns valintapiste-service.config
    (:require [clojure.edn :as edn]))

(defn readConfigurationFile
    "Reads configuration file"
    [location]
    (assoc (-> location
    (slurp)
    (edn/read-string))))
