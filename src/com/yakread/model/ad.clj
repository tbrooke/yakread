(ns com.yakread.model.ad 
  (:require
   [com.biffweb :as biff]
   [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver]]))

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

(def module {:resolvers [ad-id
                         xt-id
                         effective-bid
                         user-ad]})
