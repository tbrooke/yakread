(ns com.yakread.app.subscriptions.view
  (:require [clojure.string :as str]
            [com.biffweb :as biff :refer [<<-]]
            [com.yakread.lib.middleware :as lib.middle]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [com.yakread.lib.pipeline :as lib.pipe]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.serialize :as lib.serialize]
            [com.yakread.lib.ui :as lib.ui]
            [com.yakread.model.subscription :as model.sub]
            [lambdaisland.uri :as uri]
            [xtdb.api :as xt]))

(def page-route
  ["/dev/subscriptions/view/:sub-id"
   {:name :app.subscriptions.view/page
    :get (lib.pathom/handler
          [:app.shell/app-shell
           {(? :params/sub) [:sub/title
                             :sub/user]}
           {(? :params/item) [:xt/id
                              :item/title]}]
          (fn [{:keys [biff/router session path-params]}
               {:keys [app.shell/app-shell]
                current-item :params/item
                {:sub/keys [title user] :as sub} :params/sub}]
            (if (not= user (:uid session))
              {:status 303
               :biff.router/name :app.subscriptions/page}
              (app-shell
               {:title title}
               ;; TODO display item
               (when current-item
                 [:pre.mb-6 (pr-str current-item)])
               (lib.ui/page-header {:title     title
                                    :back-href (lib.route/path router :app.subscriptions/page {})})
               [:div#content (lib.ui/lazy-load-spaced router :app.subscriptions.view.page/content path-params)]))))}])

(defn- clean-string [s]
  (str/replace (apply str (remove #{\newline
                                    (char 65279)
                                    (char 847)
                                    (char 8199)}
                                  s))
               #"\s+"
               " "))

(defn- item-byline [{:item/keys [byline
                                 author-name
                                 site-name
                                 url]}]
  (some->> [(or author-name byline)
            (or site-name
                (when url
                  (:host (uri/uri url))))]
           (keep #(some-> % str/trim not-empty))
           not-empty
           (str/join " | ")))

(defn- fancy-format-date [date]
  (let [date-fmt (if (apply = (map #(biff/crop-date % "yyyy") [date (java.util.Date.)]))
                   "d MMM"
                   "d MMM yyyy")]
    (biff/format-date date date-fmt)))

(defn- pluralize [n label]
  (str n " " label (when (not= 1 n) "s")))

(def ^:private interpunct " · ")

(defn- reading-minutes [n-characters]
  (max 1 (Math/round (/ n-characters 900.0))))

(defn- item-details [{:item/keys [url
                                  published-at
                                  fetched-at
                                  author-name
                                  byline
                                  site-name
                                  length]
                      :keys [item.extra/type
                             item.extra/date]
                      :as item}]
  (->> [(item-byline item)
        (fancy-format-date (or date published-at fetched-at))
        (when length
          (pluralize (reading-minutes length) "minute"))
        (when-some [label ({:bookmark "Bookmarked"
                            :subscription "Subscribed"
                            :new-subscription "New subscription"
                            :ad "Ad"
                            :discover "Discover"
                            :current "Continue reading"} type)]
          [:span.underline label])]
       (filter some?)
       (map #(vector :span.inline-block %))
       (biff/join interpunct)))

(def page-content-route
  ["/dev/subscriptions/view/:sub-id/content"
   {:name :app.subscriptions.view.page/content
    :get (lib.pathom/handler
          [{(? :params/sub) [:xt/id
                             {:sub/items
                              [:xt/id
                               :item/title
                               :item/unread
                               (? :item/image-with-default)
                               (? :item/author-name)
                               (? :item/byline)
                               (? :item/excerpt)
                               (? :item/fetched-at)
                               (? :item/length)
                               (? :item/published-at)
                               (? :item/site-name)
                               (? :item/url)]}]}
           {(? :params/item) [:xt/id
                              :item/title]}
           {:user/current [(? :user/use-original-links)]}]
          (fn [{:keys [biff/router session path-params]}
               {:keys [app.shell/app-shell]
                current-item :params/item
                {:keys [user/use-original-links]} :user/current
                {:sub/keys [items] conn-id :xt/id} :params/sub}]
            (if-not conn-id
              {:status 404 :body ""}
              [:<>
               [:div {:class '[flex
                               flex-col
                               gap-6
                               max-w-screen-sm
                               "max-sm:-mx-4"]}
                (for [{:item/keys [title excerpt unread image-with-default url] :as item} items]
                  [:a (if (and use-original-links url)
                        {:href url ; TODO set to /dev/read/:id
                         :target "_blank"}
                        {:href (lib.route/path router
                                 :app.subscriptions.view/page
                                 {:sub-id (lib.serialize/edn->base64
                                           {:sub/conn-id conn-id
                                            :item/id (:xt/id item)})})})
                   [:div {:class (concat '[bg-white
                                           hover:bg-neut-50
                                           p-4
                                           sm:shadow]
                                         (when unread
                                           '[max-sm:border-t-4
                                             sm:border-l-4
                                             border-tealv-500]))}
                    [:.text-neut-600.text-sm.line-clamp-2
                     (item-details item)]
                    [:.h-1]
                    [:h3 {:class '[font-bold
                                   text-xl
                                   text-neut-800
                                   leading-tight
                                   line-clamp-2]}
                     title]
                    [:.h-2]
                    [:.flex.gap-3.justify-between
                     [:div
                      (when (not= excerpt "Read more")
                        [:.line-clamp-4.text-neut-600.mb-1
                         {:style {:overflow-wrap "anywhere"}}
                         (clean-string excerpt)])
                      [:div {:class '[text-tealv-600
                                      font-semibold
                                      hover:underline
                                      inline-block]}
                       "Read more."]]
                     (when image-with-default
                       [:.relative.flex-shrink-0
                        [:img {:src (lib.ui/weserv {:url image-with-default
                                                    :w 150
                                                    :h 150
                                                    :fit "cover"
                                                    :a "attention"})
                               :_ "on error remove me"
                               :class '[rounded
                                        object-cover
                                        object-center
                                        "mt-[6px]"
                                        "w-[5.5rem]"
                                        "h-[5.5rem]"]}]
                        [:div {:style {:box-shadow "inset 0 0px 6px 1px #0000000d"}
                               :class '[absolute
                                        inset-x-0
                                        "top-[6px]"
                                        "h-[5.5rem]"
                                        rounded]}]])]]])]])))}])

(def module
  {:routes [["" {:middleware [lib.middle/wrap-signed-in]}
             page-route
             page-content-route]]})
