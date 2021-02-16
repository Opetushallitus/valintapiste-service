(ns valintapiste-service.handler
    (:require [compojure.api.sweet :refer :all]
              [valintapiste-service.access :refer [access-logger]]
              [valintapiste-service.audit :refer [audit create-audit-logger]]
              [valintapiste-service.pistetiedot :as p]
              [valintapiste-service.config :as c]
              [ring.adapter.jetty :refer [run-jetty]]
              [ring.util.http-response :refer :all]
              [ring.middleware.session :as ring-session]
              [clojure.tools.logging :as log]
              [clojure.string :as str]
              [valintapiste-service.pool :as pool]
              [schema.core :as s]
              [valintapiste-service.hakuapp :as mongo]
              [valintapiste-service.ataru :as ataru]
              [valintapiste-service.db :as db]
              [environ.core :refer [env]]
              [clj-ring-db-session.session.session-store :refer [create-session-store]]
              [clj-ring-db-session.authentication.auth-middleware :as crdsa-auth-middleware]
              [clj-ring-db-session.session.session-client :as session-client]
              [valintapiste-service.auth.session-timeout :as session-timeout]
              [valintapiste-service.auth.auth :as auth]
              [valintapiste-service.auth.cas-client :as cas]
              [ring.util.http-response :as response]
              [clj-ring-db-session.authentication.login :as crdsa-login]
              [valintapiste-service.auth.urls :as urls])
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

(defn- dev? []
       (= (:dev? env) "true"))

(defn check-authorization! [session]
      (log/info (str "checking auth" session))
      (when-not (or (dev?)
                    (some #(= "APP_VALINTOJENTOTEUTTAMINEN_CRUD" %) (-> session :identity :rights)))
                (log/error "Missing user rights: " (-> session :identity :rights))
                ;(response/unauthorized!) temporarily disabled
                ))

(defn- create-wrap-database-backed-session [session-store]
       (fn [handler]
           (ring-session/wrap-session handler
                                      {:root         "/valintapiste-service"
                                       :cookie-attrs {:secure (not (dev?))}
                                       :store        session-store})))

(defn auth-routes [login-cas-client session-store kayttooikeus-cas-client config]
      (context "/auth" []
               (middleware [session-client/wrap-session-client-headers]
                           (undocumented
                             (GET "/checkpermission" {session :session}
                                          (response/ok (:superuser session)))
                             (GET "/cas" [ticket :as request]
                                              (let [redirect-url (or (get-in request [:session :original-url])
                                                                     (urls/cas-redirect-url config))
                                                    login-provider (auth/cas-login config @login-cas-client ticket)]
                                                   (auth/login login-provider
                                                               redirect-url
                                                               @kayttooikeus-cas-client
                                                               config)))
                             (POST "/cas" [logoutRequest]
                                               (auth/cas-initiated-logout logoutRequest session-store))
                             (GET "/logout" {session :session}
                                  (crdsa-login/logout session (urls/cas-logout-url config)))))))

(defn api-routes [hakuapp ataruapp datasource basePath]
      (let [audit-logger (create-audit-logger)]
           (context "/api" []
                    :tags ["api"]

                    (GET "/haku/:hakuOID/hakukohde/:hakukohdeOID" {session :session}
                         :return [PistetietoWrapper]
                         :header-params [{sessionId :- s/Str nil}
                                         {uid :- s/Str nil}
                                         {inetAddress :- s/Str nil}
                                         {userAgent :- s/Str nil}]
                         :path-params [hakuOID :- (describe s/Str "hakuOid")
                                       hakukohdeOID :- (describe s/Str "hakukohdeOid")]
                         :summary "Hakukohteen hakemusten pistetiedot"
                         (check-authorization! session)
                         (try
                           (do
                             (logAuditSession audit-logger "Hakukohteen hakemusten pistetiedot" sessionId uid inetAddress userAgent)
                             (let [data (p/fetch-hakukohteen-pistetiedot hakuapp ataruapp datasource hakuOID hakukohdeOID)
                                   last-modified (-> data :last-modified)
                                   hakemukset (-> data :hakemukset)]
                                  (add-last-modified (ok hakemukset) last-modified)))
                           (catch Exception e (log-exception-and-return-500 e))))

                    (POST "/pisteet-with-hakemusoids" {session :session}
                          :body [hakemusoids [s/Str]]
                          :header-params [{sessionId :- s/Str nil}
                                          {uid :- s/Str nil}
                                          {inetAddress :- s/Str nil}
                                          {userAgent :- s/Str nil}]
                          :return [PistetietoWrapper]
                          :summary "Hakukohteen hakemusten pistetiedot. Hakemusten maksimimäärä on 32767 kpl."
                          (check-authorization! session)
                          (try
                            (do
                              (logAuditSession audit-logger "Hakukohteen hakemusten pistetiedot" sessionId uid inetAddress userAgent)
                              (let [data (p/fetch-hakemusten-pistetiedot datasource (map (fn [oid] {:oid oid :personOid ""}) hakemusoids))
                                    last-modified (-> data :last-modified)
                                    hakemukset (-> data :hakemukset)]
                                   (add-last-modified (ok hakemukset) last-modified)))
                            (catch Exception e (log-exception-and-return-500 e))))

                    (GET "/hakemus/:hakemusOID/oppija/:oppijaOID" {session :session}
                         :header-params [{sessionId :- s/Str nil}
                                         {uid :- s/Str nil}
                                         {inetAddress :- s/Str nil}
                                         {userAgent :- s/Str nil}]
                         :path-params [oppijaOID :- (describe s/Str "oppijaOid")
                                       hakemusOID :- (describe s/Str "hakemusOid")]
                         :return PistetietoWrapper
                         :summary "Hakemuksen pistetiedot"
                         (check-authorization! session)
                         (try
                           (do
                             (logAuditSession audit-logger "Hakemuksen pistetiedot" sessionId uid inetAddress userAgent)
                             (let [data (p/fetch-hakemusten-pistetiedot datasource [{:oid hakemusOID :personOid oppijaOID}])
                                   last-modified (-> data :last-modified)
                                   hakemukset (-> data :hakemukset)]
                                  (add-last-modified (ok (first hakemukset)) last-modified)))
                           (catch Exception e (log-exception-and-return-500 e))))

                    (PUT "/pisteet-with-hakemusoids" {session :session}
                         :body [uudet_pistetiedot [PistetietoWrapper]]
                         :header-params [{sessionId :- s/Str nil}
                                         {uid :- s/Str nil}
                                         {inetAddress :- s/Str nil}
                                         {userAgent :- s/Str nil}
                                         {save-partially :- s/Str nil}]
                         :headers [headers {s/Any s/Any}]
                         :summary "Syötä pistetiedot hakukohteen avaimilla"
                         (check-authorization! session)
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
                           (catch Exception e (log-exception-and-return-500 e)))))))

(defn new-app [hakuapp ataruapp datasource basePath config]
      "This is the new App with cas-auth"
      (log/info (str "Running new app with basepath " basePath " and config " config " and env " env))
      (let [session-store (create-session-store datasource)
            login-cas-client (delay (cas/new-cas-client config))
            kayttooikeus-cas-client (delay (cas/new-client "/kayttooikeus-service" "j_spring_cas_security_check"
                                                           "JSESSIONID" config))]
           (api
             {:swagger
              {:ui   "/"
               :spec "/swagger.json"
               :data {:info {:title       "Valintapiste-service"
                             :description "Pistetiedot"}}}}
             (context basePath []

                      (GET "/api/healthcheck"
                           []
                           :summary "Healtcheck API"
                           (ok "OK"))

                      (middleware
                        [(create-wrap-database-backed-session session-store)
                         (when-not (dev?)
                                   #(crdsa-auth-middleware/with-authentication % (urls/cas-login-url config)))]
                        (middleware [session-client/wrap-session-client-headers
                                     (session-timeout/wrap-idle-session-timeout config)]
                                    (api-routes hakuapp ataruapp datasource basePath))
                        (auth-routes login-cas-client session-store kayttooikeus-cas-client config))))))

(defn app
  "This is the App"
  [hakuapp ataruapp datasource basePath]
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
              (let [data (p/fetch-hakukohteen-pistetiedot hakuapp ataruapp datasource hakuOID hakukohdeOID)
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
    (run-jetty (new-app (partial mongo/hakemus-oids-for-hakukohde
                                 (-> mongoConnection :db))
                        (ataru/hakemus-oids-for-hakukohde
                          (-> config :host-virkailija)
                          (-> config :valintapiste-cas-username)
                          (-> config :valintapiste-cas-password))
                        datasource "/valintapiste-service" config)
               {:port         (-> config :server :port)
                :configurator (partial configure-request-log (-> config :environment))})))

