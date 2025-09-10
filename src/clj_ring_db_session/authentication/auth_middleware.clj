(ns clj-ring-db-session.authentication.auth-middleware
  (:require
    [buddy.auth :refer [authenticated?]]
    [buddy.auth.middleware :refer [wrap-authentication]]
    [buddy.auth.accessrules :refer [wrap-access-rules success error]]
    [buddy.auth.backends.session :refer [session-backend]]
    [clojure.data.json :as json]
    [ring.util.request :refer [request-url]]
    [clj-ring-db-session.authentication.login :refer [logged-in?]]))

(def backend (session-backend))

(defn any-access [_] true)

(defn- create-authenticated-access []
  (fn [request]
    (if (logged-in? request)
      true
      (error "Authentication required"))))

(defn- send-not-authenticated-api-response [& _]
  {:status  401
   :headers {"Content-Type" "application/json"}
   :body    (json/write-str {:error-message "Not authenticated"})})

(defn- create-redirect-to-login [login-url]
  (fn [request _]
    {:status  302
     :headers {"Location" login-url
               "Content-Type" "text/plain"}
     :session {:original-url (request-url request)}
     :body    (str "Access to " (:uri request) " is not authorized, redirecting to login")}))

(defn- create-rules [login-url]
  [{:pattern #".*/auth/.*"
                         :handler any-access}
                        {:pattern #".*/js/.*"
                         :handler any-access}
                        {:pattern #".*/images/.*"
                         :handler any-access}
                        {:pattern #".*/css/.*"
                         :handler any-access}
                        {:pattern #".*/favicon.ico"
                         :handler any-access}
                        {:pattern #".*/api/checkpermission"
                         :handler any-access}
                        {:pattern #".*/api/.*"
                         :handler (create-authenticated-access)
                         :on-error send-not-authenticated-api-response}
                        {:pattern #".*"
                         :handler (create-authenticated-access)
                         :on-error (create-redirect-to-login login-url)}])

(defn with-authentication [site login-url]
  (-> site
      (wrap-authentication backend)
      (wrap-access-rules {:rules (create-rules login-url)})))
