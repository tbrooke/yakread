(ns com.yakread.app.admin
  (:require
   [clojure.string :as str]
   [com.yakread.lib.middleware :as lib.mid]
   [com.yakread.lib.pathom :as lib.pathom]
   [com.yakread.lib.pipeline :as pipe]
   [com.yakread.lib.route :refer [defget defpost href hx-redirect]]
   [com.yakread.lib.ui :as ui]
   [xtdb.api :as xt]))

(defpost save-moderation
  :start
  (fn [{{:keys [block latest-item]} :params}]
    (let [block-ids (->> (if (string? block)
                           [block]
                           block)
                         (mapv parse-uuid))
          tx (into [{:db/doc-type :admin/moderation
                     :xt/id :admin/moderation
                     :admin.moderation/latest-item latest-item}]
                   (for [id block-ids]
                     {:db/doc-type :item/direct
                      :db/op :update
                      :xt/id id
                      :item.direct/candidate-status :blocked}))]
      (merge {:biff.pipe/next [(pipe/tx tx)]}
             (hx-redirect `page-route)))))

(defget page-route "/dev/admin"
  [:app.shell/app-shell
   :admin.moderation/n-items
   {:admin.moderation/next-batch
    [:item/id
     :item/ui-read-more-card]}]
  (fn [ctx {:keys [app.shell/app-shell]
            :admin.moderation/keys [n-items next-batch]}]
    (app-shell
     {:wide true}
     (ui/page-header {:title "Screen discover candidates"})
     [:div n-items " items left."]
     [:.h-6]
     [:form
      [:div.grid.grid-cols-2.gap-6
       (for [{:item/keys [id ui-read-more-card]} next-batch]
         [:<>
          (ui-read-more-card {:show-author true
                              :new-tab true})
          (ui/checkbox {:ui/label "block?"
                        :ui/size :large
                        :name "block"
                        :value id})])
       (ui/button {:type "submit"
                   :ui/size :large
                   :ui/type :primary
                   :class '[w-full]
                   :hx-post (href save-moderation
                                  {:latest-item (:item/id (last next-batch))})}
         "Save")]])))

(defonce resolver-cache (atom nil))
(comment (reset! resolver-cache nil))

(def digest-template-route
  ["/dev/admin/digest"
   {:middleware [lib.mid/wrap-profiled]
    :get
    (fn [{:keys [params] :as ctx}]
      (swap! resolver-cache
             (fn [cache]
               (into {}
                     (remove (fn [[[op-name _ _] _]]
                               (str/starts-with? (str op-name) "com.yakread.ui-components")))
                     cache)))
      (let [[output-key content-type] (if (= (:content-type params) "text")
                                        [:digest/text "text/plain"]
                                        [:digest/html "text/html"])
            ctx    (assoc ctx ::lib.pathom/resolver-cache resolver-cache)
            result (lib.pathom/process ctx {} [{:session/user [output-key]}])
            content   (get-in result [:session/user output-key])]
        {:status 200
         :headers {"content-type" content-type}
         :body content}))}])

(defn wrap-admin [handler]
  (fn [{:keys [biff/db session] :as ctx}]
    (if (contains? (:user/roles (xt/entity db (:uid session))) :admin)
      (handler ctx)
      {:status 401
       :headers {"content-type" "text/html"}
       :body "<h1>Unauthorized</h1>"})))

(def module
  {:routes ["" {:middleware [wrap-admin]}
            page-route
            save-moderation
            digest-template-route]})
