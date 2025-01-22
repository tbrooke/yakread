(ns com.yakread.model.schema)

(defn inherit [base-schema & map-args]
  [:merge base-schema (into [:map {:closed true}] map-args)])

(def ? {:optional true})
(defn r [target] {:biff/ref (if (coll? target) target #{target})})
(def ?r {:biff/ref true, :optional true})

(def schema
  {::string  [:string {:max 1000}]
   ::day     [:enum :sunday :monday :tuesday :wednesday :thursday :friday :saturday]
   :ref/uuid :uuid

   :user [:map {:closed true}
          [:xt/id                     :uuid]
          [:user/email                ::string]
          [:user/roles              ? [:set [:enum :admin]]]
          [:user/joined-at          ? :time/instant]
          [:user/digest-days        ? [:set ::day]]
          [:user/send-digest-at     ? :time/local-time]
          [:user/timezone           ? :time/zone-id]
          [:user/digest-last-sent   ? :time/instant]
          ;; When the user views an item, when possible, open the original URL in a new tab instead of displaying the
          ;; item within Yakread.
          [:user/use-original-links ? :boolean]
          ;; The user reported our emails as spam or emails to them hard-bounced, so don't send them any more emails.
          [:user/suppressed-at      ? :time/instant]
          ;; Used for email subscriptions (<username>@yakread.com)
          [:user/email-username     ? ::string]]

   :sub/base  [:map {:closed true}
               [:xt/id                    :uuid]
               [:sub/user       (r :user) :uuid]
               [:sub/created-at           :time/instant]
               [:sub/pinned-at  ?         :time/instant]]
   :sub/feed  (inherit :sub/base
                       [:sub.feed/feed (r :feed) :ref/uuid])
   ;; :sub-email is automatically created when the user receives an email with a new From field.
   :sub/email (inherit :sub/base
                       [:sub.email/from            ::string]
                       ;; If the user unsubscribes, instead of deleting the :sub-email, we set this flag. Then even if the
                       ;; newsletter sends more emails, we won't accidentally re-subscribe them.
                       [:sub.email/unsubscribed-at :time/instant])
   :sub/any   [:or :sub/feed :sub/email]

   :item/base  [:map {:closed true}
                [:xt/id               :uuid]
                [:item/ingested-at    :time/instant]
                [:item/title        ? ::string]
                [:item/url          ? ::string]
                ;; If the content is <= 1000 chars, put it in XT, otherwise, put it in S3
                [:item/content      ? ::string]
                [:item/content-key  ? :uuid]
                [:item/published-at ? :time/instant]
                [:item/excerpt      ? ::string]
                [:item/author-name  ? ::string]
                [:item/author-url   ? ::string]
                ;; An autodiscovered feed url, parsed from the item's content. Contrast with :item/feed -> :feed/url,
                ;; which is the feed URL from which this item was fetched.
                [:item/feed-url     ? ::string]
                [:item/lang         ? ::string]
                [:item/site-name    ? ::string]
                [:item/byline       ? ::string]
                [:item/length       ? :int]
                [:item/image-url    ? ::string]
                [:item/paywalled    ? :boolean]]
   :item/feed  (inherit :item/base
                        [:item.feed/feed (r :feed) :uuid]
                        ;; The RSS <guid> / Atom <id> field.
                        [:item.feed/guid ? ::string])
   :item/email (inherit :item/base
                        [:item.email/sub                (r :sub/email) :uuid]
                        [:item.email/content-key                       :uuid]
                        [:item.email/unsubscribe        ?              ::string]
                        [:item.email/reply-to           ?              ::string]
                        [:item.email/maybe-confirmation ?              :boolean])

   :feed [:map {:closed true}
          [:xt/id                :uuid]
          [:feed/url             ::string]
          [:feed/synced-at     ? :time/instant]
          [:feed/title         ? ::string]
          [:feed/description   ? ::string]
          [:feed/image-url     ? ::string]
          [:feed/etag          ? ::string]
          [:feed/last-modified ? ::string]
          [:feed/failed-syncs  ? :int]
          ;; Only :approved feeds can be recommended to other users in For You.
          [:feed/moderation    ? [:enum :approved :blocked]]]

   ;; The relationship between a :user and an :item. Ways a :user-item can be created:
   ;; - user clicks on it from For You/Subscriptions
   ;; - user adds it to Read Later/Favorites
   ;; - we send the user a digest email using this item's title in the subject line
   ;; - we recommend the item in For You and the user reports it
   :user-item [:map {:closed true}
               [:xt/id                                     :uuid]
               [:user-item/user          (r :user)         :uuid]
               [:user-item/item          (r [:item/feed
                                             :item/email]) :uuid]
               [:user-item/viewed-at     ?                 :time/instant]
               [:user-item/bookmarked-at ?                 :time/instant]
               [:user-item/favorited-at  ?                 :time/instant]
               [:user-item/digested-at   ?                 :time/instant]
               [:user-item/position      ?r                :uuid]
               ;; User clicked thumbs-down. Mutually exclusive with :user-item/favorited-at
               [:user-item/disliked-at   ?                 :time/instant]
               ;; This item was recommended in For You and the user reported it.
               [:user-item/reported-at   ?                 :time/instant]
               [:user-item/report-reason ?                 ::string]]

   ;; Split this out of :user-item because it'll change very frequently.
   :position [:map {:closed true}
              [:xt/id          :uuid]
              [:position/value :int]]

   ;; When the user clicks on item in For You, any previous items they scrolled past get added to a :skip document.
   :skip [:map {:closed true}
          [:xt/id                             :uuid]
          [:skip/user       (r :user)         :uuid]
          [:skip/skipped-at                   :time/instant]
          [:skip/items      (r [:item/feed
                                :item/email]) [:vector :uuid]]]})

(def module
  {:schema schema})
