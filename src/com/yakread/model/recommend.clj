(ns com.yakread.model.recommend
  (:require [com.biffweb :as biff :refer [q]]
            [clojure.data.generators :as gen]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.yakread.lib.error :as lib.error]
            [com.yakread.lib.core :as lib.core]
            [com.yakread.lib.user-item :as lib.usit]
            [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.wsscode.pathom3.interface.eql :as p.eql]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [xtdb.api :as xt]
            [lambdaisland.uri :as uri])
  (:import [org.apache.spark.api.java JavaSparkContext]
           [org.apache.spark.mllib.recommendation Rating ALS MatrixFactorizationModel]
           [scala Tuple2 Function1]
           [scala.reflect ClassTag$]
           [stuff MyFunction1]))

(def n-ads 1)
(def n-sub-bookmark-recs 22)
(def n-discover-recs 7)

(defresolver latest-sub-interactions
  "Returns the 10 most recent interactions (e.g. viewed, liked, etc) for a given sub."
  [{:keys [biff/db]} {:sub/keys [user items]}]
  #::pco{::pco/input [:sub/title
                 {:sub/user [:xt/id]}
                 {:sub/items [:xt/id
                              {(? :item/user-item) [(? :user-item/viewed-at)
                                                    (? :user-item/favorited-at)
                                                    (? :user-item/disliked-at)
                                                    (? :user-item/reported-at)]}]}]
         ::pco/output [{:sub/latest-interactions [:sub.interaction/type
                                                  :sub.interaction/occurred-at]}]}
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
                       (mapv :xt/id items)))]
    {:sub/latest-interactions
     (->> (concat (mapv (fn [t]
                          {:sub.interaction/type        :sub.interaction.type/skipped
                           :sub.interaction/occurred-at t})
                        skips)
                  (keep (fn [usit]
                          (some (fn [[attr type*]]
                                  (when-some [t (attr usit)]
                                    {:sub.interaction/type        type*
                                     :sub.interaction/occurred-at t}))
                                [[:user-item/favorited-at :sub.interaction.type/favorited]
                                 [:user-item/reported-at  :sub.interaction.type/reported]
                                 [:user-item/disliked-at  :sub.interaction.type/disliked]
                                 [:user-item/viewed-at    :sub.interaction.type/viewed]]))
                        user-items))
          (sort-by :sub.interaction/occurred-at #(compare %2 %1))
          (take 10)
          vec)}))

(defresolver sub-affinity
  "Models the sub as a beta distribution (constructed from the user's positive and negative
   interactions) and returns the expected value."
  [{:keys [biff/db biff.xtdb/node]} {:sub/keys [latest-interactions]}]
  #::pco{:input [{:sub/latest-interactions [:sub.interaction/type]}]
         :output [:sub/affinity]}
  (let [;; TODO can we learn the correct weights here? Or use an ML model to predict the probability of the
        ;; next interaction being positive/negative.
        interaction-type->score {:sub.interaction.type/skipped   -1
                                 :sub.interaction.type/favorited  4
                                 :sub.interaction.type/reported  -8
                                 :sub.interaction.type/disliked  -5
                                 :sub.interaction.type/viewed     2}
        scores (->> latest-interactions
                    (mapv (comp interaction-type->score :sub.interaction/type))
                    ;; Give new subs a boost (same idea as upper confidence bound exploration).
                    (cons 3))
        {alpha true beta false
         :or {alpha 0 beta 0}} (update-vals (group-by pos? scores)
                                            #(Math/abs (apply + %)))]
    {:sub/affinity (/ alpha (+ alpha beta))}))

(defresolver new-sub [{:sub/keys [latest-interactions]}]
  {::pco/input [:sub/title
                {:sub/latest-interactions [:sub.interaction/type]}]}
  {:sub/new (empty? latest-interactions)})

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
  {::pco/input [{:user/subscriptions [:sub/new
                                      :sub/title
                                      :sub/affinity
                                      (? :sub/pinned-at)
                                      {:sub/unread-items [:xt/id
                                                          :item/ingested-at
                                                          :item/n-skipped]}]}]
   ::pco/output [{:user/sub-recs [:xt/id
                                  :item/rec-type]}]}
  {:user/sub-recs
   (let [subscriptions (filterv (comp not-empty :sub/unread-items) subscriptions)

         {new-subs true old-subs false} (group-by :sub/new subscriptions)
         ;; We'll always show new subs first e.g. so the user will see any confirmation emails.
         new-subs (->> new-subs
                       (sort-by (fn [{:keys [sub/unread-items]}]
                                  (->> unread-items
                                       (mapv (comp inst-ms :item/ingested-at))
                                       (apply max)))
                                >)
                       (mapv #(assoc % :item/rec-type :item.rec-type/new-subscription)))
         {pinned true unpinned false} (->> old-subs
                                           (sort-by :sub/affinity >)
                                           (group-by (comp some? :sub/pinned-at)))
         ;; Interleave pinned and unpinned back into one sequence. Basically we sort by affinity,
         ;; but on each selection, the next pinned item has a 1/3 chance of being selected even if it has
         ;; lower affinity.
         ranked-subs (->> ((fn step [pinned unpinned]
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
                           unpinned)
                          (rerank 0.1)
                          (mapv #(assoc % :item/rec-type :item.rec-type/subscription)))
         items (for [{:keys [sub/unread-items item/rec-type]} (concat new-subs ranked-subs)
                     :let [most-recent (apply max-key (comp inst-ms :item/ingested-at) unread-items)]]
                 (assoc (if (= 0 (:item/n-skipped most-recent))
                          most-recent
                          (->> unread-items
                               rank-by-freshness
                               first))
                        :item/rec-type rec-type))]
     (vec (take n-sub-bookmark-recs items)))})

(defresolver bookmark-recs [ctx {:user/keys [unread-bookmarks]}]
  #::pco{:input [{:user/unread-bookmarks [:item/id
                                          :item/ingested-at
                                          :item/n-skipped
                                          (? :item/url)]}]
         :output [{:user/bookmark-recs [:item/id
                                        :item/rec-type]}]}
  {:user/bookmark-recs (->> unread-bookmarks
                            rank-by-freshness
                            (lib.core/distinct-by (some-fn (comp :host uri/uri :item/url) :item/id))
                            (take n-sub-bookmark-recs)
                            (mapv #(assoc % :item/rec-type :item.rec-type/bookmark)))})

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
                                  :item/n-skipped
                                  :item/rec-type]}
                 {:user/bookmark-recs [:item/id
                                       :item/n-skipped
                                       :item/rec-type]}]
         :output [{:user/for-you-recs [:item/id
                                       :item/rec-type]}]}
  (let [{new-sub-recs true
         other-sub-recs false} (group-by #(= :item.rec-type/new-subscription (:item/rec-type %))
                                         sub-recs)]
    {:user/for-you-recs (->> (pick-by-skipped bookmark-recs other-sub-recs)
                             (concat new-sub-recs)
                             (lib.core/distinct-by :item/id)
                             (take n-sub-bookmark-recs)
                             vec)}))

(def module
  {:resolvers [latest-sub-interactions
               sub-affinity
               new-sub
               sub-recs
               bookmark-recs
               for-you-recs]})

(defn median [xs]
  (first (take (/ (count xs) 2) xs)))

(defn take-rand [xs]
  (take (max 1 (* (gen/double) (count xs))) xs))

(defn foo [tuple]
  (let [index (._1 ^Tuple2 tuple)
        ratings (._2 ^Tuple2 tuple)
        avg (/ (apply + (mapv (fn [^Rating rating]
                                (.rating rating))
                              ratings))
               (count ratings))]
    [index avg]))

(def bar
  (proxy [Function1 java.io.Serializable] []
    (apply [tuple]
      (foo tuple))))

(defn -main [ctx]
  (with-open [spark (JavaSparkContext. "local[*]" "yakread")]
    (.setCheckpointDir spark "storage/spark-checkpoint")
    (let [{:keys [biff/db] :as ctx} (biff/merge-context ctx)
          user-id (biff/lookup-id db :user/email "jacob@thesample.ai")
          item-id->url (time
                        (into {}
                              (q db
                                 '{:find [item2 url]
                                   :where [[usit :user-item/item item1]
                                           [usit :user-item/favorited-at]
                                           [item1 :item/url url]
                                           [item2 :item/url url]]})))
          _ (def item-id->url item-id->url)
          all-usits (time
                     (doall
                      (q db
                         '{:find (pull usit [*])
                           :in [[item ...]]
                           :where [[usit :user-item/item item]]}
                         (keys item-id->url))))
          _ (def all-usits all-usits)
          skip-usits (time
                      (q db
                         '{:find [user item (count skip)]
                           :keys [user-item/user user-item/item user-item/n-skips]
                           :in [[item ...]]
                           :where [[skip :skip/items item]
                                   [skip :skip/user user]]}
                         (keys item-id->url)))
          _ (def skip-usits skip-usits)
          usit-key (juxt :user-item/user :user-item/item)
          user+item->skip-usit (into {}
                                     (map (juxt usit-key identity))
                                     skip-usits)
          all-usits (->> (concat (for [usit all-usits]
                                   (merge usit (user+item->skip-usit (usit-key usit))))
                                 skip-usits)
                         (lib.core/distinct-by usit-key)
                         (mapv #(assoc % :user-item/url (item-id->url (:user-item/item %)))))
          my-usits (filterv #(= (:user-item/user %) user-id)
                            all-usits)

          url-allowed? (constantly true)

          ;; build a CF model
          ;;   base it on item url, not item id
          ;; get list of unread items
          ;; we want to look at:
          ;; - CF predicted rating
          ;; - freshness (maybe?)
          ;; - popularity (bias toward long tail)

          ;; sort items by n-usits + skips unique by user, then take a percentage of the list
          ;; sort remaining items by last-liked-at, then take a percentage of the list
          ;; 90% of the time: take the max item by [- n-subject-skips, predicted rating, random]
          ;;   do the (rerank 0.1) thing, or similar
          ;;   for digests, include digests in n-seen
          ;; 10% of the time: pick a random item

          [[index->url url->index]
           [index->user user->index]]
          (for [k [:user-item/url :user-item/user]
                :let [index->x (->> all-usits
                                    (mapv k)
                                    distinct
                                    (map-indexed vector)
                                    (into {}))]]
            [index->x (into {} (map (fn [[k v]] [v k])) index->x)])

          ratings (time
                   (->> all-usits
                        (mapv (fn [usit]
                                (let [score (cond
                                              (:user-item/favorited-at usit) 1
                                              (:user-item/disliked-at usit) 0
                                              (:user-item/reported-at usit) 0
                                              (:user-item/viewed-at usit) 0.75
                                              (:user-item/skipped-at usit) (max 0 (- 0.5 (* 0.1 (:user-item/n-skips usit 0))))
                                              (:user-item/bookmarked-at usit) 0.6
                                              ;; Shouldn't ever happen; just being defensive.
                                              :else 0.5)]
                                  (Rating. (int (user->index (:user-item/user usit)))
                                           (int (url->index (:user-item/url usit)))
                                           (double score)))))
                        (.parallelize spark)
                        (.rdd)
                        (.cache)))
          _ (prn (count all-usits) (count index->user) (count index->url))
          rank 10
          iterations 20
          lambda 0.1
          alpha 0.05
          _ (println "training")
          model (time (ALS/trainImplicit ratings rank iterations lambda alpha))
          _ (println "done training")

          url->n-usits (update-vals (group-by :user-item/url all-usits)
                                    count)
          url->last-liked (update-vals (group-by :user-item/url all-usits)
                                       (fn [usits]
                                         (->> (keep :user-item/favorited-at usits)
                                              (apply max-key inst-ms))))
          url->n-cur-user-skips (into {}
                                      (keep (fn [{:user-item/keys [url n-skips]}]
                                              (when n-skips
                                                [url n-skips])))
                                      my-usits)
          _ (println "computing baselines")
          baselines (-> (into {} (.. model
                                     (recommendUsersForProducts (count user->index))
                                     (map (stuff.MyFunction1.)

                                          #_(reify Function1
                                              (apply [this tuple]
                                                (let [url (index->url (._1 ^Tuple2 tuple))
                                                      ratings (._2 ^Tuple2 tuple)
                                                      avg (/ (apply + (mapv (fn [^Rating rating]
                                                                              (.rating rating))
                                                                            ratings))
                                                             (count ratings))]
                                                  [url avg])))
                                          (.apply ClassTag$/MODULE$ clojure.lang.PersistentVector))
                                     (collect)))
                        (update-keys index->url))
          _ (biff/pprint (take 5 baselines))

          median-rating (median (sort (vals baselines)))
          _ (println "computing url->score")
          url->score (time
                      (if-some [user-idx (user->index user-id)]
                        (->> (.recommendProducts model user-idx (count index->url))
                             (into {} (map (fn [^Rating rating]
                                             [(index->url (.product rating))
                                              (.rating rating)]))))
                        baselines))
          read-urls (time
                     (into #{}
                           (keep (fn [{:user-item/keys [url] :as usit}]
                                   (when (lib.usit/read? usit)
                                     url)))
                           my-usits))

          urls (->> (vals index->url)
                    (remove read-urls)
                    (sort-by (juxt url->n-usits (comp - inst-ms url->last-liked))))
          _ (prn (count urls) "candidates")
          urls (time
                (first
                 (reduce (fn [[selected candidates] _]
                           (let [selection
                                 (if (< (gen/double) 0.1)
                                   (gen/rand-nth candidates)
                                   (->> candidates
                                        take-rand
                                        (sort-by (comp - inst-ms url->last-liked))
                                        take-rand
                                        gen/shuffle
                                        (sort-by (fn [url]
                                                   [(get url->n-cur-user-skips url 0)
                                                    (- (get url->score url median-rating))]))
                                        (rerank 0.25)
                                        first))]
                             [(conj selected selection)
                              (filterv (complement #{selection}) candidates)]))
                         [[] urls]
                         (range 5))))
          ]
    urls)))

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


  (with-open [spark (JavaSparkContext. "local[*]" "yakread")]
    :ok)

  (def spark (JavaSparkContext. "local[*]" "yakread"))


  (-main @com.yakread/system)



  )
