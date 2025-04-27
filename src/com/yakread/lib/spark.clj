(ns com.yakread.lib.spark
  (:require [com.biffweb :as biff]
            [xtdb.api :as xt :refer [q]]
            [com.yakread.lib.core :as lib.core]
            [clojure.tools.logging :as log])
  (:import [org.apache.spark.api.java JavaSparkContext]
           [org.apache.spark.mllib.recommendation Rating ALS MatrixFactorizationModel]
           [scala Tuple2 Function1]
           [scala.reflect ClassTag$]
           [com.yakread AverageRating]))

(defn- median [xs]
  (first (take (/ (count xs) 2) xs)))

(defn update-model! [{:keys [yakread/model
                             yakread/spark
                             biff.xtdb/node]}]
  (log/info "updating model")
  (let [db (xt/db node)
        url-allowed? (constantly true) ; TODO
        item-id->url (into {}
                           (filter (comp url-allowed? second))
                           (q db
                              '{:find [item2 url]
                                :where [[usit :user-item/item item1]
                                        [usit :user-item/favorited-at]
                                        [item1 :item/url url]
                                        [item2 :item/url url]]}))
        all-usits (mapv first
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
        all-usits (->> (concat (for [usit all-usits]
                                 (merge usit (user+item->skip-usit (usit-key usit))))
                               skip-usits)
                       (lib.core/distinct-by usit-key)
                       (mapv #(assoc % :user-item/url (item-id->url (:user-item/item %)))))

        [[index->url url->index]
         [index->user user->index]]
        (for [k [:user-item/url :user-item/user]
              :let [index->x (->> all-usits
                                  (mapv k)
                                  distinct
                                  (map-indexed vector)
                                  (into {}))]]
          [index->x (into {} (map (fn [[k v]] [v k])) index->x)])

        ratings (->> all-usits
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
        median-score (median (sort (vals baselines)))
        get-url->score (fn [user-id]
                         (prn (some? (user->index user-id)))
                         (let [url->score (if-some [user-idx (user->index user-id)]
                                            (->> (.recommendProducts als user-idx (count index->url))
                                                 (into {} (map (fn [^Rating rating]
                                                                 [(index->url (.product rating))
                                                                  (.rating rating)]))))
                                            baselines)]
                           (fn [url]
                             (get url->score url median-score))))
        url->n-usits (update-vals (group-by :user-item/url all-usits)
                                  count)
        url->last-liked (update-vals (group-by :user-item/url all-usits)
                                     (fn [usits]
                                       (->> (keep :user-item/favorited-at usits)
                                            (apply max-key inst-ms))))
        candidates (->> (vals index->url)
                        (sort-by (juxt url->n-usits (comp - inst-ms url->last-liked)))
                        vec)]
    (reset! model {:yakread.model/candidates candidates
                   :yakread.model/url->last-liked url->last-liked
                   :yakread.model/get-url->score get-url->score})))

(defn use-spark [ctx]
  (let [spark (JavaSparkContext. "local[*]" "yakread")
        ctx (-> ctx
                (assoc :yakread/spark spark
                       :yakread/model (atom {}))
                (update :biff/stop conj #(.close spark)))]
    (.setCheckpointDir spark "storage/spark-checkpoint")
    (update-model! ctx)
    ctx))
