#!/usr/bin/env bb

;; Test the dynamic routes system
;; Verifies route-to-path conversion and content resolution

(require '[clojure.pprint :as pprint])

;; Load the dynamic route system
(load-file "src/com/yakread/alfresco/route_resolver.clj")
(load-file "src/com/yakread/alfresco/dynamic_pages.clj")

(alias 'resolver 'com.yakread.alfresco.route-resolver)
(alias 'dynamic 'com.yakread.alfresco.dynamic-pages)

(println "🔗 Testing Dynamic Routes System")
(println "================================")

;; Test 1: Route-to-path conversion
(println "\n1. Route-to-Path Conversion:")
(println "   /about →" (resolver/route->alfresco-path "/about"))
(println "   /worship/services →" (resolver/route->alfresco-path "/worship/services"))
(println "   / →" (resolver/route->alfresco-path "/"))

;; Test 2: Path-to-route conversion
(println "\n2. Path-to-Route Conversion:")
(println "   Sites/swsdp/documentLibrary/Web Site/about →" 
         (resolver/alfresco-path->route "Sites/swsdp/documentLibrary/Web Site/about"))
(println "   Sites/swsdp/documentLibrary/Web Site →" 
         (resolver/alfresco-path->route "Sites/swsdp/documentLibrary/Web Site"))

;; Test 3: Route validation
(println "\n3. Route Validation:")
(println "   /about valid?" (resolver/route-path-valid? "/about"))
(println "   /about/../admin valid?" (resolver/route-path-valid? "/about/../admin"))
(println "   //about valid?" (resolver/route-path-valid? "//about"))

;; Test 4: Route normalization
(println "\n4. Route Normalization:")
(println "   /about/ →" (resolver/normalize-route-path "/about/"))
(println "   /about//test →" (resolver/normalize-route-path "/about//test"))
(println "   '' →" (resolver/normalize-route-path ""))

;; Test 5: Preview route content (this will attempt actual Alfresco connection)
(println "\n5. Route Content Preview:")
(println "   Testing SSH tunnel connection...")

(try
  (let [preview (dynamic/preview-route-content "/about")]
    (println "   /about preview:")
    (pprint/pprint preview))
  (catch Exception e
    (println "   Error previewing /about:" (.getMessage e))))

(try  
  (let [preview (dynamic/preview-route-content "/worship")]
    (println "   /worship preview:")
    (pprint/pprint preview))
  (catch Exception e
    (println "   Error previewing /worship:" (.getMessage e))))

(println "\n✅ Dynamic Routes System Test Complete")
(println "\nNext Steps:")
(println "1. Update your main app to use routes-v2")
(println "2. Create the corresponding Alfresco folders")
(println "3. Test the full system end-to-end")