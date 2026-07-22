# Operator Guide: Cereal-Growing Operations Coordinator

## Overview

The Cereal-Growing Operations Coordinator is a field-management robot that:

1. **Logs operational data** — planting acreage, yield, soil-test notes
2. **Schedules coordination** — planting/spraying/harvest windows, supply orders
3. **Escalates concerns** — any crop pest/disease/drought-stress issue
4. **Maintains transparency** — audit ledger traces all decisions

The robot is **not** the decision-maker. The farmer/agronomist make all
decisions about agronomic practice, pesticide application, and economic
choices. The robot **proposes** actions and escalates when human input is
needed.

## Operating the Actor

### Prerequisites

1. **Field Registration** — your field must be registered in the system
   before any operation can proceed
2. **Authorized User** — operator must be authenticated and authorized
3. **Clear Request Type** — specify what you're doing:
   - `:log-field-record` — record planting/yield/soil-test data
   - `:schedule-field-operation` — arrange a planting/spraying/harvest window
   - `:flag-crop-health-concern` — report a concern
   - `:order-supplies` — procurement request

### Workflow

1. **Submit Request**
   ```clojure
   {:field-id "field-001"
    :op :log-field-record
    :acreage 120
    :crop "wheat"
    :record-type "planting"}
   ```

2. **Actor Processes** — a compiled `langgraph-clj` `StateGraph`
   (`cerealops.operation/build`, run via `langgraph.graph/run*`):
   - `:intake` — request/context enter the graph
   - `:advise` — `CerealOpsAdvisor` proposes an action (`cerealops.advisor`)
   - `:govern` — `FieldOperationsGovernor` checks hard invariants and escalation gates (`cerealops.governor`)
   - `:decide` — rollout-phase constraints applied on top of the Governor's verdict (`cerealops.phase`)
   - `:request-approval` — reached only on `:escalate`; the graph is checkpointed and
     **paused** here (`interrupt-before`) until a human operator resumes it
   - `:commit` / `:hold` — terminal nodes; every commit/hold/approval-rejected decision
     fact is appended to the Store's audit ledger (`cerealops.store/append-ledger!`)

3. **Outcomes** (`:disposition` on the graph's returned state)
   - **`:commit`** — operation logged, robot proceeds (`:record` is present, a
     `:t :committed` fact lands in the ledger)
   - **`:escalate`** — the graph is paused at `:request-approval` pending human decision
     (audit fact `:t :approval-requested`); resume with
     `(g/run* actor {:approval {:status :approved|:rejected :by operator-id}}
               {:thread-id tid :resume? true})`
   - **`:hold`** — operation blocked, hard violation (audit fact `:t :governor-hold`, cites
     `:violations`) or an approver rejection (`:t :approval-rejected`) — both land in the
     ledger with `:disposition :hold`

### Escalation Scenarios

**Automatic escalation (always human sign-off):**
- `:flag-crop-health-concern` — any pest/disease/drought-stress issue
- Supply orders over cost threshold (default 500 currency units)
- Low confidence operations (< 0.7)

**Hard blocks (no override):**
- `:operate-field-equipment` — direct machinery operation is the farmer's authority
- `:finalize-pesticide-application` — pesticide-application decisions are agronomist/farmer authority
- Missing/unregistered field — must register first

### Resuming Escalated Operations

`cerealops.operation/build` compiles a real `langgraph-clj` `StateGraph`
(`interrupt-before #{:request-approval}`, checkpoint-based resume — mirrors
`marketentry.operation`, cloud-itonami-iso3166-ago). An `:escalate`
disposition means the graph run has been checkpointed and **paused** at
`:request-approval`, not merely "the caller should not commit": no further
node runs until a human operator resumes the SAME thread:

```clojure
;; kick off the operation -- may pause at :request-approval
(g/run* actor {:request request :context context} {:thread-id tid})

;; ... human review happens out of band ...

;; resume with a decision -- the graph continues from the checkpoint
(g/run* actor {:approval {:status :approved :by operator-id}}
        {:thread-id tid :resume? true})
;; or, to reject:
(g/run* actor {:approval {:status :rejected :by operator-id}}
        {:thread-id tid :resume? true})
```

`operation/build`'s default `:checkpointer` is an in-memory
`langgraph.checkpoint/mem-checkpointer` (per-process only); production
deployments should pass a persistent checkpointer (see
`langgraph.checkpoint/datomic-checkpointer`) so a paused operation survives
a process restart.

## Audit & Transparency

Every graph run accumulates an `:audit` vector (advisor-proposal trace,
disposition facts, and — for escalated operations — approval-granted/
approval-rejected facts). The `:commit` and `:hold` terminal nodes append
the resulting decision fact to the Store's append-only ledger
(`cerealops.store/append-ledger!`) themselves — ledger-writing is no longer
a caller responsibility; `(store/ledger store)` is always the authoritative,
immutable record of every commit/hold/approval-rejected decision.

- Every proposal produces a trace, regardless of outcome
- Every hold cites the specific Governor rule(s) violated (`:violations`)
- Every escalation cites its `:reason` (always-escalate op / high cost / low confidence)
- Every committed fact carries `:record` — the operational payload the advisor
  proposed (planting/yield data, schedule, concern, or supply order), so a
  field's full operating history is always a query over `(store/ledger store)`

## Integration

The actor provides a standard protocol (`cerealops.store/Store`) for backend
integration:

- **Field lookup** — `(store/registered-field store field-id)`
- **Field registration** — `(store/add-field store field-id field-data)`
- **Ledger read** — `(store/ledger store)`
- **Ledger append** — `(store/append-ledger! store fact)` (called by the
  compiled graph's `:commit`/`:hold` nodes; not normally called directly)

Implementations include in-memory `MemStore` (default, `cerealops.store`)
and `DatomicStore` (`langchain.db`-backed via `kotoba-lang/langchain-store`,
the same seam point all cloud-itonami actors use) — both pass the same
store-contract test (`test/cerealops/store_contract_test.cljc`).

## Safety Guarantees

- **No unsupervised decisions** — no agronomic or pesticide-application decision is
  made by the robot
- **No suppressed concerns** — crop-health concerns cannot be hidden or delayed
- **No unlogged operations** — every action is recorded in the audit ledger
- **No direct execution** — the governor gates every robot action

The robot is safe because:
1. It never decides — it proposes
2. It always escalates when needed
3. It never hides information
4. Every action is auditable
