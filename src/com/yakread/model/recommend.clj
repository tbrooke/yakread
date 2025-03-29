(ns com.yakread.model.recommend
  (:require [com.biffweb :as biff :refer [q]]
            [clojure.data.generators :as gen]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.yakread.lib.error :as lib.error]
            [com.yakread.lib.core :as lib.core]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [xtdb.api :as xt]
            [lambdaisland.uri :as uri]))

(def n-ads 1)
(def n-sub-bookmark-recs 22)
(def n-discover-recs 7)

(defresolver sub-affinity
  "Models the sub as a beta distribution (constructed from the user's positive and negative
   interactions) and returns the expected value."
  [{:keys [biff/db biff.xtdb/node]} {:sub/keys [id user items]}]
  #::pco{:input [:sub/id
                 {:sub/user [:xt/id]}
                 {:sub/items [:xt/id
                              {(? :item/user-item) [(? :user-item/viewed-at)
                                                    (? :user-item/favorited-at)
                                                    (? :user-item/disliked-at)
                                                    (? :user-item/reported-at)]}]}]
         :output [:sub/affinity]}
  (let [user-items (keep :item/user-item items)
        ;; maybe move to a batch resolver
        skips (mapv second
                    (q db
                       '{:find [skip t]
                         :in [user [item ...]]
                         :where [[skip :skip/user user]
                                 [skip :skip/items item]
                                 [skip :skip/timeline-created-at t]]}
                       (:xt/id user)
                       (mapv :xt/id items)))
        ;; TODO can we learn the correct weights for the different actions? or use an ML model to
        ;; predict the probality of the next interaction being positive/negative
        scores (->> (concat (mapv vector skips (repeat -1))
                            (keep (fn [usit]
                                    (some (fn [[k score]]
                                            (when-some [t (k usit)]
                                              [t score]))
                                          [[:user-item/favorited-at 4]
                                           [:user-item/reported-at -8]
                                           [:user-item/disliked-at -5]
                                           [:user-item/viewed-at 2]]))
                                  user-items))
                    (sort-by first #(compare %2 %1))
                    (take 10)
                    (mapv second)
                    (cons 3))
        {alpha true beta false
         :or {alpha 0 beta 0}} (update-vals (group-by pos? scores)
                                            #(Math/abs (apply + %)))]
    {:sub/affinity (/ alpha (+ alpha beta))}))

(defn rerank
  "Shuffles xs with a bias toward keeping elements close to their original places. When p=1, returns xs in
   the original order; when p=0, does an unbiased shuffle. Returns a lazy sequence."
  [p xs]
  ((fn step [xs]
     (when (not-empty xs)
       (lazy-seq
        (let [i (or (some #(when (< (gen/double) p) %)
                          (range (count xs)))
                    (long (* (gen/double) (count xs))))]
          (cons (get xs i)
                (step (into (subvec xs 0 i) (subvec xs (inc i)))))))))
   (vec xs)))

(defn rank-by-freshness [items]
  (->> items
       (sort-by (juxt :item/n-skipped (comp - inst-ms :item/ingested-at)))
       (rerank 0.1)))

(defresolver sub-recs [{:user/keys [subscriptions]}]
  #::pco{:input [{:user/subscriptions [:sub/affinity
                                       (? :sub/pinned-at)
                                       {:sub/unread-items [:xt/id
                                                           :item/ingested-at
                                                           :item/n-skipped]}]}]
         :output [{:user/sub-recs [:xt/id]}]}
  {:user/sub-recs
   (let [{pinned true unpinned false} (->> subscriptions
                                           (filterv (comp not-empty :sub/unread-items))
                                           (sort-by :sub/affinity >)
                                           (group-by (comp some? :sub/pinned-at)))
         ;; Interleave pinned and unpinned back into one sequence. Basically we sort by affinity,
         ;; but on each selection, the next pinned item has a 1/3 chance of being selected even if it has
         ;; lower affinity.
         ranked-subs (rerank
                      0.1
                      ((fn step [pinned unpinned]
                         (lazy-seq
                          (cond
                            (empty? pinned)
                            unpinned

                            (empty? unpinned)
                            pinned

                            (or (< (gen/double) 1/3)
                                (<= (:sub/affinity (first unpinned))
                                    (:sub/affinity (first pinned))))
                            (cons (first pinned)
                                  (step (rest pinned) unpinned))

                            :else
                            (cons (first unpinned)
                                  (step pinned (rest unpinned))))))
                       pinned
                       unpinned))
         items (for [{:keys [sub/unread-items]} ranked-subs
                     :let [most-recent (apply max-key (comp inst-ms :item/ingested-at) unread-items)]]
                 (if (= 0 (:item/n-skipped most-recent))
                   most-recent
                   (->> unread-items
                        rank-by-freshness
                        first)))]
     (take n-sub-bookmark-recs items))})

(defresolver bookmark-recs [ctx {:user/keys [unread-bookmarks]}]
  #::pco{:input [{:user/unread-bookmarks [:item/id
                                          :item/ingested-at
                                          :item/n-skipped
                                          (? :item/url)]}]
         :output [{:user/bookmark-recs [:item/id]}]}
  {:user/bookmark-recs (->> unread-bookmarks
                            rank-by-freshness
                            (lib.core/distinct-by (some-fn (comp :host uri/uri :item/url) :item/id))
                            (take n-sub-bookmark-recs))})

(defn- pick-by-skipped [a-items b-items]
  ((fn step [a-items b-items]
     (lazy-seq
      (cond
        (empty? a-items)
        b-items

        (empty? b-items)
        a-items

        :else
        (let [a-skipped (:item/n-skipped (first a-items))
              b-skipped (:item/n-skipped (first b-items))
              choice (if (< (gen/double) 0.25)
                       (gen/rand-nth [:a :b])
                       (gen/weighted {:a (inc b-skipped)
                                      :b (inc a-skipped)}))
              [choice-items other-items] (cond->> [a-items b-items]
                                           (= choice :b) reverse)]
          (cons (first choice-items)
                (step (rest choice-items)
                      (concat (rest other-items)
                              (take 1 other-items))))))))
   a-items
   b-items))

(defresolver for-you-recs [{:user/keys [sub-recs bookmark-recs]}]
  #::pco{:input [{:user/sub-recs [:item/id
                                  :item/n-skipped]}
                 {:user/bookmark-recs [:item/id
                                       :item/n-skipped]}]
         :output [{:user/for-you-recs [:item/id
                                       :item/rec-type]}]}
  {:user/for-you-recs (->> (pick-by-skipped
                            (map #(assoc % :item/rec-type :read-later) bookmark-recs)
                            (map #(assoc % :item/rec-type :sub) sub-recs))
                           (lib.core/distinct-by :item/id)
                           (take n-sub-bookmark-recs))})

(def module
  {:resolvers [sub-affinity
               sub-recs
               bookmark-recs
               for-you-recs]})

(comment

  (let [ctx (biff/merge-context @com.yakread/system)
        user-id (biff/lookup-id (:biff/db ctx) :user/email "...")]
    (->> (lib.pathom/process (assoc-in ctx [:session :uid] user-id)
                             {:user/id user-id}
                             [{:user/for-you-recs [:item/id
                                                   :item/rec-type
                                                   :item/n-skipped
                                                   (? :item/title)
                                                   (? :item/author-name)
                                                   (? :item/url)]}])
         :user/for-you-recs
         (take 10)
         time))

  )
