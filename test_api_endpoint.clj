#!/usr/bin/env bb

;; Test the mtzUIX Feature 1 API endpoint

(require '[babashka.curl :as curl]
         '[cheshire.core :as json])

(defn test-feature1-endpoint []
  "Test the Feature 1 content API endpoint"
  (println "🧪 Testing mtzUIX Feature 1 API endpoint")
  (println "   URL: http://localhost:8080/api/mtzuix/feature1\n")

  (try
    (let [resp (curl/get "http://localhost:8080/api/mtzuix/feature1"
                         {:headers {"Accept" "application/json"}})]

      (if (= 200 (:status resp))
        (let [data (json/parse-string (:body resp) true)]
          (println "✅ API endpoint working!")
          (println "📊 Response data:")
          (println "   Success:" (:success data))
          (println "   Component:" (:component data))
          (println "   Title:" (get-in data [:data :title]))
          (println "   Content length:" (count (get-in data [:data :html])) "characters")
          (println "   Source:" (get-in data [:data :source :type]))
          (println "   Component ID:" (get-in data [:data :componentId]))
          (println "\n📄 HTML Content preview:")
          (println "   " (subs (get-in data [:data :html]) 0 100) "...")

          {:success true :data data})

        (do
          (println "❌ API endpoint failed")
          (println "   Status:" (:status resp))
          (println "   Body:" (:body resp))
          {:success false :status (:status resp)})))

    (catch Exception e
      (println "❌ Test failed:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn -main []
  (println "Testing mtzUIX API endpoint...")
  (println "Make sure yakread is running on localhost:8080\n")

  (test-feature1-endpoint))

;; Run test
(-main)