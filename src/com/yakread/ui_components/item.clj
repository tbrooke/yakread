(ns com.yakread.ui-components.item
  (:require [com.biffweb :as biff]
            [clojure.string :as str]
            [com.yakread.lib.content :as lib.content]
            [com.yakread.lib.route :refer [href]]
            [com.yakread.lib.ui :as ui]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.routes :as routes]
            [lambdaisland.uri :as uri])
  (:import (java.time Instant ZoneOffset)
           (java.time.format DateTimeFormatter)))

(defn- reading-minutes [n-characters]
  (max 1 (Math/round (/ n-characters 900.0))))

(defresolver details [{:item/keys [doc-type
                                   byline
                                   author-name
                                   url
                                   published-at
                                   ingested-at
                                   length
                                   rec-type
                                   source]}]
  {::pco/input [:item/id
                :item/title
                :item/ingested-at
                :item/doc-type
                (? :item/byline)
                (? :item/author-name)
                (? :item/site-name)
                (? :item/url)
                (? :item/published-at)
                (? :item/length)
                (? :item/rec-type)
                {(? :item/source) [:source/title]}]}
  {:item/ui-details
   (fn [{:keys [show-author show-reading-time]
         :or {show-reading-time true}}]
     (->> [(when show-author
             (some-> (or (:source/title source) author-name byline) str/trim not-empty))
           (when (= doc-type :item/direct)
             (some-> url uri/uri :host str/trim not-empty))
           (let [offset ZoneOffset/UTC ; TODO get timezone for user
                 odt (.atOffset (or published-at ingested-at) offset)
                 same-year (= (.getYear odt)
                              (.getYear (.atOffset (Instant/now) offset)))
                 formatter (DateTimeFormatter/ofPattern (if same-year
                                                          "d MMM"
                                                          "d MMM yyyy"))]
             (.format odt formatter))
           (when (and show-reading-time length)
             (ui/pluralize (reading-minutes length) "minute"))
           (when-some [label (case rec-type
                               :item.rec-type/bookmark "Bookmarked"
                               :item.rec-type/subscription "Subscribed"
                               :item.rec-type/new-subscription "New subscription"
                               :item.rec-type/ad "Ad"
                               :item.rec-type/discover "Discover"
                               :item.rec-type/current "Continue reading"
                               nil)]
             [:span.underline label])]
          (filter some?)
          (map #(vector :span.inline-block %))
          (biff/join ui/interpunct)))})

(defresolver read-more-card [{:item/keys [id ui-details title excerpt unread image-url]}]
  {::pco/input [:item/id
                :item/unread
                :item/ui-details
                (? :item/title)
                (? :item/image-url)
                (? :item/author-name)
                (? :item/byline)
                (? :item/excerpt)
                (? :item/fetched-at)
                (? :item/length)
                (? :item/published-at)
                (? :item/site-name)
                (? :item/url)]}
  {:item/ui-read-more-card
   (fn [{:keys [highlight-unread on-click-route show-author on-click-params new-tab]}]
     [:a {:href (href on-click-route id (when (not-empty on-click-params)
                                          on-click-params))
          :target (when new-tab "_blank")}
      [:div {:class (concat '[bg-white hover:bg-neut-50
                              p-4
                              sm:shadow]
                            (when (and highlight-unread unread)
                              '[max-sm:border-t-4 sm:border-l-4 border-tealv-500]))}
       [:.text-neut-600.text-sm.line-clamp-2
        (ui-details {:show-author show-author})]
       [:.h-1]
       [:h3 {:class '[font-bold text-xl text-neut-800
                      leading-tight
                      line-clamp-2]}
        title]
       [:.h-2]
       [:.flex.gap-3.justify-between
        [:div
         (when (not= excerpt "Read more")
           [:.line-clamp-4.text-neut-600.mb-1
            {:style {:overflow-wrap "anywhere"}}
            (lib.content/clean-string excerpt)])
         [:div {:class '[text-tealv-600 font-semibold
                         hover:underline
                         inline-block]}
          "Read more."]]
        (when image-url
          [:.relative.flex-shrink-0
           [:img {:src (ui/weserv {:url image-url
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
                           rounded]}]])]]])})

(defresolver small-card [{:item/keys [id title ui-details]}]
  #::pco{:input [:item/id
                 (? :item/title)
                 :item/ui-details]}
  {:item/ui-small-card
   [:a {:href (href routes/read-item id)
        :class '[block
                 bg-white hover:bg-neut-50
                 shadow
                 p-2
                 text-sm]}
    [:.font-semibold.mr-6.line-clamp-2 (or (not-empty title) "[no title]")]
    [:.text-neut-800.mr-6.line-clamp-2 (ui-details {:show-author true :show-reading-time false})]]})

(def module
  {:resolvers [details
               read-more-card
               small-card]})
