(ns com.yakread.model.source
  (:require [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]))

(defresolver source-title [{sub-title :sub/title feed-title :feed/title :as params}]
  {::pco/input [(? :sub/title)
                (? :feed/title)]
   ::pco/output [:source/title]}
  (some->> (or sub-title feed-title)
           (hash-map :source/title)))

(def module
  {:resolvers [source-title]})
