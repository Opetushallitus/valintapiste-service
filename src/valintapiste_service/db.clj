(ns valintapiste-service.db)

(defn migrate [datasource]
  (doto (new org.flywaydb.core.Flyway)
    (.setDataSource datasource)
    (.setLocations (into-array java.lang.String ["migrations"]))
    (.migrate)))
