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
`advise -> govern -> phase-gate -> commit | escalate | hold`. 30 tests /
96 assertions green (`clojure -M:test`).

`cerealops.operation` is a synchronous stub of this flow (see its
docstring) — production wiring into a `langgraph-clj` StateGraph with
`interrupt-before`/checkpoint-based human-in-the-loop resume for escalated
operations is deferred, mirroring `cloud-itonami-isic-0141`'s own
`cattleops.operation`.

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
- `cerealops.store` — `Store` protocol + in-memory `MemStore` (field registration lookup)
- `cerealops.advisor` — `Advisor` protocol + `MockAdvisor` (the sealed LLM/decision node)
- `cerealops.governor` — `FieldOperationsGovernor`: hard invariants + escalation gates
- `cerealops.phase` — 0→3 rollout phase gate
- `cerealops.operation` — composes advisor → governor → phase into one operation run
- `cerealops.sim` — demo runner (`clojure -M:run`)

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
clojure -M:test   # run the test suite
clojure -M:lint   # clj-kondo, 0 errors / 0 warnings
clojure -M:run    # demo runner
```

## License

AGPL-3.0-or-later.
