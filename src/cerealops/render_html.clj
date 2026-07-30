(ns cerealops.render-html
  "Build-time HTML renderer. Drives the REAL actor stack deterministically.
   Usage: clojure -M:dev:render-html [out-file]."
  (:require [clojure.string :as str]
            [cerealops.store :as store]
            [cerealops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator {:actor-id "farmer-01" :role :farm-operator :phase :phase-3})
(defn- exec! [actor tid request] (g/run* actor {:request request :context operator} {:thread-id tid}))
(defn- approve! [actor tid] (g/run* actor {:approval {:status :approved :by "farmer-01"}} {:thread-id tid :resume? true}))
(defn- reject! [actor tid] (g/run* actor {:approval {:status :rejected :by "farmer-01"}} {:thread-id tid :resume? true}))

(defn run-demo! []
  (let [db (store/mem-store {:initial-fields {"field-001" {:id "field-001" :name "Test Cereal Field" :crop "wheat"}}})
        actor (op/build db)]
    (exec! actor "t1" {:op :log-field-record :field-id "field-001" :acreage 120 :crop "wheat" :record-type "planting"})
    (exec! actor "t2" {:op :flag-crop-health-concern :field-id "field-001" :concern "rust-fungus-suspected"})
    (approve! actor "t2")
    (exec! actor "t3" {:op :order-supplies :field-id "field-001" :category "seed" :cost 900})
    (reject! actor "t3")
    (exec! actor "t4" {:op :log-field-record :field-id "field-999" :acreage 50 :crop "maize" :record-type "planting"})
    db))

(defn- esc [v] (-> (str v) (str/replace "&" "&amp;") (str/replace "<" "&lt;") (str/replace ">" "&gt;")))
(defn- last-fact-for [ledger fid] (last (filter #(= (:subject %) fid) ledger)))
(defn- status-cell [ledger fid]
  (let [f (last-fact-for ledger fid)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved</span>"
      (= :approval-rejected (:t f)) "<span class=\"critical\">rejected</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :basis first)]
        (str "<span class=\"critical\">HARD hold: " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))
(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))
(def ^:private action-gate-rows
  ["        <tr><td><code>:log-field-record</code></td><td><span class=\"ok\">auto-commit when clean + registered</span></td></tr>"
   "        <tr><td><code>:flag-crop-health-concern</code></td><td><span class=\"warn\">ALWAYS human approval (crop safety)</span></td></tr>"
   "        <tr><td><code>:order-supplies</code></td><td><span class=\"warn\">human approval over cost threshold</span></td></tr>"])
(defn render [db]
  (let [ledger (vec (store/ledger db))
        field-001 (store/registered-field db "field-001")
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-0111</title>"
     "<style>body{font:14px/1.5 sans-serif;margin:0;color:#1a1a1a;background:#f5f5f5}"
     ".bar{background:#2a3a0a;color:#fff;padding:1.2rem 2rem}.bar h1{margin:0;font-size:1.15rem}"
     "main{max-width:980px;margin:1.5rem auto;padding:0 1rem}"
     ".card{background:#fff;border-radius:8px;padding:1.2rem 1.4rem;margin-bottom:1.2rem;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
     ".muted{color:#777;font-size:.82rem}table{border-collapse:collapse;width:100%;font-size:.85rem}"
     "th,td{text-align:left;padding:.42rem .5rem;border-bottom:1px solid #eee}th{font-weight:600;color:#555}"
     ".ok{color:#0a7d33}.warn{color:#9a6700}.critical{color:#b41010;font-weight:600}"
     "code{background:#f0f0f0;padding:.1rem .3rem;border-radius:3px;font-size:.8rem}</style></head><body>"
     "<header class=\"bar\"><h1>Cereal growing ops (ISIC 0111)</h1></header><main>"
     "<section class=\"card\"><h2>Fields</h2>"
     "<table><thead><tr><th>Field</th><th>Name</th><th>Crop</th><th>Status</th></tr></thead><tbody>"
     "<tr><td>" (esc (:id field-001)) "</td><td>" (esc (:name field-001)) "</td><td>" (esc (:crop field-001))
     "</td><td>" (status-cell ledger "field-001") "</td></tr>"
     "<tr><td>field-999</td><td class=\"muted\">(unregistered)</td><td class=\"muted\">-</td><td>" (status-cell ledger "field-999") "</td></tr>"
     "</tbody></table></section>"
     "<section class=\"card\"><h2>Action gate</h2>"
     "<table><thead><tr><th>Op</th><th>Gate</th></tr></thead><tbody>"
     (str/join "\n" action-gate-rows) "</tbody></table></section>"
     "<section class=\"card\"><h2>Audit ledger</h2>"
     "<table><thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead><tbody>"
     ledger-rows "</tbody></table></section></main></body></html>")))
(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!) out-file (java.io.File. out)]
    (.. out-file getParentFile mkdirs)
    (spit out-file (render db))
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts )")))
