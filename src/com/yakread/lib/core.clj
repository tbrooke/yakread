(ns com.yakread.lib.core)

(defn pred->
  "(if (pred x) (f x) x)"
  [x pred f]
  (if (pred x)
    (f x)
    x))

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

(defn group-by-to [f g xs]
  (update-vals (group-by f xs) #(mapv g %)))

(defn some-vals [m]
  (into {}
        (remove (comp nil? val))
        m))
