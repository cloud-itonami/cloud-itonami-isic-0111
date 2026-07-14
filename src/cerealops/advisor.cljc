(ns cerealops.advisor
  "CerealOpsAdvisor -- the contained LLM/decision node. This actor's
  intelligence layer proposes back-office coordination actions (field
  record logging, planting/spraying/harvest scheduling, crop-health
  concern flags, supply procurement) based on field state and operator
  input. The advisor is SEALED into the `:advise` step of the operation
  flow; every proposal is routed through the independent Governor before
  committing.

  The advisor makes proposals but has NO direct authority. Proposals are
  always censored by:
    1. Governor (field registration, closed-op allowlist,
       cost/crop-health gates)
    2. Phase gate (rollout stage)
    3. Human operator (for escalated actions)

  Current implementation is a mock advisor for testing. Production should
  use langchain/Claude or similar LLM backend (same seam point as
  `cattleops.advisor`, cloud-itonami-isic-0141)."
  )

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with :op, :effect,
    :value, :cites, :summary, :confidence (plus any op-specific top-level
    keys the Governor independently verifies, e.g. :acreage/:cost)."))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op field-id]} request]
      (case op
        :log-field-record
        {:op :log-field-record
         :effect :propose
         :acreage (:acreage request 0)
         :value {:field-id field-id
                 :acreage (:acreage request 0)
                 :crop (:crop request "unspecified")
                 :record-type (:record-type request "planting")}
         :cites ["operator-submitted-field-data"]
         :summary "Field planting/yield/soil-test record entry logged from operator submission"
         :confidence 0.9}

        :schedule-field-operation
        {:op :schedule-field-operation
         :effect :propose
         :value {:field-id field-id
                 :operation-type (:operation-type request "planting")
                 :requested-date (:requested-date request)
                 :reason (:reason request "routine-schedule")}
         :cites ["operator-scheduling-request"]
         :summary "Field operation (planting/spraying/harvest) proposed per operator request"
         :confidence 0.85}

        :flag-crop-health-concern
        {:op :flag-crop-health-concern
         :effect :propose
         :concern (:concern request "unspecified concern")
         :value {:field-id field-id
                 :concern (:concern request "unspecified concern")
                 :recommended-action "agronomist-review"}
         :cites ["operator-observation"]
         :summary "Crop pest/disease/drought-stress concern flagged for agronomist/farmer review"
         :confidence 0.8}

        :order-supplies
        {:op :order-supplies
         :effect :propose
         :cost (:cost request 0)
         :value {:field-id field-id
                 :category (:category request "seed")
                 :cost (:cost request 0)}
         :cites ["operator-procurement-request"]
         :summary "Supply order (seed/fertilizer/equipment) proposed for field"
         :confidence 0.85}

        ;; fallback -- unrecognized op. The Governor's closed allowlist
        ;; independently rejects this regardless of what the advisor says.
        {:op op
         :effect :propose
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :field-id (:field-id request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
