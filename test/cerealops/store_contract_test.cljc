(ns cerealops.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol. Mirrors
  `marketentry.store-contract-test` (cloud-itonami-iso3166-ago) /
  `energy.store-contract-test` (cloud-itonami-isic-3512)."
  (:require [clojure.test :refer [deftest is]]
            [cerealops.store :as store]))

(defn- exercise [s]
  (store/add-field s "field-x" {:id "field-x" :name "X Field" :crop "wheat"})
  ;; re-registering (update) exercises the identity-upsert path on
  ;; DatomicStore (:field/id is :db.unique/identity) the same way
  ;; MemStore's plain `assoc` re-registration does.
  (store/add-field s "field-x" {:id "field-x" :name "X Field (renamed)" :crop "wheat"})
  (store/append-ledger! s {:t :committed :op :log-field-record :subject "field-x"})
  (store/append-ledger! s {:t :approval-requested :op :flag-crop-health-concern :subject "field-x"})
  {:field  (store/registered-field s "field-x")
   :absent (store/registered-field s "no-such-field")
   :ledger (store/ledger s)})

(deftest mem-and-datomic-parity
  (let [mem (store/mem-store)
        dat (store/datomic-store)
        m (exercise mem)
        d (exercise dat)]
    (is (= (:field m) (:field d)))
    (is (= "X Field (renamed)" (:name (:field m))) "re-registration upserts, not forks history")
    (is (nil? (:absent m)))
    (is (nil? (:absent d)))
    (is (= 2 (count (:ledger m))))
    (is (= 2 (count (:ledger d))))
    (is (= (:ledger m) (:ledger d)))))

(deftest datomic-store-seeded-fields
  (let [dat (store/datomic-store {:initial-fields
                                   {"field-y" {:id "field-y" :name "Y Field"}}})]
    (is (= {:id "field-y" :name "Y Field"} (store/registered-field dat "field-y")))
    (is (empty? (store/ledger dat)))))
