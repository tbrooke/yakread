# Content Extraction Refactoring

## Overview

We've successfully extracted the HTTP client functionality from the bloated `sync_feature1.clj` file and created a universal content extraction system for the Mount Zion UCC website.

## What We've Accomplished

### ✅ HTTP Client Extraction
- **Kept `sync_feature1.clj`** as a specific test case for Feature 1 content
- **Enhanced existing `alfresco/client.clj`** with better content extraction 
- **Created universal `alfresco/content_extractor.clj`** for any website folder

### ✅ Universal Content Extraction
The new `content_extractor.clj` provides:

- **Universal folder traversal** - works with any Alfresco folder structure
- **Multiple content type support** - HTML, text, markdown, PDFs, images
- **Publish filtering** - only extract published content (with tags or folder structure)
- **Recursive traversal** - configurable depth limits
- **Content organization** - by page, by component type
- **Health checks** - verify folder accessibility
- **Comprehensive logging** - detailed extraction progress

### ✅ Clean Architecture

#### Before (Bloated):
```
sync_feature1.clj (231 lines)
├── HTTP client (babashka.curl)
├── Content extraction 
├── XTDB simulation
├── File I/O
├── Configuration
└── Main execution
```

#### After (Modular):
```
alfresco/client.clj 
├── HTTP client (clj-http)
├── Authentication
├── Node operations
└── Health checks

alfresco/content_extractor.clj
├── Universal content extraction
├── Folder traversal
├── Content filtering
├── Organization by page/type
└── Health monitoring

sync_feature1.clj (preserved)
├── Specific Feature 1 test
├── Shows original approach
└── Good for workflow testing
```

## Key Benefits

### 🎯 **Universal vs Specific**
- **`sync_feature1.clj`**: Tests specific Feature 1 workflow
- **`content_extractor.clj`**: Works with entire website structure

### 🔧 **Better Infrastructure**
- Uses existing `alfresco/client.clj` infrastructure
- Leverages `config/website_nodes.clj` configuration
- Integrates with existing logging and error handling

### 📊 **Rich Content Organization**
```clojure
;; Extract all website content
(extract-website-content-with-summary ctx)

;; Results organized multiple ways:
{:raw-results {...}           ; By component
 :by-page {...}              ; By target page
 :by-type {...}              ; By content type  
 :summary {...}}             ; Statistics
```

### 🏗️ **Flexible Extraction**
```clojure
;; Extract specific component
(extract-component-content ctx :feature1)

;; Extract published content only
(extract-published-content-only ctx)

;; Extract everything (for admin)
(extract-all-content ctx)
```

## Next Steps

### 1. **XTDB Integration**
Replace file-based simulation with actual XTDB storage:
```clojure
;; Instead of: (spit "file.edn" content)
;; Use: (biff/submit-tx ctx [{:xt/id ... :content/data content}])
```

### 2. **Content Processing Pipeline**
Create processors for different content types:
- HTML → cleaned content + metadata
- Markdown → compiled HTML
- Images → resized versions + metadata

### 3. **Sync Scheduling**
Integrate with existing sync infrastructure:
- Regular content updates
- Change detection
- Incremental sync

### 4. **Component Integration**
Connect extracted content to UI components:
- Dynamic component rendering
- Content-driven page generation

## Usage Examples

### Test the Universal Extractor
```bash
# Run the test script
bb test_universal_content_extraction.clj
```

### Use in Application Code
```clojure
(ns your.namespace
  (:require [com.yakread.alfresco.content-extractor :as extractor]))

;; Extract all website content
(let [results (extractor/extract-website-content-with-summary ctx)]
  ;; Process results for XTDB storage
  ;; Update UI components
  ;; Generate static pages
  )
```

## Configuration

The system uses `config/website_nodes.clj` for folder mappings:
- **Page folders**: `:homepage`, `:about`, `:worship`, etc.
- **Component folders**: `:feature1`, `:feature2`, `:hero`, etc.
- **Easy to extend**: Just add new folder mappings

## Testing Strategy

- **`sync_feature1.clj`**: Keep as specific test for Feature 1 workflow
- **`test_universal_content_extraction.clj`**: Test universal extraction
- **Health checks**: Built-in folder accessibility verification
- **Gradual migration**: Can run both approaches in parallel

This refactoring provides a solid foundation for scalable content management while preserving the original test case for specific workflow validation.