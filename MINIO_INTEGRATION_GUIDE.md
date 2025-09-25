# MinIO Integration Guide for Mount Zion Content Pipeline

## 🎯 Overview

We've integrated MinIO (S3-compatible object storage) to replace local file storage in the Alfresco content pipeline. This provides versioned, scalable storage for extracted content.

## 📋 What Was Implemented

### 1. MinIO Configuration (`src/com/yakread/config/minio.clj`)
- Connection settings for MinIO
- Bucket structure and naming conventions
- Helper functions for key generation
- Storage and retrieval functions

### 2. MinIO Content Service (`src/com/yakread/alfresco/minio_content_service.clj`)
- Complete storage pipeline for extracted content
- XTDB metadata integration
- Migration utilities for existing files
- Frontend content loading with fallback

### 3. Updated Routes (`src/com/yakread/app/routes.clj`)
- Modified to load content from MinIO
- Fallback to local files if MinIO unavailable
- Seamless integration with existing HTMX frontend

## 🏗️ Architecture

```
Alfresco → Content Extraction → Malli Validation → MinIO Storage → XTDB Metadata → HTMX Frontend
                                                         ↓
                                                   S3-Compatible API
                                                         ↓
                                                   Versioned Storage
```

### MinIO Bucket Structure
```
mtzion-content/
├── extracted/          # Raw extracted content from Alfresco
│   ├── feature1/
│   │   ├── latest/content.edn
│   │   └── 2024-01-15T10-30-00/content.edn
│   └── feature2/
├── processed/          # Processed content (images converted, etc)
├── website/            # Website-ready content
│   ├── feature1/
│   │   └── latest/website.edn
│   └── feature2/
├── images/             # Cached images from Alfresco
└── metadata/           # Content metadata
```

## 🚀 Setup Instructions

### 1. Ensure MinIO is Running

MinIO should be set up in Docker (per your MINIO_SETUP.md in parent directory).

```bash
# Check if MinIO is running
curl http://localhost:9000

# Access MinIO Console
open http://localhost:9001
# Login: minioadmin / minioadmin
```

### 2. Create Content Bucket

```bash
# Install MinIO Client
brew install minio/stable/mc

# Configure alias
mc alias set mtzion http://localhost:9000 minioadmin minioadmin

# Create bucket
mc mb mtzion/mtzion-content

# Set public read for website content
mc anonymous set download mtzion/mtzion-content/website/*
```

### 3. Run Setup Scripts

```bash
# Check MinIO setup
bb setup_minio_content.clj

# Test integration
bb test_minio_integration.clj
```

### 4. Migrate Existing Content

In your REPL:
```clojure
(require '[com.yakread.alfresco.minio-content-service :as minio])

;; Migrate existing local files
(minio/migrate-local-files-to-minio {})

;; Verify migration
(minio/retrieve-website-content {} "feature1")
(minio/retrieve-website-content {} "feature2")
```

## 🔄 Updated Extraction Workflow

### Before (Local Files)
```clojure
;; Old way - saves to local file
(spit "mtzuix-feature1-content.edn" (pr-str content))
```

### After (MinIO Storage)
```clojure
;; New way - stores in MinIO with versioning
(minio/store-content ctx :extracted-content "feature1" "content.edn" content)
```

### Complete Pipeline Example
```clojure
;; Extract from Alfresco and store in MinIO
(minio/extract-and-store-feature ctx "feature1" alfresco-folder-id)

;; Load for frontend display
(minio/load-content-for-page ctx "feature1")
```

## 🎯 Benefits

1. **Versioned Storage**: Every save creates timestamped version
2. **S3 Compatible**: Works with standard S3 tools and APIs
3. **Scalable**: No local file system limitations
4. **Web Accessible**: Content can be served directly via URLs
5. **Backup Ready**: Easy replication and backup with S3 tools
6. **No Manual Cleanup**: No more managing local EDN files

## 🧪 Testing

### Test Storage
```clojure
;; Store test content
(minio/store-content {} :website-content "test" "test.edn" 
                    {:title "Test" :content "Hello MinIO!"})

;; Retrieve it
(minio/retrieve-content {} :website-content "test" "test.edn")
```

### Test Frontend Loading
```clojure
;; This is what the routes use
(minio/load-content-for-page {} "feature1")
```

## 🔍 Troubleshooting

### MinIO Connection Issues
```bash
# Check MinIO is running
docker ps | grep minio

# Test connection
curl -I http://localhost:9000

# Check logs
docker logs <minio-container-id>
```

### Content Not Loading
1. Check MinIO bucket exists: `mc ls mtzion/`
2. Check content exists: `mc ls mtzion/mtzion-content/website/`
3. Check fallback to local files is working
4. Check logs for errors

### View Stored Content
```bash
# List all content
mc ls -r mtzion/mtzion-content/

# View specific file
mc cat mtzion/mtzion-content/website/feature1/latest/website.edn
```

## 📝 Code Changes Summary

1. **New Files**:
   - `src/com/yakread/config/minio.clj` - MinIO configuration
   - `src/com/yakread/alfresco/minio_content_service.clj` - Storage service
   - `setup_minio_content.clj` - Setup helper
   - `test_minio_integration.clj` - Integration test

2. **Modified Files**:
   - `src/com/yakread/app/routes.clj` - Updated to use MinIO with fallback

3. **Pipeline Integration**:
   - Existing `lib/pipeline.clj` already has S3 support
   - Content processor works unchanged
   - XTDB metadata storage added

## 🎉 Next Steps

1. **Run Migration**: Move existing content to MinIO
2. **Update Extraction Scripts**: Modify to save directly to MinIO
3. **Remove Local Files**: Once verified, remove local EDN files
4. **Add Image Caching**: Store Alfresco images in MinIO
5. **Enable Direct Serving**: Serve static content directly from MinIO

The MinIO integration is ready to use! It provides a modern, scalable storage solution for your Alfresco content pipeline while maintaining backward compatibility with existing local files.