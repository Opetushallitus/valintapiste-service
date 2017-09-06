(ns valintapiste-service.pistetiedot
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "queries.sql")

(defn fetchHakemuksenPistetiedot 
    "Returns pistetiedot for hakemus"
    [hakuOID hakemusOID]
    {:hakemusOID hakemusOID :pisteet {}})


(defn fetchHakukohteenPistetiedot 
    "Returns pistetiedot for hakukohde"
    [hakuOID hakukohdeOID]
    [])
