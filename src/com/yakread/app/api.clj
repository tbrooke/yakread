(ns com.yakread.app.api)

(def api-route
  ["/dev/api/:namespace/:name"
   {:post (fn [{:keys [path-params] :as ctx}]
            (let [sym (try (symbol (:namespace path-params) (:name path-params)) (catch Exception _))
                  mutation (some-> sym resolve meta :biff/mutation)]
              (if mutation
                (mutation ctx)
                {:status 404})))}])

(def module
  {:routes api-route})
