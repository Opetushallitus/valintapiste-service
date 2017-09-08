(ns valintapiste-service.freeport)

(defn get-free-port [defaultPort]
    (let [socket (java.net.ServerSocket. 0)]
      (.close socket)
      (.getLocalPort socket)))
