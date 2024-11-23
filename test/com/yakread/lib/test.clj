(ns com.yakread.lib.test
  (:require [clojure.tools.logging :as log]
            [clojure.set :as set]
            [clojure.test :as test]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [com.yakread :as main]
            [com.yakread.lib.route :as lib.route]
            [lambdaisland.uri :as uri]
            [xtdb.api :as xt]))

(defrecord BiffSystem []
  java.io.Closeable
  (close [this]
    (doseq [f (:biff/stop this)]
      (log/info "stopping:" (str f))
      (f))))

(def initial-system
  (merge main/initial-system
         {:biff.xtdb/topology :memory
          :biff.index/dir :tmp}))

(defn start!
  ([components]
   (start! initial-system components))
  ([system components]
   (map->BiffSystem
    (reduce (fn [system component]
              (log/info "starting:" (str component))
              (component system))
            system
            components))))

;;; ---

(defn- actual [{:keys [biff.test/fixtures
                       biff/router]}
               {:keys [route-name method handler-id ctx fixture db-contents]
                :or {method :post}}]
  (let [handler (lib.route/handler router route-name method)
        ctx (merge ctx (some-> fixture fixtures))]
    (if db-contents
      (with-open [node (xt/start-node {})]
        (xt/await-tx node (xt/submit-tx node (for [doc db-contents]
                                               [::xt/put doc])))
        (handler (assoc ctx :biff/db (xt/db node)) handler-id))
      (handler ctx handler-id))))

(defn dirname [current-ns]
  (str "test/"
       (-> (str current-ns)
           (str/replace "." "/")
           (str/replace "-" "_"))
       "/"))

(defn- examples-path [current-ns]
  (str (dirname current-ns) "examples.edn"))

(defn- fixtures-path [current-ns]
  (str (dirname current-ns) "fixtures.edn"))

(defn read-fixtures! [current-ns]
  (-> (fixtures-path current-ns)
      slurp
      edn/read-string))

(defn write-fixtures! [current-ns fixtures]
  (with-open [o (io/writer (fixtures-path current-ns))]
    (pprint fixtures o)))

(defn read-examples! [current-ns]
  (-> (examples-path current-ns)
      slurp
      edn/read-string))

(defn write-examples! [{:biff.test/keys [current-ns examples] :as ctx}]
  (let [examples (mapv #(assoc % :expected (actual ctx %)) examples)]
    (with-open [o (io/writer (examples-path current-ns))]
      (binding [*out* o]
        (println "[")
        (doseq [example examples]
          (pprint example)
          (println))
        (println "]")))))

(defn check-examples! [{:biff.test/keys [examples current-ns] :as ctx}]
  (let [written-examples (read-examples! current-ns)]
    (if (not= examples (mapv #(dissoc % :expected) written-examples))
      (test/report {:type :fail
                    :message (str "Example test cases for "
                                  current-ns
                                  " have changed. Please call write-examples!")})
      (doseq [[i {:keys [expected] :as example}] (map-indexed vector written-examples)
              :let [_actual (actual ctx example)]]
        (test/report {:type (if (= expected _actual) :pass :fail)
                      :expected expected
                      :actual _actual
                      :message (str "Example "
                                    (pr-str (dissoc example :expected))
                                    " failed.")})))))

(defmacro current-ns []
  *ns*)
