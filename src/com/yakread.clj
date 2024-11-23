(ns com.yakread
  (:require [cld.core :as cld]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :as tn-repl]
            [com.biffweb :as biff]
            [com.wsscode.pathom3.connect.indexes :as pci]
            [com.wsscode.pathom3.connect.planner :as pcp]
            [com.wsscode.pathom3.connect.runner :as pcr]
            [com.yakread.app.admin :as app.admin]
            [com.yakread.app.auth :as app.auth]
            [com.yakread.app.favorites :as app.favorites]
            [com.yakread.app.for-you :as app.for-you]
            [com.yakread.app.home :as app.home]
            [com.yakread.app.read-later :as app.read-later]
            [com.yakread.app.settings :as app.settings]
            [com.yakread.app.shell :as app.shell]
            [com.yakread.app.subscriptions :as app.subs]
            [com.yakread.app.subscriptions.add :as app.subs.add]
            [com.yakread.app.subscriptions.view :as app.subs.view]
            [com.yakread.email :as email]
            [com.yakread.lib.pathom :as lib.pathom]
            [com.yakread.lib.pipeline :as lib.pipeline]
            [com.yakread.lib.ui :as lib.ui]
            [com.yakread.middleware :as mid]
            [com.yakread.model.item :as model.item]
            [com.yakread.model.subscription :as model.sub]
            [com.yakread.model.user :as model.user]
            [com.yakread.schema :as schema]
            [com.yakread.util.biff-staging :as biffs]
            [malli.core :as malc]
            [malli.registry :as malr]
            [nrepl.cmdline :as nrepl-cmd]
            [reitit.ring :as reitit-ring])
  (:gen-class))

(def modules
  [app.admin/module
   app.auth/module
   app.favorites/module
   app.for-you/module
   app.home/module
   app.read-later/module
   app.settings/module
   app.shell/module
   app.subs/module
   app.subs.add/module
   app.subs.view/module
   model.item/module
   model.sub/module
   model.user/module
   schema/module
   (app.auth/module
    #:biff.auth{:app-path "/"
                :single-opt-in true
                :new-user-tx app.auth/new-user-tx*
                :check-state false
                :email-validator app.auth/email-valid?
                :link-expire-minutes (* 60 24 7)
                ;; TODO use router or something
                :allowed-redirects #{"/"
                                     "/home"
                                     "/advertise"
                                     "/subscriptions/add"
                                     "/read-later/add"
                                     "/favorites/add"
                                     "/dev/subscriptions/add"}})])

(def router (reitit-ring/router
             [["" {:middleware mid/default-site-middleware}
               (keep :routes modules)]
              ["" {:middleware [biff/wrap-api-defaults]}
               (keep :api-routes modules)]]))

(def handler (-> (biff/reitit-handler {:router router :on-error lib.ui/on-error-page})
                 biff/wrap-base-defaults
                 #_premium/wrap-stripe-event))

(def static-pages (apply biff/safe-merge (map :static modules)))

(defn generate-assets! [_]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [sys]
  (biff/add-libs)
  (biff/eval-files! sys)
  (generate-assets! sys)
  (test/run-all-tests #"com.yakread.*-test")
  (log/info :done))

(def malli-opts
  {:registry (malr/composite-registry
              malc/default-registry
              (apply biff/safe-merge
                     (keep :schema modules)))})

(def pathom-env (pci/register (conj (mapcat :resolvers modules)
                                    (biffs/pull-resolver malli-opts))))

(defn merge-context [{:keys [com.yakread/spark-model] :as ctx}]
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
                })
        (pcp/with-plan-cache (atom {}))
        (pcr/with-resolver-cache (atom {})))))

(defonce system (atom {}))

(def components
  [biff/use-aero-config
   biff/use-xt
   biff/use-queues
   biff/use-tx-listener
   #_train/use-spark
   biff/use-jetty
   biff/use-chime
   biff/use-beholder
   #_components/use-subetha])

(def initial-system {:biff/modules #'modules
                     :biff/merge-context-fn #'merge-context
                     :biff/after-refresh `start
                     :biff/handler #'handler
                     :biff/malli-opts #'malli-opts
                     :biff/router router
                     :biff/send-email #'email/send-email
                     :biff.beholder/on-save #'on-save
                     :biff.pipe/global-handlers lib.pipeline/global-handlers
                     :biff.xtdb/tx-fns biff/tx-fns
                     #_#_:com.yakread.subetha/accept? #'ingest/accept-email?
                     #_#_:com.yakread.subetha/deliver #'ingest/deliver-email
                     :com.yakread/home-feed-cache (atom {})
                     lib.pathom/plan-cache-kw (atom {})
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
  (let [{:keys [biff.nrepl/args]} (start)]
    (apply nrepl-cmd/-main args)))

(defn refresh []
  (doseq [f (:biff/stop @system)]
    (log/info "stopping:" (str f))
    (f))
  (tn-repl/refresh :after `start)
  :done)
