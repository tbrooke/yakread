(ns com.yakread.model.ad 
  (:require
   [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver]]))

;; TODO keep recent-cost updated (or calculate it on the fly if it's fast enough)
(defresolver effective-bid [{:ad/keys [bid budget recent-cost]}]
  {:ad/effective-bid (min bid (max 0 (- budget recent-cost)))})

(def module {:resolvers [effective-bid]})
