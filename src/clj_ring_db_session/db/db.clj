(ns clj-ring-db-session.db.db
  (:require [clojure.java.jdbc :as jdbc]))

(defmacro exec [datasource query params]
  `(jdbc/with-db-transaction [connection# {:datasource ~datasource}]
                             (~query ~params {:connection connection#})))
