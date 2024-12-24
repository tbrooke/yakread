(ns com.yakread.app.subscriptions-test
  (:require [clojure.test :refer [deftest is]]
            [com.yakread.app.subscriptions :as sut]))

(def pin-put    (get-in sut/pin-route [1 :put]))
(def pin-delete (get-in sut/pin-route [1 :delete]))

(deftest pin-route
  (is (= (pin-put {:params {:sub/id 1}} :start)
         {:biff.pipe/next [:biff.pipe/tx :biff.pipe/render],
          :biff.pipe.tx/input
          [{:db/doc-type :sub/any,
            :db/op :update,
            :xt/id 1,
            :sub/pinned-at :db/now}],
          :biff.pipe.render/route-name :app.subscriptions.page/content}))

  (is (= (pin-delete {:params {:sub/id 1}} :start)
         {:biff.pipe/next [:biff.pipe/tx :biff.pipe/render],
          :biff.pipe.tx/input
          [{:db/doc-type :sub/any,
            :db/op :update,
            :xt/id 1,
            :sub/pinned-at :db/dissoc}],
          :biff.pipe.render/route-name :app.subscriptions.page/content})))
