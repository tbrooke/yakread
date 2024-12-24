(ns com.yakread.app.subscriptions-test
  (:require [clojure.test :refer [deftest is]]
            [com.yakread.app.subscriptions :as sut]
            [com.yakread.lib.test :as lib.test]
            [com.yakread :as main]))

(def pin-examples
  (lib.test/route-examples
   [:app.subscriptions.page/pin :put :start]
   [{:doc "pin"
     :ctx {:params {:sub/id 1}}}]

   [:app.subscriptions.page/pin :delete :start]
   [{:doc "unpin"
     :ctx {:params {:sub/id 1}}}]))

(defn get-context []
  {:biff/router          main/router
   :biff.test/current-ns (lib.test/current-ns)
   :biff.test/examples   pin-examples})

(deftest examples
  (lib.test/check-examples! (get-context)))

(comment
  (lib.test/write-examples! (get-context))
  ,)
