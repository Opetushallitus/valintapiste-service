(ns valintapiste-service.pistetiedot
  (:require [jeesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :refer [blank?]]
            [valintapiste-service.siirtotiedosto :as siirtotiedosto]))

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
  "Returns pistetiedot for hakemus (max 32767)"
  [datasource hakemukset]
  (if (> (count hakemukset) 32767) (throw (IllegalArgumentException. "Max number of hakemukset is 32767!")))
  ;Postgres has hard coded limit 32767 for number of parameters in prepared statement in JDBC.
  ;If more than 32767 hakemus oids is given to sql query below, PostgreSQL will fail with error code SQLSTATE(08006)
  ;Caused by: java.io.IOException: Tried to send an out-of-range integer as a 2-byte value: 32768
  (let [connection {:datasource datasource}
        hakemusOIDs (map :oid hakemukset)
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

(defn ataru-hakemus-as-oid-and-personOid [h]
  (let [hakemus (clojure.walk/keywordize-keys h)]
    {:oid       (:hakemus_oid hakemus)
     :personOid (:henkilo_oid hakemus)}))

(defn fetch-hakukohteen-pistetiedot
  "Returns pistetiedot for hakukohde"
  [hakuapp ataruapp datasource hakuOID hakukohdeOID]
  (let [hakemukset (hakuapp hakuOID hakukohdeOID)
        hakemukset-from-ataru (map ataru-hakemus-as-oid-and-personOid (ataruapp hakuOID hakukohdeOID))]
    (fetch-hakemusten-pistetiedot datasource (concat hakemukset hakemukset-from-ataru))))

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

(defn- pistetieto-row-for-update [row]
  (if-not (row :arvo)
    (assoc row :arvo nil)
    row))

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
                                               (upsert-valintapiste! tx (pistetieto-row-for-update row))))
                                         conflicting-hakemus-oids))] data)))

(defn create-siirtotiedostot-for-pistetiedot
  "Create siirtotiedosto containing pistetiedot hakemuksittain"
  [datasource siirtotiedosto-client start-datetime end-datetime max-hakemuscount-in-file]
  (if (> max-hakemuscount-in-file 32767) (throw (IllegalArgumentException. (str "Illegal value " max-hakemuscount-in-file " for number of hakemukset per file, max number is 32767!"))))
  (let [connection {:datasource datasource}
        rows (jdbc/with-db-transaction [tx connection]
                                               (if-not (nil? start-datetime)
                                                 (find-hakemus-oids-by-timerange tx {:start start-datetime :end end-datetime})
                                                 (find-hakemus-oids-by-timelimit tx {:end end-datetime})))
        hakemus-oids (map (fn [row] (:hakemus_oid row)) rows)
        partitions (partition max-hakemuscount-in-file max-hakemuscount-in-file nil hakemus-oids)
        results (map
                  (fn [partition]
                    (let [pistetiedot-for-partition
                          (jdbc/with-db-transaction [tx connection]
                                                    (find-valintapisteet-for-hakemukset tx {:hakemus-oids partition}))
                          pistetiedot-by-hakemukset (parse-rows-by-hakemus-oid pistetiedot-for-partition)]
                      (siirtotiedosto/create-siirtotiedosto
                        siirtotiedosto-client start-datetime end-datetime pistetiedot-by-hakemukset))) partitions)]
    {:keys (filter #(not (blank? %)) results)
     :total (count hakemus-oids)
     :success (every? #(not (blank? %)) results)}))
