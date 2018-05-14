(ns valintapiste-service.handler
  (:require [compojure.api.sweet :refer :all]
            [valintapiste-service.access :refer [access-logger]]
            [valintapiste-service.audit :refer [audit create-audit-logger]]
            [valintapiste-service.pistetiedot :as p]
            [valintapiste-service.config :as c]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [valintapiste-service.pool :as pool]
            [schema.core :as s]
            [valintapiste-service.hakuapp :as mongo]
            [valintapiste-service.db :as db])
  (:import [org.eclipse.jetty.server.handler
            HandlerCollection
            RequestLogHandler]
           (org.eclipse.jetty.server Slf4jRequestLog)
           (fi.vm.sade.auditlog Audit ApplicationType))
  (:gen-class))

(def jul-over-slf4j (do (org.slf4j.bridge.SLF4JBridgeHandler/removeHandlersForRootLogger)
                        (org.slf4j.bridge.SLF4JBridgeHandler/install)))

(s/defschema Pistetieto
  {;:aikaleima s/Str
   :tunniste              s/Str
   (s/optional-key :arvo) s/Any
   :osallistuminen        (s/enum "EI_OSALLISTUNUT" "OSALLISTUI" "EI_VAADITA" "MERKITSEMATTA")
   :tallettaja            s/Str})

(s/defschema PistetietoWrapper
  {:hakemusOID                 s/Str
   (s/optional-key :sukunimi)  s/Str
   (s/optional-key :etunimet)  s/Str
   (s/optional-key :oppijaOID) s/Str
   :pisteet                    [Pistetieto]})

(defn throwIfNullsInAuditSession [auditSession]
  (if (not-any? nil? auditSession)
    :default
    (throw (Exception. "Mandatory query params missing! (sessionId uid inetAddress userAgent)"))))

(defn logAuditSession [audit-logger operation sessionId uid inetAddress userAgent]
  (do (throwIfNullsInAuditSession [sessionId uid inetAddress userAgent])
      (audit audit-logger operation sessionId uid inetAddress userAgent)))

(defn add-last-modified [response last-modified]
  (if last-modified (header response "Last-Modified" last-modified) response))

(defn log-exception-and-return-500 [e]
  (do
    (log/error "Internal server error!" e)
    (internal-server-error (.getMessage e))))

(defn app
  "This is the App"
  [hakuapp datasource basePath]
  (let [audit-logger (create-audit-logger)]
    (api
      {:swagger
       {:ui   "/"
        :spec "/swagger.json"
        :data {:info {:title       "Valintapiste-service"
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
          (try
            (do
              (logAuditSession audit-logger "Hakukohteen hakemusten pistetiedot" sessionId uid inetAddress userAgent)
              (let [data (p/fetch-hakukohteen-pistetiedot hakuapp datasource hakuOID hakukohdeOID)
                    last-modified (-> data :last-modified)
                    hakemukset (-> data :hakemukset)]
                (add-last-modified (ok hakemukset) last-modified)))
            (catch Exception e (log-exception-and-return-500 e))))

        (POST "/pisteet-with-hakemusoids"
              [hakuOID sessionId uid inetAddress userAgent]
          :body [hakemusoids [s/Str]]
          :return [PistetietoWrapper]
          :summary "Hakukohteen hakemusten pistetiedot. Hakemusten maksimimäärä on 32767 kpl."
          (try
            (do
              (logAuditSession audit-logger "Hakukohteen hakemusten pistetiedot" sessionId uid inetAddress userAgent)
              (let [data (p/fetch-hakemusten-pistetiedot datasource (map (fn [oid] {:oid oid :personOid ""}) hakemusoids))
                    last-modified (-> data :last-modified)
                    hakemukset (-> data :hakemukset)]
                (add-last-modified (ok hakemukset) last-modified)))
            (catch Exception e (log-exception-and-return-500 e))))

        (GET "/hakemus/:hakemusOID/oppija/:oppijaOID"
             [hakuOID hakemusOID oppijaOID sessionId uid inetAddress userAgent]
          :return PistetietoWrapper
          :summary "Hakemuksen pistetiedot"
          (try
            (do
              (logAuditSession audit-logger "Hakemuksen pistetiedot" sessionId uid inetAddress userAgent)
              (let [data (p/fetch-hakemusten-pistetiedot datasource [{:oid hakemusOID :personOid oppijaOID}])
                    last-modified (-> data :last-modified)
                    hakemukset (-> data :hakemukset)]
                (add-last-modified (ok (first hakemukset)) last-modified)))
            (catch Exception e (log-exception-and-return-500 e))))

        (PUT "/pisteet-with-hakemusoids"
             [hakuOID hakukohdeOID sessionId uid inetAddress userAgent save-partially]
          :body [uudet_pistetiedot [PistetietoWrapper]]
          :headers [headers {s/Any s/Any}]
          :summary "Syötä pistetiedot hakukohteen avaimilla"
          (try
            (do
              (logAuditSession audit-logger "Syötä pistetiedot hakukohteen avaimilla" sessionId uid inetAddress userAgent)
              (let [conflicting-hakemus-oids (p/update-pistetiedot datasource uudet_pistetiedot (-> headers :if-unmodified-since) save-partially)]
                (if (empty? conflicting-hakemus-oids)
                  (if save-partially
                    (ok conflicting-hakemus-oids)
                    (ok))
                  (if save-partially
                    (ok conflicting-hakemus-oids)
                    (conflict conflicting-hakemus-oids)))))
            (catch Exception e (log-exception-and-return-500 e))))))))

(def config-property "valintapisteservice-properties")

(defn read-config-file-from-args [args]
  (first (filter some?
                 (map (fn [arg]
                        (if (str/starts-with? arg config-property)
                          (subs arg (+ 1 (count config-property))) nil)) args))))

(defn- configure-request-log [environment server]
  (doto server

    (.setHandler (doto (HandlerCollection.)
                   (.addHandler (.getHandler server))
                   (.addHandler (doto (RequestLogHandler.)
                                  (.setRequestLog (doto (access-logger environment) ;access-logger
                                                    (.setLoggerName "ACCESS")
                                                    (.start)))))))))

(defn -main [& args]
  (let [config-file (read-config-file-from-args args)
        config (if (some? config-file) (c/readConfigurationFile config-file) (c/readConfigurationFile))
        datasource (pool/datasource config)
        mongoConnection (mongo/connection config)
        ]
    (db/migrate datasource)
    (run-jetty (app (partial mongo/hakemusOidsForHakukohde
                             (-> mongoConnection :db))
                    datasource "/valintapiste-service")
               {:port         (-> config :server :port)
                :configurator (partial configure-request-log (-> config :environment))})))

