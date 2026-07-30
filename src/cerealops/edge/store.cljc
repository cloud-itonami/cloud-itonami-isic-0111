(ns cerealops.edge.store
  "`cerealops.store/Store`, backed by kotobase instead of an atom.

  The actor core does not change and does not know: it is handed a Store
  and runs synchronously, exactly as `cerealops.sim` and the test suite
  run it. What changes is where the documents come from.

  Everything hard here is borrowed rather than written. `marketplace.persist`
  owns the EDN-blob codec, the document/stream contexts and the
  fail-closed check for a missing host injection; `marketplace.edge`
  owns the prefetch/run/flush bracket that makes a synchronous core
  usable over a Promise-based client. This namespace is the adapter
  between that host and this actor's four-method protocol, and it is
  deliberately the only new persistence code in the repository — the
  alternative was another copy of a 1,000-line edge layer, which is what
  cloud-itonami-isic-6492 has and what this exists to avoid repeating."
  (:require [cerealops.store :as store]
            [marketplace.persist :as persist]))

(defrecord KotobaseStore [st seed]
  store/Store
  (registered-field [_ field-id]
    (when field-id
      (persist/get-doc (persist/ctx st :field :field/id) field-id)))

  (add-field [_ field-id field-data]
    (persist/put-doc! (persist/ctx st :field :field/id)
                      (assoc field-data :field/id field-id))
    field-data)

  (ledger [_]
    (persist/read-events (persist/stream-ctx st :ledger)))

  (append-ledger! [_ fact]
    ;; The ordinal comes from the host's seq-fn, not from (count ledger).
    ;; A count is a read-modify-write and two concurrent appends collide
    ;; on it — the same reason marketplace.edge supplies one.
    (persist/append-event! (persist/stream-ctx st :ledger) seed fact)
    fact))

(defn kotobase-store
  "Build the durable Store over a HOST-INJECTED database api.

  `marketplace.persist/store` throws when `db-api` is missing or partial,
  so this actor cannot come up looking durable while writing to nothing."
  [{:keys [db-api seq-fn]}]
  (->KotobaseStore (persist/store {:db-api db-api :actor "cerealops"})
                   (or seq-fn (let [n (atom 0)] #(swap! n inc)))))
