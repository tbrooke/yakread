# Alfresco API Integration for Yakread

## Overview

This document describes the Alfresco Community Edition REST API integration setup for the yakread project. The integration provides automated extraction of API specifications from your Alfresco server and converts them to Clojure-friendly EDN format for use in content pipeline development.

## Server Configuration

**Alfresco Server**: `http://admin.mtzcg.com`
**Edition**: Community Edition
**Authentication**: Basic Auth (admin/admin)
**API Base URL**: `http://admin.mtzcg.com/alfresco/api/-default-/public/alfresco/versions/1`
**API Explorer**: `http://admin.mtzcg.com/api-explorer/`

## Available API Specifications

The integration covers 5 main API areas:

1. **Core API** (`alfresco-core`)
   - 117 endpoints, 169 data definitions
   - Main content management functionality
   - Base path: `/alfresco/api/-default-/public/alfresco/versions/1`

2. **Model API** (`alfresco-model`)
   - 4 endpoints, 14 data definitions
   - Content model definitions and metadata
   - Base path: `/alfresco/api/-default-/public/alfresco/versions/1`

3. **Search API** (`alfresco-search`)
   - 1 endpoint, 39 data definitions
   - Content search functionality
   - Base path: `/alfresco/api/-default-/public/search/versions/1`

4. **Authentication API** (`alfresco-auth`)
   - 2 endpoints, 6 data definitions
   - User authentication and tickets
   - Base path: `/alfresco/api/-default-/public/authentication/versions/1`

5. **Workflow API** (`alfresco-workflow`)
   - 21 endpoints, 33 data definitions
   - Process and task management
   - Base path: `/alfresco/api/-default-/public/workflow/versions/1`

## Scripts and Tools

### Main Update Script: `update_API.clj`

**Purpose**: Consolidated script that fetches all API specifications from Alfresco server, converts them to EDN format, and maintains version history.

**Usage**:
```bash
bb update_API.clj
```

**What it does**:
1. Tests API connectivity to Alfresco server
2. Fetches YAML API specifications from `/api-explorer/definitions/`
3. Converts YAML to EDN format using clj-yaml
4. Creates timestamped directories for versioning
5. Saves metadata about the update process
6. Provides summary of API endpoints and definitions

**Output Structure**:
```
api-specs/
├── update-metadata.edn                    # Update history and metadata
└── YYYY.MM.DD-HHMM/                      # Timestamped version directory
    ├── core.yaml/edn                     # Core API specification
    ├── model.yaml/edn                    # Model API specification
    ├── search.yaml/edn                   # Search API specification
    ├── auth.yaml/edn                     # Authentication API specification
    └── workflow.yaml/edn                 # Workflow API specification
```

### Debug and Development Scripts

- **`debug_response_structure.clj`**: Analyzes raw API response formats
- **`test_swagger_auth.clj`**: Tests different authentication methods
- **`extract_swagger_from_explorer.clj`**: Extracts API specs from HTML
- **`test_yaml_specs.clj`**: Tests individual YAML specification access
- **`yaml_to_edn.clj`**: Standalone YAML to EDN converter

## Integration with Yakread

### Loading API Specifications in Clojure

```clojure
;; Load the latest core API specification
(require '[clojure.edn :as edn])

;; Get the latest version from metadata
(def metadata (edn/read-string (slurp "api-specs/update-metadata.edn")))
(def latest-version (:current-version metadata))

;; Load specific API specs
(def core-api (edn/read-string (slurp (str "api-specs/" latest-version "/core.edn"))))
(def search-api (edn/read-string (slurp (str "api-specs/" latest-version "/search.edn"))))

;; Access API paths and definitions
(keys (:paths core-api))           ; List all available endpoints
(keys (:definitions core-api))     ; List all data type definitions
(get-in core-api [:info :title])   ; API title and info
```

### Example: Working with Node Endpoints

```clojure
;; Example: Extract node management endpoints
(def node-paths
  (->> (:paths core-api)
       (filter #(clojure.string/includes? (name (key %)) "nodes"))
       (into {})))

;; Example: Get node data definition
(def node-entry-def (get-in core-api [:definitions :NodeEntry]))
```

### Using in Yakread Content Pipeline

The EDN specifications can be used to:

1. **Generate HTTP client code** for Alfresco API calls
2. **Validate response data** using Malli schemas
3. **Build content extraction pipelines** with proper type checking
4. **Create mock data generators** for testing
5. **Generate API documentation** for the yakread project

## Tested Endpoints

The following endpoints are confirmed working with basic authentication:

- **Root Node**: `/nodes/-root-` (returns "Company Home")
- **Folder Listing**: `/nodes/-root-/children` (returns 7 folders)
- **Folder Filtering**: `/nodes/-root-/children?where=(isFolder=true)`
- **API Specifications**: All YAML specs accessible via `/api-explorer/definitions/`

## Authentication

Current setup uses:
- **Username**: `admin`
- **Password**: `admin`
- **Method**: HTTP Basic Authentication

⚠️ **Security Note**: These are default credentials and should be changed in production.

## Version Management

Each API update creates a new timestamped directory to maintain history:

- **Format**: `YYYY.MM.DD-HHMM` (e.g., `2025.09.19-0830`)
- **Metadata tracking**: `update-metadata.edn` contains complete update history
- **Rollback capability**: Previous versions remain available
- **Change detection**: Compare timestamps to identify API updates

## Troubleshooting

### Common Issues

1. **Connection Failed**: Check if Alfresco server is running at `http://admin.mtzcg.com`
2. **Authentication Failed**: Verify admin/admin credentials are correct
3. **API Not Found**: Ensure Community Edition REST API is enabled
4. **YAML Parse Error**: Check if API specifications are valid YAML format

### Debug Commands

```bash
# Test basic connectivity
bb -e "(require '[babashka.curl :as curl]) (curl/get \"http://admin.mtzcg.com/alfresco/api/-default-/public/alfresco/versions/1/nodes/-root-\" {:basic-auth [\"admin\" \"admin\"]})"

# Check API Explorer access
bb -e "(require '[babashka.curl :as curl]) (curl/get \"http://admin.mtzcg.com/api-explorer/\" {:basic-auth [\"admin\" \"admin\"]})"

# Validate EDN files
bb -e "(require '[clojure.edn :as edn]) (keys (edn/read-string (slurp \"api-specs/2025.09.19-0830/core.edn\")))"
```

## Next Steps

1. **Implement API client**: Use EDN specifications to build Alfresco HTTP client
2. **Content extraction**: Create yakread modules that fetch content from Alfresco
3. **Schema validation**: Use Malli with API definitions for data validation
4. **Automated updates**: Schedule regular API specification updates
5. **Production authentication**: Replace default credentials with secure auth

## File Locations

- **Main script**: `/Users/tombrooke/Code/trust-server/mtzion/yakread/update_API.clj`
- **API specifications**: `/Users/tombrooke/Code/trust-server/mtzion/yakread/api-specs/`
- **Debug scripts**: `/Users/tombrooke/Code/trust-server/mtzion/yakread/*_*.clj`
- **This documentation**: `/Users/tombrooke/Code/trust-server/mtzion/yakread/ALFRESCO_API_INTEGRATION.md`

---

*Last updated: 2025-09-19*
*Alfresco Community Edition REST API Integration*
*Mt Zion yakread Project*