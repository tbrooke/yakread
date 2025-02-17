(ns com.yakread.smtp
  (:require [clojure.data.generators :as gen]
            [clojure.java.process :as proc]
            [clojure.string :as str]
            [com.biffweb :as biff]
            [com.yakread.lib.content :as lib.content]
            [com.yakread.lib.core :as lib.core]
            [com.yakread.lib.smtp :as lib.smtp]
            [com.yakread.lib.pipeline :as lib.pipe]
            [rum.core :as rum]
            [xtdb.api :as xt])
  [:import (org.jsoup Jsoup)])

(defn accept? [{:keys [biff/db biff.smtp/message yakread/domain]}]
  (and (or (not domain) (= domain (:domain message)))
       (some? (biff/lookup-id db :user/email-username (str/lower-case (:username message))))))

(defn- extract-html [message]
  (let [{:keys [content content-type]}
        (->> (lib.smtp/parts-seq message)
             (filterv (comp string? :content))
             (sort-by (fn [{:keys [content content-type]}]
                        [(str/includes? content-type "html")
                         (str/includes? content "</div>")
                         (str/includes? content "<html")
                         (str/includes? content "<p>")])
                      #(compare %2 %1))
             first)]
    (if-not (str/includes? content-type "text/plain")
      content
      (rum/render-static-markup
       [:html
        [:body
         [:div {:style {:padding "1rem"}}
          (->> (str/split-lines content)
               (biff/join [:br]))]]]))))

(def deliver*
  (lib.pipe/make
   :start (fn [{:keys [biff.smtp/message]}]
            {:biff.pipe/next [:yakread.pipe/js :end]
             :yakread.pipe.js/fn-name "juice"
             :yakread.pipe.js/input {:html (extract-html message)}})
   :end (fn [{:keys [biff.smtp/message biff/db]
              {:keys [html]} :yakread.pipe.js/output}]
          (let [doc (Jsoup/parse html)
                _ (-> doc
                      (.select "a[href]")
                      (.attr "target" "_blank"))
                _ (doseq [img (.select doc "img[src^=http://]")]
                    (.attr img "src" (str/replace (.attr img "src")
                                                  #"^http://"
                                                  "https://")))
                html (.outerHtml doc)
                html (str/replace html #"#transparent" "transparent")
                raw-content-key (gen/uuid)
                parsed-content-key (gen/uuid)
                headers (:headers message)
                from (some (fn [k]
                             (->> (concat (:from message) (:reply-to message))
                                  (some k)))
                           [:personal :address])
                text (lib.content/html->text html)
                user-id (biff/lookup-id db :user/email-username (str/lower-case (:username message)))
                sub (biff/lookup db :sub/user user-id :sub.email/from from)
                sub-id (or (:xt/id sub) :db.id/new-sub)]
            {:biff.pipe/next [(lib.pipe/s3 raw-content-key (:raw message) "text/plain")
                              (lib.pipe/s3 parsed-content-key html "text/html")
                              :biff.pipe/tx]
             :biff.pipe.tx/input (concat
                                  [(lib.core/some-vals
                                    {:db/doc-type :item/email
                                     :item/ingested-at :db/now
                                     :item/title (:subject message)
                                     :item/url (some-> (get headers "list-post")
                                                       first
                                                       (str/replace #"[<>]" ""))
                                     :item/content-key parsed-content-key
                                     :item/published-at :db/now
                                     :item/excerpt (lib.content/excerpt text)
                                     :item/author-name from
                                     :item/lang (lib.content/lang html)
                                     :item/length (count text)
                                     :item.email/sub sub-id
                                     :item.email/raw-content-key raw-content-key
                                     :item.email/list-unsubscribe (first (get headers "list-unsubscribe"))
                                     :item.email/list-unsubscribe-post (first (get headers "list-unsubscribe-post"))
                                     :item.email/reply-to (some :address (:reply-to message))
                                     :item.email/maybe-confirmation (or (nil? sub) nil)})]
                                  (when-not sub
                                    [{:db/doc-type :sub/email
                                      :xt/id sub-id
                                      :sub/user user-id
                                      :sub.email/from from
                                      :sub/created-at :db/now}
                                     [::xt/fn :biff/ensure-unique {:sub/user user-id
                                                                   :sub.email/from from}]]))}))))
