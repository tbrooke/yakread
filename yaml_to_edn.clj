#!/usr/bin/env bb

;; Convert YAML API specifications to EDN format

(require '[clj-yaml.core :as yaml]
         '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.pprint :as pprint])

(def input-dir "generated-model")
(def yaml-files ["alfresco-core.yaml" "alfresco-model.yaml" "alfresco-search.yaml"])

(defn yaml-file->edn [yaml-filename]
  (let [yaml-path (str input-dir "/" yaml-filename)
        edn-filename (clojure.string/replace yaml-filename #"\.yaml$" ".edn")
        edn-path (str input-dir "/" edn-filename)]

    (println (str "Converting " yaml-filename " to " edn-filename "..."))

    (try
      ;; Read and parse YAML
      (let [yaml-content (slurp yaml-path)
            parsed-data (yaml/parse-string yaml-content)]

        ;; Write as EDN
        (with-open [writer (io/writer edn-path)]
          (pprint/pprint parsed-data writer))

        (println "✅ Successfully converted" yaml-filename)

        ;; Show some basic info about the converted file
        (println "   API Title:" (get-in parsed-data [:info :title]))
        (println "   Base Path:" (get parsed-data :basePath))
        (println "   Paths count:" (count (get parsed-data :paths {})))
        (println "   Definitions count:" (count (get parsed-data :definitions {}))))

      (catch Exception e
        (println "❌ Failed to convert" yaml-filename ":" (.getMessage e))))))

(defn -main []
  (println "=== Converting YAML API Specs to EDN ===")

  ;; Check if input directory exists
  (if (.exists (io/file input-dir))
    (do
      (println "Input directory:" input-dir)

      ;; Convert each YAML file
      (doseq [yaml-file yaml-files]
        (let [yaml-path (str input-dir "/" yaml-file)]
          (if (.exists (io/file yaml-path))
            (yaml-file->edn yaml-file)
            (println "⚠️  File not found:" yaml-path))))

      (println "\n=== Conversion Complete ===")
      (println "EDN files created in:" input-dir)
      (println "Use these EDN files in your Clojure/yakread project for API integration"))

    (println "❌ Input directory not found:" input-dir)))

(-main)