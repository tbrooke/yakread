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

(defn new-model [{:keys [yakread/spark biff/db]}]
  (log/info "updating model")
  (let [url-allowed? (constantly true) ; TODO
        direct-urls (set
                     (mapv first
                           (q db
                              '{:find [url]
                                :in [direct]
                                :where [[item :item/url url]
                                        [item :item/doc-type direct]]}
                              :item/direct)))
        item-id->url (into {}
                           (filter (fn [[_ url]]
                                     (and (url-allowed? url)
                                          (direct-urls url))))
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
    {:yakread.model/candidates candidates
     :yakread.model/url->last-liked url->last-liked
     :yakread.model/get-url->score get-url->score}))

(defn use-spark [{:keys [biff.xtdb/node] :as ctx}]
  (let [spark (doto (JavaSparkContext. "local[*]" "yakread")
                (.setCheckpointDir "storage/spark-checkpoint"))
        model (atom (new-model {:yakread/spark spark
                                :biff/db (xt/db node)}))]
    (-> ctx
        (assoc :yakread/spark spark
               :yakread/model model)
        (update :biff/stop conj #(.close spark)))))
