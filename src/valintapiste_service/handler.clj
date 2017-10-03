(ns valintapiste-service.handler
  (:require [compojure.api.sweet :refer :all]
            [valintapiste-service.pistetiedot :as p]
            [valintapiste-service.config :as c]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer :all]
            [clj-log4j2.core :as log]
            [valintapiste-service.pool :as pool]
            [schema.core :as s]
            [valintapiste-service.hakuapp :as mongo]
            [valintapiste-service.db :as db])
  (:gen-class))

(def jul-over-slf4j (do (org.slf4j.bridge.SLF4JBridgeHandler/removeHandlersForRootLogger)
                        (org.slf4j.bridge.SLF4JBridgeHandler/install)))

(s/defschema Pistetieto
  {;:aikaleima s/Str
   :tunniste s/Str
   :arvo s/Str
   :osallistuminen (s/enum "EI_OSALLISTUNUT" "OSALLISTUI" "EI_VAADITA" "MERKITSEMATTA")
   :tallettaja s/Str})

(s/defschema PistetietoWrapper
  {:hakemusOID s/Str
   (s/optional-key :sukunimi) s/Str
   (s/optional-key :etunimet) s/Str
   (s/optional-key :oppijaOID) s/Str
   :pisteet [Pistetieto]})

(defn throwIfNullsInAuditSession [auditSession] 
  (if (not-any? nil? auditSession) 
    :default
    (throw (Exception. "Mandatory query params missing! (sessionId uid inetAddress userAgent)"))))

(defn logAuditSession [sessionId uid inetAddress userAgent] 
  (do (throwIfNullsInAuditSession [sessionId uid inetAddress userAgent])
    (log/info "AuditSession {} {} {} {}" sessionId uid inetAddress userAgent)))

(defn app
  "This is the App"
  [hakuapp datasource basePath]
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Valintapiste-service"
                    :description "Pistetiedot"}
             }}}

    (context (str basePath "/api") []
      :tags ["api"]

      (GET "/healthcheck"
        []
        :summary "Healtcheck API"
        (ok "OK"))

      (GET "/haku/:hakuOID/hakukohde/:hakukohdeOID" 
        [hakuOID hakukohdeOID sessionId uid inetAddress userAgent]
        :return [PistetietoWrapper]
        :summary "Hakukohteen hakemusten pistetiedot"
        (do 
          (logAuditSession sessionId uid inetAddress userAgent)
          (let [data (p/fetch-hakukohteen-pistetiedot hakuapp datasource hakuOID hakukohdeOID)
                last-modified (-> data :last-modified)
                hakemukset (-> data :hakemukset)]
            (ok hakemukset))))

      (POST "/haku/:hakuOID/pisteet-with-hakemusoids" 
        [hakuOID sessionId uid inetAddress userAgent]
        :body [hakemusoids [s/Str]]
        :return [PistetietoWrapper]
        :summary "Hakukohteen hakemusten pistetiedot"
        (do 
          (logAuditSession sessionId uid inetAddress userAgent)
          (let [data (p/fetch-hakemusten-pistetiedot datasource hakuOID (map (fn [oid] {:oid oid :personOid ""}) hakemusoids))
                last-modified (-> data :last-modified)
                hakemukset (-> data :hakemukset)]
            (ok hakemukset))))

      (GET "/haku/:hakuOID/hakemus/:hakemusOID/oppija/:oppijaOID" 
        [hakuOID hakemusOID oppijaOID sessionId uid inetAddress userAgent]
        :return PistetietoWrapper
        :summary "Hakemuksen pistetiedot"
        (do 
          (logAuditSession sessionId uid inetAddress userAgent)
          (let [data (p/fetch-hakemusten-pistetiedot datasource hakuOID [{:oid hakemusOID :personOid oppijaOID}])
                last-modified (-> data :last-modified)
                hakemukset (-> data :hakemukset)]
            (ok (first hakemukset)))))

      (PUT "/haku/:hakuOID/hakukohde/:hakukohdeOID" 
        [hakuOID hakukohdeOID sessionId uid inetAddress userAgent]
        :body [uudet_pistetiedot [PistetietoWrapper]]
        :summary "Syötä pistetiedot hakukohteen avaimilla"
        (do 
          (logAuditSession sessionId uid inetAddress userAgent)
          (let [returns_nothing_if_succeeds (p/update-pistetiedot datasource hakuOID hakukohdeOID uudet_pistetiedot)]
            (ok)))))))

(defn -main []

  (let [config (c/readConfigurationFile)
        datasource (pool/datasource config)
        mongoConnection (mongo/connection config)
        ]
    (db/migrate datasource)
    (run-jetty (app (partial mongo/hakemusOidsForHakukohde (-> mongoConnection :db)) datasource "/valintapiste-service") {
      :port (-> config :server :port)}) ))

