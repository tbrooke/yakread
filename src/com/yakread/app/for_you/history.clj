(ns com.yakread.app.for-you.history
  (:require [com.biffweb :as biff]
            [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver ?]]
            [com.yakread.lib.ui :as ui]
            [com.yakread.lib.route :as lib.route :refer [href defget defpost-pathom]]
            [com.yakread.lib.middleware :as lib.mid]
            [com.yakread.routes :as routes]))

(defget next-batch "/dev/history/next"
  [{:session/user
    [{:user/history-items [:item/id
                           :item/ui-small-card]}]}]
  (fn [_ {{:user/keys [history-items]} :session/user}]
    (into [:<>]
          (cond-> (mapv #(ui/card-grid-card {} (:item/ui-small-card %)) history-items)
            (not-empty history-items)
            (conj (ui/lazy-load (href next-batch {:after (:item/id (last history-items))})))))))

(defget page "/dev/history"
  [:app.shell/app-shell :user/current]
  (fn [_ {:keys [app.shell/app-shell] user :user/current}]
    (app-shell
     {:wide true}
     [:<>
      (ui/page-header {:title    "Reading History"
                       :back-href (href routes/for-you)})
      [:div#content.h-full
       (ui/card-grid*
        {:ui/cols 4}
        (ui/lazy-load {:class '[col-span-full]} (href next-batch)))]])))

(def module
  {:routes [page
            ["" {:middleware [lib.mid/wrap-signed-in]}
             next-batch]]})
