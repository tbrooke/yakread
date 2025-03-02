(ns com.yakread.model.recommend
  (:require [com.biffweb :as biff :refer [q]]
            [clojure.data.generators :as gen]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [xtdb.api :as xt]))

(defresolver sub-affinity [{:keys [biff/db biff.xtdb/node]} {:sub/keys [id user items]}]
  #::pco{:input [:sub/id
                 {:sub/user [:xt/id]}
                 {:sub/items [:xt/id
                              {(? :item/user-item) [(? :user-item/viewed-at)
                                                    (? :user-item/favorited-at)
                                                    (? :user-item/disliked-at)
                                                    (? :user-item/reported-at)]}]}]}
  {:sub/affinity
   (let [user-items (keep :item/user-item items)
         skips (mapv second
                     (q db
                        '{:find [skip skipped-at]
                          :in [user [item ...]]
                          :where [[skip :skip/user user]
                                  [skip :skip/items item]
                                  [skip :skip/skipped-at skipped-at]]}
                        (:xt/id user)
                        (mapv :xt/id items)))
         t+score (concat (mapv (fn [[_ t]] [t -1]) skips)
                         (keep (fn [usit]
                                 (some (fn [[k score]]
                                         (when-some [t (k usit)]
                                           [t score]))
                                       [[:user-item/favorited-at 5]
                                        [:user-item/reported-at -5]
                                        [:user-item/disliked-at -3]
                                        [:user-item/viewed-at 3]]))
                               user-items)
                         (repeat 5
                                 [(java.time.Instant/ofEpochMilli 0)
                                  1]))]
     (->> t+score
          (sort-by first #(compare %2 %1))
          (take 10)
          (mapv second)
          (apply +)))})

(defn weights []
  (cons 1
        ((fn step [total-w prev-w]
           (let [next-w (max (/ total-w 9.0) prev-w)]
             (cons next-w
                   (lazy-seq (step (+ total-w next-w) next-w)))))
         1 1)))

(defresolver for-you-items [{:user/keys [subscriptions]}]
  #::pco{:input [{:user/subscriptions [:sub/id
                                       :sub/title
                                       :sub/affinity
                                       (? :sub/pinned-at)]}]}
  {:user/for-you-items
   (let [{pinned true unpinned false} (->> subscriptions
                                           (sort-by :sub/affinity >)
                                           (group-by (comp some? :sub/pinned-at)))
         ranked-subs (loop [ranked []
                            pinned pinned
                            unpinned unpinned]
                       (cond
                         (empty? pinned)
                         (into ranked unpinned)

                         (empty? unpinned)
                         (into ranked pinned)

                         (or (< (gen/double) 0.5)
                             (<= (:sub/affinity (first unpinned))
                                 (:sub/affinity (first pinned))))
                         (recur (conj ranked (first pinned))
                                (rest pinned)
                                unpinned)

                         :else
                         (recur (conj ranked (first unpinned))
                                pinned
                                (rest unpinned))))
         weighted-subs (mapv (fn [sub w]
                               (assoc sub ::weight w))
                             (reverse ranked-subs)
                             (weights))

         ;; TODO sample by weights
         reranked-subs nil
         ]


     )}
  )

(def module
  {:resolvers [for-you-items sub-affinity]})

(comment

  (let [ctx (biff/merge-context @com.yakread/system)
        user-id (biff/lookup-id (:biff/db ctx) :user/email "hello@obryant.dev")]
    (time (lib.pathom/process
           (assoc-in ctx [:session :uid] user-id)
           {:user/id user-id}
           [:user/for-you-items])))

  )
