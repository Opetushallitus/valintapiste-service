 (defproject valintapiste-service "0.1.0-SNAPSHOT"
   :description "FIXME: write description"
   :dependencies [[org.clojure/clojure "1.8.0"]
                  [clj-http "3.7.0"]
                  [metosin/compojure-api "1.1.11"]
                  [ring/ring-jetty-adapter "1.6.2"]
                  [com.novemberain/monger "3.1.0"]]
                  [ragtime "0.7.1"]]
   :ring {:handler valintapiste-service.handler/app}
   :uberjar-name "server.jar"
   :main valintapiste-service.handler
   :profiles {:dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [cheshire "5.5.0"]
                                  [ring/ring-mock "0.3.0"]]
                   :plugins [[lein-ring "0.12.0"]]}})
