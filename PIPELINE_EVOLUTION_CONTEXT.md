# Pipeline Evolution Context Document

## Current Pipeline Architecture

**Mount Zion UCC Website Pipeline:**
```
Alfresco → Alfresco Client → Malli Validation → Metadata → XTDB → Save to Temp Files → HTMX Frontend
```

### Recently Completed: Image Processing Fix
- **Issue**: Alfresco images not showing in frontend due to authentication-required URLs
- **Solution**: Content processor now converts Alfresco URLs to proxy URLs during extraction phase
- **Result**: Images display correctly via `/proxy/image/{node-id}` endpoint

## Future Evolution: 4 Major Improvements

## 1. Route-Driven Alfresco Folder Resolution
**Current State**: Manual mapping between routes and Alfresco folder IDs
```clojure
;; Man#ual configuration
/about → (lookup hardcoded node-id from website-nodes config)
/worship → (lookup hardcoded node-id from website-nodes config)
```

**Vision**: Reitit routing as single source of truth
```clojure
;; Dynamic resolution
/about → website/about (Alfresco folder)
/worship/services → website/worship/services (Alfresco folder)
/events/calendar → website/events/calendar (Alfresco folder)
```

**Benefits**:
- Single source of truth for navigation and content structure
- Add new pages by creating routes + Alfresco folders
- No manual mapping maintenance
- Content teams can create pages by creating folders
- Malli validates folder content matches expected page structure

**Implementation Areas**:
- Route-to-path conversion function
- Generic dynamic page handler
- Folder structure validation
- Navigation menu auto-generation from routes

### 2. Content-Model-Driven Architecture
**Current State**: Hardcoded content types (feature1 → textBlock, feature2 → htmlBlock)

**Vision**: Alfresco XML Content Models drive the entire pipeline
```xml
<!-- Alfresco Content Model -->
<type name="blog:post">
  <property name="blog:title" type="d:text" mandatory="true"/>
  <property name="blog:publishDate" type="d:date"/>
  <property name="blog:content" type="d:content"/>
</type>
```
↓ Converts to ↓
```clojure
;; Auto-generated Malli Schema
(def BlogPost
  [:map 
   [:blog/title :string]
   [:blog/publishDate inst?]
   [:blog/content :string]])
```

**Benefits**:
- Content creators define models in Alfresco UI (no developer needed)
- Automatic Malli schema generation from XML
- Type-safe content throughout entire pipeline
- Custom renderers per content type
- Extensible without code changes

**Implementation Areas**:
- Content model discovery endpoint
- XML to Malli schema conversion
- Content type registry and handlers
- Dynamic renderer selection
- Schema validation integration

### 3. MinIO Storage Layer
**Current State**: File system storage for processed content
```
mtzuix-feature1-content.edn (local file)
mtzuix-feature2-content.edn (local file)
cache/images/*.jpg (local cache)
```

**Vision**: S3-compatible MinIO storage
```
s3://yakread-content/pages/feature1.edn
s3://yakread-content/raw/{node-id}.html
s3://yakread-images/{node-id}.jpg
s3://yakread-schemas/{content-type}.edn
```

**Benefits**:
- S3-compatible (can migrate to AWS later)
- Better for distributed/containerized deployment
- Unified storage for content + images + schemas
- Versioning and backup capabilities
- Web-accessible URLs for direct serving
- Eliminates local file system dependencies

**Implementation Areas**:
- MinIO client integration (already have S3 config)
- Storage key strategy and organization
- Content save/load operations replacement
- Image cache migration
- Schema storage system

### 4. Pure Clojure Component System
**Current State**: HTMX/UIX hybrid with React-like complexity

**Vision**: Pure Clojure hiccup functions for components and layouts
```clojure
;; Components (Pure functions)
(defn blog-post-component [{:keys [title content author date]}]
  [:article.blog-post
   [:header [:h1.title title] [:div.meta author date]]
   [:div.content content]])

;; Layouts (Component frameworks)
(defn page-layout [ctx title & components]
  [:html
   [:head (site-head ctx title)]
   [:body [:div.app (site-header) components (site-footer)]]])

;; Content-model-driven rendering
(def component-registry
  {"blog:post" blog-post-component
   "event:announcement" event-card-component
   "gallery:photoSet" gallery-component})
```

**Benefits**:
- Pure Clojure - no ClojureScript/React complexity
- Functional composition - hiccup functions
- Server-side rendering - fast, SEO-friendly
- Component reusability across pages
- Layout flexibility - mix and match
- Content-model drives component selection
- All benefits of functional programming

**Implementation Areas**:
- Component function library
- Layout framework system
- Component registry and selection
- Integration with content-model system
- HTMX/UIX replacement strategy

## End-to-End Vision

### Complete Pipeline Flow
```
Alfresco XML Content Model
↓ (auto-convert)
Malli Schema
↓ (validate)
Type-Safe Content
↓ (store)
MinIO S3 Storage
↓ (route-driven)
Dynamic Page Handler
↓ (render)
Clojure Components
↓ (compose)
Hiccup Layout
↓ (serve)
Server-Rendered HTML
```

### System Properties
- **Single Source of Truth**: Reitit routes drive Alfresco folder structure
- **Type Safety**: Malli schemas throughout pipeline 
- **Content-Model Driven**: Alfresco XML models control everything
- **Storage Agnostic**: MinIO provides S3-compatible storage
- **Pure Functional**: Clojure components and layouts
- **Developer Friendly**: No build steps, pure Clojure
- **Content Creator Friendly**: Define models and content in Alfresco UI

## Implementation Strategy

Each improvement can be implemented independently:

1. **Route-Driven Folders**: Start with simple route→path mapping
2. **Content Models**: Begin with content model discovery and XML parsing
3. **MinIO Storage**: Replace file operations one at a time
4. **Component System**: Build component library alongside existing system

The final result will be a **pure Clojure, content-model-driven, S3-backed, route-driven web publishing system** powered by Alfresco's enterprise content management capabilities.

---

*Generated: September 23, 2025*
*Context for: Mount Zion UCC Website Pipeline Evolution*