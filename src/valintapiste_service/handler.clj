(ns valintapiste-service.handler
  (:require [compojure.api.sweet :refer :all]
            [valintapiste-service.pistetiedot :as p]
            [valintapiste-service.config :as c]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer :all]
            [valintapiste-service.pool :as pool]
            [schema.core :as s]
            [valintapiste-service.hakuapp :as mongo]
            [valintapiste-service.db :as db])
  (:gen-class))

(s/defschema Pistetieto
  {:tunniste s/Str
   :arvo s/Str
   :osallistuminen s/Str
   :tallettaja s/Str})

(s/defschema PistetietoWrapper
  {:hakemusOID s/Str
   :pisteet [Pistetieto]})

(defn app
  "This is the App"
  [hakuapp datasource]
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Valintapiste-service"
                    :description "Pistetiedot"}
             }}}

    (context "/api" []
      :tags ["api"]

      (GET "/haku/:hakuOID/hakukohde/:hakukohdeOID" 
        [hakuOID hakukohdeOID]
        :return [PistetietoWrapper]
        :summary "Hakukohteen hakemusten pistetiedot"
        (ok (p/fetch-hakukohteen-pistetiedot hakuapp datasource hakuOID hakukohdeOID)))
        

      (GET "/haku/:hakuOID/hakemus/:hakemusOID" 
        [hakuOID hakemusOID]
        :return PistetietoWrapper
        :summary "Hakemuksen pistetiedot"
          (ok (first (p/fetch-hakemusten-pistetiedot datasource hakuOID [hakemusOID]))))
        

      (PUT "/haku/:hakuOID/hakukohde/:hakukohdeOID" 
        [hakuOID hakukohdeOID]
        :body [uudet_pistetiedot [PistetietoWrapper]]
        :summary "Syötä pistetiedot hakukohteen avaimilla"
        (let [returns_nothing_if_succeeds (p/update-pistetiedot datasource hakuOID hakukohdeOID uudet_pistetiedot)]
          (ok))))))

(defn -main []

  (let [config (c/readConfigurationFile)
        abc (prn (-> config :db :password))
        datasource (pool/datasource config)
        mongoConnection (mongo/connection config)
        ]
    (db/migrate datasource)
    (run-jetty (app (partial mongo/hakemusOidsForHakukohde {:db mongoConnection}) datasource) {:port (-> config :server :port)}) ))

