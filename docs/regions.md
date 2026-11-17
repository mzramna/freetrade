# Regional markets (future design, not implemented)

Status: **design note only.** Version 1 has one global market. This note records how regional markets could be added
later without rewriting the economy.

## Idea
Admins define **regions**: named sets of chunks (for example, a spawn city, a desert outpost, a nether hub). Each
region has its own market:
- **Catalog:** what the region's Trade Representatives and Sell Points accept and sell. A region may trade only a subset of items.
- **Stock and prices:** each region keeps its own stock. The same item can be worth more in region X than in region Y
  because X is short of it, and the price formula runs per region.
- **Global fallback:** items that a region does not list can still trade on the global market, if the admin allows it.

Regions give geographic economies (transport and scarcity) without changing the price formula or the U.M.

## Code hook: `MarketScope`
Today every market operation goes through a `MarketScope` interface, implemented only by `GlobalScope`.
A scope answers the questions the market needs:
- which stock and base values apply to an item (`x`, `r`, `b`),
- which catalog is visible at a position or to a villager/block,
- where a transaction is written.

Adding regions means adding a `RegionScope` (or similar) that is chosen from the block or villager position through
the region index. The pricing engine, slippage, lots and backoff keep working unchanged, because they only see a scope.
Callers must not read stock directly from the database. They ask the scope.

## Database impact
- Add a `scope` column (for example `global` or `region:<id>`) to `ft_stock` and `ft_transactions`. The primary key of
  `ft_stock` becomes `(scope, item_id)`.
- A `ft_regions` table stores the region id, name, dimension, and the chunk set (as chunk coordinates or as bounding boxes plus a list).
- Existing data migrates to `scope = 'global'`, so there is no data loss.
- Postgres shared by several servers keeps working: regions are part of the shared economy, and `server_id` stays in
  the history.

## Region definition (sketch)
- Commands: `/freetrade region create <id>` (uses the chunk the admin stands in, or a selection), `add`/`remove` chunks,
  `list`, `delete`, and `catalog <id> allow|deny <item>`.
- Config: `regions.enabled`, `regions.global_fallback` (true or false), and per-region overrides for `r` and `initial_stock`.

## Open questions
- **Chunk boundaries and dimensions:** does a region span dimensions, or is it one dimension only?
- **Who trades where:** does a villager use the region it stands in, or the region of its workstation?
- **Arbitrage:** a player can buy cheap in X and sell in Y. Do we accept that (it is a sink for surplus) or add a travel cost or
  a per-region tax?
- **Recipe values:** do derived base values stay global, with only stock and `r` per region? That is the current assumption.
- **Stock transfer:** should admins or players move stock between regions, and at what cost?
- **Performance:** cache the chunk-to-region index in memory, and reload it on region edits only.
- **Migration:** what happens if a chunk belongs to two regions? Reject it, or use priority?
