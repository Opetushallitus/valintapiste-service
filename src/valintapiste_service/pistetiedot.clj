(ns valintapiste-service.pistetiedot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "queries.sql")

(defn fetch-hakemuksen-pistetiedot 
    "Returns pistetiedot for hakemus"
    [connection hakuOID hakemusOID]
    (find-valintapisteet-for-hakemus connection {:hakemus-oid hakemusOID})
    {:hakemusOID hakemusOID :pisteet {}})

(defn fetch-hakukohteen-pistetiedot 
  "Returns pistetiedot for hakukohde"
    [connection hakuOID hakukohdeOID]
    (find-valintapisteet-for-hakemukset connection {:hakemus-oids []})
    [])
