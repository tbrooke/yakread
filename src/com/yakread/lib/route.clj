(ns com.yakread.lib.route
  (:require [clojure.string :as str]
            [com.yakread.lib.serialize :as lib.serialize]
            [reitit.core :as reitit]))

;; https://ask.clojure.org/index.php/12125/add-interleave-all-like-partition-all-is-to-partition
(defn interleave-all
  "Like interleave, but stops when the longest seq is done, instead of
   the shortest."
  {:copyright "Rich Hickey, since this is a modified version of interleave"}
  ([] ())
  ([c1] (lazy-seq c1))
  ([c1 c2]
   (lazy-seq
    (let [s1 (seq c1) s2 (seq c2)]
      (cond
       (and s1 s2) ; there are elements left in both
       (cons (first s1) (cons (first s2)
                              (interleave-all (rest s1) (rest s2))))
       s1 ; s2 is done
       s1
       s2 ; s1 is done
       s2))))
  ([c1 c2 & colls]
   (lazy-seq
    (let [ss (filter identity (map seq (conj colls c2 c1)))]
      (concat (map first ss) (apply interleave-all (map rest ss)))))))

(defn path [router route-name params]
  (let [path-keys (:required (reitit/match-by-name router route-name))
        path (reitit/match->path (reitit/match-by-name router route-name params) (apply dissoc params path-keys))]
    (when-not path
      (throw (ex-info "Couldn't find a path for the given route name"
                      {:route-name route-name
                       :params params})))
    path))

(defn path* [route & args]
  (let [path-template (first
                       (if (symbol? route)
                         @(resolve route)
                         route))
        template-segments (str/split path-template #":[^/]+")
        args (mapv #(cond-> %
                      (uuid? %) lib.serialize/uuid->url)
                   args)]
    (apply str (interleave-all template-segments args))))

(defn call [router route-name method ctx]
  ((get-in (reitit/match-by-name router route-name)
           [:data method :handler])
   ctx))

(defn handler [router route-name method]
  (get-in (reitit/match-by-name router route-name)
          [:data method :handler]))
