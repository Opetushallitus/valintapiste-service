(ns valintapiste-service.ataru
  (:require [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [valintapiste-service.cas :refer [ticket-granting-ticket service-ticket]]
            [clojure.tools.logging :as log])
  (:import (java.util Date)
           (java.util.concurrent TimeUnit)))

(def ATARU-TILASTOKESKUS-READ-URL "%s/lomake-editori/api/external/tilastokeskus?hakuOid=%s&hakukohdeOid=%s")
(def SYNC-FETCH-LOCK (Object.))
(def SESSION-TTL-IN-MILLIS (.toMillis TimeUnit/MINUTES (long 30)))
(def SESSION-FETCH-TIMEOUT (.toMillis TimeUnit/SECONDS (long 5)))
(def SOCKET-TIMEOUT (.toMillis TimeUnit/SECONDS (long 30)))

(defn invalidate-cas-session [cached-session current-session]
  (if (compare-and-set! cached-session current-session nil)
    (log/info (format "Invalidating session after %s usages" (:times-used-successfully current-session)))))

(defn is-session-still-valid? [cached-session]
  (if-let [current-session @cached-session]
    (let [timestamp (:timestamp current-session)
          difference (- (.getTime (Date.)) (.getTime timestamp))]
      (if (< difference SESSION-TTL-IN-MILLIS)
        current-session
        (invalidate-cas-session cached-session current-session)))))

(defn force-fetch-new-session [host-virkailija username password]
  (log/info "Fetching new CAS session for Ataru!")
  (let [cs (clj-http.cookies/cookie-store)
        service-ticket (-> (ticket-granting-ticket cs host-virkailija username password SESSION-FETCH-TIMEOUT)
                           (service-ticket cs (str host-virkailija "/lomake-editori/auth/cas") SESSION-FETCH-TIMEOUT))
        auth-url (format "%s/lomake-editori/auth/cas?ticket=%s" host-virkailija service-ticket)
        auth-response (client/get auth-url
                        {:cookie-store cs
                         :socket-timeout SESSION-FETCH-TIMEOUT
                         :conn-timeout SESSION-FETCH-TIMEOUT
                         :cookie-policy :standard})]
    (or (and (= (:status auth-response) 200)
             {:timestamp (Date.)
              :times-used-successfully 0
              :session service-ticket
              :cookie-store cs})
        (throw (RuntimeException. "Unable to create new auth session!")))))

(defn get-cas-session [host-virkailija username password cached-session]
  (or (is-session-still-valid? cached-session)
      (locking SYNC-FETCH-LOCK
        (or (is-session-still-valid? cached-session)
            (reset! cached-session (force-fetch-new-session host-virkailija username password))))))

(defn add-successful-usage [cached-session my-session]
  (let [inc-if-same (fn [s]
                        (if (= (:session my-session) (:session s))
                          (update s :times-used-successfully inc)
                          s))]
    (swap! cached-session (fn [s]
                              (some-> s
                                inc-if-same)))))

(defn fetch-from-ataru-with-session [some-session url]
  (->
   (client/get url {:cookie-store          (:cookie-store some-session)
                    :follow-redirects      false
                    :throw-entire-message? true
                    :socket-timeout SOCKET-TIMEOUT
                    :conn-timeout SESSION-FETCH-TIMEOUT
                    :cookie-policy         :standard})
   :body
   (cheshire/parse-string)))

(defn hakemus-oids-for-hakukohde [host-virkailija username password]
  (let [cached-session (atom nil)]
    (fn [haku-oid hakukohde-oid]
        (let [tilastokeskus-read-url (format ATARU-TILASTOKESKUS-READ-URL host-virkailija haku-oid hakukohde-oid)
              some-session           (get-cas-session host-virkailija username password cached-session)
              hakemukset             (fetch-from-ataru-with-session some-session tilastokeskus-read-url)]
          (add-successful-usage cached-session some-session)
          hakemukset))))