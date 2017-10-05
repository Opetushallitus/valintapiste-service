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

(defn add-to-entry [new_entries entry]
  (let [not_nil_new_entries (into {} (filter (comp some? val) new_entries))]
    (merge not_nil_new_entries entry)))

(defn add-oppija-oid [hakemus-oid-to-hakemus hakemukset]
  (map (fn [entry] 
    (let [hakemusOID (-> entry :hakemusOID)
          hakemus (hakemus-oid-to-hakemus hakemusOID)
          oppijaOID (-> hakemus :personOid)
          etunimet (get-in hakemus [:answers :henkilotiedot :Etunimet] nil)
          sukunimi (get-in hakemus [:answers :henkilotiedot :Sukunimi] nil)]
      (add-to-entry { :oppijaOID oppijaOID
                      :etunimet etunimet
                      :sukunimi sukunimi } entry))) hakemukset))

(defn fetch-hakemusten-pistetiedot 
    "Returns pistetiedot for hakemus"
    [datasource hakuOID hakemukset]
    (let [connection {:datasource datasource}
          hakemusOIDs (map (-> :oid) hakemukset)
          hakemus-oid-to-hakemus (zipmap (map :oid hakemukset) hakemukset)
          data (jdbc/with-db-transaction [tx connection] 
            {:last-modified (new org.joda.time.DateTime (first (map (fn [i] (-> :lower i)) (last-modified-for-hakemukset tx {:hakemus-oids hakemusOIDs}))))
             :rows (find-valintapisteet-for-hakemukset tx {:hakemus-oids hakemusOIDs})})
          by-hakemus-oid (add-oppija-oid hakemus-oid-to-hakemus (parse-rows-by-hakemus-oid (-> data :rows)))
          found-hakemus-oids (map :hakemusOID by-hakemus-oid)
          missing-hakemus-oids (clojure.set/difference (set hakemusOIDs) (set found-hakemus-oids))]
          {:last-modified (-> data :last-modified) 
           :hakemukset (vec (concat by-hakemus-oid 
                               (add-oppija-oid hakemus-oid-to-hakemus 
                                  (map (fn [hk] {:hakemusOID hk :pisteet []}) missing-hakemus-oids))))}))

(defn fetch-hakukohteen-pistetiedot 
  "Returns pistetiedot for hakukohde"
  [hakuapp datasource hakuOID hakukohdeOID]
  (let [hakemukset (hakuapp hakuOID hakukohdeOID)]
        (fetch-hakemusten-pistetiedot datasource hakuOID hakemukset)))

(defn check-update-conflict [tx hakemusOIDs unmodified-since] 
  (if unmodified-since 
    (map (fn [hakemus] (-> hakemus :hakemus_oid)) (modified-since-hakemukset tx {:hakemus-oids hakemusOIDs :unmodified-since unmodified-since}))
    []))

(defn update-pistetiedot
  "Updates pistetiedot"
  [datasource hakuOID hakukohdeOID pistetietowrappers unmodified-since]
  (let [connection {:datasource datasource}
        hakemusOIDs (map (fn [hakemus] (-> hakemus :hakemusOID)) pistetietowrappers)
        data (jdbc/with-db-transaction [tx connection] 
          (let [conflicting-hakemus-oids (check-update-conflict tx hakemusOIDs unmodified-since)]
            (if (empty? conflicting-hakemus-oids) (doseq [hakemus pistetietowrappers
                        piste (:pisteet hakemus)]
                        (let [hakemusOID (:hakemusOID hakemus)
                              osallistuminen (doto (org.postgresql.util.PGobject.)
                                                (.setType "osallistumistieto")
                                                (.setValue (:osallistuminen piste)))
                              row (merge piste {:hakemus-oid hakemusOID :osallistuminen osallistuminen})]
                            (upsert-valintapiste! tx row))) conflicting-hakemus-oids)))] data))
