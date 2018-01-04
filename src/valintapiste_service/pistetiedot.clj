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
                        :pisteet    (map (fn [p] (dissoc p :hakemus_oid)) pisteet)})) group-by-hakemus-oid)))

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
           (add-to-entry {:oppijaOID oppijaOID
                          :etunimet  etunimet
                          :sukunimi  sukunimi} entry))) hakemukset))

(def europe-helsinki (org.joda.time.DateTimeZone/forID "Europe/Helsinki"))
(defn parse-date-time [stamp] (new java.sql.Timestamp (.getMillis (.parseDateTime (org.joda.time.format.ISODateTimeFormat/dateTime) stamp))))

(defn convert-timestamp [sqldate]
  (if sqldate
    (let [date-time (new org.joda.time.DateTime sqldate)
          date-time-with-zone (.withZone date-time europe-helsinki)
          date-time-inside-duration (.plusSeconds date-time-with-zone 1)]
      date-time-inside-duration) nil))

(defn fetch-hakemusten-pistetiedot
  "Returns pistetiedot for hakemus"
  [datasource hakemukset]
  (let [connection {:datasource datasource}
        hakemusOIDs (map (-> :oid) hakemukset)
        hakemus-oid-to-hakemus (zipmap (map :oid hakemukset) hakemukset)
        data (jdbc/with-db-transaction [tx connection]
                                       {:last-modified (convert-timestamp (first (map (fn [i] (-> :lower i)) (last-modified-for-hakemukset tx {:hakemus-oids hakemusOIDs}))))
                                        :rows          (find-valintapisteet-for-hakemukset tx {:hakemus-oids hakemusOIDs})})
        by-hakemus-oid (add-oppija-oid hakemus-oid-to-hakemus (parse-rows-by-hakemus-oid (-> data :rows)))
        found-hakemus-oids (map :hakemusOID by-hakemus-oid)
        missing-hakemus-oids (clojure.set/difference (set hakemusOIDs) (set found-hakemus-oids))]
    {:last-modified (-> data :last-modified)
     :hakemukset    (vec (concat by-hakemus-oid
                                 (add-oppija-oid hakemus-oid-to-hakemus
                                                 (map (fn [hk] {:hakemusOID hk :pisteet []}) missing-hakemus-oids))))}))

(defn fetch-hakukohteen-pistetiedot
  "Returns pistetiedot for hakukohde"
  [hakuapp datasource hakuOID hakukohdeOID]
  (let [hakemukset (hakuapp hakuOID hakukohdeOID)]
    (fetch-hakemusten-pistetiedot datasource hakemukset)))

(defn check-update-conflict [tx hakemusOIDs unmodified-since]
  (if unmodified-since
    (let [conflicting (map (fn [hakemus]
                             (-> hakemus :hakemus_oid))
                           (modified-since-hakemukset tx {:hakemus-oids hakemusOIDs :unmodified-since unmodified-since}))]
      conflicting)
    []))

(defn- pistetieto-to-rows [pistetieto]
  (let [hakemusOID (:hakemusOID pistetieto)]
    (map (fn [piste] (merge piste {:hakemus-oid hakemusOID :osallistuminen (doto (org.postgresql.util.PGobject.)
                                                                             (.setType "osallistumistieto")
                                                                             (.setValue (:osallistuminen piste)))})) (:pisteet pistetieto))))
(defn- pistetiedot-to-rows [pistetiedot]
  (mapcat pistetieto-to-rows pistetiedot))

(defn update-pistetiedot
  "Updates pistetiedot"
  ([datasource pistetietowrappers unmodified-since]
    (update-pistetiedot datasource pistetietowrappers unmodified-since false))
  ([datasource pistetietowrappers unmodified-since save-partially?]
   (let [connection {:datasource datasource}
        hakemusOIDs (map (fn [hakemus] (-> hakemus :hakemusOID)) pistetietowrappers)
        data (jdbc/with-db-transaction [tx connection]
                                       (let [conflicting-hakemus-oids (set (check-update-conflict tx hakemusOIDs unmodified-since))
                                             rows (filter (fn [r] (not (contains? conflicting-hakemus-oids (:hakemus-oid r)))) (pistetiedot-to-rows pistetietowrappers))]
                                         (if (or (empty? conflicting-hakemus-oids) save-partially?)
                                           (doseq [row rows]
                                             (upsert-valintapiste! tx row)))
                                         conflicting-hakemus-oids))] data)))
