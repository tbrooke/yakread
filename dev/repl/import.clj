(ns repl.import
  (:require [com.biffweb :as biff :refer [q]]
            [clojure.java.io :as io]
            [com.yakread :as main]
            [com.yakread.smtp :as smtp]
            [com.yakread.lib.route :as lib.route]
            [com.yakread.lib.pathom :as lib.pathom :refer [?]]
            [com.yakread.lib.smtp :as lib.smtp]
            [reitit.core :as reitit]
            [xtdb.api :as xt]
            [taoensso.nippy :as nippy]))

(defn import! [node]
  (xt/sync node)
  (println "done syncing")
  (let [db (xt/db node)
        dir (io/file "storage/export-23685064")]
    (doseq [i (range 6500 (count (.listFiles dir)))
            :let [f (io/file dir (str i ".nippy"))
                  docs (nippy/thaw-from-file f)]
            :when (not (xt/entity db (:xt/id (first docs))))]
      (when (= 0 (mod i 10))
        (println "batch" i)
        (prn (xt/latest-completed-tx export-node)))
      (xt/submit-tx export-node (for [doc docs]
                                  [::xt/put doc])))))

(comment

  (def export-node (:biff.xtdb/node (biff/use-xtdb {:biff.xtdb/topology :standalone,
                                                    :biff.xtdb/dir "storage/export-xtdb"})))

  (time (xt/sync export-node))
  (inc 3)

  (run! println (q (xt/db export-node)
     '{:find email
       :where [[user :user/email email]]}))

  (while true
    (import! export-node)
    (Thread/sleep 10000))

  (xt/latest-completed-tx export-node)
  (ns-publics 'xtdb.api)

  (time
   (let [dir (io/file "storage/export-23685064")]
     (doseq [f (.listFiles dir)]
       (xt/submit-tx export-node (for [doc (nippy/thaw-from-file f)]
                                   [::xt/put doc]))
       )
    
    )
   )

  (.close export-node)

  biff/use-xtdb

  (def export-node-2 (:biff.xtdb/node (biff/use-xtdb {:biff.xtdb/topology :standalone,
                                                      :biff.xtdb/dir "storage/export-xtdb-2"})))
  
  )
