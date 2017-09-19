(ns valintapiste-service.pistetiedot
  (:require [jeesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]))

(defqueries "queries.sql")

(defn parse-rows-by-hakemus-oid
    [rows]
    (let [group-by-hakemus-oid (group-by :hakemus_oid rows)]
          (map (fn [entry] (let [hakemus_oid (first entry)
                                 pisteet (second entry)] 
                                 {:hakemusOID hakemus_oid
                                  :pisteet (map (fn [p] (dissoc p :hakemus_oid)) pisteet)}) ) group-by-hakemus-oid) ))

(defn fetch-hakemusten-pistetiedot 
    "Returns pistetiedot for hakemus"
    [datasource hakuOID hakemusOIDs]
    (let [connection {:datasource datasource}
          data (jdbc/with-db-transaction [tx connection] 
            {:last-modified (last-modified-for-hakemukset tx {:hakemus-oids hakemusOIDs})
             :rows (find-valintapisteet-for-hakemukset tx {:hakemus-oids hakemusOIDs})})
          by-hakemus-oid (parse-rows-by-hakemus-oid (-> data :rows))
          found-hakemus-oids (map :hakemusOID by-hakemus-oid)
          missing-hakemus-oids (clojure.set/difference (set hakemusOIDs) (set found-hakemus-oids))]
          {:last-modified (-> data :last-modified) 
           :hakemukset (vec (concat by-hakemus-oid (map (fn [hk] {:hakemusOID hk :pisteet []}) missing-hakemus-oids)))}))

(defn fetch-hakukohteen-pistetiedot 
  "Returns pistetiedot for hakukohde"
  [hakuapp datasource hakuOID hakukohdeOID]
  (let [hakemus_oids (hakuapp hakuOID hakukohdeOID)]
        (fetch-hakemusten-pistetiedot datasource hakuOID hakemus_oids)))

(defn update-pistetiedot
  "Updates pistetiedot"
  [datasource hakuOID hakukohdeOID pistetietowrappers]
  (let [connection {:datasource datasource}]
    (doseq [hakemus pistetietowrappers
            piste (:pisteet hakemus)]
            (let [hakemusOID (:hakemusOID hakemus)
                  osallistuminen (doto (org.postgresql.util.PGobject.)
                                    (.setType "osallistumistieto")
                                    (.setValue (:osallistuminen piste)))
                  row (merge piste {:hakemus-oid hakemusOID :osallistuminen osallistuminen})]
                (upsert-valintapiste! connection row)))))
