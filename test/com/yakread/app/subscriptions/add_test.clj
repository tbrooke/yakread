(ns com.yakread.app.subscriptions.add-test
  (:require [clj-http.client :as http]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [com.biffweb :as biff]
            [com.yakread :as main]
            [com.yakread.app.subscriptions.add :as sut]
            [com.yakread.lib.pathom :as pathom]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.test :as lib.test]))

(def username-route-examples
  (for [opts [{:doc         "username taken"
               :handler-id  :start
               :db-contents #{{:xt/id 1
                               :user/email-username* "abc"}}
               :ctx         {:params {:username "abc"}}}
              {:doc         "username taken"
               :handler-id  :start
               :db-contents #{{:xt/id 1
                               :conn.email/username "abc"}}
               :ctx         {:params {:username "abc"}}}
              {:doc         "username not taken"
               :handler-id  :start
               :db-contents #{}
               :ctx         {:params {:username "abc"}}}
              {:doc        "empty username"
               :handler-id :start}

              {:doc        "early return"
               :handler-id :save-username
               :ctx        {:session {:uid 1}
                            ::sut/username "abc"
                            :biff.pipe.pathom/output {:user/current
                                                      {:user/email-username
                                                       "hello"}}}}
              {:doc        "transact"
               :handler-id :save-username
               :ctx        {:session {:uid 1}
                            ::sut/username "abc"}}

              {:doc        "fail"
               :handler-id :end
               :ctx        {::sut/username "hello"
                            :biff.pipe/exception true}}
              {:doc        "succeed"
               :handler-id :end
               :ctx        {::sut/username "hello"}}]]
    (merge {:route-name :app.subscriptions.add/username}
           opts)))

(def rss-route-examples
  (concat [{:doc        "fix the url"
            :route-name :app.subscriptions.add/rss
            :method     :post
            :handler-id :start
            :ctx        {:params {:url "example.com"}}}]
          (for [fixture [:example-com :obryant-dev :obryant-dev-feed-xml]]
            {:route-name :app.subscriptions.add/rss
             :method     :post
             :handler-id :add-urls
             :ctx        {:session {:uid 1}}
             :fixture    fixture})))

(def opml-route-examples
  [{:doc "slurp the uploaded file"
    :route-name :app.subscriptions.add/opml
    :handler-id :start
    :ctx {:params {:opml {:tempfile "/tmp/some-file"}}}}
   {:doc "extract and save the opml urls"
    :route-name :app.subscriptions.add/opml
    :handler-id :end
    :fixture :sample-opml
    :ctx {:session {:uid 1}}}
   {:doc "no urls found, show an error"
    :route-name :app.subscriptions.add/opml
    :handler-id :end
    :ctx {:session {:uid 1}
          :biff.pipe.slurp/output ""}}])

(defn get-context []
  (let [current-ns (lib.test/current-ns)
        fixtures   (merge (lib.test/read-fixtures! current-ns)
                          {:sample-opml {:biff.pipe.slurp/output
                                         (slurp (io/file (lib.test/dirname current-ns) "sample.opml"))}})]
    {:biff/router          main/router
     :biff.test/current-ns current-ns
     :biff.test/fixtures   fixtures
     :biff.test/examples   (concat username-route-examples
                                   rss-route-examples
                                   opml-route-examples)}))

(deftest examples
  (lib.test/check-examples! (get-context)))

(comment
  ;; Generate fixtures
  (let [{:biff.pipe/keys [http]} (:biff.pipe/global-handlers main/initial-system)
        http-get (fn [url]
                   (http {:biff.pipe.http/input
                          {:url url
                           :method :get
                           :headers {"User-Agent" "https://yakread.com"}}}))]
    (lib.test/write-fixtures!
     (lib.test/current-ns)
     {:example-com          (http-get "https://example.com")
      :obryant-dev          (http-get "https://obryant.dev")
      :obryant-dev-feed-xml (http-get "https://obryant.dev/feed.xml")}))

  (lib.test/write-examples! (get-context))

  ,)
