(ns com.yakread.lib.item)

(def source-id (some-fn :item.feed/feed :item.email/sub))

(def published-at (some-fn :item/published-at :item/ingested-at))
