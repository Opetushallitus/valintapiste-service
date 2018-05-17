(ns valintapiste-service.cas
  (:require [clj-http.client :as client]
            [jsoup.soup :as jsoup])
  (:import (org.apache.http.conn.util PublicSuffixMatcherLoader)))

(defn- parse-tgt-url [html]
  (first (jsoup/attr "action" (jsoup/$ (jsoup/parse html) "form"))))

(defn post-form-encoded [cookie-store url params expected-status]
  (let [response (client/post url {:form-params params
                                   :cookie-store cookie-store
                                   :cookie-policy :standard})]
    (or (and (= (:status response) expected-status)
             (:body response))
        (throw (RuntimeException. (format "Unexpected status %s!" (:status response)))))))

(defn service-ticket [tgt cookie-store service]
  (post-form-encoded cookie-store
                     tgt
                     {:service (str service)}
                     200))

(defn ticket-granting-ticket [cookie-store host-virkailija username password]
  (try
    (parse-tgt-url (post-form-encoded cookie-store
                                      (str host-virkailija "/cas/v1/tickets")
                                      {:username username
                                       :password password}
                                      201))
    (catch Exception e (throw (RuntimeException. "Unable to get TGT!")))))
