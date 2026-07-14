(ns cerealops.sim
  "Simple simulation/demo runner for the Cereal-Growing Operations
  Coordinator actor. Used to validate that the actor flow compiles and
  basic proposal flow works. Mirrors `cattleops.sim`
  (cloud-itonami-isic-0141)."
  (:require [cerealops.operation :as operation]
            [cerealops.store :as store]))

(defn demo
  "Run a simple demo scenario: register a field, propose a field-record
  log, and check the disposition flow."
  []
  (let [;; Create store with a registered field
        st (store/mem-store
            {:initial-fields
             {"field-001"
              {:id "field-001"
               :name "Test Farm North Field"
               :crop "wheat"}}})

        ;; Build actor
        actor (operation/build st)

        ;; Create a request to log a field record
        request {:op :log-field-record
                 :field-id "field-001"
                 :acreage 120
                 :crop "wheat"
                 :record-type "planting"}

        ;; Context with phase 0 (simulation)
        context {:actor-id "cereal-ops-01"
                 :role :farm-operator
                 :phase :phase-0}]

    (println "=== Cereal-Growing Operations Coordinator Demo ===")
    (println "Demo field: field-001")
    (println "Request: log-field-record")
    (println "Phase: phase-0 (simulation)")
    (println "Expected: escalate (phase-0 forces human review of all commits)")
    (println)
    (let [result (actor request context)]
      (println "Result disposition:" (:disposition result))
      result)))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
)
