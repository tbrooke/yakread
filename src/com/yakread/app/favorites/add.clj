(ns com.yakread.app.favorites.add
  (:require [clojure.string :as str]
            [clojure.data.generators :as gen]
            [com.biffweb :as biff :refer [q <<-]]
            [com.yakread.lib.middleware :as lib.middle]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [com.yakread.lib.route :refer [defget defpost href redirect]]
            [com.yakread.lib.rss :as lib.rss]
            [com.yakread.lib.ui :as ui]
            [com.yakread.lib.user :as lib.user]
            [com.yakread.routes :as routes]
            [xtdb.api :as xt]))

#_(defn add-article [{:keys [biff/db user params] :as ctx}]
    (let [{:keys [url]} params
          url (str/trim url)
          article (or (biff/lookup db :item/url url :item/type :article)
                      (biff/catchall-verbose
                       (util-art/get-article! ctx url)))
          article-id (or (:xt/id article) (random-uuid))]
      (when article
        (biff/submit-tx ctx
          (concat
           [{:db/doc-type :rec
             :db.op/upsert {:rec/user (:xt/id user)
                            :rec/item article-id}
             :rec/created-at :db/now
             :rec/viewed-at :db/now
             :rec/rating :like}]
           (when (:db/doc-type article)
             [(assoc article :xt/id article-id)]))))
      {:status 303
       :headers {"location" (util/make-url "/favorites/add"
                                           (if article
                                             {:added "true"}
                                             {:error "invalid-url"}))}}))

;; TODO test
(let [success (fn [user-id item-id]
                {:biff.pipe/next [:biff.pipe/tx]
                 :biff.pipe.tx/input [{:db/doc-type :user-item
                                       :db.op/upsert {:user-item/user user-id
                                                      :user-item/item item-id}
                                       :user-item/favorited-at :db/now}]
                 :status 303
                 :headers {"HX-Redirect" (href `page {:added true})}})
      error (fn []
              {:status 303
               :headers {"HX-Redirect" (href `page {:error true})}})]
  (defpost add-item
    :start
    (fn [{:keys [biff/db session] {:keys [url]} :params}]
      (let [url (str/trim url)]
        (if-some [item-id (biff/lookup-id db :item/url url :item/doc-type :item/direct)]
          (success (:uid session) item-id)
          {:biff.pipe/next       [:biff.pipe/http :handle-http]
           :biff.pipe.http/input {:url url
                                  :method  :get
                                  :headers {"User-Agent" "https://yakread.com/"}
                                  :socket-timeout 5000
                                  :connection-timeout 5000}
           :biff.pipe/catch      :biff.pipe/http})))

    :handle-http
    (fn [{:keys [biff/db session biff.pipe.http/output]}]
      (if-not (and (some-> output :headers (get "Content-Type") (str/includes? "text"))
                   (< (count (:body output)) (* 2 1000 1000)))
        (error)
        {:biff.pipe/next [:yakread.pipe/js :handle-readability]
         ::url (:url output)
         ::raw-html (:body output)
         :yakread.pipe.js/fn-name "readability"
         :yakread.pipe.js/input {:url (:url output) :html (:body output)}}))

    :handle-readability
    (fn [_]
      ;; TODO
      nil)))

(defget page "/dev/favorites/add"
  [:app.shell/app-shell
   {(? :session/user) [:user/id]}]
  (fn [{:keys [params] :as ctx}
       {:keys [app.shell/app-shell session/user]}]
    (app-shell
     {:title "Add favorites"}
     (ui/page-header {:title     "Add favorites"
                      :back-href (href routes/favorites-page)})
     (when-not user
       [:div {:class '["max-sm:-mx-4"
                       mb-6]}
        (ui/signup-box ctx
                       {:on-success  (href page)
                        :on-error    (href page)
                        :title       nil
                        :description "Create a Yakread account to save articles to your favorites."})])
     [:fieldset.disabled:opacity-60
      {:disabled (when-not user "disabled")}
      (ui/page-well
       (ui/section
        {}
        (when (:added params)
          (ui/callout {:ui/type :info :ui/icon nil} "Article added."))
        (when (:error params)
          (ui/callout {:ui/type :error :ui/icon nil} "We weren't able to add that article."))
        [:form
         {:hx-post (href add-item)
          :hx-indicator (str "#" (ui/dom-id ::indicator))}
         (ui/form-input
          {:ui/label "Article URL"
           :ui/submit-text "Add"
           :ui/description [:<> "You can also "
                            [:button.link {:_ "on click toggle .hidden on #bookmarklet-modal"
                                           :type "button"}
                             "add articles via bookmarklet"] "."]
           :ui/indicator-id (ui/dom-id ::indicator)
           :name "url"
           :value (:url params)
           :required true})
         (ui/modal
          {:id "bookmarklet-modal"
           :title "Add favorites via bookmarklet"}
          [:.p-4
           [:p "You can install the bookmarklet by dragging this link on to your browser toolbar or
                bookmarks menu:"]
           [:p.my-6 [:a.text-xl.text-blue-600
                     {:href "javascript:window.location=\"https://yakread.com/favorites/add?url=\"+encodeURIComponent(document.location)"}
                     "Add to favorites | Yakread"]]
           [:p.mb-0 "Then click the bookmarklet to add the current article to your favorites."]])]))])))

(def module
  {:routes [page
            ["" {:middleware [lib.middle/wrap-signed-in]}
             add-item]]})
