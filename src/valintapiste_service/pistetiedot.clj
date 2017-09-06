(ns valintapiste-service.pistetiedot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "queries.sql")

(defn fetchHakemuksenPistetiedot 
    "Returns pistetiedot for hakemus"
    [connection hakuOID hakemusOID]
    (find-valintapisteet-for-hakemus connection {:hakemus_oid hakemusOID})
    {:hakemusOID hakemusOID :pisteet {}})

(defn fetchHakukohteenPistetiedot 
  "Returns pistetiedot for hakukohde"
    [connection hakuOID hakukohdeOID]
    (find-valintapisteet-for-hakemukset connection {:hakemus_oids []})
    [])
