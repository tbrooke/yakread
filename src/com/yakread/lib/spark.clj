(ns com.yakread.lib.spark
  (:require
   [clojure.tools.logging :as log]
   [com.biffweb :as biff]
   [com.yakread.lib.ads :as lib.ads]
   [com.yakread.lib.core :as lib.core]
   [xtdb.api :as xt :refer [q]])
  (:import
   [com.yakread AverageRating]
   [org.apache.spark.api.java JavaSparkContext]
   [org.apache.spark.mllib.recommendation ALS Rating]
   [java.time Instant Period]
   [scala.reflect ClassTag$]))

(defn- median [xs]
  (first (take (/ (count xs) 2) xs)))


;; We want to end up with a sequence of direct items (maps, not IDs) with URLs that have been liked
;; at least once.

;; 1. get all the candidates
;; 2. get the ratings
;;    - are we only getting ratings for candidates? Or ratings for everything?
;;    - at a minimum we're getting ratings for items that have the same URLs as candidates
;;    - so do we also want ratings for items that haven't been liked by anyone or don't have URLs?
;;    - if it doesn't have a URL, most likely it's an email and that item will only have usits from
;;      one user
;;    - if it hasn't been liked by anyone: low signal
;;    - conclution: yes we only want ratings for ~candidates
;; 3. train a model
;; 4. return a way for users to get a sequence of candidates, including:
;;    - score
;;    - last-liked (by anyone)
;;    - id (both for recommending, and for authorizing the read page)
;;
;; item candidates are direct items; ad candidates are ads.
;;
;; ad candidate notes:
;; - we want ratings in the model for all ad interactions regardless of if those ads are active


(defn- merge-usits [a b]
  ;; TODO
  b)

;; TODO
;; rename stuff
;; do some testing
(defn new-model [{:keys [yakread/spark biff/db biff/now]}]
  (log/info "updating model")
  (when-some [[screened-to screened-to-id]
              (first
               (q db
                  '{:find [ingested-at item]
                    :where [[moderation :admin.moderation/latest-item item]
                            [item :item/ingested-at ingested-at]]}))]
    (let [;;; get the candidates
          item-candidates
          (into []
                (keep (fn [[{:keys [item.direct/candidate-status
                                    item/ingested-at]
                             :as candidate}]]
                        (when-not (or (#{:ingest-failed :blocked} candidate-status)
                                      (< (inst-ms screened-to) (inst-ms ingested-at))
                                      (and (= screened-to ingested-at)
                                           (< (compare screened-to-id id) 0)))
                          candidate)))
                (q db
                   '{:find [(pull direct-item [*])]
                     :in [direct]
                     :where [[usit :user-item/item any-item]
                             [usit :user-item/favorited-at]
                             [any-item :item/url url]
                             [direct-item :item/url url]
                             [direct-item :item/doc-type direct]]}
                   :item/direct))
          all-ads (mapv first
                        (q db
                           '{:find [(pull ad [*])]
                             :where [[ad :ad/user]]}))
          ad->recent-cost (q db
                             '{:find [ad (sum cost)]
                               :in [t]
                               :where [[click :ad.click/ad ad]
                                       [click :ad.click/cost cost]
                                       [click :ad.click/created-at created-at]
                                       [(< t created-at)]]}
                             (.minus now (Period/ofWeeks 1)))
          ad-candidates (->> all-ads
                             (mapv #(assoc % :ad/recent-cost (get ad->recent-cost (:xt/id %) 0)))
                             (filterv lib.ads/active?))

          ;;; get ratings (keys: :user, :candidate, :score)
          all-item-ids (mapv first
                             (q db
                                '{:find [item]
                                  :in [[url ...]]
                                  :where [[item :item/url url]]}
                                (mapv :item/url item-candidates)))
          [[item-usits user+item->skips]
           [ad-usits user+ad->skips]] (for [ids [all-item-ids (mapv :xt/id all-ads)]]
                                        [(mapv first
                                               (q db
                                                  '{:find (pull usit [*])
                                                    :in [[item ...]]
                                                    :where [[usit :user-item/item item]]}
                                                  ids))
                                         (into {}
                                               (map (fn [[user item n-skips]]
                                                      [[user item] n-skips]))
                                               (q db
                                                  '{:find [user item (count skip)]
                                                    :in [[item ...]]
                                                    :where [[skip :skip/items item]
                                                            [skip :skip/user user]]}
                                                  ids))])
          url->item-candidate (into {}
                                    (map (juxt :item/url identity))
                                    item-candidates)
          item-id->url (into {}
                             (q db
                                '{:find [item url]
                                  :in [[url ...]]
                                  :where [[item :item/url url]]}
                                (mapv :item/url item-candidates)))
          dedupe-item-id (comp :xt/id url->item-candidate item-id->url)
          usit-key (juxt :user-item/user :user-item/item)
          item-usits (->> item-usits
                          (mapv #(update % :user-item/item dedupe-item-id))
                          (group-by usit-key)
                          (vals)
                          (mapv #(reduce merge-usits %)))
          user+item->skips (as-> user+item->skips $
                             (mapv #(update-in % [0 1] dedupe-item-id) $)
                             (group-by key $)
                             (update-vals $ #(apply + (mapv val %))))
          candidate-usits (concat item-usits ad-usits)
          user+candidate->skip-usit (into {}
                                          (map (fn [[[user item] skips]]
                                                 [[user item]
                                                  {:user-item/user user
                                                   :user-item/item item
                                                   :user-item/skips skips}]))
                                          (merge user+item->skips user+ad->skips))
          all-usits (->> (concat (for [usit candidate-usits]
                                   (merge usit (user+candidate->skip-usit (usit-key usit))))
                                 skip-usits)
                         (lib.core/distinct-by usit-key))
          ratings (for [usit all-usits
                        :let [score (cond
                                      (:user-item/favorited-at usit) 1
                                      (:user-item/disliked-at usit) 0
                                      (:user-item/reported-at usit) 0
                                      (:user-item/viewed-at usit) 0.75
                                      (:user-item/skipped-at usit) (max 0 (- 0.5 (* 0.1 (:user-item/skips usit 0))))
                                      (:user-item/bookmarked-at usit) 0.6
                                      ;; shouldn't ever happen; just being defensive.
                                      :else 0.5)]]
                    {:user (:user-item/user usit)
                     :item (:user-item/item usit)
                     :score score})

          ;;; train a model
          [[index->candidate candidate->index]
           [_ user->index]]
          (for [k [:user :item]
                :let [index->x (->> ratings
                                    (mapv k)
                                    distinct
                                    (map-indexed vector)
                                    (into {}))]]
            [index->x (into {} (map (fn [[k v]] [v k])) index->x)])
          spark-ratings (->> (for [{:keys [user item score]} ratings]
                               (Rating. (int (user->index user))
                                        (int (candidate->index item))
                                        (double score)))
                             (.parallelize spark)
                             (.rdd)
                             (.cache))
          rank 10
          iterations 20
          lambda 0.1
          alpha 0.05
          _ (log/info "training ALS")
          als (ALS/trainImplicit ratings rank iterations lambda alpha)


          ;; Expose the model
          _ (log/info "computing baselines")
          baselines (-> (into {} (.. als
                                     (recommendUsersForProducts (count user->index))
                                     (map (AverageRating.)
                                          (.apply ClassTag$/MODULE$ clojure.lang.PersistentVector))
                                     (collect)))
                        (update-keys index->candidate))

          item-candidates-ids (into #{} (map :xt/id) item-candidates)
          ad-candidates-ids (into #{} (map :xt/id) all-ads)
          type->median-score (as-> baselines $
                               (group-by (fn [[candidate-id _]]
                                           (cond
                                             (item-candidates-ids candidate-id) :item
                                             (ad-candidates-ids candidate-id) :ad))
                                         $)
                               (update-vals $ #(or (median (sort (mapv val %))) 0.5)))
          candidate->n-usits (update-vals (group-by :user-item/item all-usits)
                                          count)
          candidate->last-liked (update-vals (group-by :user-item/item all-usits)
                                             (fn [usits]
                                               (->> (keep :user-item/favorited-at usits)
                                                    (apply max-key
                                                           inst-ms
                                                           (Instant/ofEpochMilli 0)))))
          get-candidates
          (fn [user-id]
            (let [candidate->score
                  (if-some [user-idx (user->index user-id)]
                    (->> (.recommendProducts als user-idx (count index->candidate))
                         (into {} (map (fn [^Rating rating]
                                         [(index->candidate (.product rating))
                                          (.rating rating)]))))
                    baselines)]
              (into {}
                    (for [[type* candidates median-score]
                          [[:item item-candidates item-median-score]
                           [:ad ad-candidates ad-median-score]]
                          :let [median-score (get type->median-score type*)]]
                      [type*
                       (->> (for [c candidates]
                              (assoc c
                                     :candidate/type type*
                                     :candidate/score (get candidate->score
                                                           (:xt/id c)
                                                           median-score)
                                     :candidate/last-liked (candidate->last-liked (:xt/id c))
                                     :candidate/n-usits (candidate->n-usits (:xt/id c))))
                            (sort-by (juxt :candidate/n-usits
                                           (comp - inst-ms :candidate/last-liked)))
                            vec)]))))
          candidate-ids (set (mapv :xt/id (concat item-candidates ad-candidates)))





          direct-items (into []
                             (comp (map first)
                                   (remove
                                    (fn [{:keys [item.direct/candidate-status
                                                 item/ingested-at
                                                 xt/id]}]
                                      (or (#{:ingest-failed :blocked} candidate-status)
                                          (< (inst-ms screened-to) (inst-ms ingested-at))
                                          (and (= screened-to ingested-at)
                                               (< (compare screened-to-id id) 0))))))
                             (q db
                                '{:find [(pull item [:xt/id
                                                     :item/url
                                                     :item/ingested-at
                                                     :item.direct/candidate-status])]
                                  :in [direct]
                                  :where [[item :item/doc-type direct]]}
                                :item/direct))

          direct-urls (into #{} (map :item/url) direct-items)
          ;; For any favorited non-direct items that have URLs, we add a direct item elsewhere. So
          ;; here we only need to deal with things that do have direct items.
          item-id->url (into {}
                             (q db
                                '{:find [item2 url]
                                  :in [[url ...]]
                                  :where [[usit :user-item/item item1]
                                          [usit :user-item/favorited-at]
                                          [item1 :item/url url]
                                          [item2 :item/url url]]}
                                direct-urls))
          item-usits (mapv first
                           (q db
                              '{:find [(pull usit [*])]
                                :in [[item ...]]
                                :where [[usit :user-item/item item]]}
                              (keys item-id->url)))
          skip-usits (q db
                        '{:find [user item (count skip)]
                          :keys [user-item/user user-item/item user-item/n-skips]
                          :in [[item ...]]
                          :where [[skip :skip/items item]
                                  [skip :skip/user user]]}
                        (keys item-id->url))
          usit-key (juxt :user-item/user :user-item/item)
          user+item->skip-usit (into {}
                                     (map (juxt usit-key identity))
                                     skip-usits)
          ad-usits (q db
                      '{:find [user ad created-at]
                        :keys [user-item/user user-item/item user-item/viewed-at]
                        :where [[click :ad.click/user user]
                                [click :ad.click/ad ad]
                                [click :ad.click/created-at created-at]]})
          all-usits (->> (concat (for [usit (concat item-usits ad-usits)]
                                   (merge usit (user+item->skip-usit (usit-key usit))))
                                 skip-usits)
                         (lib.core/distinct-by usit-key)
                         (mapv (fn [usit]
                                 (let [url (or (item-id->url (:user-item/item usit))
                                               (:user-item/item usit))]
                                   (assoc usit
                                          :user-item/url url ; can we get rid of this?
                                          :candidate-type (if (uuid? url)
                                                            :ad
                                                            :item))))))
          ;; can index->url just be index->item-id?
          ;; we're already mapping from url to the direct item somewhere...
          [[index->url url->index]
           [_ user->index]]
          (for [k [:user-item/url :user-item/user]
                :let [index->x (->> all-usits
                                    (mapv k)
                                    distinct
                                    (map-indexed vector)
                                    (into {}))]]
            [index->x (into {} (map (fn [[k v]] [v k])) index->x)])

          ratings (->> (for [usit all-usits
                             :let [score (cond
                                           (:user-item/favorited-at usit) 1
                                           (:user-item/disliked-at usit) 0
                                           (:user-item/reported-at usit) 0
                                           (:user-item/viewed-at usit) 0.75
                                           (:user-item/skipped-at usit) (max 0 (- 0.5 (* 0.1 (:user-item/n-skips usit 0))))
                                           (:user-item/bookmarked-at usit) 0.6
                                           ;; shouldn't ever happen; just being defensive.
                                           :else 0.5)]]
                         (rating. (int (user->index (:user-item/user usit)))
                                  (int (url->index (:user-item/url usit)))
                                  (double score)))
                       (.parallelize spark)
                       (.rdd)
                       (.cache))
          rank 10
          iterations 20
          lambda 0.1
          alpha 0.05
          _ (log/info "training ALS")
          als (ALS/trainImplicit ratings rank iterations lambda alpha)
          _ (log/info "computing baselines")
          baselines (-> (into {} (.. als
                                     (recommendUsersForProducts (count user->index))
                                     (map (AverageRating.)
                                          (.apply ClassTag$/MODULE$ clojure.lang.PersistentVector))
                                     (collect)))
                        (update-keys index->url))
          candidate-type->urls (-> (group-by :candidate-type all-usits)
                                   (update-vals (fn [usits]
                                                  (->> usits
                                                       (mapv :user-item/url)
                                                       distinct
                                                       vec))))
          [item-median-score
           ad-median-score] (for [k [:item :ad]]
                              (or (median (sort (mapv baselines (candidate-type->urls k)))) 0.5))
          get-url->score (fn [user-id]
                           (let [url->score (if-some [user-idx (user->index user-id)]
                                              (->> (.recommendProducts als user-idx (count index->url))
                                                   (into {} (map (fn [^Rating rating]
                                                                   [(index->url (.product rating))
                                                                    (.rating rating)]))))
                                              baselines)]
                             (fn [url]
                               (get url->score url (if (uuid? url)
                                                     ad-median-score
                                                     item-median-score)))))
          url->n-usits (update-vals (group-by :user-item/url all-usits)
                                    count)
          url->last-liked (update-vals (group-by :user-item/url all-usits)
                                       (fn [usits]
                                         (->> (keep :user-item/favorited-at usits)
                                              (apply max-key
                                                     inst-ms
                                                     (Instant/ofEpochMilli 0)))))
          item-candidates (->> (candidate-type->urls :item)
                               (sort-by (juxt url->n-usits (comp - inst-ms url->last-liked)))
                               vec)
          candidate-ids (into #{} (map :xt/id) direct-items)]
      (log/info "done")
      {:yakread.model/candidates item-candidates
       :yakread.model/candidate-ids candidate-ids
       :yakread.model/url->last-liked url->last-liked
       :yakread.model/get-url->score get-url->score})))

(defn use-spark [{:keys [biff.xtdb/node] :as ctx}]
  (let [spark (doto (JavaSparkContext. "local[*]" "yakread")
                (.setCheckpointDir "storage/spark-checkpoint"))
        model (atom (new-model {:yakread/spark spark
                                :biff/db (xt/db node)
                                :biff/now (Instant/now)}))]
    (-> ctx
        (assoc :yakread/spark spark
               :yakread/model model)
        (update :biff/stop conj #(.close spark)))))

(comment
  (-> (new-model (biff/merge-context @com.yakread/system))
      :yakread.model/candidates
      count)
  )
