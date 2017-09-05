 (defproject valintapiste-service "0.1.0-SNAPSHOT"
   :description "FIXME: write description"
   :dependencies [[org.clojure/clojure "1.8.0"]
                  [clj-http "3.7.0"]
                  [environ "1.1.0"]
                  [metosin/compojure-api "1.1.11"]
                  [ring/ring-jetty-adapter "1.6.2"]
                  [com.novemberain/monger "3.1.0"]
                  [org.postgresql/postgresql "9.4.1208"]
                  [com.zaxxer/HikariCP "2.7.0"]
                  [ragtime "0.7.1"]]
                  [webjure/jeesql "0.4.6"]]
   :aliases {"testpostgres" ["with-profile" "testpostgres" "trampoline" "run"]
             "migrate" ["run" "-m" "valintapiste-service.db/migrate"]}
   :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
   :ring {:handler valintapiste-service.handler/app}
   :uberjar-name "server.jar"
   :main valintapiste-service.handler
   :profiles {:testpostgres {:main valintapiste-service.testpostgres
                             :source-paths ["src" "testpostgres"]
                             :dependencies [[ru.yandex.qatools.embed/postgresql-embedded "2.4"]]}
              :dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [cheshire "5.5.0"]
                                  [ring/ring-mock "0.3.0"]]
                   :plugins [[lein-ring "0.12.0"]]}})
