(ns clj-ring-db-session.session.session-client)

(defn wrap-session-client-headers [handler]
  [handler]
  (fn [{:keys [headers] :as req}]
      (let [user-agent (get headers "user-agent")
            client-ip  (or (get headers "x-real-ip")
                           (get headers "x-forwarded-for")
                           (:remote-addr req))]
        (handler (-> req
                     (assoc-in [:session :user-agent] user-agent)
                     (assoc-in [:session :client-ip] client-ip))))))
