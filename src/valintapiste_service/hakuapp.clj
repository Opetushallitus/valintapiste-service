(ns valintapiste-service.hakuapp
    (:require [monger.core :as mg]
        [monger.collection :as mc]
        [monger.operators :refer :all]
        [monger.query :as mq])
    (:import [com.mongodb MongoOptions ServerAddress]))

(defn connection 
    "Establish connection with MongoURI"
    [config]
    (mg/connect-via-uri (-> config :mongo :uri)))

(defn hakemus-oids-for-hakukohde
    "Fetch all active 'hakemus OIDs' in 'hakukohde'"
    [db hakuOID hakukohdeOID]
    (let [coll "application"]
          (mq/with-collection db coll
            (mq/find { 
                "applicationSystemId" hakuOID 
                "authorizationMeta.applicationPreferences.preferenceData.Koulutus-id" hakukohdeOID
                "state" { $in ["ACTIVE", "INCOMPLETE"] }
                })
            (mq/fields ["oid" "personOid" "answers.henkilotiedot.Etunimet" "answers.henkilotiedot.Sukunimi"]))))
