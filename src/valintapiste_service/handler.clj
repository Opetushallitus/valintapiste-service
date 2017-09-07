(ns valintapiste-service.handler
  (:require [compojure.api.sweet :refer :all]
            [valintapiste-service.pistetiedot :as p]
            [valintapiste-service.config :as c]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer :all]
            [valintapiste-service.pool :as pool]
            [schema.core :as s]
            [valintapiste-service.haku.haku :as mongo]
            [valintapiste-service.db :as db]))

(s/defschema Pistetieto
  {:hakemusOID s/Str
   :pisteet {s/Keyword s/Str}})

(defn app
  "This is the App"
  [mongo datasource]
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
        :return [Pistetieto]
        :summary "Hakukohteen hakemusten pistetiedot"
        (ok (p/fetch-hakukohteen-pistetiedot datasource hakuOID hakukohdeOID)))

      (GET "/haku/:hakuOID/hakemus/:hakemusOID" 
        [hakuOID hakemusOID]
        :return Pistetieto
        :summary "Hakemuksen pistetiedot"
        (ok (p/fetch-hakemuksen-pistetiedot datasource hakuOID hakemusOID)))

      (PUT "/haku/:hakuOID/hakukohde/:hakukohdeOID" 
        [hakuOID hakukohdeOID]
        :body [uudet_pistetiedot [Pistetieto]]
        :return s/Int
        :summary "Syötä pistetiedot hakukohteen avaimilla"
        (ok (p/update-pistetiedot datasource hakuOID hakukohdeOID uudet_pistetiedot))))))

(defn -main []

  (let [config (c/readConfigurationFile)
        abc (prn (-> config :db :password))
        datasource (pool/datasource config)
        ;mongoConnection (mongo/connection config)
        ]
    (db/migrate datasource)
    (run-jetty (app "mongo" datasource) {:port (-> config :server :port)}) ))

