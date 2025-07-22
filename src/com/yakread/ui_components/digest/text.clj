(ns com.yakread.ui-components.digest.text 
  (:require
   [com.wsscode.pathom3.connect.operation :as pco :refer [? defresolver]]
   [com.yakread.lib.route :as lib.route :refer [href]]
   [com.yakread.lib.ui-email :as uie]
   [com.yakread.routes :as routes]))

(defresolver settings [{:keys [biff/base-url]}
                       {:digest.settings/keys [freq-text time-text]}]
  {::settings
   (str "You're getting this personalized digest " freq-text
        " at " time-text ".\n"
        "Change settings: " (str base-url (href routes/settings-page)) "\n")})

(defresolver text [{:keys [::settings] :as input}]
  {::pco/input [{(? :user/ad-rec) [:xt/id
                                   :ad/title
                                   :ad/click-cost]}
                {:user/discover-recs [:xt/id
                                      (? :item/title)]}
                {:user/icymi-recs [:xt/id
                                   (? :item/title)
                                   :item/rec-type]}
                {:user/digest-sub-items [:xt/id
                                         (? :item/title)]}
                {:user/digest-bookmarks [:xt/id
                                         (? :item/title)]}]}
  {:digest/text
   (uie/text settings)})

(def module {:resolvers [settings
                         text]})
