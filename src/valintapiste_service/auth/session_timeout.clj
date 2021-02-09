(ns valintapiste-service.auth.session-timeout
    (:require [cheshire.core :as json]
              [clojure.string :as string]
              [ring.middleware.session-timeout :as session-timeout]
              [ring.util.http-response :as response]))

(defn- timeout-handler [auth-url]
       (fn [{:keys [uri]}]
           (if (string/starts-with? uri "/valintapiste-service/") ;fixme
               (response/unauthorized (json/generate-string {:redirect auth-url}))
               (response/found auth-url))))

(defn- timeout-options [config]
       {:timeout         (get-in config [:session :timeout] 28800)
        :timeout-handler (timeout-handler (-> config :cas.login))})

(defn wrap-idle-session-timeout [config]
      (fn [handler]
          (let [options (timeout-options config)]
               (session-timeout/wrap-idle-session-timeout handler options))))
