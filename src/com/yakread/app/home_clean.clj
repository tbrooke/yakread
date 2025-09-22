(ns com.yakread.app.home-clean
  "Clean home module that delegates to routes"
  (:require
   [com.yakread.app.routes :as routes]))

;; This file now just exports the routes module
;; All route logic has been moved to routes.clj

(def module
  "Simple delegation to routes module"
  routes/module)