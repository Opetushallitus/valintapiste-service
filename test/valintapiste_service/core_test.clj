(ns valintapiste-service.core-test
  (:require [cheshire.core :as cheshire]
            [clojure.test :refer :all]
            [clj-log4j2.core :as log]
            [valintapiste-service.test-db :as testdb]
            [valintapiste-service.db :as db]
            [cheshire.core :refer :all]
            [valintapiste-service.pool :as pool]
            [valintapiste-service.config :as c]
            [valintapiste-service.handler :refer :all]
            [valintapiste-service.pistetiedot :as p]
            [valintapiste-service.freeport :as freeport]
            [valintapiste-service.testpostgres :as postgre]
            [ring.mock.request :as mock]))

(def configAndDatasource 
  (let [defaultConfig (c/readConfigurationFile)
        isOk (testdb/testConnection defaultConfig)
        finalConfig (if (-> isOk :connectionWorks) defaultConfig (update-in defaultConfig [:db :port] freeport/get-free-port))]
        (if (-> isOk :connectionWorks)
          (do (log/info "PostgreSQL connection works! Using it for tests! " finalConfig)
            {:datasource (pool/datasource finalConfig) :config finalConfig})
          (do (log/info "Starting PostgreSQL for tests! " finalConfig)
            (postgre/startPostgreSQL finalConfig)
            {:datasource (pool/datasource finalConfig) :config finalConfig}))))

(def config (-> configAndDatasource :config))
(def datasource (-> configAndDatasource :datasource))
  
(defn clean [datasource]
  (doto (new org.flywaydb.core.Flyway)
    (.setDataSource datasource)
    (.clean)))

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

(defn- insert-test-data []
  (let [test-data [{:hakemusOID "UPDATE_TEST"
                    :pisteet [{:tunniste "TRY_TO_UPDATE"
                              :arvo "UPDATE_FAILED!"
                              :osallistuminen "OSALLISTUI"
                              :tallettaja "1.2.3.4"}]}
                   {:hakemusOID "CONFLICT_TEST"
                    :pisteet [{:tunniste "TRY_TO_UPDATE"
                              :arvo "UPDATE_FAILED!"
                              :osallistuminen "OSALLISTUI"
                              :tallettaja "1.2.3.4"}]}
                   {:hakemusOID "testi-hakemus-1"
                    :pisteet [{:tunniste "piste-1"
                               :arvo "10"
                               :osallistuminen "OSALLISTUI"
                               :tallettaja "1.2.3.4"}]}
                   {:hakemusOID "testi-hakemus-2"
                    :pisteet [{:tunniste "piste-1" 
                               :arvo "9"
                               :osallistuminen "OSALLISTUI"
                               :tallettaja "1.2.3.4"}
                              {:tunniste "piste-2"
                               :arvo "5"
                               :osallistuminen "OSALLISTUI"
                               :tallettaja "1.2.3.4"}]}]]
    (p/update-pistetiedot datasource test-data nil)))

(defn valintapiste-test-fixture [f]
  (clean datasource)
  (db/migrate datasource)
  (insert-test-data)
  (f)
  (.shutdown datasource))

(use-fixtures :once valintapiste-test-fixture)

(def auditSession {:sessionId "sID" :uid "1.2.246.1.1.1" :inetAddress "127.0.0.1" :userAgent "uAgent"})

(deftest valintapiste-tests
  (let [mockedMongo (fn [hakuOID hakukohdeOID] [{:oid "testi-hakemus-1" :personOid "1.2.3.4"} {:oid "1.2.3.4" :personOid "1.2.3.4"}])
        mockedAtaru (fn [hakuOID hakukohdeOID] [])]
    (testing "Test GET /haku/.../hakukohde/... returns list of hakukohteen pistetiedot"
      (let [response ((new-app mockedMongo mockedAtaru datasource "" config) (-> (mock/request :get "/api/haku/1.2.3.4/hakukohde/1.2.3.4" auditSession)))
            headers (:headers response)
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body [{:hakemusOID "testi-hakemus-1", 
                      :oppijaOID "1.2.3.4"
                      :pisteet [{:tunniste "piste-1", :arvo "10", :osallistuminen "OSALLISTUI", :tallettaja "1.2.3.4"}]} 
                     {:hakemusOID "1.2.3.4", :oppijaOID "1.2.3.4" :pisteet []}]))))

    (testing "Test GET /haku/.../hakukohde/... returns empty pistetiedot"
      (let [response ((new-app (fn [hakuOID hakukohdeOID] []) mockedAtaru datasource "" config) (-> (mock/request :get "/api/haku/1.2.3.4/hakukohde/1.2.3.4" auditSession)))
            headers (:headers response)
            body     (parse-body (:body response))]
            
        (is (= (:status response) 200))
        (is (= body []))))

    (testing "Test GET /haku/.../hakukohde/... returns one pistetieto (from hakuapp)"
      (let [response ((new-app (fn [hakuOID hakukohdeOID] [{:oid "1.2.3.4" :personOid "1.2.3.5"}])
                           (fn [hakuOID hakukohdeOID] [])
                           datasource "" config) (-> (mock/request :get  "/api/haku/1.2.3.4/hakukohde/1.2.3.4" auditSession)))
            headers (:headers response)
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body [{:hakemusOID "1.2.3.4", :oppijaOID "1.2.3.5" :pisteet []}]))))

    (testing "Test GET /haku/.../hakukohde/... returns one pistetieto (from ataru)"
      (let [response ((new-app (fn [hakuOID hakukohdeOID] [])
                           (fn [hakuOID hakukohdeOID] [{"hakemus_oid" "1.2.3.4" "henkilo_oid" "1.2.3.5"}])
                           datasource "" config) (-> (mock/request :get  "/api/haku/1.2.3.4/hakukohde/1.2.3.4" auditSession)))
            headers (:headers response)
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body [{:hakemusOID "1.2.3.4", :oppijaOID "1.2.3.5" :pisteet []}]))))


    (testing "Test GET /hakemus/... returns hakemuksen pistetiedot"
      (let [response ((new-app mockedMongo mockedAtaru datasource "" config) (-> (mock/request :get "/api/hakemus/1.2.3.4/oppija/1.2.3.6" auditSession)))
            headers (:headers response)
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body {:hakemusOID "1.2.3.4" :oppijaOID "1.2.3.6" :pisteet []}))))
    
    (testing "Test POST /pisteet-with-hakemusoids"
      (let [json-body (generate-string ["1.2.3.4"])
            response ((new-app mockedMongo mockedAtaru datasource "" config)
                      (-> (mock/request :post "/api/pisteet-with-hakemusoids" json-body)
                          (mock/query-string auditSession)
                          (mock/content-type "application/json")))
            headers (:headers response)
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body [{:hakemusOID "1.2.3.4" :oppijaOID "" :pisteet []}]))))

    (testing "Test PUT /pisteet-with-hakemusoids put pistetiedot for 'hakukohteen tunnisteet'"
      (let [initial-get ((new-app mockedMongo mockedAtaru datasource "" config) (-> (mock/request :get "/api/haku/1.2.3.4/hakukohde/1.2.3.4" auditSession)))
            headers (:headers initial-get)
            timestamp (get headers "Last-Modified");(.toString (org.joda.time.DateTime/now (org.joda.time.DateTimeZone/forID "Europe/Helsinki") ))
            json-body (generate-string [{:hakemusOID "UPDATE_TEST"
                                          :pisteet [{ :tunniste "TRY_TO_UPDATE"
                                                      :arvo "UPDATE_SUCCEEDED!"
                                                      :osallistuminen "OSALLISTUI"
                                                      :tallettaja "1.2.3.4"}]}
                                        {:hakemusOID "1"
                                        :pisteet [{ :tunniste "A"
                                                    :arvo "A"
                                                    :osallistuminen "OSALLISTUI"
                                                    :tallettaja "1.2.3.4"}]}
                                        {:hakemusOID "2"
                                        :pisteet [
                                                    { :tunniste "B"
                                                    :arvo "B"
                                                    :osallistuminen "OSALLISTUI"
                                                    :tallettaja "1.2.3.4"}]}])
            response ((new-app mockedMongo mockedAtaru datasource "" config)
                      (-> (mock/request :put "/api/pisteet-with-hakemusoids" json-body)
                          (mock/query-string auditSession)
                          (mock/header "If-Unmodified-Since" timestamp) 
                          (mock/content-type "application/json")))]
        (is (= (:status response) 200))))

    (testing "Test PUT /pisteet-with-hakemusoids... fails when too late"
      (let [json-body (generate-string [{:hakemusOID "CONFLICT_TEST"
                                          :pisteet [{ :tunniste "TRY_TO_UPDATE"
                                                      :arvo "UPDATE_SUCCEEDED!"
                                                      :osallistuminen "OSALLISTUI"
                                                      :tallettaja "1.2.3.4"}]}])
            response ((new-app mockedMongo mockedAtaru datasource "" config)
                      (-> (mock/request :put "/api/pisteet-with-hakemusoids" json-body)
                          (mock/query-string auditSession)
                          (mock/header "If-Unmodified-Since" "2017-10-04T14:36:01.059+03:00") 
                          (mock/content-type "application/json")))
            body     (parse-body (:body response))]
        (is (= (:status response) 409))
        (is (= body ["CONFLICT_TEST"] ))))

    (testing "Test PUT /pisteet-with-hakemusoids... succeeds with one conflict and no new entries"
      (let [json-body (generate-string [{:hakemusOID "CONFLICT_TEST"
                                         :pisteet [{ :tunniste "TRY_TO_UPDATE"
                                                    :arvo "UPDATE_SUCCEEDED!"
                                                    :osallistuminen "OSALLISTUI"
                                                    :tallettaja "1.2.3.4"}]}])
            response ((new-app mockedMongo mockedAtaru datasource "" config)
                       (-> (mock/request :put "/api/pisteet-with-hakemusoids" json-body)
                           (mock/query-string (merge auditSession {:save-partially "true"}))
                           (mock/header "If-Unmodified-Since" "2017-10-04T14:36:01.059+03:00")
                           (mock/content-type "application/json")))
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body ["CONFLICT_TEST"] ))))

    (testing "Test PUT /pisteet-with-hakemusoids... succeeds fully when allowing partial save"
      (let [json-body (generate-string [{:hakemusOID "NOT_CONFLICT_TEST_2"
                                         :pisteet [{ :tunniste "TRY_TO_UPDATE"
                                                    :arvo "UPDATE_SUCCEEDED!"
                                                    :osallistuminen "OSALLISTUI"
                                                    :tallettaja "1.2.3.4"}]}])
            response ((new-app mockedMongo mockedAtaru datasource "" config)
                       (-> (mock/request :put "/api/pisteet-with-hakemusoids" json-body)
                           (mock/query-string (merge auditSession {:save-partially "true"}))
                           (mock/header "If-Unmodified-Since" "2017-10-04T14:36:01.059+03:00")
                           (mock/content-type "application/json")))
            body     (parse-body (:body response))]
        (prn response)
        (is (= (:status response) 200))
        (is (= body []))))

    (testing "Test PUT /pisteet-with-hakemusoids... missing arvo"
      (let [json-body (generate-string [{:hakemusOID "MISSING_ARVO_TEST"
                                         :pisteet [{ :tunniste "TRY_TO_UPDATE"
                                                    :osallistuminen "OSALLISTUI"
                                                    :tallettaja "1.2.3.4"}]}])
            response ((new-app mockedMongo mockedAtaru datasource "" config)
                       (-> (mock/request :put "/api/pisteet-with-hakemusoids" json-body)
                           (mock/query-string (merge auditSession {:save-partially "true"}))
                           (mock/header "If-Unmodified-Since" "2017-10-04T14:36:01.059+03:00")
                           (mock/content-type "application/json")))
            body     (parse-body (:body response))]
        (prn response)
        (is (= (:status response) 200))
        (is (= body []))))

    (testing "Test PUT /pisteet-with-hakemusoids... succeeds partially"
      (let [json-body (generate-string [{:hakemusOID "CONFLICT_TEST"
                                         :pisteet [{ :tunniste "TRY_TO_UPDATE"
                                                    :arvo "UPDATE_SUCCEEDED!"
                                                    :osallistuminen "OSALLISTUI"
                                                    :tallettaja "1.2.3.4"}]}
                                        {:hakemusOID "NOT_CONFLICT_TEST"
                                         :pisteet [{ :tunniste "TRY_TO_UPDATE"
                                                    :arvo "UPDATE_SUCCEEDED!"
                                                    :osallistuminen "OSALLISTUI"
                                                    :tallettaja "1.2.3.4"}]}])
            response ((new-app mockedMongo mockedAtaru datasource "" config)
                       (-> (mock/request :put "/api/pisteet-with-hakemusoids" json-body)
                           (mock/query-string (merge auditSession {:save-partially "true"}))
                           (mock/header "If-Unmodified-Since" "2017-10-04T14:36:01.059+03:00")
                           (mock/content-type "application/json")))
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body ["CONFLICT_TEST"] ))))))
