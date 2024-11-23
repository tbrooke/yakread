(ns com.yakread.schema
  (:require [com.biffweb :refer [doc-schema] :rename {doc-schema doc}]))

(defn conn-doc [m]
  (doc (update m
               :required
               (fnil into [])
               [[:xt/id :conn/id]
                :conn/user])))

(defn item-doc [& required]
  (doc {:required (into [[:xt/id :item/id]
                         :item/title
                         :item/fetched-at]
                        required)
        :optional [:item/content
                   :item/url
                   :item/published-at
                   :item/author-name
                   :item/author-url
                   :item/inferred-feed-url
                   :item/lang
                   :item/site-name
                   :item/byline
                   :item/length
                   :item/excerpt
                   :item/image
                   :item/backfill-id
                   :item/flags
                   :item.email/unsubscribe
                   :item.email/from-address
                   :item.email/reply-to
                   :item.email/maybe-confirmation
                   :item.email/hidden]}))

(defn job-doc [type opts]
  (doc (merge-with into
                   {:required [[:xt/id          :job/id]
                               [:job/type       [:enum type]]
                               [:job/status     [:enum :init :processing :done :error]]
                               [:job/created-at inst?]]}
                   opts)))

(def schema
  {:user/id :uuid
   :day     [:enum :sunday :monday :tuesday :wednesday :thursday :friday :saturday]
   ;; todo add wildcard param for query params
   :user    (doc {:required [[:xt/id                   :user/id]
                             [:user/email              :string]]
                  :optional [[:user/current-batch      :uuid]
                             [:user/signup-href        :string]
                             [:user/referrer           :string]
                             [:user/referred-by        :user/id]
                             [:user/referral-value     integer?]
                             [:user/joined-at          inst?]
                             [:user/subscribed-days    [:set :day]]
                             [:user/use-original-links :boolean]
                             [:user/last-sent          inst?]
                             ;; hour UTC
                             [:user/send-time          :int]
                             [:user/suppressed         :boolean]
                             [:user/signup-event       :event/id]
                             [:user/auto-subbed        :boolean]
                             [:user/survey-sent-at     inst?]
                             [:user/referral-code      :string]
                             [:user/customer-id        :string]
                             [:user/plan               [:enum :quarter :annual]]
                             [:user/cancel-at          inst?]
                             [:user/onboarding-state   [:enum :active :done]]

                             [:user/activation-username-sent inst?]
                             [:user/activation-import-gmail-sent inst?]
                             [:user/activation-suggestions-sent inst?]
                             [:user/activation-install-pwa-sent inst?]
                             [:user/email-username* :string]]})

   :ad/id :uuid
   :ad (doc {:required [[:xt/id             :ad/id]
                        [:ad/user           :user/id]
                        [:ad/state          [:enum :pending :approved :rejected]]
                        [:ad/balance        integer?]
                        [:ad/recent-cost    integer?]]
             :optional [[:ad/customer-id    :string]
                        [:ad/session-id     :string]
                        [:ad/payment-method :string]
                        [:ad/card-details   map?]
                        [:ad/bid            integer?]
                        [:ad/budget         integer?]
                        [:ad/url            :string]
                        [:ad/title          :string]
                        [:ad/description    :string]
                        [:ad/image          :string]
                        [:ad/paused         :boolean]
                        [:ad/updated-at     inst?]
                        [:ad/payment-failed :boolean]]})

   :ad.click (doc {:required [[:xt/id                :uuid]
                              [:ad.click/user        :user/id]
                              [:ad.click/ad          :ad/id]
                              [:ad.click/created-at  inst?]
                              [:ad.click/cost        integer?]
                              [:ad.click/source      [:enum :web :email]]]
                   :optional [[:ad.click/auth-user   :user/id]
                              [:ad.click/timeline-id inst?]]})

   :ad.credit (doc {:required [[:xt/id                :uuid]
                               [:ad.credit/ad         :ad/id]
                               [:ad.credit/type       [:enum :charge :manual]]
                               [:ad.credit/amount     integer?]
                               [:ad.credit/created-at inst?]]
                    :optional [[:ad.credit/status     [:enum
                                                       :pending
                                                       :confirmed
                                                       :failed]]]})

   :conn/id         :uuid
   :conn/user       :user/id
   :conn/synced-at  inst?
   :conn/rss        (conn-doc {:required [[:conn.rss/url               :string]
                                          [:conn.rss/subscribed-at     inst?]]
                               :optional [:conn/synced-at
                                          [:conn/feed                  :feed/id]
                                          [:conn.rss/from-opml         :string]
                                          [:conn.rss/source            [:enum
                                                                        :manual
                                                                        :article]]
                                          [:conn.rss/title             :string]]})
   :conn/email      (conn-doc {:required [[:conn/singleton-type        [:enum :email]]
                                          [:conn.email/username        :string]]})

   :item/id                       :uuid
   :item/title                    :string
   :item/content                  :string
   :item/fetched-at               inst?
   :item/url                      :string
   :item/published-at             inst?
   :item/author-name              :string
   :item/author-url               :string
   :item/inferred-feed-url        [:or :string [:enum :none :unchecked]]
   :item/lang                     :string
   :item/site-name                :string
   :item/byline                   :string
   :item/length                   number?
   :item/excerpt                  :string
   :item/image                    :string
   :item/backfill-id              number?
   :item/flags                    [:set [:enum :paywalled]]
   :item.email/reply-to           :string
   :item.email/maybe-confirmation :boolean
   :item.email/from-address       :string
   :item.email/unsubscribe        :string
   :item.email/hidden             :boolean
   :item/rss          (item-doc [:item.rss/feed-url       :string])
   :item/email        (item-doc [:item.email/user         :user/id]
                                [:item.email/raw-content  :string])
   :item/article      (item-doc [:item/type               [:enum :article]])


   :bookmark/id :uuid
   :bookmark (doc {:required [[:xt/id               :bookmark/id]
                              [:bookmark/created-at inst?]
                              [:bookmark/user       :user/id]
                              [:bookmark/state      [:enum :new :processed]]]
                   :optional [[:bookmark/urls       [:vector :string]]
                              [:bookmark/items      [:vector :item/id]]]})

   :rss/id :uuid
   :rss (doc {:required [[:xt/id             :rss/id]
                         [:rss/url           :string]
                         [:rss/synced-at     inst?]]
              :optional [[:rss/title         string?]
                         [:rss/description   string?]
                         [:rss/image         string?]
                         [:rss/etag          string?]
                         [:rss/last-modified string?]
                         [:rss/failed        :boolean]
                         [:rss/mod-state     [:enum :approved :blocked]]]})

   :current/id :uuid
   :current (doc {:required [[:xt/id        :current/id]
                             [:current/user :user/id]
                             [:current/item :item/id]]})

   :rec/id :uuid
   :rec (doc {:required [[:xt/id             :rec/id]
                         [:rec/user          :user/id]
                         [:rec/item          :item/id]
                         [:rec/created-at    inst?]]
              :optional [[:rec/batch         :uuid]
                         [:rec/position      :int]
                         [:rec/viewed-at     inst?]
                         [:rec/skipped       boolean?]
                         [:rec/rating        [:enum :like :dislike]]
                         [:rec/reported-at   inst?]
                         [:rec/report-reason :string]
                         ;; deprecated
                         [:rec/type       [:enum
                                           :discovery
                                           :ad
                                           :findka-essays
                                           :manual
                                           :read-later
                                           :select]]
                         ;; this is, uh, basically what type should've been
                         [:rec/flags      [:set [:enum
                                                 :social
                                                 :discover
                                                 :read-later
                                                 :rss
                                                 :email
                                                 :book
                                                 :open
                                                 :favorites]]]
                         [:rec/read-later :boolean]
                         [:rec/source [:enum
                                       :email
                                       :home
                                       :subscriptions
                                       :read-later
                                       :books
                                       :discover
                                       :open
                                       :favorites]]]})

   :view/id :uuid
   :view (doc {:required [[:xt/id            :view/id]
                          [:view/user        :user/id]
                          [:view/timeline-id inst?]
                          [:view/items       [:vector [:or :item/id :ad/id]]]]
               :optional [[:view/source      [:enum :web :email]]]})

   :event/id :uuid
   :event (doc {:required [[:xt/id                      :event/id]
                           [:event/type                 [:enum
                                                         :page-view
                                                         :signup
                                                         :subscribe
                                                         :navigate]]
                           [:event/timestamp            inst?]]
                :optional [[:event/ip                   :string]
                           [:event/user                 :user/id]
                           [:event/variant              :string]
                           [:event.page-view/path       :string]
                           [:event.page-view/referrer   :string]
                           [:event.subscribe/opml       :string]
                           [:event.subscribe/feeds      [:vector :string]]
                           [:event.navigate/page        :keyword]
                           [:event.navigate/inner-width number?]
                           [:event.navigate/display-mode :keyword]]
                :wildcards {'event.params               any?}})

   :pinned (doc {:required [[:xt/id              :uuid]
                            [:pinned/user        :user/id]]
                 :optional [[:pinned/newsletters [:set :string]]
                            [:pinned/rss         [:set :conn/id]]]})

   ;;; DEPRECATED ==============================================================

   :profile/id :uuid
   :profile    (doc {:required [[:xt/id            :profile/id]
                                [:profile/user     :user/id]
                                [:profile/username :string]
                                [:profile/title    :string]]})

   :feed/id :uuid
   :feed    (doc {:required [[:xt/id            :feed/id]
                             [:feed/user        :user/id]
                             [:feed/rss-url     :string]
                             [:feed/sort-time   inst?]
                             [:feed/signups     integer?]
                             [:feed/downloads   integer?]]
                  :optional [[:feed/title       :string]
                             [:feed/url         :string]
                             [:feed/description :string]
                             [:feed/image       :string]]})

   :conn/pocket     (conn-doc {:required [[:conn/singleton-type        [:enum :pocket]]
                                          [:conn.pocket/username       :string]
                                          [:conn.pocket/access-token   :string]]
                               :optional [:conn/synced-at]})
   :conn/instapaper (conn-doc {:required [[:conn/singleton-type        [:enum :instapaper]]
                                          [:conn.instapaper/username   :string]
                                          [:conn.instapaper/user-id    integer?]
                                          [:conn.instapaper/token      :string]
                                          [:conn.instapaper/secret     :string]]
                               :optional [:conn/synced-at]})
   :conn/twitter    (conn-doc {:required [[:conn/singleton-type        [:enum :twitter]]
                                          [:conn.twitter/username      :string]
                                          [:conn.twitter/user-id       :string]
                                          [:conn.twitter/refresh-token :string]
                                          [:conn.twitter/access-token  :string]
                                          [:conn.twitter/expires-at    inst?]]
                               :optional [:conn/synced-at]})
   :conn/discord    (conn-doc {:required [[:conn/singleton-type        [:enum :discord]]
                                          [:conn.discord/username      :string]
                                          [:conn.discord/refresh-token :string]
                                          [:conn.discord/access-token  :string]
                                          [:conn.discord/expires-at    inst?]]
                               :optional [:conn/synced-at]})
   :conn/mastodon   (conn-doc {:required [[:conn/singleton-type        [:enum :mastodon]]
                                          [:conn.mastodon/username     :string]
                                          [:conn.mastodon/server       :string]
                                          [:conn.mastodon/access-token :string]]
                               :optional [[:conn.mastodon/last-id      :string]
                                          :conn/synced-at]})
   :conn/epub       (conn-doc {:required [[:conn.epub/title            :string]
                                          [:conn.epub/author-name      :string]
                                          [:conn.epub/items            [:vector :item/id]]]
                               :optional [[:conn.epub/next-item        :item/id]
                                          [:conn.epub/type             [:enum :series :epub]]]})

   :mastodon (doc {:required [[:xt/id :uuid]
                              [:mastodon/server :string]
                              [:mastodon/client-id :string]
                              [:mastodon/client-secret :string]]})

   :item/pdf          (item-doc [:item.pdf/content        :string]
                                [:item.pdf/filename       :string]
                                [:item.pdf/user           :user/id])
   :item/epub         (item-doc [:item/type               [:enum :epub]])
   :item/twitter      (item-doc [:item.twitter/user-id    :string]
                                [:item.twitter/tweet-ids  [:vector :string]]
                                [:item.twitter/author-ids [:vector :string]])
   :item/social (item-doc [:item.social/user     :user/id]
                          [:item.social/post-ids [:vector :keyword]])

   :page/pocket (doc {:required [[:xt/id                :uuid]
                                 [:page/fetched-at      inst?]
                                 [:page.pocket/username :string]
                                 [:page.pocket/urls     [:set :string]]]})
   :page/instapaper (doc {:required [[:xt/id                   :uuid]
                                     [:page/fetched-at         inst?]
                                     [:page.instapaper/user-id integer?]
                                     [:page.instapaper/urls    [:set :string]]]})

   ;; additional keys not used in DB
   :item/conn-type [:enum :pocket :rss :email :twitter]

   :discq/id :uuid
   :discq (doc {:required [[:xt/id            :discq/id]
                           [:discq/user       :user/id]
                           [:discq/items      [:sequential :item/id]]
                           [:discq/updated-at inst?]]})

   :queue/id :uuid
   :queue (doc {:required [[:xt/id              :queue/id]
                           [:queue/user         :user/id]
                           [:queue/items        [:sequential :item/id]]
                           [:queue/updated-at   inst?]]
                :optional [[:queue/book-weights [:map-of :conn/id number?]]]})

   :hcti (doc {:required [[:xt/id       :uuid]
                          [:hcti/url    :string]
                          [:hcti/params map?]]})

   :notif/id     :uuid
   :notification (doc {:required [[:xt/id            :notif/id]
                                  [:notif/user       :user/id]
                                  [:notif/created-at inst?]
                                  [:notif/state      [:enum :unread :read]]]
                       :optional [[:notif/new-email  :item/id]
                                  [:notif/html       :string]]})

   :screen/id :uuid
   :screen (doc {:required [[:xt/id             :screen/id]
                            [:screen/approved   [:vector :string]]
                            [:screen/blocked    [:vector :string]]
                            [:screen/created-at inst?]]})

   :mfeed/id :uuid
   :mainfeed (doc {:required [[:xt/id            :mfeed/id]
                              [:mfeed/url        :string]
                              [:mfeed/hits       number?]
                              [:mfeed/misses     number?]
                              [:mfeed/top-posts  [:vector :item/id]]
                              [:mfeed/updated-at inst?]]})})

(def module
  {:schema schema})

;; fs storage:
;; - :item/content -> html
;; - :item.email/raw-content -> email contents
;; - [:com.yakread.feat.ingest/url-contents url] -> raw html
;; - :com.yakread.feat.ingest/failed-urls -> set of urls
;; - :com.yakread.feat.ingest/last-fetched -> inst
;; - [:com.yakread.feat.read/position uid] -> number or something
;;
