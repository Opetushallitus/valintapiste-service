(ns valintapiste-service.siirtotiedosto
  (:require [cheshire.core :as json]
            [clojure.java.io :refer [input-stream]]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.tools.logging :as log])
  (:import (fi.vm.sade.valinta.dokumenttipalvelu SiirtotiedostoPalvelu)))

(def datetime-format "yyyy-MM-dd'T'HH:mm:ss")
(def datetime-parser (f/formatter datetime-format (t/default-time-zone)))

(defn create-siirtotiedosto [^SiirtotiedostoPalvelu siirtotiedosto-client start-datetime end-date-time pistetiedot]
  (let [json (json/generate-string pistetiedot)
        stream (input-stream (.getBytes json))
        start (str (f/unparse datetime-parser start-datetime))
        end (str (f/unparse datetime-parser end-date-time))]
    (try (. (.saveSiirtotiedosto siirtotiedosto-client start end "valintapiste-service" "pistetiedot" stream 2) key)
         (catch Exception e
           (log/error (str "Transform file creation failed: " (.getMessage e)))
           ""
           ))))