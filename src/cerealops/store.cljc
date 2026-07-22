(ns cerealops.store
  "SSoT for the cereal-growing operations coordinator, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior cloud-itonami actor in this fleet uses (mirrors
  `marketentry.store`, cloud-itonami-iso3166-ago; `energy.store`,
  cloud-itonami-isic-3512):

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/cerealops/store_contract_test.cljc).

  A registered field is the minimal unit of authority: a farm's field must
  be registered before ANY proposal referencing it can be considered by
  the Governor (see `cerealops.governor`'s `field-registered` invariant).
  Field data is opaque to this namespace -- callers/backends decide what a
  field record contains (name, location, crop, acreage, etc); this Store
  only answers \"is this field-id registered, and if so what's on file\".
  Because the field payload shape is intentionally open (unlike
  `marketentry.engagement`/`energy.site`, which have a fixed field set),
  `DatomicStore` stores it as a single opaque EDN-blob attribute
  (`:field/payload`, via `langchain-store.core`'s `enc`/`dec*`) rather than
  expanding it into per-key Datomic attributes -- the same blob convention
  every sibling DatomicStore already uses for its own opaque payloads
  (e.g. `marketentry.store`'s `:assessment/payload`).

  The append-only audit ledger (`ledger`/`append-ledger!`) is this actor's
  core missing plumbing until now: `cerealops.operation`'s `:commit`/
  `:hold` graph nodes append every committed/held/approval-rejected
  decision fact here, so a field's operating history (every
  `:log-field-record` / `:schedule-field-operation` /
  `:flag-crop-health-concern` / `:order-supplies` decision) is always a
  query over an immutable log -- the same discipline every sibling actor's
  ledger provides. The ledger stays append-only on every backend."
  (:require [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (registered-field [store field-id]
    "Retrieve a registered field record by ID. Returns nil if the
    field-id is nil or not registered.")
  (add-field [store field-id field-data]
    "Register or update a field in the store. Used by tests, simulation,
    and operator onboarding.")
  (ledger [store]
    "The append-only audit ledger: every committed/held/approval-rejected
    decision fact, in append order.")
  (append-ledger! [store fact]
    "Append one immutable decision fact to the ledger. Returns fact."))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [fields ledger-atom]
  Store
  (registered-field [_store field-id]
    (when field-id
      (get @fields field-id)))
  (add-field [_store field-id field-data]
    (swap! fields assoc field-id field-data)
    field-data)
  (ledger [_store] @ledger-atom)
  (append-ledger! [_store fact]
    (swap! ledger-atom conj fact)
    fact))

(defn mem-store
  "Create an in-memory store. `initial-fields` is an optional map of
  field-id -> field-record."
  [& [{:keys [initial-fields] :or {initial-fields {}}}]]
  (MemStore. (atom initial-fields) (atom [])))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  `:field/payload` is stored as an EDN string blob (via
  `langchain-store.core`) so `langchain.db` doesn't try to expand an
  opaque, caller-defined field record into sub-entities. The identity-
  schema builder, EDN-blob codec and seq-keyed event-log read/append are
  the shared kotoba-lang/langchain-store machinery (ADR-2607141600) --
  the seam ~190 actors hand-roll; this store keeps only its domain
  wiring."
  (ls/identity-schema [:field/id :ledger/seq]))

(defrecord DatomicStore [conn]
  Store
  (registered-field [_store field-id]
    (when field-id
      (ls/dec* (d/q '[:find ?p .
                      :in $ ?fid
                      :where [?e :field/id ?fid] [?e :field/payload ?p]]
                    (d/db conn) field-id))))
  (add-field [_store field-id field-data]
    (d/transact! conn [{:field/id field-id :field/payload (ls/enc field-data)}])
    field-data)
  (ledger [_store] (ls/read-stream conn :ledger/seq :ledger/fact))
  (append-ledger! [store fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger store)) fact)
    fact))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `initial-fields`
  (field-id -> field-record); empty when omitted."
  [& [{:keys [initial-fields] :or {initial-fields {}}}]]
  (let [s (->DatomicStore (d/create-conn schema))]
    (doseq [[field-id field-data] initial-fields]
      (add-field s field-id field-data))
    s))
