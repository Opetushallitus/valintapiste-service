(ns valintapiste-service.pistetiedot
  (:require [jeesql.core :refer [defqueries]]))

(defn to-pistetieto [row]
  {(:tunnus row) (:arvo row)})

(defqueries "queries.sql")

(defn fetch-hakemuksen-pistetiedot 
    "Returns pistetiedot for hakemus"
    [datasource hakuOID hakemusOID]
    (let [connection {:datasource datasource}
        pistetiedot (find-valintapisteet-for-hakemus connection {:hakemus-oid hakemusOID})]
      {:hakemusOID hakemusOID :pisteet pistetiedot}))

(defn fetch-hakukohteen-pistetiedot 
  "Returns pistetiedot for hakukohde"
  [datasource hakuOID hakukohdeOID]
  (let [hakemus_oids []] ;TODO: Resolve hakemus_oids based on hakukohde oid here
    (find-valintapisteet-for-hakemukset {:datasource datasource} {:hakemus-oids hakemus_oids})
    []))

(defn- upsert-pisteet! [connection hakemus-oid pisteet]
  (if (empty? pisteet)
    0
    (let [piste (first pisteet)
          data {:hakemus-oid hakemus-oid
                :tunniste (first piste)
                :arvo (first (rest piste))}]
      (+ (upsert-valintapiste! connection data)
         (upsert-pisteet! connection hakemus-oid (rest pisteet))))))

(defn- update-pistetiedot-rec [connection pistetiedot]
  (if (empty? pistetiedot)
    0
    (let [pistetieto (first pistetiedot)
          hakemus-oid (:hakemusOID pistetieto)
          pisteet (seq (:pisteet pistetieto))]
      (+ (upsert-pisteet! connection hakemus-oid pisteet)
         (update-pistetiedot-rec connection (rest pistetiedot))))))

(defn update-pistetiedot
  "Updates pistetiedot"
  [datasource hakuOID hakukohdeOID pistetiedot]
  (let [connection {:datasource datasource}]
    (update-pistetiedot-rec connection pistetiedot)))

