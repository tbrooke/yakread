(ns com.yakread.lib.admin
  (:require [com.yakread.lib.ui :as ui]
            [com.yakread.lib.route :refer [href]]))

(def pages
  [{:id :screen-discover :route 'com.yakread.app.admin/page-route :label "Screen discover"}
   {:id :monitor :route 'com.yakread.app.admin.monitor/page-route :target "_blank" :label "Monitor"}])

(defn navbar [active]
  [:.flex.gap-4.mb-6.max-sm:mx-4
   (for [{:keys [label id route target]} pages]
     (ui/pill {:ui/label label
               :href (href route)
               :target target
               :data-active (str (= active id))}))])

