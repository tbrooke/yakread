(ns com.yakread.lib.alfresco-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.yakread.lib.alfresco :as sut]
            [com.yakread.lib.test :as lib.test]
            [clj-http.client :as http]
            [clojure.data.json :as json]))

;; Test data based on your Alfresco investigation
(def test-config
  {:base-url "http://generated-setup-alfresco-1:8080"
   :username "admin"
   :password "admin"})

(def mock-repository-info
  {:entry {:id "81ef2d03-6e8f-4860-af2d-036e8fe86043"
           :edition "Community"
           :version {:major "25"
                     :minor "2" 
                     :patch "0"
                     :hotfix "0"
                     :schema "20100"
                     :label "r8f1631fb-blocal"
                     :display "25.2.0.0 (r8f1631fb-blocal) schema 20100"}
           :statusReadOnly false
           :auditEnabled true}})

(def mock-sites-response
  {:list {:pagination {:count 1
                      :hasMoreItems false
                      :totalItems 1
                      :skipCount 0
                      :maxItems 100}
         :entries [{:entry {:id "swsdp"
                           :guid "b4cff62a-664d-4d45-9302-98723eac1319"
                           :title "Sample: Web Site Design Project"
                           :description "A sample site used for demo purposes"
                           :visibility "PUBLIC"
                           :preset "site-dashboard"
                           :role "SiteConsumer"}}]}})

(def mock-document-library-response
  {:list {:pagination {:count 9
                      :hasMoreItems false
                      :totalItems 9
                      :skipCount 0
                      :maxItems 100}
         :entries [{:entry {:id "21f2687f-7b6c-403a-b268-7f7b6c803a85"
                           :name "Web Site"
                           :nodeType "cm:folder"
                           :isFolder true
                           :isFile false
                           :createdAt "2025-09-15T15:33:50.843+0000"
                           :modifiedAt "2025-09-17T10:59:51.663+0000"
                           :createdByUser {:id "admin" :displayName "Administrator"}
                           :modifiedByUser {:id "Tom Brooke" :displayName "Tom Brooke"}}}
                  {:entry {:id "65a6ea88-5ee2-4fbb-a6ea-885ee21fbb93"
                           :name "Worship"
                           :nodeType "cm:folder"
                           :isFolder true
                           :isFile false}}
                  {:entry {:id "fa91ac07-b236-4f86-91ac-07b2365f8602"
                           :name "Youth"
                           :nodeType "cm:folder"
                           :isFolder true
                           :isFile false}}]}})

(def mock-website-folder-response
  {:list {:pagination {:count 8
                      :hasMoreItems false
                      :totalItems 8
                      :skipCount 0
                      :maxItems 100}
         :entries [{:entry {:id "9faac48b-6c77-4266-aac4-8b6c7752668a"
                           :name "Home Page"
                           :nodeType "cm:folder"
                           :isFolder true
                           :isFile false
                           :createdAt "2025-09-17T10:50:42.969+0000"}}
                  {:entry {:id "2cf1aac5-8577-499e-b1aa-c58577a99ea0"
                           :name "Worship"
                           :nodeType "cm:folder"
                           :isFolder true
                           :isFile false
                           :createdAt "2025-09-17T10:54:49.129+0000"}}
                  {:entry {:id "bb44a590-1c61-416b-84a5-901c61716b5e"
                           :name "Activities"
                           :nodeType "cm:folder"
                           :isFolder true
                           :isFile false
                           :createdAt "2025-09-17T10:58:33.531+0000"}}]}})

(def mock-cmis-repository-info
  {:repositoryId "81ef2d03-6e8f-4860-af2d-036e8fe86043"
   :repositoryName "Main Repository"
   :repositoryDescription "Main Repository"
   :vendorName "Alfresco"
   :productName "Alfresco Repository (Community)"
   :productVersion "25.2.0.0 (r8f1631fb-blocal) schema 20100"
   :rootFolderId "9ebe4a64-94c1-481f-be4a-6494c1181f91"
   :capabilities {:contentStreamUpdatability "anytime"
                 :changes "none"
                 :renditions "read"}})

;; Test examples following Jacob's pattern
(def rest-api-examples
  (lib.test/fn-examples
   [[#'sut/get-repository-info nil]
    [{:doc "get repository info"
      :ctx {:biff.test/fixtures {:config test-config}}
      :expected {:status 200
                :body mock-repository-info}}]]
   
   [[#'sut/list-sites nil]
    [{:doc "list all sites"
      :ctx {:biff.test/fixtures {:config test-config}}
      :expected {:status 200
                :body mock-sites-response}}]]
   
   [[#'sut/get-site-containers "swsdp"]
    [{:doc "get site containers for Mt Zion site"
      :ctx {:biff.test/fixtures {:config test-config}}
      :expected {:status 200
                :body {:list {:entries [{:entry {:folderId "8f2105b4-daaf-4874-9e8a-2152569d109b"
                                               :folderName "documentLibrary"}}]}}}}]]
   
   [[#'sut/list-folder-children "8f2105b4-daaf-4874-9e8a-2152569d109b"]
    [{:doc "list document library contents"
      :ctx {:biff.test/fixtures {:config test-config}}
      :expected {:status 200
                :body mock-document-library-response}}]]
   
   [[#'sut/list-folder-children "21f2687f-7b6c-403a-b268-7f7b6c803a85"]
    [{:doc "list Web Site folder contents"
      :ctx {:biff.test/fixtures {:config test-config}}
      :expected {:status 200
                :body mock-website-folder-response}}]]
   
   [[#'sut/search-nodes "TYPE:\"cm:content\" AND PATH:\"/app:company_home/st:sites/cm:swsdp/cm:documentLibrary/cm:Web_x0020_Site//*\""]
    [{:doc "search for content in Web Site folder"
      :ctx {:biff.test/fixtures {:config test-config}}
      :expected {:status 200
                :body {:list {:pagination {:count 0}
                             :entries []}}}}]]))

(def cmis-api-examples
  (lib.test/fn-examples
   [[#'sut/cmis-get-repository-info nil]
    [{:doc "get CMIS repository info"
      :ctx {:biff.test/fixtures {:config test-config}}
      :expected {:status 200
                :body mock-cmis-repository-info}}]]
   
   [[#'sut/cmis-query "SELECT * FROM cmis:folder WHERE IN_FOLDER('21f2687f-7b6c-403a-b268-7f7b6c803a85')"]
    [{:doc "CMIS query for Web Site folder children"
      :ctx {:biff.test/fixtures {:config test-config}}
      :expected {:status 200
                :body {:results [{:properties {"cmis:objectId" "9faac48b-6c77-4266-aac4-8b6c7752668a"
                                              "cmis:name" "Home Page"
                                              "cmis:objectTypeId" "cmis:folder"}}
                                {:properties {"cmis:objectId" "2cf1aac5-8577-499e-b1aa-c58577a99ea0"
                                              "cmis:name" "Worship"
                                              "cmis:objectTypeId" "cmis:folder"}}]}}}]]
   
   [[#'sut/cmis-get-object "21f2687f-7b6c-403a-b268-7f7b6c803a85"]
    [{:doc "get Web Site folder object via CMIS"
      :ctx {:biff.test/fixtures {:config test-config}}
      :expected {:status 200
                :body {:properties {"cmis:objectId" "21f2687f-7b6c-403a-b268-7f7b6c803a85"
                                   "cmis:name" "Web Site"
                                   "cmis:objectTypeId" "cmis:folder"
                                   "cmis:createdBy" "admin"
                                   "cmis:creationDate" "2025-09-15T15:33:50.843+0000"
                                   "cmis:lastModifiedBy" "Tom Brooke"
                                   "cmis:lastModificationDate" "2025-09-17T10:59:51.663+0000"}}}}]]))

(def integration-examples
  (lib.test/fn-examples
   [[#'sut/get-mtzion-website-structure nil]
    [{:doc "get complete Mt Zion website folder structure"
      :ctx {:biff.test/fixtures {:config test-config}}
      :expected {:site-id "swsdp"
                :site-name "Sample: Web Site Design Project"
                :document-library-id "8f2105b4-daaf-4874-9e8a-2152569d109b"
                :website-folder-id "21f2687f-7b6c-403a-b268-7f7b6c803a85"
                :website-folders [{:id "9faac48b-6c77-4266-aac4-8b6c7752668a"
                                  :name "Home Page"
                                  :type "folder"
                                  :path "/Sites/swsdp/documentLibrary/Web Site/Home Page"}
                                 {:id "2cf1aac5-8577-499e-b1aa-c58577a99ea0"
                                  :name "Worship"
                                  :type "folder"
                                  :path "/Sites/swsdp/documentLibrary/Web Site/Worship"}
                                 {:id "bb44a590-1c61-416b-84a5-901c61716b5e"
                                  :name "Activities"
                                  :type "folder"
                                  :path "/Sites/swsdp/documentLibrary/Web Site/Activities"}]}}]]
   
   [[#'sut/sync-folder-to-yakread "21f2687f-7b6c-403a-b268-7f7b6c803a85"]
    [{:doc "sync Alfresco folder content to Yakread items"
      :ctx {:biff.test/fixtures {:config test-config
                               :user-id "user123"}}
      :expected {:synced-folders 8
                :synced-documents 0
                :created-items []
                :updated-items []}}]]))

(defn get-context []
  {:biff.test/current-ns (lib.test/current-ns)
   :biff.test/examples (concat rest-api-examples
                              cmis-api-examples
                              integration-examples)})

(deftest examples
  (lib.test/check-examples! (get-context)))

(comment
  ;; Use this to write the examples to files
  (lib.test/write-examples! (get-context))
  
  ;; Individual test calls for development
  (sut/get-repository-info test-config)
  (sut/list-sites test-config)
  (sut/get-mtzion-website-structure test-config)
  )
