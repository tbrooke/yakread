(ns com.yakread.smtp
  )

(def dev-promise (atom (promise)))

(defn accept? [ctx {:keys [from to username domain]}]
  true)

(defn deliver* [ctx msg]
  (println "deliver")
  (deliver @dev-promise msg))
