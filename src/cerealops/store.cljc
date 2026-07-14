(ns cerealops.store
  "Store abstraction for cereal-growing field/planting records. Current
  implementation is an in-memory map; production should migrate to
  Datomic/kotoba-server (the same seam point all cloud-itonami actors
  use). Mirrors `cattleops.store` (cloud-itonami-isic-0141) in shape.

  A registered field is the minimal unit of authority: a farm's field must
  be registered before ANY proposal referencing it can be considered by
  the Governor (see `cerealops.governor`'s `field-registered` invariant).
  Field data is opaque to this namespace -- callers/backends decide what a
  field record contains (name, location, crop, acreage, etc); this Store
  only answers \"is this field-id registered, and if so what's on file\".")

;; Protocol for swappable store implementations
(defprotocol Store
  (registered-field [store field-id]
    "Retrieve a registered field record by ID. Returns nil if the
    field-id is nil or not registered."))

;; In-memory implementation (MemStore) for development/testing
(defrecord MemStore [fields]
  Store
  (registered-field [_store field-id]
    (when field-id
      (get @fields field-id))))

(defn mem-store
  "Create an in-memory store. `initial-fields` is an optional map of
  field-id -> field-record."
  [& [{:keys [initial-fields] :or {initial-fields {}}}]]
  (MemStore. (atom initial-fields)))

(defn add-field
  "Register or update a field in the store. Used by tests and
  simulation."
  [^MemStore store field-id field-data]
  (swap! (:fields store) assoc field-id field-data)
  field-data)
