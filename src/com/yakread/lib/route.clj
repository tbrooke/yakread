(ns com.yakread.lib.route
  (:require [reitit.core :as reitit]))

(defn path [router route-name params]
  (let [path-keys (:required (reitit/match-by-name router route-name))
        path (reitit/match->path (reitit/match-by-name router route-name params) (apply dissoc params path-keys))]
    (when-not path
      (throw (ex-info "Couldn't find a path for the given route name"
                      {:route-name route-name
                       :params params})))
    path))

(defn call [router route-name method ctx]
  ((get-in (reitit/match-by-name router route-name)
           [:data method :handler])
   ctx))

(defn handler [router route-name method]
  (get-in (reitit/match-by-name router route-name)
          [:data method :handler]))
