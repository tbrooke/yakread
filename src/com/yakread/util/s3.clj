(ns com.yakread.util.s3
  (:require [com.biffweb :as biff]
            [buddy.core.mac :as mac]
            [clj-http.client :as http]
            [clojure.string :as str]))

(defn hmac-sha1-base64 [secret s]
  (-> (mac/hash s {:key secret :alg :hmac+sha1})
      biff/base64-encode))

(defn md5-base64 [body]
  (with-open [f (cond
                 (string? body) (java.io.ByteArrayInputStream. (.getBytes body))
                 :else (java.io.FileInputStream. body))]
    (let [buffer (byte-array 1024)
          md (java.security.MessageDigest/getInstance "MD5")]
      (loop [nread (.read f buffer)]
        (if (pos? nread)
          (do (.update md buffer 0 nread)
              (recur (.read f buffer)))
          (biff/base64-encode (.digest md)))))))

(defn format-date [date & [format]]
  (.format (doto (new java.text.SimpleDateFormat (or format biff/rfc3339))
             (.setTimeZone (java.util.TimeZone/getTimeZone "UTC")))
           date))

(defn body->bytes [body]
  (cond
   (string? body) (.getBytes body)
   :else (let [out (byte-array (.length body))]
           (with-open [in (java.io.FileInputStream. body)]
             (.read in out)
             out))))

(defn request [{:keys [biff/secret
                       s3/origin
                       s3/access-key]
                default-bucket :s3/bucket}
               {:keys [method
                       key
                       body
                       headers
                       bucket]}]
  ;; See https://docs.aws.amazon.com/AmazonS3/latest/userguide/RESTAuthentication.html
  (let [bucket (or bucket default-bucket)
        date (format-date (java.util.Date.) "EEE, dd MMM yyyy HH:mm:ss Z")
        path (str "/" bucket "/" key)
        md5 (some-> body md5-base64)
        headers' (->> headers
                      (map (fn [[k v]]
                             [(str/trim (str/lower-case k)) (str/trim v)]))
                      (into {}))
        content-type (get headers' "content-type")
        headers' (->> headers'
                      (filter (fn [[k v]]
                                (str/starts-with? k "x-amz-")))
                      (sort-by first)
                      (map (fn [[k v]]
                             (str k ":" v "\n")))
                      (apply str))
        string-to-sign (str method "\n" md5 "\n" content-type "\n" date "\n" headers' path)
        signature (hmac-sha1-base64 (secret :s3/secret-key) string-to-sign)
        auth (str "AWS " access-key ":" signature)
        s3-opts {:method method
                 :url (str origin path)
                 :headers (merge {"Authorization" auth
                                  "Date" date
                                  "Content-MD5" md5}
                                 headers)
                 :body (some-> body body->bytes)
                 :socket-timeout 10000
                 :connection-timeout 10000}]
    (http/request s3-opts)))

(defn get-body [ctx bucket key]
  (:body (request ctx
                  {:bucket bucket
                   :method "GET"
                   :key key})))

(defn upload-item-content [ctx item]
  (request ctx {:bucket "yakread-content"
                :method "PUT"
                :key (:item/content item)
                :body (:item.extra/html item)
                :headers {"x-amz-acl" "private"
                          "content-type" "text/plain"}}))
