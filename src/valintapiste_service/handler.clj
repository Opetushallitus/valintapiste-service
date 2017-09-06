(ns valintapiste-service.handler
  (:require [compojure.api.sweet :refer :all]
            [valintapiste-service.pistetiedot :as p]
            [valintapiste-service.config :as c]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer :all]
            [environ.core :refer [env]]
            [schema.core :as s]
            [valintapiste-service.db :as db]))

(s/defschema Pistetieto
  {:hakemusOID s/Str
   :pisteet {s/Keyword s/Str}})

(defn app
  "This is the App"
  [mongo postgre]
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
        (ok (p/fetch-hakukohteen-pistetiedot postgre hakuOID hakukohdeOID)))

      (GET "/haku/:hakuOID/hakemus/:hakemusOID" 
        [hakuOID hakemusOID]
        :return Pistetieto
        :summary "Hakemuksen pistetiedot"
        (ok (p/fetch-hakemuksen-pistetiedot postgre hakuOID hakemusOID)))

      (PUT "/haku/:hakuOID/hakukohde/:hakukohdeOID" 
        [hakuOID hakukohdeOID]
        :body [uudet_pistetiedot [Pistetieto]]
        :return s/Int
        :summary "Syötä pistetiedot hakukohteen avaimilla"
        (ok 2)))))

(defn -main []

  (let [config (c/readConfigurationFile (env :valintapisteservice-properties))
        migrate "Execute migration first!"
        mongo "Connect to MongoDB"
        postgre "Create connection pool"]
    (db/migrate)
    (run-jetty (app mongo postgre) {:port 8000}) ))

