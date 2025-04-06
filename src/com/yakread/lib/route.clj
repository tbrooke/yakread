(ns com.yakread.lib.route
  (:require [clojure.string :as str]
            [com.yakread.lib.serialize :as lib.serialize]
            [com.yakread.util.biff-staging :as biffs]
            [com.biffweb :as biff]
            [reitit.core :as reitit]
            [com.yakread.lib.core :as lib.core]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.yakread.lib.pipeline :as lib.pipe]
            [lambdaisland.uri :as uri]
            [taoensso.nippy :as nippy]
            [ring.middleware.anti-forgery :as csrf]
            [cheshire.core :as cheshire]))

(defn- encode-uuid [x]
  (if (uuid? x)
    (lib.serialize/uuid->url x)
    x))

(def ^:dynamic *testing* false)

(defn href [route & args]
  (let [path-template (first
                       (if (symbol? route)
                         @(resolve route)
                         route))
        template-segments (str/split path-template #":[^/]+")
        {path-args false query-params true} (group-by map? args)
        path-args (mapv encode-uuid path-args)
        query-params (apply merge query-params)
        path (apply str (lib.core/interleave-all template-segments path-args))]
    (if *testing*
      [path query-params]
      (str path
           (when (not-empty query-params)
             (str "?" (uri/map->query-string {:npy (nippy/freeze-to-string query-params)})))))))

(defn href-safe [{:keys [session biff/jwt-secret]} route & args]
  (let [path-template (first
                       (if (symbol? route)
                         @(resolve route)
                         route))
        template-segments (str/split path-template #":[^/]+")
        {path-args false query-params true} (group-by map? args)
        path-args (mapv encode-uuid path-args)
        query-params (apply merge query-params)
        path (apply str (lib.core/interleave-all template-segments path-args))
        token (delay (lib.serialize/ewt-encode jwt-secret (assoc query-params
                                                                 :uid (:uid session)
                                                                 :path path)))]
    (str path
         (when (not-empty query-params)
           (str "?" (uri/map->query-string {:ewt @token}))))))

(defn action [action-name route & {:as opts}]
  (let [url (href route (:params opts {}))
        opts (-> opts
                 (dissoc :params)
                 (assoc-in [:headers :x-csrf-token] csrf/*anti-forgery-token*))]
    (str "@" (name action-name) "('" url "', " (cheshire/generate-string opts) ")")))

(defn redirect [& args]
  {:status 303
   :headers {"Location" (apply href args)}})

(defn hx-redirect [& args]
  {:status 303
   :headers {"HX-Redirect" (apply href args)}})

(defn call [router route-name method ctx]
  ((get-in (reitit/match-by-name router route-name)
           [:data method :handler])
   ctx))

(defn handler [router route-name method]
  (get-in (reitit/match-by-name router route-name)
          [:data method :handler]))

(defn wrap-nippy-params [handler]
  (fn
    ([{:keys [biff/jwt-secret params] :as ctx}]
     (handler
      (cond-> ctx
        (:npy params) (update :params merge (nippy/thaw-from-string (:npy params)))
        (:ewt params) (assoc :biff/safe-params (lib.serialize/ewt-decode jwt-secret (:ewt params))))))
    ([ctx handler-id]
     (handler ctx handler-id))))

(defmacro defget [sym path query handler]
  `(def ~sym [~path {:name ~(keyword (str *ns*) (str sym))
                     :get (wrap-nippy-params (lib.pathom/handler ~query ~handler))}]))

(defn safe-for-url? [s]
  (boolean (re-matches #"[a-zA-Z0-9-_.+!*]+" s)))

(defmacro defpost [sym & pipe-args]
  (let [href (str "/_biff/api/" *ns* "/" sym)]
    (assert (safe-for-url? (str sym)) (str "URL segment would contain invalid characters: " sym))
    (assert (safe-for-url? (str *ns*)) (str "URL segment would contain invalid characters: " *ns*))
    `(def ~sym [~href {:name ~(keyword (str *ns*) (str sym))
                       :post (wrap-nippy-params (lib.pipe/make ~@pipe-args))}])))

(def ? lib.pathom/?)

(defmacro defpost-pathom [sym query f & args]
  `(defpost ~sym
     :start (lib.pipe/pathom-query ~query :start*)
     :start* (let [f# ~f]
               (fn [ctx#]
                 (f# ctx# (:biff.pipe.pathom/output ctx#))))
     ~@args))
