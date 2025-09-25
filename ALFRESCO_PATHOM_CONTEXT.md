# Alfresco + Pathom Integration Context

## 📅 Last Updated: 2024-01-15

## 🎯 Current State

We're building a flexible Pathom-based system to handle Alfresco's content models for the Mount Zion UCC website. The goal is to create a system that can adapt to both existing and future content models without requiring code changes.

## 📚 Background Context

### The Challenge
- Alfresco has a complex content model system with custom types, aspects, and dynamic properties
- Mt. Zion is using basic HTML editor in Alfresco for blog posts/articles
- Content authors use inline CSS which needs to be converted to Tailwind classes
- The website uses HTMX, Pathom, and Biff framework

### Key Architectural Decisions
1. **Start Simple**: Begin with basic cm:content before adding complexity
2. **Parse Don't Complicate**: Convert inline CSS to Tailwind in the app, not in Alfresco
3. **Flexible Discovery**: Let Pathom discover content properties dynamically
4. **Small Steps**: Build incrementally rather than over-engineering

## 🏗️ What We've Built So Far

### 1. Content Model Resolver (`src/com/yakread/alfresco/content_model_resolver.clj`)
**Purpose**: Flexible Pathom resolvers that discover and handle any Alfresco content type

**Key Features**:
- `node-type-info` - Discovers content type and aspects
- `node-properties` - Gets all properties regardless of namespace
- Dynamic property resolvers for any namespace (mtz:, custom:, etc.)
- Content type router for processing decisions
- Model introspection for discovering available properties

**Example Usage**:
```clojure
(build-content-graph ctx "node-id-123")
;; Returns complete content graph with all properties
```

### 2. Basic Pathom Content (`src/com/yakread/alfresco/pathom_content.clj`)
**Purpose**: Simple starting resolvers for standard Alfresco content

**Key Features**:
- Basic content properties (name, type, size, dates)
- Content metadata (title, description, author)
- Content body retrieval
- Folder-based content discovery
- Auto-classification based on folder structure

**Current Mt. Zion Structure**:
```
Sites/swsdp/documentLibrary/Web Site/
├── Home Page/
│   ├── Feature 1/
│   ├── Feature 2/  (contains "Blood Drive" HTML)
│   ├── Feature 3/
│   └── Hero/
├── About/
├── Worship/
├── Activities/
└── Events/
```

### 3. Content Model Schemas (`src/com/yakread/alfresco/content_model_schemas.clj`)
**Purpose**: Flexible Malli schemas that validate based on content type

**Key Features**:
- Base schemas for standard types (cm:content, cm:folder)
- Aspect schemas (taggable, versionable, geographic)
- Mt. Zion specific schemas (webContent, event, ministry)
- Dynamic schema builder based on type + aspects
- Schema registry for extensibility

## 🔍 What We Discovered

### Blood Drive Document Analysis
From exploring the Mt. Zion site, we found:
```clojure
{:node-id "7d6d4b12-a339-4f10-ad4b-12a339bf1080"
 :name "Blood Drive"
 :type "cm:content"
 :mime-type "text/html"
 :properties {:cm:title "Blood Drive - Feature 2"
              :cm:description "Blood Drive event feature for Home Page"}}
```

**HTML Content**:
```html
<p style="text-align: center;">&nbsp;</p>
<h2 style="text-align: center; font-family: sans-serif; color: black;">Blood Drive</h2>
<p>&nbsp;</p>
<p><img src="http://domain.com/alfresco/www/E0BEC726-B2F8-4ED4-B95D-BFD5F1AE2C0F.jpg"></p>
<p>&nbsp;</p>
```

### Key Observations
1. Currently using standard `cm:content` type (no custom types yet)
2. HTML editor produces simple inline-styled content
3. Images stored with UUID references
4. Folder structure implies page/section placement

## 🚀 Next Steps

### Immediate (Testing & Discovery)
1. **Test Pathom Resolvers**
   ```clojure
   ;; Connect to real Alfresco
   (def ctx {:alfresco alfresco-config})
   
   ;; Discover properties
   (parser ctx [{[:alfresco/node-id "7d6d4b12-a339-4f10-ad4b-12a339bf1080"]
                 [:model/property-namespaces
                  :model/all-properties]}])
   ```

2. **Explore Existing Content Models**
   - Query different content types in the repository
   - Document custom properties already in use
   - Identify patterns in folder organization

### Short-term (Implementation)
1. **CSS to Tailwind Parser**
   - Map common inline styles to Tailwind classes
   - Handle text-align, font-family, colors
   - Process embedded images

2. **Content Pipeline Integration**
   - Connect Pathom resolvers to existing pipeline
   - Store parsed content in MinIO
   - Update bitemporal tracking

3. **Template System**
   - Create templates for blog posts, articles, features
   - Apply Tailwind classes based on content type
   - Handle responsive design

### Medium-term (Content Model Evolution)
1. **Custom Content Types** (if needed)
   - mtz:blogPost
   - mtz:event
   - mtz:announcement

2. **Aspect Development**
   - SEO properties
   - Event information
   - Social media metadata

## 🔗 Related Documentation

- **Route-Driven Implementation**: `ROUTE_DRIVEN_IMPLEMENTATION.md`
- **MinIO Integration**: `MINIO_INTEGRATION_GUIDE.md`
- **Bitemporal Content**: `BITEMPORAL_CONTENT_GUIDE.md`
- **Original Context**: `MOUNT_ZION_ALFRESCO_INTEGRATION_CONTEXT.md`

## 💡 Key Principles Moving Forward

1. **Keep Content Creation Simple**: Authors use Alfresco's HTML editor as-is
2. **Smart Processing**: Do CSS→Tailwind conversion in Pathom/app layer
3. **Flexible Discovery**: Let the system adapt to content models
4. **Incremental Enhancement**: Start with basics, add features as needed
5. **Small Steps**: Test each piece before building the next

## 🎯 Success Metrics

- ✅ Content authors can create content without learning new tools
- ✅ System adapts to new content types without code changes
- ✅ Clean Tailwind-styled output on the website
- ✅ Pathom graph queries work across all content types
- ✅ Validation catches content issues early

## 📝 Session Notes

- Discussed Jacob O'Bryant's article on structuring codebases with Pathom
- Decided to start with basic content models before tackling CSS parsing
- Created flexible foundation that can grow with Mt. Zion's needs
- Emphasized small steps over big architectural changes

## 🔄 To Resume Next Time

1. Set up Alfresco connection (SSH tunnel if needed)
2. Test the Pathom resolvers with real content
3. Discover what content models/properties exist
4. Build CSS→Tailwind parser
5. Integrate with existing pipeline

The foundation is ready - next session will be about connecting to real data and building the practical pieces!