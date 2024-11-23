(ns com.yakread.lib.error)

(defn merge-ex-data [ex & data]
  (doto (ex-info (.getMessage ex)
                 (apply merge
                        (ex-data ex)
                        (when-not (instance? clojure.lang.ExceptionInfo ex)
                          {:biff.error/ex-type (type ex)})
                        data))
    (.setStackTrace (.getStackTrace ex))))

(defmacro with-ex-data [data & body]
  `(try
     ~@body
     (catch Exception e#
       (throw (merge-ex-data e# ~data)))))

(defn request-ex-data [{:keys [biff.error/param-keys
                               biff.error/param-paths]
                        :or {param-keys [:uri :params :path-params :session]
                             param-paths [[:reitit.core/match :data :name]]}
                        :as request}]
  (reduce (fn [data path]
            (assoc-in data path (get-in request path)))
          (select-keys request param-keys)
          param-paths))

(defn wrap-request-ex-data [handler]
  (fn [request]
    (with-ex-data (request-ex-data request)
      (handler request))))
