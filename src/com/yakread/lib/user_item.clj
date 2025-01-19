(ns com.yakread.lib.user-item)

(defn read? [doc]
  (boolean (some (or doc {}) [:user-item/viewed-at
                              :user-item/favorited-at
                              :user-item/disliked-at
                              :user-item/reported-at])))
