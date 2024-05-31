(ns valintapiste-service.siirtotiedosto
  (:require [cheshire.core :as json]
            [clojure.java.io :refer [input-stream]]
            [clj-time.core :as t]
            [clj-time.format :as f])
  (:import (fi.vm.sade.valinta.dokumenttipalvelu SiirtotiedostoPalvelu)))
(def datetime-format "yyyy-MM-dd'T'HH:mm:ss")
(def datetime-parser (f/formatter datetime-format (t/default-time-zone)))

(defn create-siirtotiedosto-client [config]
  (new SiirtotiedostoPalvelu
       (str (-> config :siirtotiedostot :aws-region))
       (str (-> config :siirtotiedostot :s3-bucket))
       (str (-> config :siirtotiedostot :s3-target-role-arn))))
(defn create-siirtotiedosto [^SiirtotiedostoPalvelu siirtotiedosto-client execution-id execution-sub-id pistetiedot]
  (let [json (json/generate-string pistetiedot)
        stream (input-stream (.getBytes json))]
    (. (.saveSiirtotiedosto
              siirtotiedosto-client "valintapiste_service" "pistetieto" "" execution-id execution-sub-id stream 2)
            key)))
