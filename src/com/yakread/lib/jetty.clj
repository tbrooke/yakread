(ns com.yakread.lib.jetty
  (:require [com.biffweb :as biff]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty])
  (:import [org.eclipse.jetty.util.thread QueuedThreadPool]
           [java.util.concurrent Executors]))

(defn use-jetty [{:biff/keys [host port handler]
                  :or {host "localhost"
                       port 8080}
                  :as ctx}]
  (let [thread-pool (doto (new QueuedThreadPool)
                      (.setVirtualThreadsExecutor (Executors/newVirtualThreadPerTaskExecutor)))
        server (jetty/run-jetty (fn [req]
                                  (handler (merge (biff/merge-context ctx) req)))
                                {:host host
                                 :port port
                                 :join? false
                                 :allow-null-path-info true})]
    (log/info "Jetty running on" (str "http://" host ":" port))
    (update ctx :biff/stop conj #(.stop server))))
