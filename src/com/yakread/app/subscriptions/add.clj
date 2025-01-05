(ns com.yakread.app.subscriptions.add
  (:require [clojure.data.generators :as gen]
            [com.biffweb :as biff :refer [q <<-]]
            [com.yakread.lib.middleware :as lib.middle]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [com.yakread.lib.pipeline :as lib.pipe]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.rss :as lib.rss]
            [com.yakread.lib.ui :as lib.ui]
            [com.yakread.lib.user :as lib.user]
            [xtdb.api :as xt]))

(def page-route
  ["/dev/subscriptions/add"
   {:name :app.subscriptions.add/page
    :get (lib.pathom/handler
          [:app.shell/app-shell
           {(? :user/current) [:xt/id
                               (? :user/email-username)
                               (? :user/suggested-email-username)]}]

          (fn [{:keys [biff/router params] :as ctx}
               {:keys [app.shell/app-shell] user :user/current}]
            (app-shell
             {:title "Add subscriptions"}
             (lib.ui/page-header {:title     "Add subscriptions"
                                  :back-href (lib.route/path router :app.subscriptions/page {})})
             (when-not user
               [:div {:class '["max-sm:-mx-4"
                               mb-6]}
                (lib.ui/signup-box ctx
                                   {:on-success  :app.subscriptions.add/page
                                    :on-error    :app.subscriptions.add/page
                                    :title       nil
                                    :description (str "Create a Yakread account to subscribe to "
                                                      "newsletters and RSS feeds.")})])
             [:fieldset.disabled:opacity-60
              {:disabled (when-not user "disabled")}
              (lib.ui/page-well
               (lib.ui/section
                {:title "Newsletters"}
                (if-some [username (:user/email-username user)]
                  [:div "Sign up for newsletters with "
                   [:span.font-semibold username "@yakread.com"]]
                  (biff/form
                    {:action (lib.route/path router :app.subscriptions.add/username {})
                     :hx-indicator "#username-indicator"}
                    (lib.ui/uber-input
                     {:label "Username"
                      :description "You can subscribe to newsletters after you pick a username."
                      :postfix "@yakread.com"
                      :name "username"
                      :value (or (:email-username params)
                                 (:user/suggested-email-username user))
                      :required true
                      :submit-text "Save"
                      :indicator-id "username-indicator"
                      :error (when (= (:error params) "username-unavailable")
                               "That username is unavailable.")}))))
               (lib.ui/section
                {:title "RSS feeds"}
                (when-some [n (some-> (:added-feeds params) parse-long)]
                  [:div {:class '[bg-tealv-50
                                  border-l-4
                                  border-tealv-200
                                  p-3
                                  text-neut-800]}
                   "Subscribed to " (lib.ui/pluralize n "feed") "."])
                (biff/form
                  {:action (lib.route/path router :app.subscriptions.add/rss {})
                   :hx-indicator (str "#" (lib.ui/dom-id ::rss-indicator))}
                  (lib.ui/uber-input
                   {:label "Website or feed URL" ; TODO spinner icon
                    :name "url"
                    :value (:url params)
                    :required true
                    :submit-text "Subscribe"
                    :description [:<> "You can also "
                                  [:button.link {:_ "on click toggle .hidden on #bookmarklet-modal"
                                                 :type "button"}
                                   "subscribe via bookmarklet"] "."]
                    :indicator-id (lib.ui/dom-id ::rss-indicator)
                    :error (when (= (:error params) "invalid-rss-feed")
                             "We weren't able to subscribe to that URL.")})
                  (lib.ui/modal
                   {:id "bookmarklet-modal"
                    :title "Subscribe via bookmarklet"}
                   [:.p-4
                    [:p "You can install the bookmarklet by dragging this link "
                     "onto your browser toolbar or bookmarks menu:"]
                    [:p.my-6 [:a.text-xl.text-blue-600
                              {:href "javascript:window.location=\"https://yakread.com/subscriptions/add?url=\"+encodeURIComponent(document.location)"}
                              "Subscribe | Yakread"]]
                    [:p.mb-0 "Then click the bookmarklet to subscribe to the RSS feed for the current page."]]))
                (biff/form
                  {:action (lib.route/path router :app.subscriptions.add/opml {})
                   :hx-indicator (str "#" (lib.ui/dom-id ::opml-indicator))
                   :enctype "multipart/form-data"}
                  (lib.ui/uber-input
                   {:label "OPML file"
                    :name "opml"
                    :type "file"
                    :accept ".opml"
                    :required true
                    :submit-text "Import"
                    :submit-opts {:class '["w-[92px]"]}
                    :indicator-id (lib.ui/dom-id ::opml-indicator)
                    :error (when (= (:error params) "invalid-opml-file")
                             "We weren't able to import that file.")}))))])))}])

(def username-route
  ["/dev/subscriptions/add/username"
   {:name :app.subscriptions.add/username
    :post (let [response (fn [success username]
                           (merge {:status 303
                                   :biff.router/name :app.subscriptions.add/page}
                                  (when-not success
                                    {:biff.router/params
                                     {:error "username-unavailable"
                                      :email-username username}})))]
            (lib.pipe/make
             :start
             (fn [{:keys [biff/db session params]}]
               (let [username (lib.user/normalize-email-username (:username params))]
                 (cond
                   (:user/email-username (xt/entity db (:uid session)))
                   (response true nil)

                   (or (empty? username)
                       (biff/lookup-id db :user/email-username username))
                   (response false (:username params))

                   :else
                   {:biff.pipe/next     [:biff.pipe/tx :end]
                    :biff.pipe.tx/input [{:db/doc-type :user
                                          :db/op :update
                                          :xt/id (:uid session)
                                          :user/email-username [:db/unique username]}]
                    :biff.pipe/catch    :biff.pipe/tx
                    ::username          username})))

             :end
             (fn [{:keys [biff.pipe/exception ::username]}]
               (response (not exception) username))))}])

(defn- subscribe-feeds-tx [db user-id feed-urls]
  (let [url->feed (into {} (q db
                              '{:find [url feed]
                                :in [[url ...]]
                                :where [[feed :feed/url url]]}
                              feed-urls))
        new-urls  (remove url->feed feed-urls)]
    (for [[i url] (map-indexed vector feed-urls)
          :let [feed-id (get url->feed url (gen/uuid))]
          doc (concat [{:db/doc-type    :sub/feed
                        :db.op/upsert   {:sub/user user-id
                                         :sub.feed/feed feed-id}
                        :sub/created-at [:db/default :db/now]}]
                      (when-not (url->feed url)
                        [{:db/doc-type :feed
                          :db/op       :create
                          :xt/id       feed-id
                          :feed/url    [:db/unique url]}]))]
      doc)))

(defn- sync-rss-jobs [tx priority]
  (for [{:keys [feed/url xt/id]} tx
        :when url]
    {:biff.pipe/current :biff.pipe/queue
     :biff.pipe.queue/id :work.subscription/sync-feed
     :biff.pipe.queue/job {:feed/id id :biff/priority priority}}))

(def rss-route
  ["/dev/subscriptions/add/rss"
   {:name :app.subscriptions.add/rss
    :post (lib.pipe/make
           :start
           (fn [{{:keys [url]} :params}]
             {:biff.pipe/next       [:biff.pipe/http :add-urls]
              :biff.pipe.http/input {:url     (lib.rss/fix-url url)
                                     :method  :get
                                     :headers {"User-Agent" "https://yakread.com/"}}
              :biff.pipe/catch      :biff.pipe/http})

           :add-urls
           (fn [{:keys [biff/db session biff.pipe.http/output]}]
             (let [feed-urls (some->> output
                                      lib.rss/parse-urls
                                      (mapv :url)
                                      (take 20)
                                      vec)
                   tx (subscribe-feeds-tx db (:uid session) feed-urls)]
               (if (empty? feed-urls)
                 {:status             303
                  :biff.router/name   :app.subscriptions.add/page
                  :biff.router/params {:error "invalid-rss-feed"
                                       :url (:url output)}}
                 {:biff.pipe/next     (into [:biff.pipe/tx] (sync-rss-jobs tx 0))
                  :biff.pipe.tx/input tx
                  :biff.pipe.tx/retry :add-urls ; TODO implement
                  :status             303
                  :biff.router/name   :app.subscriptions.add/page
                  :biff.router/params {:added-feeds (count feed-urls)}}))))}])

(def opml-route
  ["/dev/subscriptions/add/opml"
   {:name :app.subscriptions.add/opml
    :post (lib.pipe/make
           :start
           (fn [{{{:keys [tempfile]} :opml} :params}]
             {:biff.pipe/next        [:biff.pipe/slurp :end]
              :biff.pipe.slurp/input tempfile})

           :end
           (fn [{:keys [biff/db session biff.pipe.slurp/output]}]
             (if-some [urls (not-empty (lib.rss/extract-opml-urls output))]
               (let [tx (subscribe-feeds-tx db (:uid session) urls)]
                 {:biff.pipe/next     (into [:biff.pipe/tx] (sync-rss-jobs tx 5))
                  :biff.pipe.tx/input tx
                  :biff.pipe.tx/retry :end ; TODO implement
                  :status             303
                  :biff.router/name   :app.subscriptions.add/page
                  :biff.router/params {:added-feeds (count urls)}})
               {:status             303
                :biff.router/name   :app.subscriptions.add/page
                :biff.router/params {:error "invalid-opml-file"}})))}])

(def module
  {:routes [page-route
            ["" {:middleware [lib.middle/wrap-signed-in]}
             username-route
             rss-route
             opml-route]]})
