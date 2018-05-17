 (defproject valintapiste-service "0.1.0-SNAPSHOT"
   :description "FIXME: write description"
   :deploy-repositories {"snapshots" {:url "https://artifactory.oph.ware.fi/artifactory/oph-sade-snapshot-local"}
   "releases" {:url "https://artifactory.oph.ware.fi/artifactory/oph-sade-release-local"}}
   :repositories [["oph-releases" "https://artifactory.oph.ware.fi/artifactory/oph-sade-release-local"]
   ["oph-snapshots" "https://artifactory.oph.ware.fi/artifactory/oph-sade-snapshot-local"]
   ["ext-snapshots" "https://artifactory.oph.ware.fi/artifactory/ext-snapshot-local"]]
   :dependencies [[org.clojure/clojure "1.8.0"]
                  [clj-http "3.9.0"]
                  [environ "1.1.0"]
                  [cheshire "5.8.0"]
                  [fi.vm.sade/auditlogger "8.0.0-SNAPSHOT"]
                  [metosin/compojure-api "1.1.11"]
                  [ring/ring-jetty-adapter "1.6.2"]
                  [com.novemberain/monger "3.1.0"]
                  [org.postgresql/postgresql "42.1.4"]
                  [org.slf4j/jul-to-slf4j "1.7.25"]
                  [org.clojure/tools.logging "0.4.0"]
                  [hikari-cp "1.8.0"]
                  [clj-time "0.14.0"]
                  [org.apache.logging.log4j/log4j-api "2.9.0"]
                  [org.apache.logging.log4j/log4j-core "2.9.0"]
                  [org.apache.logging.log4j/log4j-slf4j-impl "2.9.0"]
                  [clj-log4j2 "0.2.0"]
                  [clj-soup/clojure-soup "0.1.3"]
                  [org.flywaydb/flyway-core "4.2.0"]
                  [webjure/jeesql "0.4.6"]]
   :prep-tasks ["compile"]
   ;:eval-in :classloader
   ;:bootclasspath true
   :aliases {"testpostgres" ["with-profile" "testpostgres" "trampoline" "run"]
             "migrate" ["run" "-m" "valintapiste-service.db/migrate"]}
   :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
   :ring {:handler valintapiste-service.handler/app}
   :uberjar-name "valintapiste-service-0.1.0-SNAPSHOT-standalone.jar"
   :resource-paths ["resources"]
   :jvm-opts ["-Dlog4j.configurationFile=log4j2.test.properties"
              "-Dvalintapisteservice-properties=test.valintapisteservice.edn"
              "-XX:+TieredCompilation" "-XX:TieredStopAtLevel=1"]
   :main valintapiste-service.handler
   :aot [valintapiste-service.handler]
   :plugins [[lein-ring "0.12.0"]
             [lein-resource "14.10.2"]
             [com.jakemccrary/lein-test-refresh "0.21.1"]
             [lein-autoreload "0.1.1"]
             [lein-deploy-artifacts "0.1.0"]]
   :profiles {:uberjar {:prep-tasks ["compile" "resource"]}
              :testpostgres {:main valintapiste-service.testpostgres
                             :source-paths ["src" "test"]
                             :dependencies [[ru.yandex.qatools.embed/postgresql-embedded "2.4"]]}
              :dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [org.clojure/java.jdbc "0.7.1"]
                                  [ru.yandex.qatools.embed/postgresql-embedded "2.4"]
                                  [ring/ring-mock "0.3.1"]]}})

