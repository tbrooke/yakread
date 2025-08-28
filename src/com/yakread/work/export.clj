(ns com.yakread.work.export 
  (:require
   [clojure.java.io :as io]
   [com.yakread.lib.core :as lib.core]
   [com.yakread.lib.pipeline :as pipe :refer [defpipe]]
   [clojure.tools.logging :as log]
   [rum.core :as rum]))

(defpipe export-user-data
  :start
  (fn [{:keys [biff/job]}]
    (log/info ::start)
    (let [{:keys [user/id]} job]
      {:biff.pipe/next [(pipe/pathom {:user/id id}
                                     [:user/id
                                      :user/email
                                      :user/timezone
                                      :user.export/feed-subs
                                      :user.export/bookmarks
                                      :user.export/favorites])
                        :biff.pipe/temp-dir
                        :upload]}))

  :upload
  (fn [{:keys [biff/now
               biff.pipe.pathom/output]
        tmp-dir :biff.pipe.temp-dir/path}]
    (log/info ::upload)
    (let [{:user/keys [id email timezone]
           :user.export/keys [feed-subs bookmarks favorites]} output
          dir (io/file tmp-dir (str "yakread-export-"
                                    (lib.core/fmt-inst now "yyyy-MM-dd" timezone)
                                    "-" (inst-ms now) "-" id))
          zipfile (str dir ".zip")
          s3-key (.getName (io/file zipfile))]
      {:biff.pipe/next (concat
                        (for [[basename content] [["feed-subscriptions.opml" feed-subs]
                                                  ["bookmarks.csv" bookmarks]
                                                  ["favorites.csv" favorites]]]
                          (pipe/write (io/file dir basename) content))
                        [(pipe/shell "zip" "-r" s3-key (.getName dir) :dir tmp-dir)
                         (pipe/delete-files dir)
                         (pipe/s3 'yakread.s3.export
                                  s3-key
                                  (io/file zipfile)
                                  "application/zip")
                         (pipe/delete-files zipfile)
                         (pipe/s3-presigned-url 'yakread.s3.export
                                                s3-key
                                                (.plusSeconds now (* 60 60 24 7)))
                         :send])
       ::email email}))

  :send
  (fn [{:keys [biff.pipe.s3.presigned-url/output ::email]}]
    (log/info ::upload)
    {:biff.pipe/next [(pipe/email {:template :export
                                   :to email
                                   :download-url output})]}))

(def module
  {:queues [{:id :work.export/export-user-data
             :consumer #'export-user-data
             :n-threads 1}]})
