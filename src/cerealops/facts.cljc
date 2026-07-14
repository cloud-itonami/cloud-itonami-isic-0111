(ns cerealops.facts
  "Reference facts for cereal-growing operations coordination: supply
  category cost policy and cereal-crop classification. This namespace
  contains pure lookup functions for domain reference data -- the Governor
  and Advisor consult these instead of inventing thresholds. Mirrors
  `cattleops.facts` (cloud-itonami-isic-0141) in shape.")

(def supply-categories
  "Procurement categories this actor may propose orders for, and the
  default cost threshold above which an order proposal must escalate for
  human sign-off (farmer/ops-manager)."
  {"seed"
   {:id "seed" :name "種子" :cost-threshold 500}

   "fertilizer"
   {:id "fertilizer" :name "肥料" :cost-threshold 500}

   "equipment"
   {:id "equipment" :name "設備" :cost-threshold 1000}})

(defn supply-category-by-id [id]
  (get supply-categories id))

(def default-cost-threshold
  "Fallback escalation threshold used when a supply-order proposal doesn't
  cite a known category (never invent a lower bar than this)."
  500)

(def cereal-crops
  "Cereal crops this actor's field records may cover (ISIC 0111: growing
  of cereals except rice -- rice growing is ISIC 0112, out of scope)."
  {"wheat"   {:id "wheat" :name "小麦"}
   "maize"   {:id "maize" :name "とうもろこし"}
   "barley"  {:id "barley" :name "大麦"}
   "sorghum" {:id "sorghum" :name "モロコシ"}
   "oats"    {:id "oats" :name "エンバク"}
   "rye"     {:id "rye" :name "ライ麦"}
   "millet"  {:id "millet" :name "キビ・アワ"}})

(defn cereal-crop-by-id [id]
  (get cereal-crops id))
