(ns cerealops.edge.worker
  "The cereal-ops actor's Worker — routes only.

  Everything that is not specific to this actor lives in
  `marketplace.edge`: the kotobase client, the prefetch/run/flush
  bracket, the ledger ordinal, the fail-closed check for a missing seed.
  That namespace's docstring says it is written \"for any actor\", and
  this is the first non-marketplace actor to take it at its word — which
  is the point of this file existing at all. The alternative is what
  `cloud-itonami-isic-6492` did: 1,229 lines of its own edge, of which
  1,003 were generic.

  What is specific to cereal operations, and stays here:

    - which documents a request NAMES. An operation names one field, so
      that is what gets prefetched; nothing else is pulled in on the
      chance it might be wanted.
    - which routes exist, and which of them may write.

  What stays out: every judgement. Whether an operation may commit, or
  must escalate to a human, is `cerealops.governor`'s and is unchanged by
  being reachable over HTTP.

  ## Approval is deliberately not a route

  The actor compiles with `interrupt-before #{:request-approval}` and the
  default in-memory checkpointer. An interrupt parks the run in that
  checkpointer, and a Worker isolate does not survive to the next
  request — so a `POST /operations/{tid}/approve` would look like a
  resume and reliably find nothing to resume. Exposing it would be worse
  than omitting it: an escalation that silently never completes is how a
  governor gets bypassed in practice.

  A durable checkpointer over the same kotobase store is what makes that
  route honest, and it is not in this change."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [langgraph.graph :as g]
            [cerealops.edge.store :as kstore]
            [cerealops.operation :as operation]
            [cerealops.store :as store]
            [marketplace.edge :as edge]))

(defn- run-operation
  "One supervised actor pass over a durable store.

  `:wants` names exactly the field the request named. The actor then runs
  synchronously — the same code path `cerealops.sim` and the tests drive
  — and every write it recorded is flushed in one transact."
  [client body]
  (let [field-id (get body "field-id")
        request (cond-> {:op (keyword (get body "op"))}
                  field-id (assoc :field-id field-id)
                  (get body "acreage") (assoc :acreage (get body "acreage"))
                  (get body "crop") (assoc :crop (get body "crop"))
                  (get body "record-type") (assoc :record-type (get body "record-type"))
                  (get body "category") (assoc :category (get body "category"))
                  (get body "cost") (assoc :cost (get body "cost"))
                  (get body "concern") (assoc :concern (get body "concern")))
        context (get body "context")]
    (edge/with-store
      {:client client
       :wants {:field (if field-id [field-id] [])}
       :store-fn kstore/kotobase-store}
      (fn [st]
        (let [actor (operation/build st)
              thread (or (get body "thread-id") (str "t-" (hash [request context])))]
          (g/run* actor
                  {:request request
                   :context (when context (reader/read-string (pr-str context)))}
                  {:thread-id thread}))))))

(defn- routes [client request env method path]
  (cond
    (and (= method "GET") (= path "/health"))
    (js/Promise.resolve
     (edge/json {:ok true
                 :service "cloud-itonami-isic-0111"
                 :isic-rev5 "0111"
                 :store "kotobase.net via kotoba-lang/kotobase-client"
                 :host "kotoba-lang/marketplace edge (shared)"
                 :governor "cerealops.governor"
                 ;; Stated because a caller cannot otherwise tell that an
                 ;; escalated operation has nowhere to be resumed from.
                 :approval "escalations are recorded; resume is not exposed
                            (in-memory checkpointer, see ns docstring)"}
                200))

    (and (= method "POST") (= path "/operations"))
    (if-not (edge/authorised? request env)
      (js/Promise.resolve (edge/json {:error "unauthorised"} 401))
      (-> (.json request)
          (.then #(run-operation client (js->clj %)))
          (.then #(edge/json % 200))))

    (and (= method "GET") (str/starts-with? path "/fields/"))
    (-> (edge/read-doc client :field (subs path (count "/fields/")))
        (.then (fn [f] (edge/json (or f {:error "not found"}) (if f 200 404)))))

    (and (= method "GET") (= path "/fields"))
    (-> (edge/read-all client :field)
        (.then (fn [fs] (edge/json {:fields (mapv :field/id fs)} 200))))

    ;; /escalations and /ledger, implemented once in marketplace.edge. This
    ;; actor escalates rather than committing on a machine's say-so, and
    ;; without a way to READ those the gate is a black hole.
    :else (edge/ledger-routes client request env method path :cerealops)))

(def app
  (clj->js
   {:fetch (fn [request env _ctx]
             (edge/serve "cloud-itonami-isic-0111" request env routes))}))
