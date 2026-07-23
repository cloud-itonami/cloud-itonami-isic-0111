(ns cerealops.operation-graph-test
  "Integration tests for `cerealops.operation/build` -- proves the REAL
  compiled `langgraph.graph` StateGraph runs end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / escalate-approve /
  escalate-reject routes. No prior test file in this repo exercised
  `operation/build` at all -- every other test covers
  governor/phase/advisor/store in isolation, which proves those pure
  functions work but not that the graph wiring actually threads them
  together."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [cerealops.operation :as operation]
            [cerealops.store :as store]))

(def ^:private op-context {:actor-id "operator-01" :phase :phase-3})

(defn- exec
  ([actor tid request] (exec actor tid request op-context))
  ([actor tid request context]
   (g/run* actor {:request request :context context} {:thread-id tid})))

(defn- store-with-field []
  (store/mem-store {:initial-fields {"field-1" {:crop "wheat" :acreage 40}}}))

(deftest commit-path-clean-log-field-record-in-phase-3
  (testing "phase-3 is full autonomy -- a clean, registered-field proposal
            commits straight through the REAL compiled graph with no
            interrupt, and the ledger is verified EMPTY before the run
            so the post-run fact is genuinely this run's own effect"
    (let [s (store-with-field)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [result (exec actor "t-commit"
                         {:op :log-field-record :field-id "field-1"
                          :acreage 40 :crop "wheat" :record-type "planting"})
            state (:state result)]
        (is (= :done (:status result)))
        (is (= :commit (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :log-field-record (:op (first ledger)))))))))

(deftest hard-hold-unregistered-field-blocks-before-escalation
  (testing "a proposal against a field-id that was NEVER registered is a
            HARD governor violation -- the real graph routes straight to
            :hold, never pausing for human approval"
    (let [s (store/mem-store)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [result (exec actor "t-hold"
                         {:op :log-field-record :field-id "field-999"
                          :acreage 10})
            state (:state result)]
        (is (= :done (:status result)) "no interrupt -- HARD holds never pause for approval")
        (is (= :hold (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :governor-hold (:t (first ledger))))
          (is (some #{:field-not-registered} (map :rule (:violations (first ledger))))))))))

(deftest hard-hold-equipment-operation-permanently-blocked
  (testing ":operate-field-equipment is a permanent, un-overridable block
            regardless of phase, proven end-to-end through the compiled
            graph"
    (let [s (store-with-field)
          actor (operation/build s)
          result (exec actor "t-equipment"
                       {:op :operate-field-equipment :field-id "field-1"})]
      (is (= :hold (:disposition (:state result))))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (some #{:equipment-or-pesticide-decision-blocked}
                  (map :rule (:violations (first ledger)))))))))

(deftest escalate-then-approve-commits-and-genuinely-consults-advisor
  (testing ":flag-crop-health-concern ALWAYS escalates (Governor
            always-escalate-op, independent of phase) -- the real graph
            GENUINELY interrupts (checkpointed) at :request-approval, and
            the ledger stays EMPTY until a human resumes it. A distinctive,
            randomly generated concern string (impossible to have been
            hardcoded in cerealops.operation) threads through
            :advise -> :govern -> :decide -> :commit"
    (let [distinctive-concern (str "TEST-CONCERN-" (rand-int 1000000000))
          s (store-with-field)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [held (exec actor "t-escalate"
                       {:op :flag-crop-health-concern :field-id "field-1"
                        :concern distinctive-concern})]
        (is (= :interrupted (:status held)))
        (is (= [:request-approval] (:frontier held)))
        (is (empty? (store/ledger s)) "not yet committed -- awaiting human sign-off")
        (let [approved (g/run* actor {:approval {:status :approved :by "agronomist-01"}}
                               {:thread-id "t-escalate" :resume? true})
              approved-state (:state approved)]
          (is (= :done (:status approved)))
          (is (= :commit (:disposition approved-state)))
          (let [ledger (store/ledger s)]
            (is (= 1 (count ledger)))
            (is (= :committed (:t (first ledger))))
            (is (= distinctive-concern (:concern (:record (first ledger))))
                "the committed fact's record carries the INJECTED
                distinctive concern string -- proof the graph genuinely
                threads the Advisor's real proposal through rather than
                hardcoding a pass-string")))))))

(deftest escalate-then-reject-holds
  (testing "a human agronomist rejecting an escalated
            :flag-crop-health-concern routes to :hold via the
            :request-approval node's own decision, and durably records
            the rejection -- not a hand-rolled parallel path"
    (let [s (store-with-field)
          actor (operation/build s)
          _held (exec actor "t-reject"
                      {:op :flag-crop-health-concern :field-id "field-1"
                       :concern "possible rust infection"})
          rejected (g/run* actor {:approval {:status :rejected :by "agronomist-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))))))

(deftest phase-0-forces-escalate-on-otherwise-clean-commit
  (testing "phase-0 (default-phase) is simulation-only -- no proposal ever
            autonomously commits, even a Governor-clean one; proving the
            phase gate, not just the Governor, drives this graph's
            routing"
    (let [s (store-with-field)
          actor (operation/build s)
          held (exec actor "t-phase0"
                     {:op :log-field-record :field-id "field-1" :acreage 40}
                     (assoc op-context :phase :phase-0))]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s)) "not yet committed -- awaiting human sign-off"))))
