# Mt Zion Alfresco Integration - Project Context

**Save this file as: `/Users/tombrooke/Code/trust-server/mtzion/yakread/ALFRESCO_INTEGRATION_CONTEXT.md`**

## Project Overview

Integration of Mt Zion's Alfresco document management system with Yakread, allowing content from the church website folders to be synced and managed within the Yakread interface.

## Current Status: Phase 1 Complete ✅

**Date**: September 17, 2025  
**Phase**: API Discovery & Code Generation Complete  
**Next**: Implementation & Testing  

## What Was Accomplished

### 1. Alfresco API Discovery
- **Successfully connected** to Alfresco Mesh server on desktop
- **Discovered Mt Zion site structure**:
  - Site ID: `swsdp` 
  - Site Name: "Sample: Web Site Design Project" (actually Mt Zion)
  - Document Library ID: `8f2105b4-daaf-4874-9e8a-2152569d109b`
  - **Website Folder ID**: `21f2687f-7b6c-403a-b268-7f7b6c803a85` ⭐
  - **Website Subfolders**: Home Page, Worship, Activities, Contact, News, Outreach, Preschool, About

### 2. Alfresco Server Details
- **Base URL**: `http://generated-setup-alfresco-1:8080` (internal network only)
- **Version**: Alfresco Community 25.2.0.0
- **Repository ID**: `81ef2d03-6e8f-4860-af2d-036e8fe86043`
- **Authentication**: admin/admin
- **Challenge**: Internal hostname requires SSH tunnel or port mapping for external access

### 3. Code Artifacts Generated

Four major code artifacts were created following Jacob's Yakread patterns:

#### A. Alfresco Client Library
**File**: `src/com/yakread/lib/alfresco.clj`
- Complete REST API client
- CMIS protocol support  
- Error handling and validation
- High-level integration functions
- Health checks and connectivity testing

**Key Functions**:
- `get-mtzion-website-structure` - Main integration point
- `sync-folder-to-yakread` - Convert Alfresco folders to Yakread items
- `health-check` - Connectivity validation

#### B. Admin Routes & UI
**File**: `src/com/yakread/app/admin/alfresco.clj`
- Web interface for Alfresco management
- Dashboard with connection status
- Manual sync controls
- Real-time sync results display
- Follows Yakread's UI patterns

**Routes**:
- `/admin/alfresco` - Main dashboard
- `/admin/alfresco/sync-folder/:folder-id` - Sync specific folder
- `/admin/alfresco/sync-all` - Sync all Mt Zion folders

#### C. Test Suite
**File**: `test/com/yakread/lib/alfresco_test.clj`
- Follows Jacob's `lib.test/fn-examples` pattern
- Comprehensive REST and CMIS API tests
- Integration tests for Yakread compatibility
- Mock data based on actual Alfresco structure

#### D. Test Fixtures
**File**: `test/com/yakread/lib/alfresco_test/fixtures.edn`
- Mock responses matching real Alfresco data
- Configuration examples
- Expected Yakread item structures

## Yakread Integration Points

### Configuration Required
Add to `config.edn`:
```clojure
:alfresco/base-url "http://generated-setup-alfresco-1:8080"
:alfresco/username "admin" 
:alfresco/password "admin"
```

### Module Registration
Add to `main.clj` modules vector:
```clojure
com.yakread.app.admin.alfresco/module
```

### Dependencies
No new dependencies required - uses existing:
- `clj-http.client` for HTTP requests
- `clojure.data.json` for JSON handling
- Existing Biff and XTDB infrastructure

## Network Connectivity Challenge ⚠️

**Problem**: Internal hostname `generated-setup-alfresco-1:8080` not accessible externally

**Solutions** (choose one):
1. **SSH Tunnel**: `ssh -L 8080:generated-setup-alfresco-1:8080 user@dev-server`
2. **Docker Port Mapping**: Add `"8080:8080"` to docker-compose.yml
3. **Test from internal network**: Run from same network as Alfresco

## Next Development Phases

### Phase 2: XTDB BiTemporal Storage 🎯
**Goal**: Leverage XTDB's bitemporal capabilities for content versioning

**Key Concepts**:
- **Valid Time**: When content existed in Alfresco 
- **Transaction Time**: When synced to Yakread
- Historical queries and audit trails
- Rollback capabilities

**Implementation Areas**:
- Temporal timestamp management
- Content version tracking  
- Sync operation auditing

### Phase 3: UI Components & CSS 🎨
**Goal**: Reuse Jacob's existing UI components for consistency

**Approach**:
- Extend existing `lib.ui` patterns
- Maintain Yakread design aesthetic
- Progressive disclosure for complex features

**Components to Leverage**:
- Card layouts for folder display
- Status badges for sync health
- Form patterns for configuration
- Button styles for actions

### Phase 4: ClojureScript UIX Frontend ⚡
**Goal**: Maximum flexibility with maximum simplicity

**Features Planned**:
- Real-time sync status updates
- Hierarchical folder tree views
- Time-aware content browsing
- Search across temporal data
- Intuitive drag-and-drop organization

## Development Strategy

### Testing Approach
1. **Copy-paste generated code** for speed
2. **Expect issues** (imports, syntax, integration)
3. **Debug with hybrid approach**:
   - Claude + MCP REPL for logic issues
   - Calva/ECA in editor for syntax fixes
   - Real environment testing

### Implementation Order
1. ✅ **API Discovery & Modeling** - Complete
2. 🔄 **Basic Integration** - Copy files, resolve connectivity
3. 🔄 **Manual Sync Testing** - Verify folder sync works
4. ⏳ **XTDB Temporal Features** - Add bitemporal storage
5. ⏳ **UI Enhancement** - Improve frontend experience
6. ⏳ **Background Sync** - Automated polling/webhooks

## Key Files to Create

When implementing, create these files in Yakread project:

```
src/com/yakread/lib/alfresco.clj              # API client
src/com/yakread/app/admin/alfresco.clj         # Routes & UI  
test/com/yakread/lib/alfresco_test.clj         # Test suite
test/com/yakread/lib/alfresco_test/fixtures.edn # Test data
```

## Debug Information

### Known Yakread Patterns (discovered via analysis)
- Uses `lib.test/fn-examples` for testing
- HTTP client via `clj-http.client`
- UI via `lib.ui` namespace  
- Middleware via `lib.mid`
- Admin routes under `/admin/*`
- XTDB for data persistence

### Alfresco Endpoint Examples
```bash
# Repository info
GET /alfresco/api/-default-/public/alfresco/versions/1/repositories/-default-

# Site contents  
GET /alfresco/api/-default-/public/alfresco/versions/1/sites/swsdp/containers

# Folder children
GET /alfresco/api/-default-/public/alfresco/versions/1/nodes/21f2687f-7b6c-403a-b268-7f7b6c803a85/children

# CMIS query
GET /alfresco/api/-default-/public/cmis/versions/1.1/browser/query?q=SELECT%20*%20FROM%20cmis:folder
```

## Success Metrics

### Phase 2 (Implementation) Success:
- [ ] Admin dashboard accessible at `/admin/alfresco`
- [ ] Green "Connected" status badge visible
- [ ] Mt Zion website folders displayed
- [ ] Manual sync creates Yakread items
- [ ] No errors in application logs

### Phase 3+ Success:
- [ ] Automatic background sync working
- [ ] Temporal queries returning historical data
- [ ] UI responsive and intuitive
- [ ] Search integration functional

## Architecture Decisions

### Why This Approach
- **Leverages XTDB strengths**: Bitemporal data perfect for content versioning
- **Follows established patterns**: Consistency with existing Yakread codebase  
- **Modular design**: Easy to enhance and maintain
- **Security conscious**: Keeps Alfresco on internal network

### Trade-offs Made
- **Network complexity**: Internal-only access requires setup
- **Manual sync initially**: Background automation comes later
- **REST over native**: Using HTTP rather than direct integration

## Contact Context

This integration was developed through conversation with Claude (Anthropic) on September 17, 2025. The human (Tom Brooke) is working on Mt Zion church website integration with their existing Yakread application.

**Yakread Location**: `/Users/tombrooke/Code/trust-server/mtzion/yakread/`  
**Alfresco**: Running via Docker on internal development network  
**Goal**: Seamless content management bridging Alfresco CMS and Yakread's interface

---

**For Claude/ClaudeCode/ECA**: This context should provide everything needed to continue development. The generated code artifacts are complete and follow established patterns, but will need testing and debugging in the real environment. Focus on connectivity first, then basic sync functionality, then temporal features.
