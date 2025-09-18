# Mt Zion Alfresco Integration - Project Context

**Save this file as: `/Users/tombrooke/Code/trust-server/mtzion/yakread/ALFRESCO_INTEGRATION_CONTEXT.md`**

## Project Overview

Integration of Mt Zion's Alfresco document management system with Yakread, allowing content from the church website folders to be synced and managed within the Yakread interface.

## Current Status: Phase 4A - Schema-Driven Pipeline Architecture

**Date**: September 18, 2025  
**Phase**: Enhanced Schema-Driven Pipeline Design  
**Status**: Implementation Ready - Malli Integration Needed  

## Architecture Evolution

### Phase 1: Complete ✅ (Sep 17)
- Basic API discovery and code generation
- REST API client implementation
- Admin UI scaffolding
- Basic folder sync functionality

### Phase 2: Complete ✅ (Sep 18)
- Content pointer system implemented
- Type detection logic via regex pattern matching
- Component mapping from Alfresco items to UIX components
- Fake data pipeline for offline development

### Phase 3: Complete ✅ (Sep 18)
- Malli schema definitions for Mt Zion content types
- Structured content schemas (News, Events, Contact, Ministry, etc.)
- Content validation framework design
- Page-level content organization schemas

### Phase 4A: IN PROGRESS 🔄
**Enhanced Schema-Driven Pipeline Implementation**

## Schema-Driven Architecture

### Pipeline Flow:
1. **Alfresco** stores content with custom properties/aspects
2. **Yakread** syncs and validates against Malli schemas
3. **XTDB** stores both raw CMS data + validated data
4. **UIX/SSR** renders from validated data props

### Benefits Achieved:
- ✅ **No guessing** - explicit component types via CMS metadata
- ✅ **Validation** - Malli ensures data integrity  
- ✅ **Flexibility** - Same content, different variants/styling
- ✅ **Performance** - SSR for fast initial loads
- ✅ **Developer Experience** - Schema-first development

## Current Implementation Status

### Files Created/Modified:
1. `src/com/yakread/lib/alfresco.clj` - Enhanced with content pointer system
2. `src/com/yakread/api/content.clj` - API endpoints for UIX integration
3. `src/app/schema/mtzion.clj` - Comprehensive Malli schemas
4. Test fixtures and mock data for offline development

### Content Pointer System (IMPLEMENTED):
```clojure
{:xt/id (random-uuid)
 :content/type :content.type/image
 :content/target-page :page/home
 :content/target-component :uix.component/hero-image
 :content/alfresco-id "fake-hero-img-123"
 :content/validated-data {...}  ; ← MISSING INTEGRATION
 :content/raw-alfresco-data {...}}
```

### Type Detection Logic (IMPLEMENTED):
- Regex pattern matching for content type inference
- File extension analysis
- Folder context awareness
- Component mapping rules

### Malli Schema Registry (IMPLEMENTED):
- BaseContent, NewsComponent, EventComponent schemas
- ContactComponent, MinistryComponent schemas
- MediaComponent, DocumentComponent schemas
- ContentPointer and ComponentMapping schemas

## CRITICAL INTEGRATION GAP 🚨

**Missing Bridge**: Content pointers are created but don't use Malli validation.

**Current Flow**:
Alfresco Item → Content Pointer → XTDB (raw data only)

**Needed Flow**: 
Alfresco Item → Content Pointer → **Malli Validation** → XTDB (raw + validated data)

## Immediate Next Steps

### 1. Add Malli Schema Support to Content Pointer System
**File to modify**: `src/com/yakread/lib/alfresco.clj`
**Function to enhance**: `create-content-pointer`

Missing functionality:
- Schema selection based on content type
- Content validation and coercion
- Error handling for validation failures
- Storage of both raw and validated data

### 2. Enhance Alfresco Sync to Read Custom Properties/Aspects
**Current limitation**: Only reading basic file/folder properties
**Needed enhancement**: Extract Alfresco aspects and custom properties

### 3. Add Validation Layer in API Endpoints
**File to modify**: `src/com/yakread/api/content.clj`
**Enhancement needed**: Serve validated data to UIX components

### 4. Create UIX SSR Integration in Yakread
**Future phase**: Server-side rendering with validated content

## Technical Challenges Identified

### 1. Schema Selection Logic
**Problem**: How to automatically determine which Malli schema applies to each Alfresco item?

**Current approach**: Pattern matching with regex (brittle)
**Needed approach**: Alfresco aspects/properties drive schema selection

### 2. Content Data Extraction
**Problem**: Content pointers reference Alfresco items but don't store actual content

**Missing functionality**:
- Image data/URLs
- Text content extraction
- Document metadata
- Media file handling

### 3. Validation Error Handling
**Problem**: What happens when real Alfresco content doesn't match schemas?

**Needed strategies**:
- Graceful degradation
- Content quarantine
- Admin notifications
- Schema evolution support

## Mt Zion Website Structure (Discovered)

### Site Details
- **Site ID**: `swsdp` 
- **Site Name**: "Sample: Web Site Design Project" (actually Mt Zion)
- **Document Library ID**: `8f2105b4-daaf-4874-9e8a-2152569d109b`
- **Website Folder ID**: `21f2687f-7b6c-403a-b268-7f7b6c803a85`

### Page Structure (Maps to Malli Schemas):
- Home Page → `HomePage` schema
- Worship → `WorshipPage` schema
- Activities → Events and Ministry schemas
- Contact → `ContactPage` schema
- News → `NewsPage` schema
- Outreach → Ministry schemas
- Preschool → Ministry schemas
- About → Contact and Ministry schemas

## Development Environment

### Network Connectivity: 
**Status**: Currently offline (SSH ports not open)
**Development approach**: Using fake data and offline development
**Testing strategy**: Mock Alfresco responses with real data structures

### Tooling Setup:
- **ClojureMCP**: Attempted bleeding-edge setup, encountered instability
- **Current approach**: Hybrid development (VS Code/Calva, NVIM, Claude Desktop)
- **Testing**: Using existing Yakread patterns with fake data

## Configuration Required

### Yakread config.edn additions:
```clojure
:alfresco/base-url "http://generated-setup-alfresco-1:8080"
:alfresco/username "admin" 
:alfresco/password "admin"
```

### Module registration in main.clj:
```clojure
com.yakread.app.admin.alfresco/module
com.yakread.api.content/module
```

## Testing Strategy

### Phase 4A Testing:
1. **Schema Validation Testing**: Validate fake Alfresco data against Malli schemas
2. **Content Pointer Creation**: Test enhanced content pointer generation
3. **API Integration**: Verify content API returns validated data
4. **UIX Integration**: Test component rendering with validated data

### Test Data Available:
- Mock Alfresco responses in `fixtures.edn`
- Fake content generation functions
- Sample content pointers for each component type

## Success Metrics for Phase 4A

- [ ] Content pointers include validated data alongside raw Alfresco data
- [ ] Malli schemas successfully validate all test content types
- [ ] API endpoints serve schema-validated content to UIX
- [ ] Validation errors handled gracefully with fallback content
- [ ] Schema registry lookup working for all content types

## Architecture Decisions Made

### Schema-First Approach:
**Decision**: Define Malli schemas before implementing validation logic
**Rationale**: Ensures consistent data structures across the entire pipeline

### Bitemporal Storage:
**Decision**: Use XTDB's bitemporal features for content versioning
**Implementation**: `:xt/valid-from` and `:xt/valid-to` tracking

### Content Pointer Abstraction:
**Decision**: Abstract layer between Alfresco and UIX components
**Benefits**: Decouples CMS from frontend, enables schema validation

### Declarative Component Mapping:
**Decision**: Use data-driven component selection instead of imperative logic
**Goal**: Make component mapping configurable rather than hardcoded

## Next Session Focus

### Immediate Implementation Priority:
1. **Enhance `create-content-pointer` function** with Malli validation
2. **Test schema validation** with existing fake data
3. **Debug and refine** schema selection logic
4. **Verify API integration** serves validated content

### Code Integration Points:
- Bridge `app.schema.mtzion` schemas with `lib.alfresco` content pointers
- Enhance `content-pointer-to-api-format` to serve validated data
- Add error handling for validation failures

### Testing Approach:
- Use existing fake data pipeline for testing
- Validate against all defined Malli schemas
- Test component mapping logic with various content types
- Verify API responses match expected UIX formats

---

**For Development Context**: This integration represents a sophisticated content management pipeline that bridges traditional CMS (Alfresco) with modern web development (UIX/React). The schema-driven approach ensures data integrity while maintaining flexibility for church content management needs.

**Key Innovation**: Using Malli schemas as the contract between Alfresco content and UIX components, with XTDB providing bitemporal storage for content versioning and audit trails.
