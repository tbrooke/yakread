(ns com.yakread.lib.middleware
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [com.biffweb :as biff :refer [q]]
   [com.wsscode.pathom3.error :as p.error]
   [com.yakread.lib.datastar :as lib.d*]
   [com.yakread.settings :as settings]
   [com.yakread.util :as util]
   [rum.core :as rum]
   [xtdb.api :as xt]))

(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as ctx}]
    (if (:uid session)
      (handler ctx)
      {:status 303
       :headers {"location" "/signin?error=not-signed-in"}})))

(defn wrap-edn-json-params [handler]
  (fn [{:keys [params] :as ctx}]
    (handler
     (cond-> ctx
       (:edn params) (update :params merge (edn/read-string (:edn params)))))))

(defn wrap-pathom-error [handler]
  (fn [ctx]
    (try
      (handler ctx)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (cond
            (and (= (::p.error/cause data) ::p.error/attribute-missing)
                 (some #(str/starts-with? (namespace %) "params") (keys (:missing data))))
            {:status 400 :body "invalid input" :headers {"Content-type" "text/plain"}}

            (and (= (::p.error/cause data) ::p.error/attribute-missing)
                 (some #(= "session" (namespace %)) (keys (:missing data))))
            {:status 401}

            :else
            (throw e)))))))

(defn wrap-render-body [handler]
  (fn [ctx]
    (let [response (handler ctx)]
      (cond-> response
        (vector? (:body response)) (update :body rum/render-static-markup)))))

(defn- render [body]
  (str "<!DOCTYPE html>\n" (rum/render-static-markup body)))

(defn wrap-render-rum [handler]
  (fn [ctx]
    (let [response (handler ctx)]
      (cond
        (vector? response)
        {:status 200
         :headers {"content-type" "text/html"}
         :body (render response)}

        (vector? (:body response))
        (update response :body render)

        :else
        response))))

(defn user-from-params [{:keys [biff/db
                                biff/secret
                                params
                                anti-forgery-token] :as req}]
  (let [{:keys [intent email iat]} (when (:auth params)
                                     (biff/jwt-decrypt (:auth params) (secret :biff/jwt-secret)))
        success (= intent "signin")
        user (when success
               (biff/lookup db :user/email email))
        valid-from (get settings/invalid-sessions (:xt/id user))]
    (when (and (or (nil? valid-from) (< valid-from iat))
               user)
      (assoc user :user/iat iat))))

(defn user-from-session [{:keys [biff/db session] :as sys}]
  (let [session-valid (if-some [iat (get settings/invalid-sessions (:uid session))]
                        (and (some? (:iat session))
                             (< iat (:iat session)))
                        true)]
    (when (and session-valid (:uid session))
      (xt/entity db (:uid session)))))

(defn get-user [{:keys [biff/db] :as sys}]
  (or (user-from-params sys) (user-from-session sys)))

(defn wrap-redirect-signed-in [handler]
  (fn [{:keys [biff/db params] :as sys}]
    (if (and (get-user sys) (not (:noredirect params)))
      {:status 303
       :headers {"location" "/home"}}
      (handler sys))))

(defn add-rec [{:keys [biff/db user path-params] :as sys}]
  (let [rec (some->> (:rec path-params)
                     parse-uuid
                     (xt/pull db '[* {:rec/item [*]}]))]
    (if (= (:xt/id user) (:rec/user rec))
      (assoc sys :rec rec)
      sys)))

(defn wrap-signed-in* [handler on-error]
  (let [handler (biff/wrap-render-rum handler)]
    (fn [{:keys [biff/db session] :as sys}]
      (let [user (get-user sys)
            from-params (:user/iat user)]
        (if (some? user)
          (cond-> (handler (-> sys
                               (assoc :user user
                                      :url-auth (some? from-params)
                                      :admin (util/admin? user)
                                      :analyst (util/analyst? user))
                               add-rec))
            from-params (update :session
                                (fn [new-session]
                                  (assoc (or new-session session)
                                         :uid (:xt/id user)
                                         :iat (:user/iat user)))))
          (on-error sys))))))

(defn wrap-signed-in [handler]
  (wrap-signed-in* handler
                   (constantly {:status 303
                                :headers {"location" "/signin?error=not-signed-in"}})))

(defn wrap-maybe-signed-in [handler]
  (wrap-signed-in* handler handler))

(defn wrap-admin [handler]
  (fn [{:keys [biff/db admin] :as sys}]
    (if admin
      (handler sys)
      {:status 303
       :headers {"location" "/"}})))

(defn wrap-analyst [handler]
  (fn [{:keys [biff/db analyst] :as sys}]
    (if analyst
      (handler sys)
      {:status 303
       :headers {"location" "/"}})))

(defn wrap-merge [handler f]
  (fn [sys]
    (handler (merge sys (f sys)))))

(defn get-conns [{:keys [biff/db user]}]
  (let [conns (q db
                 '{:find (pull conn [* {:conn.epub/next-item [*]}])
                   :in [user]
                   :where [[conn :conn/user user]]}
                 (:xt/id user))]
    {:conns conns
     :conn/email (first (filter :conn.email/username conns))
     :conn/rss (->> conns
                    (sort-by :conn.rss/subscribed-at #(compare %2 %1))
                    (filter :conn.rss/url))
     :conn/pocket (first (filter :conn.pocket/username conns))
     :conn/instapaper (first (filter :conn.instapaper/user-id conns))
     :conn/twitter (first (filter :conn.twitter/username conns))
     :conn/discord (first (filter :conn.discord/username conns))
     :conn/mastodon (first (filter :conn.mastodon/username conns))
     :conn/epubs (->> conns
                      (filter :conn.epub/title)
                      (sort-by (juxt :conn.epub/author-name :conn.epub/title)))}))

(defn wrap-auth-aliases [handler]
  (fn [{:keys [biff/db params uri] :as req}]
    (let [email (biff/normalize-email (:email params))
          [username domain] (some-> email (str/split #"@"))
          alias-for (and (str/starts-with? uri "/auth/")
                         (= domain "yakread.com")
                         (-> (biff/lookup db '[{:conn/user [:user/email]}] :conn.email/username username)
                             :conn/user
                             :user/email))
          req (if alias-for
                (assoc-in req [:params :email] alias-for)
                req)
          resp (handler req)
          resp (if (and alias-for (get-in resp [:headers "location"]))
                 (update-in resp [:headers "location"] str/replace alias-for (:email params))
                 resp)]
      resp)))

(def default-site-middleware
  [biff/wrap-site-defaults
   wrap-render-rum
   wrap-auth-aliases
   wrap-edn-json-params
   wrap-pathom-error
   lib.d*/wrap-sse-response])

(defn wrap-options [handler options]
  (fn [ctx]
    (handler (merge options ctx))))
