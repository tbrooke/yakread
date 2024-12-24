(ns com.yakread.lib.core)

(defn pred->
  "(if (pred x) (f x) x)"
  [x pred f]
  (if (pred x)
    (f x)
    x))
