(ns com.yakread.util
  (:require [better-cond.core :as b]
            [babashka.process :refer [process]]
            [cld.core :as cld]
            [com.biffweb :as biff :refer [q letd]]
            [clj-http.client :as http]
            [clj-xpath.core :as xpath]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.stacktrace :as st]
            [cheshire.core :as cheshire]
            [lambdaisland.uri :as uri]
            [xtdb.api :as xt]
            [ring.middleware.anti-forgery :as anti-forgery]
            [clojure.tools.logging :as log]
            [clojure.tools.logging.impl :as log-impl]
            [clojure.walk :as walk]
            [hickory.core :as hickory]
            [hickory.render :as hickr]
            [remus]
            [ring.util.io :as ring-io]
            [ring.util.mime-type :as mime]
            [ring.util.time :as ring-time]
            [malli.util :as mu]
            [pantomime.extract :as pant]
            [taoensso.nippy :as nippy]
            [com.yakread.settings :as settings]
            [com.yakread.util.s3 :as s3])
  (:import [javax.mail.internet MimeMessage InternetAddress MimeBodyPart MimeMultipart]
           [javax.mail Session Authenticator PasswordAuthentication Message Transport
            Message$RecipientType]
           [org.jsoup Jsoup]))

(defn pad [n _val coll]
  (take n (concat coll (repeat _val))))

(defn format-columns
  "Formats rows of text into columns.

  Example:
  ```
  (doseq [row (format-columns [[\"hellooooooooo \" \"there\"]
  [\"foo \" \"bar\"]
  [\"one column\"]])]
  (println row))
  hellooooooooo there
  foo           bar
  one column
  ```"
  [rows]
  (let [n-cols (apply max (map count rows))
        rows (map (partial pad n-cols " ") rows)
        lens (apply map (fn [& column-parts]
                          (apply max (map count column-parts)))
                    rows)
        fmt (str/join (map #(str "%" (when (not (zero? %)) (str "-" %)) "s") lens))]
    (->> rows
         (map #(apply (partial format fmt) %))
         (map str/trimr))))

(defn print-table
  "Prints a nicely formatted table.

  Example:
  ```
  (print-table
  [[:foo \"Foo\"] [:bar \"Bar\"]]
  [{:foo 1 :bar 2} {:foo 3 :bar 4}])
  => Foo  Bar
  1    2
  3    4
  ```"
  [header-info table]
  (let [[ks header] (apply map vector header-info)
        header (map #(str % "  ") header)
        body (->> table
                  (map (apply juxt ks))
                  (map (fn [row] (map #(str % "  ") row))))
        rows (concat [header] body)]
    (doseq [row (format-columns rows)]
      (println row))))


(defn with-js-tools [f]
  (let [proc (process ["node" (.getPath (io/resource "tools.js"))])
        lock (Object.)]
    (try
     (with-open [stdin (io/writer (:in proc))
                 stdout (io/reader (:out proc))]
       (f (fn [command opts]
            (locking lock
              ;(prn :with-js-tools command (:url opts))
              (binding [*out* stdin]
                (println (cheshire/generate-string (assoc opts :command command))))
              (binding [*in* stdout]
                (cheshire/parse-string (read-line) true))))))
     (catch Exception e
       (println (slurp (:err proc)))
       (throw e)))))

(defn nippy-spit [f x]
  (with-open [o (io/output-stream f)]
    (.write o (nippy/freeze x))))

(defn nippy-slurp [f]
  (when (.exists (io/file f))
    (nippy/thaw
     (with-open [in (io/input-stream f)
                 out (java.io.ByteArrayOutputStream.)]
       (io/copy in out)
       (.toByteArray out)))))

;; Candidates for com.biffweb

(defn fs-exists? [k]
  (let [path (str "storage/kv/" (hash k))
        f (io/file path)]
    (.exists f)))

(defn fs-get [k & [default]]
  (let [path (str "storage/kv/" (hash k))
        f (io/file path)]
    (if (.exists f)
      (biff/catchall
       (.setLastModified f (inst-ms (java.util.Date.)))
       (edn/read-string (slurp path)))
      default)))

(defn fs-put [k v]
  (let [path (str "storage/kv/" (hash k))]
    (io/make-parents path)
    (spit path (pr-str v))))

(defn fs-update [k f & xs]
  (fs-put k (apply f (fs-get k) xs)))

(defn fs-delete [k]
  (let [path (str "storage/kv/" (hash k))
        f (io/file path)]
    (io/delete-file f)))

(defn kv-get [doc-type k & [default]]
  (let [file (io/file "storage/kv2" doc-type (str (hash k)))]
    (if (.exists file)
      (biff/catchall
       (.setLastModified file (inst-ms (java.util.Date.)))
       (if (str/starts-with? doc-type "nippy")
         (nippy-slurp file)
         (edn/read-string (slurp file))))
      default)))

(defn kv-put [doc-type k v]
  (let [file (io/file "storage/kv2" doc-type (str (hash k)))]
    (io/make-parents file)
    (if (str/starts-with? doc-type "nippy")
      (nippy-spit file v)
      (spit file (pr-str v)))))

(defn kv-update [doc-type k f & xs]
  (kv-put doc-type k (apply f (kv-get doc-type k) xs)))

(defn make-url [& args]
  (let [[args query] (if ((some-fn map? nil?) (last args))
                       [(butlast args) (last args)]
                       [args {}])]
    (-> (apply uri/assoc-query
               (str/replace (str "/" (str/join "/" args)) #"/+" "/")
               (apply concat query))
        str
        (str/replace #"\?$" ""))))

(defn assoc-url [url & kvs]
  (str (apply uri/assoc-query url kvs)))

(defn query-encode [s]
  (some-> s
          (java.net.URLEncoder/encode "UTF-8")
          (str/replace "+" "%20")))

(defn something? [x]
  (if (or (coll? x) (string? x))
    (boolean (not-empty x))
    (some? x)))

(defn assoc-url* [url & kvs]
  (let [query (->> kvs
                   (partition 2)
                   (filter (comp something? second))
                   (map (fn [[k v]]
                          (str (name k) "=" (query-encode (str v)))))
                   (str/join "&")
                   not-empty)]
    (if query
      (str url "?" query)
      url)))

(defn send-email [{:keys [biff/secret
                          postmark/from]}
                  {:keys [to subject html text stream reply-to]
                   from-override :from}]
  (http/post "https://api.postmarkapp.com/email"
             {:as :json
              :content-type :json
              :headers {"X-Postmark-Server-Token" (secret :postmark/api-key)}
              :form-params
              (biff/assoc-some
               {:From (or from-override from)
                :To to
                :Subject subject
                :HtmlBody html
                :MessageStream stream}
               :TextBody text
               :ReplyTo reply-to)}))

;; ---

(defn normalize-email-username [username]
  (-> (or username "")
      (str/lower-case)
      (str/replace #"[^a-z0-9\.]" "")
      (->> (take 20)
           (apply str))))

(defn every-n-minutes [n]
  (fn []
    (iterate #(biff/add-seconds % (* n 60)) (java.util.Date.))))

(defn new-doc? [db-before doc k]
  (and (contains? doc k)
       (not (contains? (xt/entity db-before (:xt/id doc)) k))))

(defn tx-docs [tx]
  (for [[op & args] (::xt/tx-ops tx)
        :when (= op ::xt/put)
        :let [[doc] args]]
    doc))

(defn map-from [f xs]
  (into {} (map (juxt f identity)) xs))

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

(defn clean-html [item html]
  (clean-html*
   (if (< (inst-ms (:item/fetched-at item))
          (inst-ms #inst "2023-03-01T18:14:14.393-00:00"))
     (do
       (println "running juice")
       (time (juice html)))
     (do
       (println "skipping juice")
       html))))

(defn find-rss-url [base-url html]
  (let [doc (Jsoup/parse html base-url)]
    (some-> (concat (.select doc "link[type=application/rss+xml]")
                    (.select doc "link[type=application/atom+xml]"))
            first
            (.attr "abs:href"))))

(defn find-feed-urls [base-url html]
  (let [doc (Jsoup/parse html base-url)]
    (->> (.select doc "link[type=application/rss+xml], link[type=application/atom+xml]")
         (keep (fn [node]
                 (when (not-empty (.attr node "href"))
                   (.attr node "abs:href")))))))

(defn http-get [url]
  (http/get url {:headers {"User-Agent" "https://yakread.com/"}
                 :socket-timeout 5000
                 :connection-timeout 5000}))

(defn remus-parse [response]
  (biff/catchall
   (remus/parse-http-resp (cond-> response
                            true
                            (update :body #(io/input-stream (.getBytes %)))

                            (contains? (:headers response) "Content-Type")
                            (assoc-in [:headers "Content-Type"] "application/xml")))))


(defn fix-url [url]
  (if (and (not-empty url)
           (not (str/starts-with? url "http")))
    (str "https://" url)
    url))

(defn fetch-rss-url [url]
  (biff/catchall
   (let [url (fix-url url)
         response (http/get url {:headers {"User-Agent" "https://yakread.com/"}})
         found-url (find-rss-url url (:body response))]
     (cond
      (not-empty (:entries (remus-parse response)))
      url

      (some-> found-url
              (remus/parse-url {:headers {"User-Agent" "https://yakread.com/"}})
              :feed
              some?)
      found-url))))

(defn fetch-feeds [url]
  (biff/catchall
   (let [url (fix-url url)
         response (http/get url {:headers {"User-Agent" "https://yakread.com/"}})
         doc (delay (Jsoup/parse (:body response) url))]
     (if (not-empty (:entries (remus-parse response)))
       [{:url url}]
       (for [element (.select @doc (str "link[type=application/rss+xml], "
                                        "link[type=application/atom+xml]"))]
         {:url (.attr element "abs:href")
          :title (.attr element "title")})))))

(defn distinct-by
  "Returns a lazy sequence of the elements of coll with duplicates removed.
  Returns a stateful transducer when no collection is provided."
  {:added "1.0"
   :static true}
  ([f]
   (fn [rf]
     (let [seen (volatile! #{})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (let [y (f input)]
            (if (contains? @seen y)
              result
              (do (vswap! seen conj y)
                  (rf result input)))))))))
  ([f coll]
   (let [step (fn step [xs seen]
                (lazy-seq
                  ((fn [[input :as xs] seen]
                     (when-let [s (seq xs)]
                       (let [y (f input)]
                         (if (contains? seen y)
                           (recur (rest s) seen)
                           (cons input (step (rest s) (conj seen y)))))))
                   xs seen)))]
     (step coll #{}))))

(defn mean [coll]
  (let [sum (apply + coll)
        count (count coll)]
    (if (pos? count)
      (/ sum count)
      0)))

(defn serve-static-file [file]
  {:status 200
   :headers {"content-length" (str (.length file))
             "last-modified" (ring-time/format-date (ring-io/last-modified-date file))
             "content-type" (mime/ext-mime-type (.getName file))}
   :body file})

(defn expand-now [x]
  (if (= x :now)
    (java.util.Date.)
    x))

(defn seconds-between [t1 t2]
  (quot (- (inst-ms (expand-now t2)) (inst-ms (expand-now t1))) 1000))

(defn update-existing [m k f & args]
  (if (contains? m k)
    (apply update m k f args)
    m))

(defn sample-by [f xs]
  (let [xs (set xs)
        next-x (delay (f xs))]
    (if (empty? xs)
      nil
      (lazy-seq (cons @next-x (sample-by f (disj xs @next-x)))))))

(defn split-by [f xs]
  [(filter f xs)
   (remove f xs)])

(defn update-vals' [f m]
  (update-vals m f))

(def all-days
  [:monday :tuesday :wednesday :thursday :friday :saturday :sunday])

(defn assoc-context [sys]
  (-> sys
      biff/assoc-db
      (assoc :now (java.util.Date.))))

(defmacro list-when [condition & body]
  `(when ~condition
     (list ~@body)))

(defn valid-item? [item]
  (not-empty (:item/title item)))

(defn lang [html]
  (or (biff/catchall
       (some-> html
               (Jsoup/parse)
               (.text)
               cld/detect
               first
               not-empty))
      "en"))

(defn save-html! [tx]
  (vec
   (for [doc tx
         :let [id (str (random-uuid))]]
     (if (contains? doc :html)
       (do
        (fs-put id (:html doc))
        (-> doc
            (dissoc :html)
            (assoc :item/content id)))
       doc))))

(defmacro nolog [& body]
  `(binding [log/*logger-factory* clojure.tools.logging.impl/disabled-logger-factory]
     ~@body))

(defn profile [f]
  (let [t (doto (Thread. f)
            (.start))
        stacks (loop [stacks []]
                 (Thread/sleep 10)
                 (if (= Thread$State/TERMINATED (.getState t))
                   stacks
                   (recur (conj stacks (.getStackTrace t)))))]
    (rand-nth stacks)))

(defn truncate [s n]
  (if (<= (count s) n)
    s
    (str (subs s 0 (dec n)) "…")))

(defn parse-date-robust [d]
  (->> [biff/rfc3339
        "yyyy-MM-dd'T'HH:mm:ssXXX"]
       (keep (fn [fmt]
               (biff/catchall (biff/parse-date d fmt))))
       first))

(defn html->text [html]
  (.text (Jsoup/parse html)))

(defn reading-minutes [n-characters]
  (max 1 (Math/round (/ n-characters 900.0))))

(defn title [{:keys [item/title item/type item/author-name]}]
  (let [ret (or (not-empty title) "[no title]")]
    (if (and (= type :epub)
             author-name
             (str/starts-with? ret (str author-name ": ")))
      (subs title (count (str author-name ": ")))
      title)))

(defn weserv [opts]
  (str (apply uri/assoc-query "https://images.weserv.nl/"
              (apply concat opts))))

(defn make-excerpt [html]
  (some-> html
          (str/trim)
          (str/replace #"\s+" " ")
          (truncate 500)))

(defn rm-r [file]
  (->> (file-seq file)
       reverse
       (run! io/delete-file)))

(defn pred-> [x pred f]
  (if (pred x)
    (f x)
    x))

(defn panto-parse [html]
  (-> html
      (.getBytes "UTF-8")
      (java.io.ByteArrayInputStream.)
      pant/parse
      (update-vals first)
      biff/catchall))

(defn parse-opml [body]
  (biff/catchall-verbose
   (let [doc (xpath/xml->doc body)]
     {:title (xpath/$x:text "//head//title" doc)
      :feeds (doall
              (for [m (some->> doc
                              (xpath/$x:attrs* "//outline")
                              (filter :xmlUrl)
                              doall)]
               (let [image (->> m
                                (filter (fn [[k _]]
                                          (str/ends-with? (str k) "image")))
                                doall
                                (sort-by (comp count str first))
                                first
                                second)]
                 (biff/assoc-some m :image image))))})))

(defn read-config [{:keys [biff/config]}]
  (com.biffweb.impl.util/read-config config))

(defn ad-status [ad]
  (let [ad (or ad {})
        steps (filter
               some?
               [(when-not (or (:ad/payment-method ad)
                              (< (:ad/balance ad 0) -200))
                  "Add a payment method.")
                (when-not (and (every? ad [:ad/bid :ad/budget])
                               (or (< 0 (:ad/bid ad))))
                  "Set a maximum cost-per-click and weekly budget.")
                (when-not (every? ad [:ad/url :ad/title :ad/description :ad/image])
                  "Set a URL, title, description, and image.")])]
    {:status (cond
              (empty? ad) :none
              (:ad/paused ad) :paused
              (not-empty steps) :incomplete
              (= (:ad/state ad) :rejected) :rejected
              (= (:ad/state ad) :pending) :pending
              :else :running)
     :steps steps}))

(defn admin? [user]
  (contains? settings/admins (:user/email user)))

(defn analyst? [user]
  (contains? settings/analysts (:user/email user)))

(defn test? [user]
  (str/ends-with? (:user/email user "") "@jacobobryant.com"))

(defn fmt-dollars [amount]
  (format "%.2f" (/ amount 100.0)))

(defn random-code []
  (apply str (repeatedly 6 #(rand-nth "abcdefghijklmnopqrstuvwxyz0123456789"))))

(defn new-referral-code [db]
  (let [existing (set (q db
                         '{:find code
                           :where [[user :user/referral-code code]]}))]
    (->> (repeatedly random-code)
         (remove existing)
         first)))

(defn take-percent [p xs]
  (take (* p (count xs)) xs))

(defn cloud-fn [{:cloud-fns/keys [base-url url secret]} endpoint opts]
  (let [base-url (or base-url
                     (str/replace url #"sample/hello$" "yakread/"))]
    (http/post (str base-url endpoint)
               {:headers {"X-Require-Whisk-Auth" secret}
                :as :json
                :form-params opts
                :socket-timeout 10000
                :connection-timeout 10000})))

(defn plan-active? [{:user/keys [plan cancel-at]}]
  (and plan
       (or (not cancel-at)
           (< (inst-ms (java.util.Date.))
              (inst-ms cancel-at)))))

(defn load-content [ctx {:keys [item/content] :as item}]
  (if (fs-exists? content)
    (clean-html item (fs-get content))
    (:body (s3/request ctx
                       {:bucket "yakread-content"
                        :method "GET"
                        :key content}))))

(def item-date (some-fn :item/published-at :item/fetched-at))

(defn dupe-db [{:keys [biff/db biff.xtdb/node]}]
  (xt/db node (::xt/valid-time (xt/db-basis db))))

(defn db-pcalls [ctx & fns]
  (apply
   pcalls
   (for [f fns]
     (fn [] (f (assoc ctx :biff/db (dupe-db ctx)))))))

(defmacro future-verbose [& body]
  `(future
    (try
     ~@body
     (catch Exception e#
       (st/print-stack-trace e#)
       (flush)))))

(defn add-images [db items]
  (let [url->image (into {} (q db
                               '{:find [url image]
                                 :in [[url ...]]
                                 :where [[rss :rss/url url]
                                         [rss :rss/image image]]}
                               (distinct
                                (concat
                                 (keep :item.rss/feed-url items)
                                 (keep :item/inferred-feed-url items)))))]
    (for [{:keys [item.rss/feed-url
                  item/inferred-feed-url]
           :as item} items
          :let [image (some url->image [feed-url inferred-feed-url])]]
      (merge (when image {:item/image image})
             item))))
