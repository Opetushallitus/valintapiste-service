(ns valintapiste-service.access
  (:require [cheshire.core :refer :all])
  (:import [org.eclipse.jetty.server.handler
            HandlerCollection
            RequestLogHandler]
           (org.eclipse.jetty.server Slf4jRequestLog)))

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
        (proxy-super write (generate-string message))))))