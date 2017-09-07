(ns valintapiste-service.db)

(def PATHS_TO_MIGRATIONS (into-array java.lang.String ["migrations"]))

(defn migrate [datasource]
  (doto (new org.flywaydb.core.Flyway)
    (.setDataSource datasource)
    (.setLocations PATHS_TO_MIGRATIONS)
    (.migrate)))
