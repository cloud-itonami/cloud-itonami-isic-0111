(ns cerealops.facts-test
  (:require [clojure.test :refer [deftest is are testing]]
            [cerealops.facts :as facts]))

(deftest supply-category-lookup
  (testing "Lookup valid supply category"
    (let [c (facts/supply-category-by-id "seed")]
      (is (= "seed" (:id c)))
      (is (= "種子" (:name c)))))

  (testing "Lookup invalid supply category"
    (is (nil? (facts/supply-category-by-id "unknown")))))

(deftest supply-category-cost-thresholds
  (testing "Category-specific cost thresholds"
    (are [id expected] (= expected (:cost-threshold (facts/supply-category-by-id id)))
      "seed"        500
      "fertilizer"  500
      "equipment"   1000)))

(deftest default-cost-threshold-value
  (testing "Default fallback threshold matches the conservative baseline"
    (is (= 500 facts/default-cost-threshold))))

(deftest cereal-crop-lookup
  (testing "Lookup valid cereal crop"
    (are [id expected-name] (= expected-name (:name (facts/cereal-crop-by-id id)))
      "wheat"   "小麦"
      "maize"   "とうもろこし"
      "barley"  "大麦"
      "sorghum" "モロコシ"
      "oats"    "エンバク"
      "rye"     "ライ麦"
      "millet"  "キビ・アワ"))

  (testing "Lookup invalid cereal crop"
    (is (nil? (facts/cereal-crop-by-id "unknown"))))

  (testing "Rice is out of scope (ISIC 0112, not this actor)"
    (is (nil? (facts/cereal-crop-by-id "rice")))))
