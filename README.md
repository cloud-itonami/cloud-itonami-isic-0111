# cloud-itonami-isic-0111

Open Occupation Blueprint for **ISIC Rev. 4 0111**: Growing of cereals (except rice).

This repository implements a forkable OSS **cereal-growing operations
coordinator**: a field-management and record-keeping robot manages planting/yield
logging, field-operation scheduling, and supply procurement under a governor-gated
actor, so a cereal farm keeps its own operational records and maintains full
transparency over decisions.

**Maturity: `:implemented`.** `src/cerealops/` implements the
`CerealOpsAdvisor` (`cerealops.advisor`) and the independent
`FieldOperationsGovernor` (`cerealops.governor`), composed by
`cerealops.operation` following the itonami actor pattern (ADR-2607011000):
`intake -> advise -> govern -> decide -> commit | request-approval -> commit
| hold`, compiled to a real `langgraph-clj` `StateGraph`
(`langgraph.graph/state-graph` + `compile-graph`, mirroring
`marketentry.operation`, cloud-itonami-iso3166-ago) with
`interrupt-before #{:request-approval}` and checkpoint-based
human-in-the-loop resume for escalated operations. Every commit/hold/
approval-rejected decision fact is appended to `cerealops.store`'s
append-only audit ledger (`ledger`/`append-ledger!`), implemented on
both `MemStore` and a `DatomicStore` (backed by `langchain.db` via
`kotoba-lang/langchain-store`) that pass the same store-contract test
(`test/cerealops/store_contract_test.cljc`). 32 tests / 105 assertions
green (`clojure -M:dev:test`); the demo runner
(`clojure -M:dev:run`) drives the compiled graph end-to-end through a
commit path, an escalate→approve→commit path, an escalate→reject→hold
path, and a hard-hold path, printing the resulting audit ledger.

## What this does NOT do

This actor coordinates **back-office logistics only**. It explicitly does **NOT**:

- **Direct field-equipment operation** — remains the farmer's exclusive authority
- **Pesticide-application decisions** — remains the agronomist/farmer authority
- **Agronomic decision authority** (what/when/how much to plant, spray, or harvest) —
  remains human authority; this actor only coordinates the logistics around those
  decisions
- **Direct execution of any kind** — any proposal for direct field-equipment control
  or finalizing a pesticide-application decision is a hard block

## HARD invariants (always hold, never overridable)

1. **field-not-registered** — the request's `field-id` must resolve to a
   registered field in the Store before any proposal can proceed
2. **no-execution** — every proposal's `:effect` must be `:propose` (the governor
   never directly operates field equipment, never finalizes a
   pesticide-application decision)
3. **equipment-or-pesticide-decision-blocked** — `:operate-field-equipment` and
   `:finalize-pesticide-application` proposals are unconditionally, permanently
   blocked
4. **op-not-allowed** — any op outside the closed allowlist below is rejected
5. **field-record-invalid** — `:log-field-record` with a non-positive acreage is
   rejected

## Always-escalate operations (human sign-off, regardless of confidence)

- `:flag-crop-health-concern` — any pest/disease/drought-stress concern →
  automatic escalation
- `:order-supplies` over its category cost threshold (default 500 currency
  units; see `cerealops.facts/supply-categories`)
- Any proposal with confidence below the Governor's floor (0.7)

## Operational requests (closed allowlist, all `:effect :propose`)

```text
:log-field-record
  — record planting/yield/soil-test data
  — requires a registered field; non-positive acreage is rejected

:schedule-field-operation
  — propose a planting/spraying/harvest scheduling operation
  — does NOT make agronomic decisions

:flag-crop-health-concern
  — surface a pest, disease, or drought-stress concern
  — ALWAYS escalates for human review

:order-supplies
  — procurement for seed, fertilizer, equipment
  — escalates if cost exceeds its category threshold
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs the
physical domain work**. Here a field-management robot handles:

- Field record logging and entry
- Field-operation scheduling and reminders
- Supply inventory and ordering
- Audit ledger maintenance

The **FieldOperationsGovernor** is the independent safety layer that gates all
proposals before a robot action is executed. The governor never dispatches
hardware directly; `:high`/`:safety-critical` actions (such as escalated
crop-health concerns or high-cost supply orders) require human sign-off.

## Core Contract

```text
operational request (log, schedule, concern, order)
        |
        v
CerealOpsAdvisor -> FieldOperationsGovernor -> phase gate -> commit, or escalate for human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated operation can dispatch a robot action the governor refuses, suppress an
operating record, or hide a crop-health concern without governor approval and audit
evidence.

## Module structure

Mirrors `cloud-itonami-isic-0141` (`cattleops.*`) module-for-module:

- `cerealops.facts` — reference data: supply-category cost thresholds, cereal crops
- `cerealops.registry` — pure independent verification functions (cost/acreage/confidence)
- `cerealops.store` — `Store` protocol: field registration lookup + append-only audit
  ledger, implemented by `MemStore` (in-memory, default) and `DatomicStore`
  (`langchain.db`-backed, via `kotoba-lang/langchain-store`)
- `cerealops.advisor` — `Advisor` protocol + `MockAdvisor` (the sealed LLM/decision
  node; a real-LLM `Advisor` implementation is the documented next seam, same as
  every sibling cloud-itonami actor's advisor)
- `cerealops.governor` — `FieldOperationsGovernor`: hard invariants + escalation gates
- `cerealops.phase` — 0→3 rollout phase gate
- `cerealops.operation` — compiles the `langgraph-clj` `StateGraph`: advise → govern →
  decide → commit | request-approval → commit | hold, with `interrupt-before` +
  checkpoint-based resume for escalated operations
- `cerealops.sim` — demo runner (`clojure -M:dev:run`)

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISIC Rev. 4 `0111`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Testing

```bash
clojure -M:dev:test   # run the test suite (langgraph/langchain-store resolved via local sibling checkouts)
clojure -M:lint       # clj-kondo, 0 errors / 0 warnings
clojure -M:dev:run    # demo runner -- drives the compiled StateGraph end-to-end
```

`:dev` pins the transitive `langchain` dependency to the in-monorepo local
checkout (`../../kotoba-lang/langchain`) for offline workspace development;
a standalone fork should override `deps.edn`'s `:local/root` coordinates
with git coordinates (see `deps.edn`'s own comment).

## License

AGPL-3.0-or-later.
