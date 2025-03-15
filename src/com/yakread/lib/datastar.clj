(ns com.yakread.lib.datastar
  (:require [com.biffweb :as biff]
            [clojure.java.io :as io]
            [ring.core.protocols :as rp]
            [rum.core :as rum]
            [cheshire.core :as cheshire]))

(defn fmt-event [event]
  (str "event: datastar-" (:name event) "\n"
       (apply str
              (for [[k values] (dissoc event :name)
                    v values]
                (str "data: " (name k) " "
                     (cond
                       (vector? v) (rum/render-static-markup v)
                       (map? v) (cheshire/generate-string v)
                       :else v)
                     "\n")))
       "\n"))

(defn merge-fragments [& fragments]
  {:name "merge-fragments" :fragments (vec fragments)})

(defn merge-signals [& signals]
  {:name "merge-signals" :signals (vec signals)})

(defn execute-script [& scripts]
  {:name "execute-script" :script (vec scripts)})

(defrecord StreamResponse [f]
  rp/StreamableResponseBody
  (write-body-to-stream [this _response output-stream]
    (f output-stream)))

(defn wrap-sse-response [handler]
  (fn [ctx]
    (let [response (handler ctx)]
      (if-some [sse (:sse response)]
        (-> (merge {:status 200
                    :body (StreamResponse.
                           (fn [output-stream]
                             (with-open [writer (io/writer output-stream)]
                               (doseq [event sse]
                                 (.write writer (fmt-event event))
                                 (.flush writer)))))}
                   response)
            (update :headers
                    merge
                    {"Cache-Control" "nocache"
                     "Content-Type"  "text/event-stream"}
                    (when (some #(= % (:protocol ctx)) [nil "HTTP/1.1"])
                      {"Connection" "keep-alive"})))
        response))))

(comment
  {:name "merge-fragments" :fragments [[:div#hello "Hello, World!"]]}
  {:name "merge-signals" :signals [{:foo {:bar 1}}]}
  {:name "execute-script" :script ["console.log('Success!')"]})
