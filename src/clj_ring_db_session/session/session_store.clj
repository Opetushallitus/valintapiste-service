(ns clj-ring-db-session.session.session-store
  (:require [ring.middleware.session.store :refer [SessionStore]]
            [yesql.core :refer [defqueries]]
            [clj-ring-db-session.db.db :refer [exec]])
  (:import (java.util UUID)))

(defqueries "sql/session-queries.sql")

(defn generate-new-random-key [] (str (UUID/randomUUID)))

(defprotocol OphSessionStore
  (read-data [this key])
  (add-data [this key data])
  (save-data [this key data])
  (delete-data [this key])
  (logout-by-ticket! [this ticket]))

(defrecord DatabaseStore [datasource]

  SessionStore
  (read-session [this key]
    (read-data this key))
  (write-session [this key data]
    (if key
      (save-data this key data)
      (add-data this (generate-new-random-key) data)))
  (delete-session [this key]
    (delete-data this key)
    nil)

  OphSessionStore
  (read-data [_ key]
    (when-let [data (:data (first (exec datasource yesql-get-session-query {:key key})))]
      (assoc data :key key)))

  (add-data [_ key data]
    (exec datasource yesql-add-session-query! {:key key :data (dissoc data :key)})
    key)

  (save-data [_ key data]
    (exec datasource yesql-update-session-query! {:key key :data (dissoc data :key)})
    key)

  (delete-data [_ key]
    (exec datasource yesql-delete-session-query! {:key key})
    key)

  (logout-by-ticket! [_ ticket]
    (exec datasource yesql-logout-by-ticket-query! {:ticket ticket})))

(defn create-session-store [datasource] (->DatabaseStore datasource))
