(ns clj-ring-db-session.authentication.login
  (:require [ring.util.response :as resp]
            [clj-ring-db-session.session.session-store :as session-store]))

(defn login [params]
  (let [{:keys [username
                henkilo
                ticket
                success-redirect-url]} params]
    (-> (resp/redirect success-redirect-url)
        (assoc :session {:identity  {:username   username
                                     :first-name (:kutsumanimi henkilo)
                                     :last-name  (:sukunimi henkilo)
                                     :oid        (:oidHenkilo henkilo)
                                     :lang       (or (some #{(-> henkilo :asiointiKieli :kieliKoodi)}
                                                           ["fi" "sv" "en"])
                                                     "fi")
                                     :ticket     ticket}
                         :logged-in true}))))

(defn logout [session logout-url]
  (-> (resp/redirect logout-url)
      (assoc :session {:identity  nil
                       :logged-in false})))

(defn cas-initiated-logout [ticket oph-session-store]
  (session-store/logout-by-ticket! oph-session-store ticket))

(defn logged-in? [request]
  (-> request :session :logged-in))
