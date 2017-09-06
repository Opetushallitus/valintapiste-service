(ns valintapiste-service.pistetiedot
  (:require [jeesql.core :refer [defqueries]]))

(defn to-pistetieto [row]
  {(:tunnus row) (:arvo row)})

(defqueries "queries.sql")

(defn fetch-hakemuksen-pistetiedot 
    "Returns pistetiedot for hakemus"
    [connection hakuOID hakemusOID]
    (let [pistetiedot (find-valintapisteet-for-hakemus connection {:hakemus-oid hakemusOID})]
      {:hakemusOID hakemusOID :pisteet pistetiedot}))

(defn fetch-hakukohteen-pistetiedot 
  "Returns pistetiedot for hakukohde"
    [connection hakuOID hakukohdeOID]
    (find-valintapisteet-for-hakemukset connection {:hakemus-oids []})
    [])
