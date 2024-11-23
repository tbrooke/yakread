(ns com.yakread.email
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [cheshire.core :as cheshire]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [com.yakread.render :as render]
            [rum.core :as rum]))

(defn signin-link [{:keys [to url user-exists]}]
  {:to to
   :subject "Sign in to Yakread"
   :html-body (render/email
               :title "Sign in to Yakread"
               :hide-unsubscribe true
               :content
               [:<>
                [:div
                 (if user-exists
                   "We received a request to sign in to Yakread using this email address. "
                   "Welcome to Yakread! ")
                 "Click the link to sign in:"]
                (render/h-space "32px")
                (render/button
                 {:href url
                  :label "Click here to sign in"
                  :bg-color "#17b897"
                  :text-color "white"})
                (render/h-space "32px")
                [:div
                 (if user-exists
                   "If you did not request this link, you can ignore this email."
                   [:<>
                    "If you didn't mean to sign up for Yakread, you can "
                    [:a {:href "{{{ pm:unsubscribe }}}"} "unsubscribe"] " from future emails."])]
                (render/h-space "8px")])
   :text-body (str (if user-exists
                     "We received a request to sign in to Yakread using this email address. "
                     "Welcome to Yakread! ")
                   "Click here to sign in:\n"
                   "\n"
                   url "\n"
                   "\n"
                   (if user-exists
                     "If you did not request this link, you can ignore this email."
                     (str "If you didn't mean to sign up for Yakread, you can click here to unsubscribe "
                          "from future emails: "
                          "{{{ pm:unsubscribe }}}\n"))
                   "\n"
                   "138 E 12300 S, Unit #654, Draper, UT 84020")
   :stream "outbound"})

(defn signin-code [{:keys [to code user-exists]}]
  {:to to
   :subject "Sign in to Yakread"
   :html-body (render/email
               :title "Sign in to Yakread"
               :hide-unsubscribe true
               :content
               [:<>
                [:div
                 (if user-exists
                   "We received a request to sign in to Yakread using this email address. "
                   "Welcome to Yakread! ")
                 "Enter the following code to sign in:"]
                (render/h-space "16px")
                [:div {:style {:font-size "2rem"}} code]
                (render/h-space "16px")
                [:div
                 "This code will expire in three minutes. "
                 (if user-exists
                   "If you did not request this code, you can ignore this email."
                   [:<>
                    "If you didn't mean to sign up for Yakread, you can "
                    [:a {:href "{{{ pm:unsubscribe }}}"} "unsubscribe"] "."])]
                (render/h-space "8px")])
   :text-body (str (if user-exists
                     "We received a request to sign in to Yakread using this email address. "
                     "Welcome to Yakread! ")
                   "Enter the following code to sign in:\n"
                   "\n"
                   code "\n"
                   "\n"
                   "This code will expire in three minutes. "
                   (if user-exists
                     "If you did not request this code, you can ignore this email."
                     (str "If you didn't mean to sign up for Yakread, you can click here to unsubscribe: "
                          "{{{ pm:unsubscribe }}}\n"))
                   "\n"
                   "138 E 12300 S, Unit #654, Draper, UT 84020")
   :stream "outbound"})

(defn template [k opts]
  ((case k
     :signin-link signin-link
     :signin-code signin-code)
   opts))

(defn unsuppress! [{:keys [biff/secret]} email]
  (log/info "inactive, unsuppressing")
  (< (:status (http/post "https://api.postmarkapp.com/message-streams/outbound/suppressions/delete"
                         {:headers {"X-Postmark-Server-Token" (secret :postmark/api-key)}
                          :as :json
                          :content-type :json
                          :form-params {:Suppressions [{:EmailAddress email}]}}))
     400))

(defn send-postmark* [{:keys [biff/secret postmark/from]} form-params]
  (http/post "https://api.postmarkapp.com/email"
             {:headers {"X-Postmark-Server-Token" (secret :postmark/api-key)}
              :as :json
              :content-type :json
              :form-params (merge {:from from} (cske/transform-keys csk/->PascalCase form-params))
              :throw-exceptions false}))

(defn send-postmark [ctx form-params]
  (let [result (send-postmark* ctx form-params)
        success (< (:status result) 400)
        inactive (when-not success
                   (some-> (:body result)
                           (cheshire/parse-string true)
                           :ErrorCode
                           (= 406)))
        result (if (and inactive
                        (= (:stream form-params) "outbound")
                        (unsuppress! ctx (:to form-params)))
                 (do
                   (Thread/sleep 3000)
                   (send-postmark* ctx form-params))
                 result)
        success (< (:status result) 400)]
    (when-not success
      (log/error (:body result)))
    success))

(defn send-console [ctx form-params]
  (println "TO:" (:to form-params))
  (println "SUBJECT:" (:subject form-params))
  (println)
  (println (:text-body form-params))
  (println)
  (println "To send emails instead of printing them to the console, add your"
           "API keys for Postmark and Recaptcha to config.edn.")
  true)

(defn send-email [{:keys [biff/secret recaptcha/site-key] :as ctx} opts]
  (let [form-params (if-some [template-key (:template opts)]
                      (template template-key opts)
                      opts)]
    (if (every? some? [(secret :postmark/api-key)
                       (secret :recaptcha/secret-key)
                       site-key])
      (send-postmark ctx form-params)
      (send-console ctx form-params))))
