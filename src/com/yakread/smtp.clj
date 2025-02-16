(ns com.yakread.smtp
  (:require [clojure.java.process :as proc]
            [clojure.string :as str]
            [com.biffweb :as biff]
            [com.yakread.lib.smtp :as lib.smtp]
            [com.yakread.lib.pipeline :as lib.pipe]
            [rum.core :as rum])
  [:import (org.jsoup Jsoup)])

(def dev-promise (atom (promise)))

(defn accept? [{:keys [biff/db biff.smtp/message yakread/domain]}]
  (and (or (not domain) (= domain (:domain message)))
       (some? (biff/lookup-id db :user/email-username (str/lower-case (:username message))))))

(defn extract-html [message]
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
   :end (fn [{{:keys [html]} :yakread.pipe.js/output}]
          (let [doc (Jsoup/parse html)
                _ (-> doc
                      (.select "a[href]")
                      (.attr "target" "_blank"))
                _ (doseq [img (.select doc "img[src^=http://]")]
                    (.attr img "src" (str/replace (.attr img "src")
                                                  #"^http://"
                                                  "https://")))
                html (.outerHtml doc)
                html (str/replace html #"#transparent" "transparent")]
            {:html html}
            ;; TODO store this in
            ;; - store in s3 (raw and juiced)
            ;; - submit tx
            #_{:biff.pipe/next [{:biff.pipe/current  :biff.pipe/s3
                               :biff.pipe.s3/input {:method  "PUT"
                                                    :key     (str content-key)
                                                    :body    content
                                                    :headers {"x-amz-acl"    "private"
                                                              "content-type" "text/html"}}}
                              ]}

            
            )

          )))

(comment

  (-> output*
      :body
      keys)

  (defn juice [html]
    (let [path (str "/tmp/" (rand-int 100000))
          _ (try (biff/sh "npx" "juice"
                          "--web-resources-images" "false"
                          "--web-resources-scripts" "false"
                          "/dev/stdin" path :in html)
              (catch Exception e
                (throw (ex-info "Juice crashed"
                                {:cause :juice}
                                e))))
          ret (slurp path)]
      (io/delete-file (io/file path))
      (str/replace ret #"#transparent" "transparent")))

  (defn clean-html* [html]
    (let [doc (Jsoup/parse html)]
      (-> doc
          (.select "a[href]")
          (.attr "target" "_blank"))
      (doseq [img (.select doc "img[src^=http://]")]
        (.attr img "src" (str/replace (.attr img "src")
                                      #"^http://"
                                      "https://")))
      (.outerHtml doc)))

  (defn robust-juice [ctx html]
    (let [cloud-juiced (biff/catchall (util/cloud-fn ctx "juice" {:html html}))
          cloud-cleaned (some-> cloud-juiced
                                :body
                                :html
                                util/clean-html*
                                (str/replace #"#transparent" "transparent"))
          local-cleaned (-> html
                            util/juice
                            util/clean-html*
                            delay)]
      (cond
        (nil? cloud-juiced)
        (log/info "cloud-fn juice failed")

        (nil? cloud-cleaned)
        (log/info "local cloud-fn juiced clean failed"))
      (or cloud-cleaned @local-cleaned)))

  (let [html (extract-html msg*)]
    (count html))

  
  
  )
