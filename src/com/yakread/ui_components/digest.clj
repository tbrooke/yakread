(ns com.yakread.ui-components.digest
  (:require
   [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver]]) 
  (:import
   [java.time.format DateTimeFormatter]))

(defresolver settings [{:user/keys [digest-days send-digest-at]}]
  {:digest.settings/freq-text (case (count digest-days)
                                7 "daily"
                                1 "weekly"
                                (str (count digest-days) "x/week"))
   :digest.settings/time-text (.format send-digest-at (DateTimeFormatter/ofPattern "h:mm a"))})

(def module {:resolvers [settings]})
