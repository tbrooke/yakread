(ns com.yakread.model.feed
  (:require
   [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver]]))

(defresolver feed-title [{:keys [feed/url]}]
  {:feed/title url})

(def module
  {:resolvers [feed-title]})
