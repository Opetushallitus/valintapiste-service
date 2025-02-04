(ns valintapiste-service.siirtotiedosto-app
  (:require [jeesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [valintapiste-service.config :as c]
            [valintapiste-service.db :as db]
            [valintapiste-service.pool :as pool]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [valintapiste-service.handler :refer [read-config-file-from-args]]
            [valintapiste-service.pistetiedot :as p]
            [valintapiste-service.siirtotiedosto :refer [create-siirtotiedosto-client datetime-parser]])
  (:import java.util.UUID)
  (:gen-class))

(defqueries "queries.sql")

(defn- siirtotiedosto-data [last-siirtotiedosto-data execution-id datetime]
  (let [base-data {:id execution-id :window_end (f/unparse datetime-parser datetime) :run_start datetime
                   :window_start nil :run_end nil :success nil :info nil :error_message nil}]
  (if (empty? last-siirtotiedosto-data)
    base-data
    (let [last-data (first last-siirtotiedosto-data)]
      (if (= true (:success last-data))
        (merge base-data {:window_start (:window_end last-data)})
        (merge base-data {:window_start (:window_start last-data)}) ;;retry
        )))))

(defn- update-siirtotiedosto-data [orig-data operation-results]
  (let [base-data (merge orig-data {:run_end (t/now)})]
    (if (= true (:success operation-results))
      (merge base-data {:success true :info {:count (:total operation-results)}})
      (merge base-data {:success false :error-message (:error-msg operation-results)}))))

(defn -main [& args]
  (let [config-file (read-config-file-from-args args)
        config (if (some? config-file) (c/readConfigurationFile config-file) (c/readConfigurationFile))
        datasource (pool/datasource config)
        _ (db/migrate datasource)
        connection {:datasource datasource}
        siirtotiedosto-client (create-siirtotiedosto-client config)
        execution-id (str (UUID/randomUUID))
        current-datetime (t/now)
        last-siirtotiedosto-data (jdbc/with-db-transaction [tx connection] (latest-siirtotiedosto-data tx))
        upsert-data (fn [data] (jdbc/with-db-transaction [tx connection] (upsert-siirtotiedosto-data! tx data)))
        new-siirtotiedosto-data (siirtotiedosto-data last-siirtotiedosto-data execution-id current-datetime)
        start-datetime (if (:window_start new-siirtotiedosto-data)
                         (f/parse datetime-parser (:window_start new-siirtotiedosto-data))
                         (t/epoch))]
    (log/info (str "Launching siirtotiedosto operation " execution-id ". Previous data: " (first last-siirtotiedosto-data) ", new data " new-siirtotiedosto-data))
    (upsert-data new-siirtotiedosto-data)
    (let [updated-siirtotiedosto-data (->> (p/create-siirtotiedostot-for-pistetiedot datasource
                                                            siirtotiedosto-client
                                                            start-datetime
                                                            current-datetime
                                                            (-> config :siirtotiedostot :max-hakemuscount-in-file)
                                                            execution-id)
                                         (update-siirtotiedosto-data new-siirtotiedosto-data))]
      (if (= true (:success updated-siirtotiedosto-data))
        (log/info (str "Created siirtotiedostot " (json/generate-string updated-siirtotiedosto-data)))
        (log/error (str "Siirtotiedosto operation failed; " (json/generate-string updated-siirtotiedosto-data))))
      (upsert-data updated-siirtotiedosto-data))))