# Bitemporal Content Management with XTDB & MinIO

## 🎯 Architecture Overview

Your enhanced architecture leverages the strengths of both systems:

- **MinIO**: Stores versioned content (the actual HTML, images, etc.)
- **XTDB**: Tracks bitemporal metadata (when content was displayed, who published it, why)

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│    Alfresco     │────▶│  Content        │────▶│     MinIO       │
│  (Source Data)  │     │  Extraction     │     │  (Versioned     │
└─────────────────┘     └─────────────────┘     │   Storage)      │
                                                 └────────┬────────┘
                                                          │
                        ┌─────────────────┐               │
                        │      XTDB       │◀──────────────┘
                        │  (Bitemporal    │
                        │   Metadata)     │
                        └────────┬────────┘
                                 │
                        ┌────────▼────────┐
                        │   Website/UI    │
                        │  (Time-aware    │
                        │   Display)      │
                        └─────────────────┘
```

## 📅 Bitemporal Concepts

### Valid Time vs Transaction Time

- **Valid Time**: When content should be displayed on the website
- **Transaction Time**: When we recorded the change in the system

Example:
```clojure
;; On Nov 15th, schedule Christmas content to show Dec 1-26
{:tx-time #inst "2024-11-15"      ; When we made this decision
 :valid-from #inst "2024-12-01"   ; When to start showing it
 :valid-to #inst "2024-12-26"}    ; When to stop showing it
```

## 🔄 Key Workflows

### 1. Publishing Content

```clojure
;; Publish extracted content immediately
(publish-extracted-content ctx "feature1"
  :published-by "content-team"
  :reason "Weekly content update")

;; Schedule future publication
(schedule-content-publication ctx "feature1" 
  #inst "2024-12-01"
  "content-team"
  "Holiday content")

;; Set content expiration
(expire-content ctx "feature1"
  #inst "2024-12-26"
  "content-team"
  "End of holiday season")
```

### 2. Time Travel Queries

```clojure
;; What content was live on Christmas last year?
(what-was-live-on-date ctx #inst "2023-12-25")

;; View content history for a feature
(get-content-history ctx "feature1")

;; Load content as it appeared on specific date
(load-content-for-page ctx "feature1" 
  :as-of-time #inst "2023-12-25")
```

### 3. Content Rollback

```clojure
;; Rollback to previous version
(rollback-to-version ctx "feature1"
  #inst "2024-11-01"  ; Target date
  "Content had errors"
  "admin")
```

## 🎯 Benefits of This Approach

### 1. **Efficient Storage**
- Content stored once in MinIO, referenced by XTDB
- No duplication of large HTML/image data
- S3 versioning provides content history

### 2. **Powerful Queries**
- "What did the website look like on date X?"
- "Who published what and when?"
- "When was this content live?"

### 3. **Audit Trail**
```clojure
(who-published-what-when ctx)
;; Returns:
;; ["feature1" "admin" "Initial launch" #inst "2024-01-01" #inst "2024-01-01T10:00:00"]
;; ["feature1" "content-team" "Weekly update" #inst "2024-01-08" #inst "2024-01-08T14:30:00"]
```

### 4. **Scheduled Changes**
- Schedule holiday content months in advance
- Auto-expire time-sensitive content
- Plan content transitions

## 🏗️ Implementation Details

### XTDB Schema

```clojure
{:xt/id :content/feature1
 :content/type :content.type/website-display
 :content/feature "feature1"
 
 ;; MinIO references (not content itself)
 :content/minio-key "website/feature1/v2/website.edn"
 :content/minio-version-id "abc123"
 
 ;; Publishing metadata
 :content/published-by "content-team"
 :content/publish-reason "Weekly update"
 :content/publish-notes "Added new announcements"
 
 ;; Summary data (for queries without fetching from MinIO)
 :content/title "Welcome to Mt Zion"
 :content/has-images true
 :content/item-count 3
 
 ;; Bitemporal validity
 :xt/valid-from #inst "2024-01-08"
 :xt/valid-to #inst "9999-12-31"}
```

### MinIO Structure

```
mtzion-content/
└── website/
    └── feature1/
        ├── v1/
        │   └── website.edn  (version from Jan 1)
        ├── v2/
        │   └── website.edn  (version from Jan 8)
        └── latest/
            └── website.edn  (symlink to current)
```

## 📊 Example Scenarios

### Scenario 1: Holiday Content

```clojure
;; In November, prepare holiday content
(let [content (extract-from-alfresco ctx "holiday-folder")]
  (store-extracted-content ctx "homepage" content))

;; Schedule it to go live Dec 1st
(schedule-content-publication ctx "homepage"
  #inst "2024-12-01"
  "content-team"
  "Holiday season content")

;; Auto-expire after holidays
(expire-content ctx "homepage"
  #inst "2024-12-26"
  "content-team"
  "End holiday content")
```

### Scenario 2: Emergency Rollback

```clojure
;; Something went wrong with today's update
(rollback-to-version ctx "homepage"
  (.minus (java.time.Instant/now) 1 java.time.temporal.ChronoUnit/DAYS)
  "Broken links in new content"
  "admin")
```

### Scenario 3: Compliance Audit

```clojure
;; Auditor asks: "What did users see on March 15th?"
(let [snapshot (what-was-live-on-date ctx #inst "2024-03-15")]
  (generate-website-snapshot snapshot))

;; Show complete audit trail
(who-published-what-when ctx)
```

## 🚀 Next Steps

1. **Enhanced UI for Time Travel**
   - Add date picker to preview site at any point in time
   - Visual timeline of content changes

2. **Automated Workflows**
   - Auto-publish on approval
   - Content expiration warnings
   - Scheduled extraction from Alfresco

3. **Advanced Features**
   - A/B testing with different valid times
   - Gradual rollouts (different content for different users)
   - Content versioning with approval workflow

## 💡 Best Practices

1. **Always Document Changes**
   ```clojure
   (publish-content ctx "feature1"
     :published-by (current-user ctx)
     :reason "Specific reason for change"
     :notes "Detailed notes about what changed")
   ```

2. **Test Before Publishing**
   ```clojure
   ;; Preview how content will look
   (let [future-time #inst "2024-12-01"]
     (preview-content-at-time ctx "feature1" future-time))
   ```

3. **Regular Snapshots**
   ```clojure
   ;; Take snapshots for compliance
   (schedule-job ctx :daily-snapshot
     (fn [ctx] (snapshot-all-content ctx)))
   ```

This bitemporal approach gives you powerful time-travel capabilities while keeping storage efficient through MinIO's versioning!