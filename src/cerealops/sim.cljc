(ns cerealops.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a registered field
  through a clean phase-3 auto-commit, an always-escalate crop-health
  concern (human approves), a high-cost supply order (human rejects),
  and a hard-hold (unregistered field), then prints the resulting
  audit ledger. Mirrors `marketentry.sim` (cloud-itonami-iso3166-ago)."
  (:require [langgraph.graph :as g]
            [cerealops.operation :as operation]
            [cerealops.store :as store]))

(def farmer {:actor-id "farmer-01" :role :farm-operator :phase :phase-3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "farmer-01"}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "farmer-01"}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through a commit path, an
  escalate->approve->commit path, an escalate->reject->hold path, and
  a hard-hold path; print each result and the final audit ledger."
  []
  (let [st (store/mem-store
            {:initial-fields
             {"field-001"
              {:id "field-001"
               :name "Test Farm North Field"
               :crop "wheat"}}})
        actor (operation/build st)]

    (println "=== Cereal-Growing Operations Coordinator Demo ===")

    (println "\n== log-field-record field-001 (phase-3, governor-clean -> commit) ==")
    (println (exec-op actor "t1"
                      {:op :log-field-record :field-id "field-001"
                       :acreage 120 :crop "wheat" :record-type "planting"}
                      farmer))

    (println "\n== flag-crop-health-concern field-001 (ALWAYS escalates -- farmer approves) ==")
    (let [r (exec-op actor "t2"
                     {:op :flag-crop-health-concern :field-id "field-001"
                      :concern "疫病の可能性"}
                     farmer)]
      (println r)
      (println "-- farmer/agronomist approves --")
      (println (approve! actor "t2")))

    (println "\n== order-supplies field-001 over cost threshold (escalates -- farmer rejects) ==")
    (let [r (exec-op actor "t3"
                     {:op :order-supplies :field-id "field-001"
                      :category "seed" :cost 900}
                     farmer)]
      (println r)
      (println "-- farmer rejects --")
      (println (reject! actor "t3")))

    (println "\n== log-field-record field-999 (unregistered -> HARD hold, no interrupt) ==")
    (println (exec-op actor "t4"
                      {:op :log-field-record :field-id "field-999"
                       :acreage 50 :crop "maize"}
                      farmer))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger st)] (println f))

    {:ledger (store/ledger st)}))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
  )
