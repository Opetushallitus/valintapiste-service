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
              [clj-ring-db-session.session.session-store :refer [create-session-store]]
              [clj-ring-db-session.authentication.auth-middleware :as crdsa-auth-middleware]
              [clj-ring-db-session.session.session-client :as session-client]
              [valintapiste-service.auth.session-timeout :as session-timeout]
              [valintapiste-service.auth.auth :as auth]
              [valintapiste-service.auth.cas-client :as cas]
              [ring.util.http-response :as response]
              [clj-ring-db-session.authentication.login :as crdsa-login]
              [valintapiste-service.auth.urls :as urls]
              [clojure.string :refer [split]]
              [clj-time.core :as t]
              [clj-time.format :as f]
              [valintapiste-service.siirtotiedosto :refer [datetime-parser datetime-format]])
  (:import [org.eclipse.jetty.server.handler
            HandlerCollection
            RequestLogHandler]
           (fi.vm.sade.valinta.dokumenttipalvelu SiirtotiedostoPalvelu))
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

(defn parseDatetime
  ([datetimeStr fieldDesc]
   (parseDatetime datetimeStr fieldDesc nil))
  ([datetimeStr fieldDesc default]
  (if-not (nil? datetimeStr)
    (try (f/parse datetime-parser datetimeStr)
       (catch java.lang.IllegalArgumentException _
         (response/bad-request!
           (str "Illegal " fieldDesc " '" datetimeStr "', allowed format: '" datetime-format "'"))))
    default
    )))

(defn throwIfNullsInAuditSession [auditSession]
  (if (not-any? nil? auditSession)
    :default
    (throw (Exception. "Mandatory query params missing! (sessionId uid inetAddress userAgent)"))))

(defn logProxyAuditSession [audit-logger operation sessionId uid inetAddress userAgent]
      (throwIfNullsInAuditSession [sessionId uid inetAddress userAgent])
      (audit audit-logger operation sessionId uid inetAddress userAgent))

(defn logDirectAuditSession [audit-logger operation session]
      (let [sessionId (:key session)
            uid (get-in session [:identity :oid])
            inetAddress (:client-ip session)
            userAgent (:user-agent session)]
           (throwIfNullsInAuditSession [sessionId uid inetAddress userAgent])
           (audit audit-logger operation sessionId uid inetAddress userAgent)))

(defn logAuditSession
      [audit-logger operation sessionId uid inetAddress userAgent session config]
      (if (or (:dev? config)
              (some #{(get-in session [:identity :username])} (split (:proxy-users config) #",")))
          (logProxyAuditSession audit-logger operation sessionId uid inetAddress userAgent)
          (logDirectAuditSession audit-logger operation session)))


(defn add-last-modified [response last-modified]
  (if last-modified (header response "Last-Modified" last-modified) response))

(defn log-exception-and-return-500 [e]
  (do
    (log/error "Internal server error!" e)
    (internal-server-error (.getMessage e))))

(defn check-authorization! [session dev?]
      (when-not (or dev?
                    (some #(= "VALINTOJENTOTEUTTAMINEN-CRUD" %) (-> session :identity :rights)))
                (do (log/warn "Missing user rights: " (-> session :identity :rights))
                    (response/unauthorized!))))

(defn- create-wrap-database-backed-session [session-store dev]
       (fn [handler]
           (ring-session/wrap-session handler
                                      {:root         "/valintapiste-service"
                                       :cookie-attrs {:secure (not dev)}
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

(defn api-routes [hakuapp ataruapp datasource config audit-logger siirtotiedosto-client]
      (let [dev? (:dev? config)]
           (context "/api" []
                    :tags ["api"]

                    (GET "/haku/:hakuOID/hakukohde/:hakukohdeOID" {session :session}
                         :return [PistetietoWrapper]
                         :query-params [{sessionId :- s/Str nil}
                                         {uid :- s/Str nil}
                                         {inetAddress :- s/Str nil}
                                         {userAgent :- s/Str nil}]
                         :path-params [hakuOID :- (describe s/Str "hakuOid")
                                       hakukohdeOID :- (describe s/Str "hakukohdeOid")]
                         :summary "Hakukohteen hakemusten pistetiedot"
                         (check-authorization! session dev?)
                         (log/info (str "*** " (:proxy-users config) (vector? (:proxy-users config)) ))
                         (try
                           (do
                             (logAuditSession audit-logger "Hakukohteen hakemusten pistetiedot" sessionId uid inetAddress userAgent session config)
                             (let [data (p/fetch-hakukohteen-pistetiedot hakuapp ataruapp datasource hakuOID hakukohdeOID)
                                   last-modified (-> data :last-modified)
                                   hakemukset (-> data :hakemukset)]
                                  (add-last-modified (ok hakemukset) last-modified)))
                           (catch Exception e (log-exception-and-return-500 e))))

                    (POST "/pisteet-with-hakemusoids" {session :session}
                          :body [hakemusoids [s/Str]]
                          :query-params [{sessionId :- s/Str nil}
                                          {uid :- s/Str nil}
                                          {inetAddress :- s/Str nil}
                                          {userAgent :- s/Str nil}]
                          :return [PistetietoWrapper]
                          :summary "Hakukohteen hakemusten pistetiedot. Hakemusten maksimimäärä on 32767 kpl."
                          (check-authorization! session dev?)
                          (try
                            (do
                              (logAuditSession audit-logger "Hakukohteen hakemusten pistetiedot" sessionId uid inetAddress userAgent session config)
                              (let [data (p/fetch-hakemusten-pistetiedot datasource (map (fn [oid] {:oid oid :personOid ""}) hakemusoids))
                                    last-modified (-> data :last-modified)
                                    hakemukset (-> data :hakemukset)]
                                   (add-last-modified (ok hakemukset) last-modified)))
                            (catch Exception e (log-exception-and-return-500 e))))

                    (GET "/hakemus/:hakemusOID/oppija/:oppijaOID" {session :session}
                         :query-params [{sessionId :- s/Str nil}
                                         {uid :- s/Str nil}
                                         {inetAddress :- s/Str nil}
                                         {userAgent :- s/Str nil}]
                         :path-params [oppijaOID :- (describe s/Str "oppijaOid")
                                       hakemusOID :- (describe s/Str "hakemusOid")]
                         :return PistetietoWrapper
                         :summary "Hakemuksen pistetiedot"
                         (check-authorization! session dev?)
                         (try
                           (do
                             (logAuditSession audit-logger "Hakemuksen pistetiedot" sessionId uid inetAddress userAgent session config)
                             (let [data (p/fetch-hakemusten-pistetiedot datasource [{:oid hakemusOID :personOid oppijaOID}])
                                   last-modified (-> data :last-modified)
                                   hakemukset (-> data :hakemukset)]
                                  (add-last-modified (ok (first hakemukset)) last-modified)))
                           (catch Exception e (log-exception-and-return-500 e))))

                    (GET "/siirtotiedosto" {session :session}
                      :query-params [{sessionId :- s/Str nil}
                                         {uid :- s/Str nil}
                                         {inetAddress :- s/Str nil}
                                         {userAgent :- s/Str nil}
                                         {startDateTime :- s/Str nil}
                                         {endDateTime :- s/Str nil}]
                      :summary "Tallentaa annetulla aikavälillä luodut / muokatut pistetiedot hakemuksittain siirtotiedostoon"
                      (check-authorization! session dev?)
                      (let [start (parseDatetime startDateTime "startDateTime")
                            end (parseDatetime endDateTime "endDateTime" (t/now))]
                        (ok (p/create-siirtotiedostot-for-pistetiedot datasource siirtotiedosto-client start end (-> config :siirtotiedostot :max-hakemuscount-in-file)))
                        ))

                    (PUT "/pisteet-with-hakemusoids" {session :session}
                         :body [uudet_pistetiedot [PistetietoWrapper]]
                         :query-params [{sessionId :- s/Str nil}
                                         {uid :- s/Str nil}
                                         {inetAddress :- s/Str nil}
                                         {userAgent :- s/Str nil}
                                         {save-partially :- s/Str nil}]
                         :headers [headers {s/Any s/Any}]
                         :summary "Syötä pistetiedot hakukohteen avaimilla"
                         (check-authorization! session dev?)
                         (try
                           (do
                             (logAuditSession audit-logger "Syötä pistetiedot hakukohteen avaimilla" sessionId uid inetAddress userAgent session config)
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
      (let [dev? (boolean (:dev? config))
            session-store (create-session-store datasource)
            login-cas-client (delay (cas/new-cas-client config))
            kayttooikeus-cas-client (delay (cas/new-client "/kayttooikeus-service" "j_spring_cas_security_check"
                                                           "JSESSIONID" config))
            audit-logger (create-audit-logger)
            siirtotiedosto-client (new SiirtotiedostoPalvelu
                                        (str (-> config :siirtotiedostot :aws-region))
                                        (str (-> config :siirtotiedostot :s3-bucket)))]
           (log/info (str "Starting new app with dev mode " dev?))
           (api
             {:swagger
              {:ui   (str basePath "/api-docs")
               :spec (str basePath "/swagger.json")
               :data {:info {:title       "Valintapiste-service"
                             :description "Pistetiedot"}
                      :tags [{:name "Valintapiste-service" :description "Pistetiedot API"}]}}}
             (context basePath []

                      (GET "/api/healthcheck"
                           []
                           :summary "Healtcheck API"
                           (ok "OK"))

                      (middleware
                        [(create-wrap-database-backed-session session-store dev?)
                         (when-not dev?
                                   #(crdsa-auth-middleware/with-authentication % (urls/cas-login-url config)))]
                        (middleware [session-client/wrap-session-client-headers
                                     (session-timeout/wrap-idle-session-timeout config)]
                                    (api-routes hakuapp ataruapp datasource config audit-logger siirtotiedosto-client))
                        (auth-routes login-cas-client session-store kayttooikeus-cas-client config))))))

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

