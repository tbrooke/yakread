(ns com.yakread.app.home)

;; This file previously contained Yakread authentication functionality
;; which is not needed for the Mount Zion UCC website.
;; 
;; Admin access will be handled through:
;; - Internal network access behind nginx, or
;; - Single Sign-On (SSO) with Alfresco
;;
;; Mount Zion UCC website routes are now in com.yakread.app.routes

(def module
  {:routes []})