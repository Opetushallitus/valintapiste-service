(ns valintapiste-service.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema Pistetieto
  {:hakemusOID s/Str
   :pisteet {s/Str s/Str}})

(defn app
  "This is the App"
  [state]
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info {:title "Valintapiste-service"
                    :description "Pistetiedot"}
             }}}

    (context "/api" []
      :tags ["api"]

      (GET "/haku/:hakuOID/hakukohde/:hakukohdeOID" []
        :return [Pistetieto]
        :summary "Hakukohteen hakemusten pistetiedot"
        (ok []))

      (GET "/haku/:hakuOID/hakemus/:hakemusOID" []
        :return Pistetieto
        :summary "Hakemuksen pistetiedot"
        (ok {:hakemusOID "1.2.3.4" :pisteet {}})))))
      
(defn -main []
  (run-jetty (app 66) {:port 8000}) )