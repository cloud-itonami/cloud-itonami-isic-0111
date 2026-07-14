# Business Model: Cereal-Growing Operations Coordinator

## Classification

- Repository: `cloud-itonami-isic-0111`
- ISIC Rev. 4: `0111`
- Industry: Growing of cereals (except rice)
- Social impact: food-security, rural-employment, environmental-stewardship

## Customer

- Small-to-medium cereal farms (wheat, maize, barley, sorghum, oats, rye, millet)
- Grain cooperatives and contract growers
- Diversified row-crop operations that include cereal acreage
- Smallholder cereal producers (extension-service integrations)

## Offer

- Field management and record-keeping
- Planting/spraying/harvest scheduling coordination
- Crop-health and pest/disease tracking
- Supply procurement coordination
- Audit trail and transparency

## Revenue

- SaaS subscription (per-hectare-per-season pricing)
- Supply chain integration fees
- API access for agronomist/extension-service partners
- Data analytics and reporting add-ons

## Trust Controls

- No direct field-equipment operation without human sign-off
- No finalized pesticide-application decisions by the actor
- All field-operation scheduling proposals are proposals, not commands
- Field registration is required before any operation
- All crop-health concerns are automatically escalated
- High-cost supply orders require approval
- Audit ledger is append-only and never editable

## What we do NOT do

- **Agronomic decisions** (what/when/how much to plant, spray, harvest) — the
  farmer/agronomist decides
- **Pesticide-application decisions** — the agronomist/farmer decides
- **Direct field-equipment operation** — the robot manages records and logistics only
- **Economic decisions** (crop mix, marketing, land use) — remain human authority

## Supported Operations

### Field Record Logging
- Planting records (crop, acreage, date)
- Yield records
- Soil-test data
- Field-condition notes (logging only, not decision-making)

### Field-Operation Scheduling
- Schedule planting, spraying, harvest windows
- Track equipment/labor availability
- Propose follow-up field visits (not order them directly)

### Crop-Health Concern Escalation
- Flag suspected pest infestation
- Report disease symptoms or drought stress
- Automatic escalation to farmer/agronomist

### Supply Procurement
- Seed orders
- Fertilizer orders
- Equipment procurement
- Cost threshold escalation for large orders
