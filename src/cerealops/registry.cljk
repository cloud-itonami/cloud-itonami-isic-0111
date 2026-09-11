(ns cerealops.registry
  "Pure validation functions for cereal-growing operations. These are
  called by the Governor to independently verify proposal parameters --
  the LLM advisor's confidence is NOT sufficient to override these checks.
  Mirrors `cattleops.registry` (cloud-itonami-isic-0141) in shape."
  )

(defn cost-exceeds-threshold?
  "Independently verify a proposed spend against its category/default
  threshold. Inclusive at the boundary (exactly-at-threshold does not
  escalate)."
  [cost threshold]
  (> cost threshold))

(defn acreage-non-positive?
  "A logged planting/harvest acreage of zero or negative is not a real
  observation -- reject it as a HARD violation rather than silently
  accepting bad data into the field record."
  [acreage]
  (<= acreage 0))

(defn confidence-below-floor?
  "Independently verify a proposal's stated confidence against the
  Governor's confidence floor."
  [confidence floor]
  (< confidence floor))
