(ns valintapiste-service.handler
  (:require [compojure.api.sweet :refer :all]
            [valintapiste-service.pistetiedot :as p]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema Pistetieto
  {:hakemusOID s/Str
   :pisteet {s/Str s/Str}})

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
        (ok (p/fetchHakukohteenPistetiedot hakuOID hakukohdeOID)))

      (GET "/haku/:hakuOID/hakemus/:hakemusOID" 
        [hakuOID hakemusOID]
        :return Pistetieto
        :summary "Hakemuksen pistetiedot"
        (ok (p/fetchHakemuksenPistetiedot hakuOID hakemusOID))))))
      
(defn -main []
  (let [migrate "Execute migration first!"
        mongo "Connect to MongoDB"
        postgre "Create connection pool"]
    (run-jetty (app mongo postgre) {:port 8000}) ))