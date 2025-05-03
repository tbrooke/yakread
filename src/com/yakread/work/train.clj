(ns com.yakread.work.train
  (:require [com.biffweb :as biff :refer [q]]
            [com.yakread.lib.core :as lib.core]
            [com.yakread.lib.item :as lib.item]
            [com.yakread.lib.spark :as lib.spark]
            [com.yakread.lib.pipeline :as lib.pipe :refer [defpipe]]
            [clojure.tools.logging :as log]))

(defn retrain [{:keys [yakread/model] :as ctx}]
  (reset! model (lib.spark/new-model ctx)))

(defpipe add-candidate!
  (lib.item/add-item-pipeline*
   {:get-url    (comp :item/url :biff/job)
    :on-success (fn [ctx item]
                  (log/info "Ingested candidate" (:item/url item))
                  {})
    :on-error   (fn [ctx {:keys [item/url]}]
                  (let [{:keys [status]} (ex-data (:biff.pipe/exception ctx))]
                    (if (= status 429)
                      (do
                        (log/warn "Received status 429 when fetching candidate" url)
                        {:biff.pipe/next [(lib.pipe/sleep 10000)]})
                      {:biff.pipe/next
                       [(lib.pipe/tx [{:db/op                        :create
                                       :db/doc-type                  :item/direct
                                       :item/url                     url
                                       :item/ingested-at             :db/now
                                       :item/doc-type                :item/direct
                                       :item.direct/candidate-status :ingest-failed}])
                        (lib.pipe/sleep 2000)]})))}))

(defpipe queue-add-candidate
  :start
  (fn [{:keys [biff/db biff/queues]}]
    (when (= 0 (.size (:work.train/add-candidate queues)))
      (let [urls (q db
                    '{:find url
                      :where [[usit :user-item/item item]
                              [usit :user-item/favorited-at]
                              [item :item/url url]]})
            direct-urls (set
                         (q db
                            '{:find url
                              :in [[url ...] direct]
                              :where [[item :item/url url]
                                      [item :item/doc-type direct]]}
                            urls
                            :item/direct))
            urls (vec (remove direct-urls urls))]
        (when (not-empty urls)
          (log/info "Found" (count urls) "candidate URLs"))
        {:biff.pipe/next (for [url urls]
                           (lib.pipe/queue :work.train/add-candidate {:item/url url}))}))))

(def module
  {:tasks [{:task     #'retrain
            :schedule (lib.core/every-n-minutes 30)}
           {:task     #'queue-add-candidate
            :schedule (lib.core/every-n-minutes 10)}]
   :queues [{:id        :work.train/add-candidate
             :consumer  #'add-candidate!
             :n-threads 1}]})
