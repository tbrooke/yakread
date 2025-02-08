(ns com.yakread.app.subscriptions.view-test
  (:require [clojure.test :refer [deftest is]]
            [com.yakread.app.subscriptions.view :as sut]
            [com.yakread.lib.test :as lib.test]
            [com.yakread :as main]))

(def mutation-examples
  (lib.test/mutation-examples
   [`sut/mark-read :end]
   [{:ctx {:biff.pipe.pathom/output {:session/user {:xt/id 1}
                                     :params/item {:xt/id 2}}}}]

   [`sut/mark-unread :end]
   [{:ctx {:biff.pipe.pathom/output
           {:session/user {:xt/id 1}
            :params/item {:xt/id 2
                          :item/sub {:xt/id #uuid "d6c955e8-68d2-4779-84d6-abd823b2f20b"}}}}}]

   [`sut/toggle-favorite :end]
   [{:ctx {:biff.pipe.pathom/output
           {:params/item
            {:item/id #uuid "46e5bf4c-dc56-402a-ad7d-03610274c067"
             :item/user-item {:xt/id 2}}}}}]

   [`sut/not-interested :end]
   [{:ctx {:biff.pipe.pathom/output
           {:params/item
            {:item/sub {:xt/id #uuid "46e5bf4c-dc56-402a-ad7d-03610274c067"}
             :item/user-item {:xt/id 1}}}}}]

   [`sut/unsubscribe :end]
   [{:ctx {:biff.pipe.pathom/output
           {:params/item
            {:item/sub {:sub/id 1
                        :sub/doc-type :sub/email}}}}}
    {:ctx {:biff.pipe.pathom/output
           {:params/item
            {:item/sub {:sub/id 1
                        :sub/doc-type :sub/feed}}}}}]))

(defn get-context []
  {:biff/router          main/router
   :biff.test/current-ns (lib.test/current-ns)
   :biff.test/examples   mutation-examples})

(deftest examples
  (lib.test/check-examples! (get-context)))

(comment
  (lib.test/write-examples! (get-context))
  ,)
