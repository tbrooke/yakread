(ns com.yakread
  (:require [cld.core :as cld]
            [clojure.data.generators :as gen]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [clojure.java.io :as io]
            [com.biffweb :as biff]
            [com.yakread.modules :as modules]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.operation :as pco]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.yakread.lib.email :as lib.email]
            [com.yakread.lib.auth :as lib.auth]
            [com.yakread.lib.jetty :as lib.jetty]
            [com.yakread.lib.middleware :as lib.middleware]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.yakread.lib.pipeline :as lib.pipeline]
            [com.yakread.lib.route :as lib.route :refer [href]]
            [com.yakread.lib.smtp :as lib.smtp]
            [com.yakread.lib.ui :as ui]
            [com.yakread.routes :as routes]
            [com.yakread.smtp :as smtp]
            [com.yakread.util.biff-staging :as biffs]
            [malli.core :as malli]
            [malli.experimental.time :as malli.t]
            [malli.util :as malli.u]
            [malli.registry :as malr]
            [nrepl.cmdline :as nrepl-cmd]
            [reitit.ring :as reitit-ring]
            [time-literals.read-write :as time-literals]
            [clojure.tools.namespace.find :as ns-find])
  (:gen-class))

(def modules
  (concat modules/modules
          [(lib.auth/module
            #:biff.auth{:app-path "/"
                        :email-validator lib.auth/email-valid?
                        :link-expire-minutes (* 60 24 7)
                        :allowed-redirects #{"/"
                                             (href routes/for-you)
                                             (href routes/add-sub-page)
                                             (href routes/add-bookmark-page)
                                             (href routes/add-favorite-page)
                                             ;; TODO use routes.clj
                                             "/advertise"}})]))

(def router (reitit-ring/router
             [["" {:middleware lib.middleware/default-site-middleware}
               (keep :routes modules)]
              ["" {:middleware [biff/wrap-api-defaults]}
               (keep :api-routes modules)]]))

(def handler (-> (biff/reitit-handler {:router router :on-error ui/on-error-page})
                 biff/wrap-base-defaults
                 #_premium/wrap-stripe-event))

(def static-pages (apply biff/safe-merge (map :static modules)))

(defn generate-assets! [_]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [sys]
  (biff/add-libs)
  (biffs/generate-modules-file!
   {:output-file "src/com/yakread/modules.clj"
    :search-dirs ["src/com/yakread/app"
                  "src/com/yakread/model"
                  "src/com/yakread/ui_components"
                  "src/com/yakread/work"]})
  (when-not (:clojure.tools.namespace.reload/error (biff/eval-files! sys))
    (generate-assets! sys)
    (test/run-all-tests #"com.yakread.*-test")
    (log/info :done)))

(def malli-opts
  {:registry (apply malr/composite-registry
                    (malli/default-schemas)
                    (malli.t/schemas)
                    (malli.u/schemas)
                    (keep :schema modules))})

(def pathom-env (pci/register (->> (mapcat :resolvers modules)
                                   (concat (biffs/pull-resolvers malli-opts))
                                   (mapv lib.pathom/wrap-debug))))

(defn merge-context [{:keys [com.yakread/spark-model
                             biff/jwt-secret]
                      :as ctx}]
  (let [;snapshots (biff/index-snapshots ctx)
        ]
    (-> ctx
        (biff/assoc-db)
        (merge pathom-env
               (when spark-model
                 (biff/select-ns-as @spark-model nil 'com.yakread.spark))
               {:biff/router router
                :biff.pipe/global-handlers lib.pipeline/global-handlers
                ;:biff/db (:biff/db snapshots)
                ;:biff.index/snapshots snapshots
                :biff/now (java.time.Instant/now)
                :com.yakread/sign-redirect (fn [url]
                                             {:redirect url
                                              :redirect-sig (biffs/signature (jwt-secret) url)})
                :biff/href (partial lib.route/href-safe ctx)})
        (pcp/with-plan-cache (atom {})))))

(defonce system (atom {}))

(def components
  [biff/use-aero-config
   biff/use-xt
   biff/use-queues
   biff/use-tx-listener
   #_train/use-spark
   lib.jetty/use-jetty
   biff/use-chime
   biff/use-beholder
   lib.smtp/use-server])

(def initial-system {:biff/modules #'modules
                     :biff/merge-context-fn #'merge-context
                     :biff/after-refresh `start
                     :biff/handler #'handler
                     :biff/malli-opts #'malli-opts
                     :biff/router router
                     :biff/send-email #'lib.email/send-email
                     :biff.beholder/on-save #'on-save
                     :biff.pipe/global-handlers lib.pipeline/global-handlers
                     :biff.xtdb/tx-fns biff/tx-fns
                     #_#_:com.yakread.subetha/accept? #'ingest/accept-email?
                     #_#_:com.yakread.subetha/deliver #'ingest/deliver-email
                     :com.yakread/home-feed-cache (atom {})
                     lib.pathom/plan-cache-kw (atom {})
                     :biff.smtp/accept? #'smtp/accept?
                     :biff.smtp/deliver #'smtp/deliver*
                     :biff/components components})

(defn start []
  (let [new-system (reduce (fn [system component]
                             (log/info "starting:" (str component))
                             (component system))
                           initial-system
                           components)]
    (reset! system new-system)
    (generate-assets! new-system)
    (log/info "System started.")
    (log/info "Go to" (:biff/base-url new-system))
    new-system))

(defn -main [& args]
  (cld/default-init!)
  (time-literals/print-time-literals-clj!)
  (alter-var-root #'gen/*rnd* (constantly (java.util.Random. (inst-ms (java.time.Instant/now)))))
  (let [{:keys [biff.nrepl/args]} (start)]
    (apply nrepl-cmd/-main args)))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start)
  :done)
