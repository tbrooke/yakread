(ns com.yakread.model.ad
  (:require
   [com.biffweb :as biff]
   [com.wsscode.pathom3.connect.operation :as pco :refer [? defresolver]]
   [com.yakread.lib.content :as lib.content]
   [com.yakread.lib.core :as lib.core]))

;; TODO keep recent-cost updated (or calculate it on the fly if it's fast enough)
(defresolver effective-bid [{:ad/keys [bid budget recent-cost]}]
  {:ad/effective-bid (min bid (max 0 (- budget recent-cost)))})

(defresolver ad-id [{:keys [xt/id ad/user]}]
  {:ad/id id})

(defresolver xt-id [{:keys [ad/id]}]
  {:xt/id id})

(defresolver user-ad [{:keys [biff/db]} {:keys [xt/id]}]
  {::pco/output [{:user/ad [:xt/id]}]}
  (when-some [ad-id (biff/lookup-id db :ad/user id)]
    {:user/ad {:xt/id ad-id}}))

(defresolver url-with-protocol [{:keys [ad/url]}]
  {:ad/url-with-protocol (lib.content/add-protocol url)})

(def ^:private required-fields
  [:ad/payment-method
   :ad/bid
   :ad/budget
   :ad/url
   :ad/title
   :ad/description
   :ad/image-url])

(defresolver state [{:ad/keys [paused
                               payment-failed
                               approve-state]
                     :as ad}]
  {::pco/input (into [(? :ad/paused)
                      (? :ad/payment-failed)
                      :ad/approve-state]
                     (mapv ? required-fields))
   ::pco/output [:ad/state
                 :ad/incomplete-fields]}
  (let [incomplete-fields (remove (comp lib.core/something? ad) required-fields)]
    {:ad/state (cond
                 payment-failed :payment-failed
                 paused :paused
                 (not-empty incomplete-fields) :incomplete
                 (= approve-state :approved) :running
                 (= approve-state :rejected) :rejected
                 (= approve-state :pending) :pending)
     :ad/incomplete-fields incomplete-fields}))

(def module {:resolvers [ad-id
                         xt-id
                         effective-bid
                         user-ad
                         url-with-protocol
                         state]})
