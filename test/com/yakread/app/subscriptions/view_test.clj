(ns com.yakread.app.subscriptions.view-test
  (:require [clojure.test :refer [deftest is]]
            [com.yakread.app.subscriptions.view :as sut]
            [com.yakread.lib.test :as lib.test]
            [com.yakread :as main]))

(def route-examples
  (lib.test/route-examples
   [:app.subscriptions.view.read/mark-read :post :end]
   [{:ctx {:biff.pipe.pathom/output {:session/user {:xt/id 1}
                                     :params/item {:xt/id 2}}}}]

   [:app.subscriptions.view.read/favorite :post :end]
   [{:ctx {:biff.pipe.pathom/output
           {:params/item
            {:item/id #uuid "46e5bf4c-dc56-402a-ad7d-03610274c067"
             :item/user-item {:xt/id 2}}}}}]))

(defn get-context []
  {:biff/router          main/router
   :biff.test/current-ns (lib.test/current-ns)
   :biff.test/examples   route-examples})

(deftest examples
  (lib.test/check-examples! (get-context)))

(comment
  (lib.test/write-examples! (get-context))
  ,)
