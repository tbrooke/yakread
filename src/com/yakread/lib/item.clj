(ns com.yakread.lib.item
  (:require [clojure.data.generators :as gen]
            [clojure.string :as str]
            [com.biffweb :as biff]
            [com.yakread.lib.route :refer [href hx-redirect]]
            [com.yakread.lib.core :as lib.core]
            [com.yakread.lib.content :as lib.content]
            [com.yakread.lib.rss :as lib.rss]))

(def source-id (some-fn :item.feed/feed :item.email/sub))

(def published-at (some-fn :item/published-at :item/ingested-at))

(defn add-item-pipeline [{:keys [user-item-kvs
                                 redirect-to]}]
  (let [success (fn [user-id item-id]
                  (merge {:biff.pipe/next [:biff.pipe/tx]
                          :biff.pipe.tx/input [(merge {:db/doc-type :user-item
                                                       :db.op/upsert {:user-item/user user-id
                                                                      :user-item/item item-id}}
                                                      user-item-kvs)]}
                         (some-> redirect-to (hx-redirect {:added true}))))
        error (fn []
                (some-> redirect-to (hx-redirect {:error true})))]
    {:start
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
     (fn [{:keys [biff.pipe.http/output]}]
       (if-not (and (some-> output :headers (get "Content-Type") (str/includes? "text"))
                    (< (count (:body output)) (* 2 1000 1000)))
         (error)
         {:biff.pipe/next [:yakread.pipe/js :handle-readability]
          ::url (:url output)
          ::raw-html (:body output)
          :yakread.pipe.js/fn-name "readability"
          :yakread.pipe.js/input {:url (:url output) :html (:body output)}}))

     :handle-readability
     (fn [{:keys [session ::url ::raw-html]
           {:keys [content title byline length siteName textContent]} :yakread.pipe.js/output}]
       (if (empty? content)
         (error)
         (let [{[image] :og/image
                [published-time] :article/published-time} (lib.content/pantomime-parse raw-html)
               content (lib.content/normalize content)
               inline-content (<= (count content) 1000)
               item (lib.core/some-vals
                     {:db/doc-type :item/direct
                      :item/doc-type :item/direct
                      :xt/id (gen/uuid)
                      :item/ingested-at :db/now
                      :item/title title
                      :item/url url
                      :item/content-key (when-not inline-content (gen/uuid))
                      :item/content (when inline-content content)
                      :item/published-at (some-> published-time lib.content/parse-instant)
                      :item/excerpt (some-> textContent lib.content/excerpt)
                      :item/feed-url (-> (lib.rss/parse-urls* url raw-html) first :url)
                      :item/lang (lib.content/lang raw-html)
                      :item/site-name siteName
                      :item/byline byline
                      :item/length length
                      :item/image-url image})]
           (merge-with into
                       (when-not inline-content
                         {:biff.pipe/next [:biff.pipe/s3]
                          :biff.pipe.s3/input {:method  "PUT"
                                               :key     (str (:item/content-key item))
                                               :body    content
                                               :headers {"x-amz-acl"    "private"
                                                         "content-type" "text/html"}}})
                       {:biff.pipe.tx/input [item]}
                       (success (:uid session) (:xt/id item))))))}))
