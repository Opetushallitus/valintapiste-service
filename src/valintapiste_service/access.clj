(ns valintapiste-service.access
  (:require [cheshire.core :refer :all]
            [clojure.tools.logging.impl :as impl])
  (:import [org.eclipse.jetty.server.handler
            HandlerCollection
            RequestLogHandler]
           (org.eclipse.jetty.server Slf4jRequestLog)))

(def ^{:private true} access-logger-impl (impl/get-logger (impl/find-factory) "ACCESS"))

(defn- nil-to-slash [m]
    (into {} (for [[k v] m] [k (if (nil? v)
                                 "-"
                                 (str v))])))

(defn access-logger [environment]
  (proxy [Slf4jRequestLog] []
    (log [req resp]
      (let [message (nil-to-slash {:timestamp (.print (org.joda.time.format.ISODateTimeFormat/dateTime) (System/currentTimeMillis))
                                   :responseCode (.getStatus resp)
                                   :request (str (.getMethod req) (.getRequestURI req))
                                   :responseTime nil
                                   :requestMethod (.getMethod req)
                                   :service "valintapiste-service"
                                   :environment environment
                                   :customer "OPH"
                                   :user-agent (.getHeader req "user-agent")
                                   :caller-id nil
                                   :x-forwarded-for (.getHeader req "x-forwarded-for")
                                   :remote-ip (.getRemoteAddr req)
                                   :session nil
                                   :response-size (.getWritten (.getOutputStream resp))
                                   :referer nil})]
        (access-logger-impl (generate-string message))))))