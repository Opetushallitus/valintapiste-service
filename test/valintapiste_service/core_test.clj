(ns valintapiste-service.core-test
  (:require [cheshire.core :as cheshire]
            [clojure.test :refer :all]
            [clj-log4j2.core :as log]
            [valintapiste-service.db :as db]
            [cheshire.core :refer :all]
            [valintapiste-service.pool :as pool]
            [valintapiste-service.config :as c]
            [valintapiste-service.handler :refer :all]
            [valintapiste-service.pistetiedot :as p]
            [ring.mock.request :as mock]))

(def config (c/readConfigurationFile))
(def datasource (pool/datasource config))

(defn clean [datasource]
  (doto (new org.flywaydb.core.Flyway)
    (.setDataSource datasource)
    (.clean)))

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

(defn my-test-fixture [f]
  (clean datasource)
  (db/migrate datasource)
  (f))

(use-fixtures :once my-test-fixture)

(deftest valintapisteTests
  (let [abc "ABC"]
  (testing "Test GET /haku/.../hakukohde/... returns list of hakukohteen pistetiedot"
    (let [response ((app abc datasource) (-> (mock/request :get  "/api/haku/1.2.3.4/hakukohde/1.2.3.4")))
          body     (parse-body (:body response))]
      (is (= (:status response) 200))
      (is (= body []))))

  (testing "Test GET /haku/.../hakemus/... returns hakemuksen pistetiedot"
    (let [response ((app "mocked mongo" datasource) (-> (mock/request :get  "/api/haku/1.2.3.4/hakemus/1.2.3.4")))
          body     (parse-body (:body response))]
      (is (= (:status response) 200))
      (is (= body {:hakemusOID "1.2.3.4" :pisteet {}}))))
      
  (testing "Test PUT /haku/.../hakukohde/... put pistetiedot for 'hakukohteen tunnisteet'"
    (let [json-body (generate-string [{:hakemusOID "1" :pisteet {"A" "D"}}
                                                    {:hakemusOID "2" :pisteet {"C" "B"}}])
          response ((app "mocked mongo" datasource) 
                    (-> (mock/request :put "/api/haku/1.2.3.4/hakukohde/1.2.3.4" json-body)
                        (mock/content-type "application/json")))
          body     (parse-body (:body response))]
      (is (= (:status response) 200))
      (is (= body 2))))))
    
