# MinIO Setup for Yakread Project

## Overview
MinIO has been configured as a local S3-compatible storage solution for the Yakread project. This allows transitioning from filesystem storage to S3-like storage while maintaining compatibility with existing S3 infrastructure.

## Files Created

### 1. `docker-compose.yml`
MinIO service configuration with:
- MinIO server on port 9000 (API)
- MinIO console on port 9001 (Web UI)
- Auto-created `yakread-bucket`
- Persistent volume storage
- Health checks and restart policies

### 2. `.env`
Environment variables:
```
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin123
MINIO_ENDPOINT=http://localhost:9000
MINIO_BUCKET=yakread-bucket
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin123
```

### 3. `minio-config.edn`
Clojure configuration for Biff S3 integration:
```clojure
{:yakread.minio/access-key "minioadmin"
 :yakread.minio/secret-key "minioadmin123"
 :yakread.minio/origin "http://localhost:9000"
 :yakread.minio/bucket "yakread-bucket"
 :yakread.minio/region "us-east-1"

 ;; Biff S3 configuration namespace
 :biff.s3/access-key "minioadmin"
 :biff.s3/secret-key "minioadmin123"
 :biff.s3/origin "http://localhost:9000"
 :biff.s3/bucket "yakread-bucket"
 :biff.s3/region "us-east-1"}
```

## Existing S3 Infrastructure

### Key Files
- `yakread/src/com/yakread/lib/s3.clj` - S3 operations and presigned URLs
- `yakread/src/com/yakread/lib/pipeline.clj` - Pipeline with S3 handlers

### Existing S3 Functions
- `com.yakread.lib.s3/request` - Main S3 request function
- `com.yakread.lib.s3/presigned-url` - Generate presigned URLs
- `com.yakread.lib.s3/mock-request` - Filesystem fallback (currently used)

### Pipeline Handlers Available
- `:biff.pipe/s3` - S3 operations handler (line 139-140)
- `:biff.pipe.s3/presigned-url` - Presigned URL handler (line 141-142)

## Getting Started

### 1. Start MinIO
```bash
# Ensure Docker Desktop is running
docker-compose up -d
```

### 2. Access MinIO Console
- URL: http://localhost:9001
- Username: `minioadmin`
- Password: `minioadmin123`

### 3. Integration Steps

#### Option A: Update Biff Configuration
Merge `minio-config.edn` into your main config:
```clojure
;; In your main config.edn or equivalent
{:biff.s3/access-key "minioadmin"
 :biff.s3/secret-key "minioadmin123"
 :biff.s3/origin "http://localhost:9000"
 :biff.s3/bucket "yakread-bucket"
 :biff.s3/region "us-east-1"}
```

#### Option B: Environment-based Configuration
Use environment variables in your Clojure code:
```clojure
(defn minio-config []
  {:biff.s3/access-key (System/getenv "MINIO_ACCESS_KEY")
   :biff.s3/secret-key (System/getenv "MINIO_SECRET_KEY")
   :biff.s3/origin (System/getenv "MINIO_ENDPOINT")
   :biff.s3/bucket (System/getenv "MINIO_BUCKET")
   :biff.s3/region "us-east-1"})
```

### 4. Switch from Filesystem to MinIO
Replace calls to `lib.s3/mock-request` with `lib.s3/request` in your pipeline.

## Usage Examples

### Using Pipeline Functions (Recommended)
```clojure
;; Store data in MinIO
(lib.pipeline/s3 :biff.s3 "my-file.json" (json/encode data) "application/json")

;; Get presigned URL for file access
(lib.pipeline/s3-presigned-url :biff.s3 "my-file.json" (plus-minutes (now) 10))
```

### Direct S3 Function Usage
```clojure
;; PUT request
(lib.s3/request ctx {:key "my-file.json"
                     :method "PUT"
                     :body (json/encode data)
                     :headers {"content-type" "application/json"}})

;; GET request
(lib.s3/request ctx {:key "my-file.json"
                     :method "GET"})
```

## Migration Path

1. **Phase 1**: Set up MinIO and test with new data
2. **Phase 2**: Update pipeline to use MinIO instead of filesystem
3. **Phase 3**: Migrate existing filesystem data to MinIO buckets
4. **Phase 4**: Remove filesystem fallback code

## Troubleshooting

### Common Issues
- **Docker not running**: Start Docker Desktop
- **Port conflicts**: Ensure ports 9000 and 9001 are available
- **Connection refused**: Check MinIO container is running with `docker ps`

### Verification Commands
```bash
# Check MinIO container status
docker ps | grep minio

# View MinIO logs
docker-compose logs minio

# Test MinIO API
curl http://localhost:9000/minio/health/live
```

## Next Steps for Development

1. **Test Connection**: Verify MinIO is accessible from your Clojure REPL
2. **Update Configuration**: Choose integration approach (config file vs environment)
3. **Test Pipeline**: Use existing S3 pipeline functions with MinIO
4. **Data Migration**: Plan migration of existing filesystem data
5. **Production Setup**: Configure for server deployment with proper security

## Security Notes for Production

- Change default credentials (`minioadmin/minioadmin123`)
- Use environment variables for secrets
- Configure proper bucket policies
- Enable TLS/SSL for HTTPS access
- Set up proper network security