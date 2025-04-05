(ns com.yakread.app.read-later
  (:require [com.biffweb :as biff]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.ui :as ui]
            [com.yakread.lib.route :as lib.route :refer [href defget defpost-pathom]]
            [com.yakread.lib.middleware :as lib.mid]
            [com.yakread.routes :as routes]))

(defn- empty-state []
  (ui/empty-page-state {:icons ["bookmark-regular-sharp"]
                        :text "Bookmark articles so you can read them later."
                        :btn-label "Add bookmarks"
                        :btn-href (href routes/add-bookmark-page)}))

(defget page-content "/dev/read-later/content"
  [{:session/user
    [{:user/bookmarks [:item/id
                       :item/ui-small-card
                       {:item/user-item [:user-item/bookmarked-at]}]}]}]
  (fn [_ {{:user/keys [bookmarks]} :session/user}]
    (if (empty? bookmarks)
      (empty-state)
      (ui/card-grid
       {:ui/cols 4}
       (->> bookmarks
            (sort-by (comp :user-item/bookmarked-at :item/user-item) #(compare %2 %1))
            (mapv :item/ui-small-card))))))

(defget page "/dev/read-later"
  [:app.shell/app-shell (? :user/current)]
  (fn [_ {:keys [app.shell/app-shell] user :user/current}]
    (app-shell
     {:wide true}
     [:<>
      (ui/page-header {:title    "Read later"
                       :add-href (href routes/add-bookmark-page)})
      (if user
        [:div#content.h-full (ui/lazy-load-spaced (href page-content))]
        (empty-state))])))

(def module
  {:routes [page
            ["" {:middleware [lib.mid/wrap-signed-in]}
             page-content]]})
