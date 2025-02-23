(ns com.yakread.app.favorites
  (:require [com.biffweb :as biff]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.ui :as ui]
            [com.yakread.lib.route :as lib.route :refer [href defget defpost-pathom]]
            [com.yakread.lib.middleware :as lib.mid]
            [com.yakread.routes :as routes]))

(defresolver item-card [{:item/keys [id title details]}]
  {:item.view/card
   [:a {:href "" ; TODO go to For You
        :class '[block
                 bg-white hover:bg-neut-50
                 shadow
                 p-2
                 text-sm]}
    [:.truncate.font-semibold.mr-6 (or (not-empty title) "[no title]")]
    [:.text-neut-800.mr-6 details]]})

(defn- empty-state []
  (ui/empty-page-state {:icons ["star-regular-sharp"]
                        :text "Your starred articles will be saved here."
                        :btn-label "Add articles"
                        :btn-href (href routes/add-favorite-page)}))

(defget page-content "/dev/favorites/content"
  [{:session/user
    [{:user/favorites [:item/id
                       :item.view/card
                       {:item/user-item [:user-item/favorited-at]}]}]}]
  (fn [_ {{:user/keys [favorites]} :session/user}]
    (if (empty? favorites)
      (empty-state)
      (ui/card-grid
       {:ui/cols 4}
       (->> favorites
            (sort-by (comp :user-item/favorited-at :item/user-item) #(compare %2 %1))
            (mapv :item.view/card))))))

(defget page "/dev/favorites"
  [:app.shell/app-shell (? :user/current)]
  (fn [_ {:keys [app.shell/app-shell] user :user/current}]
    (app-shell
     {:wide true}
     [:<>
      (ui/page-header {:title    "Favorites"
                       :add-href (href routes/add-favorite-page)})
      (if user
        [:div#content (ui/lazy-load-spaced (href page-content))]
        (empty-state))])))

(def module
  {:routes [page
            ["" {:middleware [lib.mid/wrap-signed-in]}
             page-content]]
   :resolvers [item-card]})
