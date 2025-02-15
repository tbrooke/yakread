(ns com.yakread.app.subscriptions-test
  (:require [clojure.test :refer [deftest is]]
            [com.yakread.app.subscriptions :as sut]
            [com.yakread.lib.test :as lib.test]
            [com.yakread :as main]))

(def pin-examples
  (lib.test/route-examples
   [::sut/toggle-pin :post :start*]
   [{:doc "pin"
     :ctx {:biff.pipe.pathom/output
           {:params/sub
            {:sub/id 1
             :sub/doc-type :sub/feed}}}}
    {:doc "unpin"
     :ctx {:biff.pipe.pathom/output
           {:params/sub
            {:sub/id 1
             :sub/pinned-at "now"
             :sub/doc-type :sub/feed}}}}]))

(defn get-context []
  {:biff/router          main/router
   :biff.test/current-ns (lib.test/current-ns)
   :biff.test/examples   pin-examples})

(deftest examples
  (lib.test/check-examples! (get-context)))

(comment
  (lib.test/write-examples! (get-context))
  ,)
