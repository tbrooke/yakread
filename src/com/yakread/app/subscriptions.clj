(ns com.yakread.app.subscriptions
  (:require [cheshire.core :as cheshire]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [com.biffweb :as biff]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.htmx :as lib.htmx]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.yakread.lib.pipeline :as lib.pipe]
            [com.yakread.lib.route :as lib.route :refer [href defget defpost-pathom]]
            [com.yakread.lib.serialize :as lib.serialize]
            [com.yakread.lib.ui :as ui]
            [com.yakread.lib.middleware :as lib.mid]
            [com.yakread.routes :as routes]
            [com.yakread.util :as util]
            [xtdb.api :as xt]
            [taoensso.tufte :as tufte]
            [clojure.tools.logging :as log])
  (:import [java.time Instant]))

(defpost-pathom unsubscribe
  [{:params/sub [:sub/id
                 :sub/doc-type
                 {(? :sub/latest-item)
                  [:item/id
                   (? :item.email/list-unsubscribe)
                   (? :item.email/list-unsubscribe-post)]}]}]
  (fn [_ {{:sub/keys [id doc-type latest-item] :as sub} :params/sub}]
    (let [base {:status 204
                :headers {"HX-Location" (href routes/subs-page)}
                :biff.pipe/next [:biff.pipe/tx]
                :biff.pipe.tx/retry false}]
      (case doc-type
        :sub/feed
        (merge base
               {:biff.pipe.tx/input [[::xt/delete id]]})

        :sub/email
        (let [{:item.email/keys [list-unsubscribe list-unsubscribe-post]} latest-item
              url (second (re-find #"<(http[^>]+)>" (or list-unsubscribe "")))
              email (second (re-find #"<mailto:([^>]+)>" (or list-unsubscribe "")))]
          (merge-with into
                      base
                      {:biff.pipe.tx/input [{:db/doc-type :sub/email
                                             :db/op :update
                                             :xt/id id
                                             :sub.email/unsubscribed-at :db/now}]}
                      (cond
                        (and url (= (some-> list-unsubscribe-post str/lower-case)
                                    "list-unsubscribe=one-click"))
                        {:biff.pipe/next [:biff.pipe/http]
                         :biff.pipe.http/input {:url url :method :post}
                         :biff.pipe/catch :biff.pipe/http}

                        email
                        {:biff.pipe/next [:biff.pipe/email]
                         :biff.pipe.email/input {:to email :subject "unsubscribe"}}

                        url
                        {:headers {"HX-Trigger" (cheshire/generate-string {:yak/open-new-tab url})}})))))))

(defpost-pathom toggle-pin
  [{:params/sub [:sub/id
                 :sub/doc-type
                 (? :sub/pinned-at)]}]
  (fn [_ {{:sub/keys [id pinned-at doc-type]}
          :params/sub}]
    {:biff.pipe/next              [:biff.pipe/tx :biff.pipe/render*]
     :biff.pipe.tx/input          [{:db/doc-type   doc-type
                                    :db/op         :update
                                    :xt/id         id
                                    :sub/pinned-at (if pinned-at
                                                     :db/dissoc
                                                     :db/now)}]
     :biff.pipe.render/route      `page-content-route}))

(defpost-pathom resubscribe
  [{:params.checked/subscriptions
    [:sub/id]}]
  (fn [_ {:keys [params.checked/subscriptions]}]
    {:biff.pipe/next [:biff.pipe/tx]
     :biff.pipe.tx/input (for [{:sub/keys [id]} subscriptions]
                           {:db/doc-type :sub/email
                            :db/op :update
                            :xt/id id
                            :sub.email/unsubscribed-at :db/dissoc})
     :status 204
     :headers {"HX-Redirect" (href `unsubs-page)}}))

(defresolver sub-card [{:sub/keys [id title unread published-at pinned-at]}]
  #::pco{:input [:sub/id
                 :sub/title
                 :sub/unread
                 (? :sub/published-at)
                 (? :sub/pinned-at)]}
  {:sub.view/card
   [:.relative
    [:div {:class '[absolute
                    top-1.5
                    right-0]}
     (ui/overflow-menu
      {:ui/rounded true}
      (let [[hx-method label] (if pinned-at
                                [:hx-delete "Unpin"]
                                [:hx-put "Pin"])]
        (ui/overflow-button
         {:hx-post (href toggle-pin {:sub/id id})
          :hx-target "#content"
          :hx-on:htmx:before-request "this.closest('.sub-card').remove()"}
         label))
      (ui/overflow-button
       {:hx-post (href unsubscribe {:sub/id id})
        :hx-confirm (ui/confirm-unsub-msg title)}
       "Unsubscribe"))]
    [:a {:href (href routes/view-sub-page id)
         :class (concat '[block
                          bg-white
                          shadow
                          p-2
                          text-sm
                          hover:bg-neut-50]
                        (when (< 0 unread)
                          '[border-l-4
                            border-tealv-500]))}
     [:.truncate.font-semibold.mr-6 (or (not-empty title) "[no title]")]
     [:.text-neut-800.mr-6 unread " unread posts"]]]})

(defn- empty-state []
  (ui/empty-page-state {:icons ["envelope-regular-sharp"
                                "square-rss-regular-sharp"]
                        :text [:span {:class '["max-w-[22rem]"
                                               inline-block]}
                               "Customize your experience by subscribing to newsletters and RSS feeds."]
                        :btn-label "Add subscriptions"
                        :btn-href (href routes/add-sub-page)}))

(defget unsubs-page "/dev/unsubscribed"
  [:app.shell/app-shell
   {:session/user
    [{:user/unsubscribed
      [:sub/id
       :sub/title
       :sub.email/unsubscribed-at]}]}]
  (fn [_ {:keys [app.shell/app-shell]
          {:user/keys [unsubscribed]} :session/user}]
    (app-shell
     {:title "Unsubscribed"}
     (ui/page-header {:title     "Unsubscribed"
                      :back-href (href routes/subs-page)})
     (ui/page-well
      [:form.space-y-4
       {:hx-post (href resubscribe)}
       (ui/callout {:ui/type :info}
                   "These newsletters are hidden from your subscriptions list. Even if you move a
                    newsletter back to your subscriptions, you won't receive new posts unless you
                    re-subscribe on the newsletter's website.")
       (for [{:sub/keys [id title]} (sort-by :sub.email/unsubscribed-at #(compare %2 %1) unsubscribed)]
         (ui/checkbox {:name (str "subs[" id "]") :ui/label title}))
       (ui/button {:type "submit"} "Move to subscriptions")]))))

(defget page-content-route "/dev/subscriptions/content"
  [{:session/user
    [{:user/subscriptions [:sub/id
                           :sub/title
                           :sub.view/card
                           (? :sub/published-at)
                           (? :sub/pinned-at)
                           (? :sub.email/unsubscribed-at)]}
     {:user/unsubscribed [:sub/id]}]}]
  (fn [_ {{:user/keys [subscriptions unsubscribed]} :session/user}]
    (let [{pinned-subs true unpinned-subs false} (group-by (comp some? :sub/pinned-at) subscriptions)]
      [:<>
       (ui/page-header {:title    "Subscriptions"
                        :add-href (href routes/add-sub-page)
                        :actions (when (not-empty unsubscribed)
                                   (ui/overflow-menu
                                    {:ui/rounded true
                                     :ui/icon "ellipsis-regular"
                                     :ui/hover-shade :dark}
                                    (ui/overflow-button
                                     {:href (href unsubs-page)}
                                     "Unsubscribed newsletters")))})
       (if (empty? subscriptions)
         (empty-state)
         [:div.grow.flex.flex-col
          (biff/join [:div#sub-divider {:class '[my-10
                                                 border border-neut-200]}]
                     (for [[id subscriptions] [["pinned" pinned-subs]
                                               ["unpinned" unpinned-subs]]
                           :when (not-empty subscriptions)
                           :let [cnt (count subscriptions)]]
                       [:div {:id id
                              :class '["grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5"
                                       gap-4]}
                        (for [[i sub] (->> subscriptions
                                           (sort-by :sub/published-at #(compare %2 %1))
                                           (map-indexed vector))]
                          [:.sub-card {:style {:z-index (- (+ 10 cnt) i)}}
                           (:sub.view/card sub)])]))])])))

(defget page-route "/dev/subscriptions"
  [:app.shell/app-shell (? :user/current)]
  (fn [_ {:keys [app.shell/app-shell] user :user/current}]
    (app-shell
     {:wide true}
     (if user
       [:div#content (ui/lazy-load-spaced (href page-content-route))]
       [:<>
        (ui/page-header {:title    "Subscriptions"
                         :add-href (href routes/add-sub-page)})
        (empty-state)]))))

(def module
  {:resolvers [sub-card]
   :routes [page-route
            ["" {:middleware [lib.mid/wrap-signed-in]}
             page-content-route
             toggle-pin
             resubscribe
             unsubs-page
             unsubscribe]]})
