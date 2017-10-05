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
          (do (log/info "PostgreSQL connection works! Using it for tests!")
            {:datasource (pool/datasource finalConfig) :config finalConfig})
          (do (log/info "Starting PostgreSQL for tests!")
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
    (p/update-pistetiedot datasource "1.2.3.4" "1.2.3.4" test-data nil)))

(defn valintapiste-test-fixture [f]
  (clean datasource)
  (db/migrate datasource)
  (insert-test-data)
  (f)
  (.shutdown datasource))

(use-fixtures :once valintapiste-test-fixture)

(def auditSession {:sessionId "sID" :uid "uID" :inetAddress "1.2.3.4" :userAgent "uAgent"})

(deftest valintapiste-tests
  (let [mockedMongo (fn [hakuOID hakukohdeOID] [{:oid "testi-hakemus-1" :personOid "1.2.3.4"} {:oid "1.2.3.4" :personOid "1.2.3.4"}])]
    (testing "Test GET /haku/.../hakukohde/... returns list of hakukohteen pistetiedot"
      (let [response ((app mockedMongo datasource "") (-> (mock/request :get "/api/haku/1.2.3.4/hakukohde/1.2.3.4" auditSession)))
            headers (:headers response)
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body [{:hakemusOID "testi-hakemus-1", 
                      :oppijaOID "1.2.3.4"
                      :pisteet [{:tunniste "piste-1", :arvo "10", :osallistuminen "OSALLISTUI", :tallettaja "1.2.3.4"}]} 
                     {:hakemusOID "1.2.3.4", :oppijaOID "1.2.3.4" :pisteet []}]))))

    (testing "Test GET /haku/.../hakukohde/... returns empty pistetiedot"
      (let [response ((app (fn [hakuOID hakukohdeOID] []) datasource "") (-> (mock/request :get "/api/haku/1.2.3.4/hakukohde/1.2.3.4" auditSession)))
            headers (:headers response)
            body     (parse-body (:body response))]
            
        (is (= (:status response) 200))
        (is (= body []))))

    (testing "Test GET /haku/.../hakukohde/... returns one pistetieto"
      (let [response ((app (fn [hakuOID hakukohdeOID] [{:oid "1.2.3.4" :personOid "1.2.3.5"}]) datasource "") (-> (mock/request :get  "/api/haku/1.2.3.4/hakukohde/1.2.3.4" auditSession)))
            headers (:headers response)
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body [{:hakemusOID "1.2.3.4", :oppijaOID "1.2.3.5" :pisteet []}]))))


    (testing "Test GET /haku/.../hakemus/... returns hakemuksen pistetiedot"
      (let [response ((app mockedMongo datasource "") (-> (mock/request :get "/api/haku/1.2.3.4/hakemus/1.2.3.4/oppija/1.2.3.6" auditSession)))
            headers (:headers response)
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body {:hakemusOID "1.2.3.4" :oppijaOID "1.2.3.6" :pisteet []}))))
    
    (testing "Test POST /haku/.../pisteet-with-hakemusoids"
      (let [json-body (generate-string ["1.2.3.4"])
            response ((app mockedMongo datasource "") 
                      (-> (mock/request :post "/api/haku/1.2.3.4/pisteet-with-hakemusoids" json-body)
                          (mock/query-string auditSession)
                          (mock/content-type "application/json")))
            headers (:headers response)
            body     (parse-body (:body response))]
        (is (= (:status response) 200))
        (is (= body [{:hakemusOID "1.2.3.4" :oppijaOID "" :pisteet []}]))))

    (testing "Test PUT /haku/.../hakukohde/... put pistetiedot for 'hakukohteen tunnisteet'"
      (let [json-body (generate-string [{:hakemusOID "UPDATE_TEST"
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
            response ((app mockedMongo datasource "") 
                      (-> (mock/request :put "/api/haku/1.2.3.4/hakukohde/1.2.3.4" json-body)
                          (mock/query-string auditSession)
                          (mock/header "If-Unmodified-Since" (.toString (org.joda.time.DateTime/now (org.joda.time.DateTimeZone/forID "Europe/Helsinki") ))) 
                          (mock/content-type "application/json")))]
        (is (= (:status response) 200))))

    (testing "Test PUT /haku/.../hakukohde/... fails when too late"
      (let [json-body (generate-string [{:hakemusOID "UPDATE_TEST"
                                          :pisteet [{ :tunniste "TRY_TO_UPDATE"
                                                      :arvo "UPDATE_SUCCEEDED!"
                                                      :osallistuminen "OSALLISTUI"
                                                      :tallettaja "1.2.3.4"}]}])
            response ((app mockedMongo datasource "") 
                      (-> (mock/request :put "/api/haku/1.2.3.4/hakukohde/1.2.3.4" json-body)
                          (mock/query-string auditSession)
                          (mock/header "If-Unmodified-Since" "2017-10-04T14:36:01.059+03:00") 
                          (mock/content-type "application/json")))
            body     (parse-body (:body response))]
        (is (= (:status response) 409))
        (is (= body ["UPDATE_TEST"] ))))))

