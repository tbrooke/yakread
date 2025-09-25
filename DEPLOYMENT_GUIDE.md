# Mount Zion Route-Driven Alfresco Deployment Guide

## 🎯 Overview

The reitit route-driven Alfresco system is **COMPLETE** and ready for deployment! This guide walks you through enabling it safely.

## 📋 Current Status: ✅ READY

- ✅ Route resolution system - COMPLETE
- ✅ Dynamic page handler - COMPLETE  
- ✅ New routes system - COMPLETE
- ✅ Migration safety system - COMPLETE
- ✅ SSH tunnel setup - COMPLETE
- ✅ Connection testing - COMPLETE

## 🚀 Quick Deployment (5 Minutes)

### Step 1: Set up SSH Tunnel to Alfresco

```bash
# Set up SSH tunnel to access remote Alfresco
./setup_alfresco_tunnel.sh

# Test the connection
bb test_alfresco_connection.clj
```

### Step 2: Enable Dynamic Routes

```bash
# Already done by enable_dynamic_routes.clj
# Migration system is now active in your routes.clj
```

### Step 3: Test and Activate

1. **Start your application** (it will use static routes initially)

2. **In a REPL**, load the migration helper:
   ```clojure
   (load-file "enable_dynamic_routes_repl.clj")
   ```

3. **Test routes** before switching:
   ```clojure
   (test-route "/about")     ; Test individual route
   (test-all)               ; Test all main routes
   ```

4. **Enable dynamic routes**:
   ```clojure
   (enable!)                ; Switch to dynamic routes
   ```

5. **If any issues occur**, quickly fallback:
   ```clojure
   (disable!)               ; Fallback to static routes
   ```

## 🏗️ Alfresco Content Setup

Create these folders in Alfresco (via Share interface):

```
Sites/swsdp/documentLibrary/Web Site/
├── about/                    → /about page
├── worship/                  → /worship page  
├── activities/              → /activities page
├── events/                  → /events page
├── contact/                 → /contact page
├── worship/                 
│   ├── services/            → /worship/services page
│   └── music/               → /worship/music page
└── events/
    └── calendar/            → /events/calendar page
```

Upload HTML files to these folders - they'll automatically become web pages!

## 🎊 Revolutionary Benefits

### Before (Manual System)
- Developer creates route definition
- Developer maps route to node-ID manually
- Content team uploads to specific node-ID
- Manual coordination required for new pages

### After (Route-Driven System)  
- Content team creates folder: `Web Site/community-outreach/`
- Content team uploads HTML file to folder
- Page automatically available at `/community-outreach`
- **Zero developer involvement needed!**

## 📡 SSH Tunnel Details

The system connects to your remote Alfresco through an SSH tunnel:

```
Local (http://localhost:8080) 
  ↓ SSH Tunnel
SSH Server (tmb@trust)
  ↓ Docker Network  
Alfresco Container (generated-setup-alfresco-1:8080)
```

**To manage the tunnel:**
```bash
# Start tunnel
./setup_alfresco_tunnel.sh

# Stop tunnel  
pkill -f "ssh.*8080:generated-setup-alfresco-1.*tmb@trust"

# Check tunnel status
pgrep -f "ssh.*8080:generated-setup-alfresco-1.*tmb@trust"
```

## 🔧 Configuration Files

The system automatically updated these files:

- ✅ `src/com/yakread/app/routes.clj` - Migration system activated
- ✅ `src/com/yakread/alfresco/content_processor.clj` - Added `has-images?` function  
- ✅ All route-driven files already existed and tested

## 🧪 Testing Commands

```bash
# Test route conversion logic
bb test_route_conversion.clj

# Test Alfresco connection  
bb test_alfresco_connection.clj

# Test dynamic routes readiness
bb test_dynamic_routes_simple.clj
```

## 🔄 Migration Safety

The system includes comprehensive safety measures:

1. **Feature Flag**: Easy on/off switching
2. **Gradual Migration**: Can enable specific routes only
3. **Quick Rollback**: Instant fallback to static routes
4. **Status Monitoring**: Real-time migration status
5. **Route Validation**: Ensures routes work before switching

## 📊 Monitoring

Check system status anytime:

```clojure
;; In REPL
(require '[com.yakread.app.routes-migration :as migration])
(migration/get-migration-status)

;; Route testing
(require '[com.yakread.alfresco.dynamic-pages :as dynamic])
(dynamic/preview-route-content "/about")
```

## 🚨 Troubleshooting

### SSH Connection Issues
```bash
# Test direct SSH
ssh tmb@trust

# Check if Alfresco container is running
ssh tmb@trust "docker ps | grep alfresco"
```

### Route Resolution Issues
```clojure
;; Test specific folder resolution
(require '[com.yakread.alfresco.route-resolver :as resolver])
(resolver/resolve-route-content 
  {:base-url "http://localhost:8080" :username "admin" :password "admin"}
  "/about")
```

### Fallback Procedure
```clojure
;; Immediate fallback to static routes
(disable!)

;; Check what went wrong
(migration/get-migration-status)
```

## 🎯 Success Metrics

You'll know it's working when:

- ✅ SSH tunnel connects to Alfresco
- ✅ Repository API returns version info  
- ✅ Sites API shows available sites
- ✅ Route tests return folder information
- ✅ Dynamic routes can be enabled/disabled
- ✅ New pages appear by creating folders

## 🚀 Next Steps After Deployment

1. **Train Content Team**: Show them how to create folders = create pages
2. **Add More Routes**: System auto-discovers new folders
3. **Enhanced Features**: 
   - Auto-navigation menu generation
   - Content-model integration  
   - Breadcrumb support
   - Route caching for performance

## 🎉 Congratulations!

Once deployed, you'll have achieved:

- **Single Source of Truth**: Reitit routes drive everything
- **Content Team Empowerment**: Create pages without developers
- **Zero Manual Mapping**: No more node-ID management  
- **Automatic Discovery**: System finds content automatically
- **Safe Migration**: Can switch back and forth safely

The Mount Zion website is now truly **route-driven**! 🎊