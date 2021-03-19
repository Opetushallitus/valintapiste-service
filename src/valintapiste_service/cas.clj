(ns valintapiste-service.cas
  (:require [clj-http.client :as client]
            [clj-http.cookies :as cookies]
            [jsoup.soup :as jsoup]))

(defn- parse-tgt-url [html]
  (first (jsoup/attr "action" (jsoup/$ (jsoup/parse html) "form"))))

(defn post-form-encoded [url params expected-status timeout-ms caller-id]
  (let [response (client/post url {:form-params params
                                   :socket-timeout timeout-ms
                                   :conn-timeout timeout-ms
                                   :headers {"caller-id" caller-id
                                             "CSRF" caller-id}
                                   :cookies { "CSRF" {:value caller-id :path "/"}}
                                   :cookie-policy :standard})]
    (or (and (= (:status response) expected-status)
             (:body response))
        (throw (RuntimeException. (format "Unexpected status %s!" (:status response)))))))

(defn service-ticket [tgt service timeout-ms caller-id]
  (post-form-encoded tgt
                     {:service (str service)}
                     200
                     timeout-ms
                     caller-id))

(defn ticket-granting-ticket [host-virkailija username password timeout-ms caller-id]
  (try
    (parse-tgt-url (post-form-encoded (str host-virkailija "/cas/v1/tickets")
                                      {:username username
                                       :password password}
                                      201
                                      timeout-ms
                                      caller-id))
    (catch Exception e (throw (RuntimeException. "Unable to get TGT!")))))
