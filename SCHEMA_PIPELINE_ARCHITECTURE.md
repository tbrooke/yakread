# Schema-Integrated Pipeline Architecture

## Overview

This document describes the complete Malli schema-integrated content pipeline from Alfresco → yakread → mtz-uix. The pipeline ensures type safety, data validation, and consistent data transformation across all components.

## Architecture Components

### 1. Alfresco API Client (`com.yakread.alfresco.client`)
- **Purpose**: HTTP client for Alfresco REST API
- **Features**:
  - Authenticated requests to remote Alfresco server
  - Node operations (get, list, search)
  - Content extraction and metadata parsing
  - Health checking and error handling

### 2. Malli Schema Registry (`com.yakread.alfresco.schema`)
- **Purpose**: Unified schema definitions for the entire pipeline
- **Key Schemas**:
  - `Node`: Alfresco node structure from API
  - `YakreadContent`: XTDB storage format with timestamps
  - `UIXContent`: Frontend-optimized content format
  - `UIXPageContent`: Complete page response structure

### 3. XTDB Storage Layer (`com.yakread.alfresco.storage`)
- **Purpose**: Schema-validated storage and retrieval
- **Features**:
  - Automatic schema validation on storage
  - Content transformation pipelines
  - Sync operations with conflict resolution
  - Data migration and cleanup utilities

### 4. Content API (`com.yakread.app.content`)
- **Purpose**: REST endpoints for content operations
- **Endpoints**: See [API Endpoints](#api-endpoints) section

## Data Flow

```
Alfresco Server → [HTTP Client] → [Schema Validation] → XTDB → [Transform] → UIX Frontend
     ↓                             ↓                      ↓         ↓
API Specs (YAML) → Malli Schemas → Storage Format → UIX Format → Components
```

### Step-by-Step Flow

1. **API Specification Loading**
   - Alfresco API specs loaded from `api-specs/` directory
   - OpenAPI definitions converted to Malli schemas
   - Schema registry initialized with validation rules

2. **Content Fetching**
   - Client connects to remote Alfresco server (`admin.mtzcg.com`)
   - Authenticated requests retrieve node metadata and content
   - Raw responses validated against Alfresco API schemas

3. **Data Transformation**
   - Alfresco nodes transformed to yakread storage format
   - Schema validation ensures data integrity
   - Timestamps and sync metadata added

4. **XTDB Storage**
   - Validated content stored in XTDB with unique document IDs
   - Automatic deduplication based on Alfresco node IDs
   - Version tracking and sync status maintained

5. **Frontend Serving**
   - Content retrieved from XTDB and transformed for UIX
   - Response format validated against UIX schemas
   - JSON served to mtz-uix frontend with CORS headers

## API Endpoints

### Direct Alfresco Endpoints
- `GET /api/content/alfresco/health` - Check Alfresco connectivity
- `GET /api/content/alfresco/homepage` - Direct Alfresco content (fallback)
- `GET /api/content/alfresco/path/:path` - Content by Alfresco path
- `POST /api/content/alfresco/search` - Search Alfresco content

### Schema-Integrated Pipeline
- `POST /api/content/alfresco/sync` - Sync Alfresco → XTDB
- `GET /api/content/schema/page/:page` - Schema-validated content for UIX

### Admin and Debugging
- `GET /api/content/stats` - Content statistics and validation status
- `POST /api/content/validate` - Validate data against schemas

### Legacy/Testing
- `GET /api/content/fake/homepage` - Fake content for testing

## Schema Definitions

### Core Data Types

#### Alfresco Node
```clojure
[:map
 [:id :string]
 [:name :string]
 [:nodeType :string]
 [:isFile :boolean]
 [:isFolder :boolean]
 [:createdAt [:re #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}"]]
 [:modifiedAt [:re #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}"]]
 ;; ... additional fields
 ]
```

#### Yakread Content (XTDB Storage)
```clojure
[:map
 [:xt/id :uuid]
 [:yakread/type [:enum "alfresco-content"]]
 [:yakread/created-at inst?]
 [:content/id :string]
 [:alfresco/node-id :string]
 [:alfresco/name :string]
 [:content/text [:maybe :string]]
 ;; ... additional fields
 ]
```

#### UIX Content (Frontend)
```clojure
[:map
 [:id :string]
 [:type [:enum "text" "html" "pdf" "image" "document"]]
 [:targetComponent [:enum "textBlock" "htmlBlock" "documentViewer"]]
 [:alfresco [:map [:id :string] [:name :string] [:path :string]]]
 [:content [:maybe :string]]
 ;; ... additional fields
 ]
```

## Schema Transformations

### Alfresco → Yakread
- Adds XTDB document ID and timestamps
- Normalizes field names to kebab-case
- Adds sync metadata and version tracking
- Maps MIME types to target components

### Yakread → UIX
- Flattens nested structures for frontend consumption
- Maps content types to UIX component types
- Adds display formatting and ordering
- Ensures all required fields are present

## Environment Configuration

### Alfresco Connection
```bash
ALFRESCO_BASE_URL=http://admin.mtzcg.com
ALFRESCO_USERNAME=admin
ALFRESCO_PASSWORD=admin
```

### Dependencies Added
```clojure
clj-http/clj-http {:mvn/version "3.12.3"}
clj-yaml/clj-yaml {:mvn/version "1.0.27"}
```

## Usage Examples

### 1. Sync Content from Alfresco
```bash
curl -X POST http://localhost:8080/api/content/alfresco/sync
```

### 2. Get Schema-Validated Content
```bash
curl http://localhost:8080/api/content/schema/page/homepage
```

### 3. Check System Status
```bash
curl http://localhost:8080/api/content/stats
```

### 4. Validate Custom Data
```bash
curl -X POST http://localhost:8080/api/content/validate \
  -H "Content-Type: application/json" \
  -d '{"schema": "uix/Content", "data": {...}}'
```

## Benefits of Schema Integration

### 1. Type Safety
- Compile-time validation of data structures
- Prevents runtime errors from malformed data
- Clear contracts between system components

### 2. Data Quality
- Automatic validation on storage and retrieval
- Schema migration and evolution support
- Consistent data transformation pipelines

### 3. Development Experience
- Rich error messages for validation failures
- Self-documenting data structures
- Easy testing with schema-based generators

### 4. Maintainability
- Centralized schema definitions
- Automated validation across pipeline
- Clear separation of concerns

## Integration with mtz-uix

The UIX frontend can now consume schema-validated content:

```clojure
;; In mtz-uix
(defn fetch-homepage-content []
  (fetch-json "http://localhost:8080/api/content/schema/page/homepage"))
```

The response is guaranteed to match the `UIXPageContent` schema, providing type safety for frontend components.

## Next Steps

1. **Test Full Pipeline**: Verify end-to-end data flow
2. **Content Fetching**: Implement actual text extraction from Alfresco files
3. **UIX Integration**: Update frontend to use schema endpoints
4. **Monitoring**: Add metrics and alerting for pipeline health
5. **Performance**: Optimize queries and caching strategies

---

*Schema-integrated pipeline implemented: 2025-09-19*
*Mt Zion yakread Project - Alfresco Integration*