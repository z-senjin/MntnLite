# RuneLite 1.13 monetary API migration

Microbot 2.6.25 follows RuneLite 1.13.0's `long` item prices and Grand Exchange offer amounts.

The following Microbot methods now return `long`: `Rs2ItemModel.getPrice()`, `Rs2TileItemModel.getTotalValue()`, `Rs2Pvp.calculateRisk()`, and `GrandExchangeOfferDetails.getPrice()` / `getSpent()`. The offer-details constructor accepts `long` price and spent arguments. Ground-item prices retain Microbot's public getter, now returning `long`.

Recompile dependent plugins against 2.6.25: changing return types changes JVM method descriptors. Previously compiled plugins calling these methods or RuneLite's changed price methods are not binary compatible. Update plugin versions and minimum client versions before publishing rebuilt artifacts.

Keep unit prices, stack totals, accumulators, comparisons and formatted display values as `long`. Use `mapToLong`, `comparingLong`, and `0L` Optional fallbacks. Do not cast monetary values back to `int`; compound assignments to int accumulators also silently narrow. Item IDs and quantities remain integers.

Coordinate the client release with rebuilt Hub artifacts. Validate Hub builds against the candidate shaded client with `-PmicrobotClientPath=/absolute/path/to/client.jar` before publishing; do not compile the migrated Hub sources against the previous stable client.
