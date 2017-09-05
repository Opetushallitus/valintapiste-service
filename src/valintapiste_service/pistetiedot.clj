(ns valintapiste-service.pistetiedot)

(defn fetchHakemuksenPistetiedot 
    "Returns pistetiedot for hakemus"
    [hakuOID hakemusOID]
    {:hakemusOID hakemusOID :pisteet {}})


(defn fetchHakukohteenPistetiedot 
    "Returns pistetiedot for hakukohde"
    [hakuOID hakukohdeOID]
    [])
