(ns valintapiste-service.pistetiedot
  (:require [jeesql.core :refer [defqueries]]
            [valintapiste-service.haku.haku :as hakuapp]))

(defn to-pistetieto [row]
  {(:tunniste row)
   {:tunniste row
    :arvo (:arvo row)
    :osallistuminen (:osallistuminen row)
    :tallettaja (:tallettaja row)}})

(defqueries "queries.sql")

(defn fetch-hakemuksen-pistetiedot 
    "Returns pistetiedot for hakemus"
    [datasource hakuOID hakemusOID]
    (let [connection {:datasource datasource}
          pistetiedot (find-valintapisteet-for-hakemus connection {:hakemus-oid hakemusOID})]
      {:hakemusOID hakemusOID :pisteet pistetiedot}))

(defn fetch-hakukohteen-pistetiedot 
  "Returns pistetiedot for hakukohde"
  [hakuapp datasource hakuOID hakukohdeOID]
  (let [hakemus_oids (hakuapp hakuOID hakukohdeOID)]
    (find-valintapisteet-for-hakemukset {:datasource datasource} {:hakemus-oids hakemus_oids})
    []))

(defn- upsert-pisteet! [connection hakemus-oid pisteet]
  (if (empty? pisteet)
    0
    (let [piste (first pisteet)
          tunniste (name (first piste))
          pistetieto (first (rest piste))
          osallistuminen (doto (org.postgresql.util.PGobject.)
                           (.setType "osallistumistieto")
                           (.setValue (:osallistuminen pistetieto)))
          query-data {:hakemus-oid hakemus-oid
                :tunniste tunniste
                :arvo (:arvo pistetieto)
                :osallistuminen osallistuminen
                :tallettaja (:tallettaja pistetieto)}]
      (+ (upsert-valintapiste! connection query-data)
         (upsert-pisteet! connection hakemus-oid (rest pisteet))))))

(defn- update-pistetiedot-rec [connection pistetietowrappers]
  (if (empty? pistetietowrappers)
    0
    (let [pistetietowrapper (first pistetietowrappers)
          hakemus-oid (:hakemusOID pistetietowrapper)
          pisteet (seq (:pisteet pistetietowrapper))]
      (+ (upsert-pisteet! connection hakemus-oid pisteet)
         (update-pistetiedot-rec connection (rest pistetietowrappers))))))

(defn update-pistetiedot
  "Updates pistetiedot"
  [datasource hakuOID hakukohdeOID pistetietowrappers]
  (let [connection {:datasource datasource}]
    (update-pistetiedot-rec connection pistetietowrappers)))
