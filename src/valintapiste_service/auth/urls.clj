(ns valintapiste-service.auth.urls)

(defn kayttooikeus-service-kayttooikeus-kayttaja-url [config username]
      (str (-> config :host-virkailija) "/kayttooikeus-service/kayttooikeus/kayttaja?username=" username))

(defn redirect-to-login-failed-page-url [config]
      (str (-> config :host-virkailija) "/valintapiste-service/virhe"))

(defn valintapiste-service-login-url [config]
      (str (-> config :host-virkailija) "/valintapiste-service/auth/cas"))

(defn cas-login-url [config]
      (let [host (-> config :host-virkailija)]
           (str host "/cas/login?service=" host "/valintapiste-service/auth/cas")))
(defn cas-logout-url [config]
      (let [host (-> config :host-virkailija)]
           (str host "/cas/logout?service=" host "/valintapiste-service/auth/cas")))

(defn cas-redirect-url [config]
      (str (-> config :host-virkailija) "/valintapiste-service/auth/checkpermission"))
