# Free Trade: configuration reference

This page documents every setting of Free Trade: the server config file, the values file, and the price formula
language. For a step-by-step guide to setting up a server or a modpack, see the
[server and modpack guide](server-modpack-guide.md).

## Contents

- [Files and where they live](#files-and-where-they-live)
- [freetrade-server.toml](#freetrade-servertoml)
  - [[market]](#market)
  - [[currency]](#currency)
  - [[fees]](#fees)
  - [[machines]](#machines)
  - [[database]](#database)
  - [[triggers]](#triggers)
- [values.json](#valuesjson)
  - [Structure](#structure)
  - [Tags and priority](#tags-and-priority)
  - [Derived values from recipes](#derived-values-from-recipes)
  - [Labor](#labor)
  - [Recalc versus reload](#recalc-versus-reload)
  - [Unpriced items](#unpriced-items)
- [Price formula language](#price-formula-language)
  - [Variables, operators and functions](#variables-operators-and-functions)
  - [How a price is computed](#how-a-price-is-computed)
  - [Worked examples](#worked-examples)

## Files and where they live

| File | Where | Created | Purpose |
|------|-------|---------|---------|
| `freetrade-server.toml` | `config/` in the instance folder: the server folder, or the game folder in singleplayer. Applies to every world of the instance. | On first start. A modpack can ship it in `config/`. | All settings except item values. |
| `freetrade-server.toml` | `serverconfig/` in a world folder | You create it, if the world needs its own settings | Replaces the instance file for that world only. NeoForge writes a `readme.txt` there. |
| `values.json` | `config/freetrade/` | On the first load, with a default list | Base values, reference stock, starting stock and excluded items. |
| `freetrade.db` | World folder (SQLite) | On first start | The market database. |

A world that has its own `serverconfig/freetrade-server.toml` uses that copy instead of the instance file. Changes to
the instance file do not reach such a world.

The server applies `freetrade-server.toml` when NeoForge loads or reloads the config. Database settings (`[database]`)
are read only when the server starts, so change them with the server stopped. Changes to `values.json` are applied by
`/freetrade reload`, and derived values need `/freetrade recalc` (see [Recalc versus reload](#recalc-versus-reload)).

## freetrade-server.toml

The file has six sections. Each key lists its type, default, allowed range, and an example.

```toml
[market]
formula = "b * (r / x)^1"
price_ceiling = -1.0
min_unit_price = 0.001
reference_stock = 1000
initial_stock = "REFERENCE"
random_min = 0.5
random_max = 1.5
dynamic_propagation = true
labor_percent = 0.0
accept_damaged_items = true

[currency]
currency_items = ["minecraft:emerald=1", "minecraft:emerald_block=9"]
lightmans_coins = true
lightmans_bank = true
lightmans_um_per_core_value = 0.01

[fees]
tax_on_sell_percent = 0.0
tax_on_buy_percent = 0.0
backoff_enabled = true
backoff_threshold = 3
backoff_window_seconds = 600
backoff_percent = 10.0
backoff_max_level = 20
treasury_initial = -1

[machines]
interval_ticks = 20
batch_size = 64
villager_max_stacks = 27

[database]
type = "SQLITE"
sqlite_file = "freetrade.db"
postgres_url = "jdbc:postgresql://localhost:5432/freetrade"
postgres_user = "freetrade"
postgres_password = ""
server_id = "default"
cache_refresh_seconds = 10
history_days = 90

[triggers]
check_interval_ticks = 100
```

### [market]

| Key | Type | Default | Range | Explanation | Example |
|-----|------|---------|-------|-------------|---------|
| `formula` | string | `b * (r / x)^1` | valid formula | Price of one unit as a function of stock `x`, base value `b` and reference stock `r`. An invalid formula is rejected (an error is logged) and the default is used. See [Price formula language](#price-formula-language). | `"b * (r / x)^2"` |
| `price_ceiling` | double | `-1.0` | -1 to 1e15 | Maximum price of one unit, in U.M. `-1` means no ceiling. | `25.0` |
| `min_unit_price` | double | `0.001` | 0 to 1e9 | Minimum price of one unit, in U.M. Keeps very abundant items from being worth 0. | `0.01` |
| `reference_stock` | integer | `1000` | 1 to 2305843009213693951 | Default `r`: the stock at which an item costs its base value. Each item can override it in `values.json`. | `4096` |
| `initial_stock` | enum | `"REFERENCE"` | `REFERENCE`, `RANDOM`, `ZERO` | Stock an item gets the first time the market sees it. `REFERENCE` = `r`, so the price starts at the base value. `RANDOM` = a random value between `random_min * r` and `random_max * r`. `ZERO` = empty, so players must sell first. Per-item `initial_stock` in `values.json` always wins. | `"ZERO"` |
| `random_min` | double | `0.5` | 0 to 100 | Lower bound of `RANDOM` starting stock, as a multiple of `r`. | `0.25` |
| `random_max` | double | `1.5` | 0 to 100 | Upper bound of `RANDOM` starting stock, as a multiple of `r`. If lower than `random_min`, `random_min` is used. | `2.0` |
| `dynamic_propagation` | boolean | `true` | true / false | Derived items take their base value from the current price of their ingredients, so market moves propagate through recipes. Declared values are not affected. | `false` |
| `labor_percent` | double | `0.0` | 0 to 1000 | Percent added to a crafted item's value on top of its ingredients, for each crafting step. Steps compound along a chain. | `5.0` |
| `accept_damaged_items` | boolean | `true` | true / false | Accept damaged tools and armor, paying in proportion to the remaining durability. When `false`, damaged items are refused. | `false` |

### [currency]

| Key | Type | Default | Range | Explanation | Example |
|-----|------|---------|-------|-------------|---------|
| `currency_items` | list of strings | `["minecraft:emerald=1", "minecraft:emerald_block=9"]` | `item_id=value`, value at least 0.001 | Items that are money, each with its value in U.M. (decimals allowed). Money has a fixed value and is never traded as goods. Change is given in the largest denominations first. An entry without `=`, or with a value below 0.001, is rejected. | `["minecraft:emerald=1", "minecraft:gold_ingot=0.5"]` |
| `lightmans_coins` | boolean | `true` | true / false | When Lightman's Currency is installed, its coins are accepted as money. | `false` |
| `lightmans_bank` | boolean | `true` | true / false | When Lightman's Currency is installed, players can pay and be paid through their bank account. | `false` |
| `lightmans_um_per_core_value` | double | `0.01` | 0.001 to 1e9 | How many U.M. one core value of Lightman's Currency is worth. A coin is worth its core value times this rate. | `0.05` |

### [fees]

| Key | Type | Default | Range | Explanation | Example |
|-----|------|---------|-------|-------------|---------|
| `tax_on_sell_percent` | double | `0.0` | 0 to 100 | Percent of every sale to the association that goes to the tax account (operators' account). The player receives the rest. | `5.0` |
| `tax_on_buy_percent` | double | `0.0` | 0 to 1000 | Percent added to every purchase from the association. The extra money goes to the tax account. | `2.5` |
| `backoff_enabled` | boolean | `true` | true / false | Turns the anti-storage backoff on or off. | `true` |
| `backoff_threshold` | integer | `3` | 1 to 1000 | Round trips (sell, then buy back the same item within the window) before the penalty starts growing. | `5` |
| `backoff_window_seconds` | integer | `600` | 1 to 2592000 | Length of the window that defines a round trip, and of one idle window (seconds). | `1200` |
| `backoff_percent` | double | `10.0` | 0 to 1000 | Percent added to the buy price for each penalty level. | `15.0` |
| `backoff_max_level` | integer | `20` | 1 to 1000 | Maximum penalty level. With the defaults, the price rises by at most 200%. | `10` |
| `treasury_initial` | integer (U.M.) | `-1` | -1 to 9223372036854775 | `-1` means an infinite treasury: trades do not touch it, so it always pays. Any other value makes the treasury finite: selling to the association is paid from it, buying from it refills it, and when it is empty the association stops buying from players. Its value is the starting balance, which is written only when the database is first created. Later changes do not reset the balance; use `/freetrade treasury deposit` or `remove`. See [Economy settings](server-modpack-guide.md#economy-settings-tax-treasury-backoff). | `100000` |

### [machines]

| Key | Type | Default | Range | Explanation | Example |
|-----|------|---------|-------|-------------|---------|
| `interval_ticks` | integer | `20` | 1 to 72000 | Ticks (20 per second) between automatic sales and purchases of the Sell Point and the Auto Buyer. | `40` |
| `batch_size` | integer | `64` | 1 to 100000 | Most units a machine trades in one operation. | `256` |
| `villager_max_stacks` | integer | `27` | 1 to 27 | Stacks of one item a player can put in the Trade Representative's sell grid. The grid has 27 slots, so the range stops at 27. | `9` |

### [database]

| Key | Type | Default | Range | Explanation | Example |
|-----|------|---------|-------|-------------|---------|
| `type` | enum | `"SQLITE"` | `SQLITE`, `POSTGRES` | `SQLITE` keeps the market in a file inside the world. `POSTGRES` is recommended for servers, and several servers can share one PostgreSQL database, which makes one economy. All of them must run the same modpack. | `"POSTGRES"` |
| `sqlite_file` | string | `"freetrade.db"` | file name or absolute path | SQLite file, relative to the world folder, or an absolute path. Used only when `type` is `SQLITE`. | `"market/freetrade.db"` |
| `postgres_url` | string | `"jdbc:postgresql://localhost:5432/freetrade"` | JDBC URL | JDBC URL of the PostgreSQL database. Used only when `type` is `POSTGRES`. | `"jdbc:postgresql://db.internal:5432/freetrade"` |
| `postgres_user` | string | `"freetrade"` | any | PostgreSQL user. | `"freetrade_mod"` |
| `postgres_password` | string | `""` | any | PostgreSQL password. It is stored in plain text in this file, so restrict access to it. | `"change-me"` |
| `server_id` | string | `"default"` | any | Name of this server in the transaction history. Give every server that shares a database a different name. | `"survival-1"` |
| `cache_refresh_seconds` | integer | `10` | 1 to 3600 | Seconds between reloads of stock, balances and triggers from the database. Used only with `POSTGRES`, where other servers can change the same data. | `5` |
| `history_days` | integer | `90` | 0 to 36500 | Days of transaction history kept. Old rows are removed about once an hour. `0` keeps history forever. | `30` |

### [triggers]

| Key | Type | Default | Range | Explanation | Example |
|-----|------|---------|-------|-------------|---------|
| `check_interval_ticks` | integer | `100` | 1 to 72000 | Ticks between periodic checks of the treasury and tax account triggers. Triggers are also checked after every trade or account change. | `20` |

## values.json

`config/freetrade/values.json` sets the base value, reference stock and starting stock of items, and which items are
excluded. If the file does not exist, Free Trade creates it with a default list of values when it first loads.

### Structure

```jsonc
{
  "values": {
    "minecraft:diamond": 16,
    "#minecraft:logs": 4
  },
  "reference_stock": {
    "minecraft:cobblestone": 4096,
    "#c:ingots": 500
  },
  "initial_stock": {
    "minecraft:iron_ingot": 256
  },
  "excluded": [
    "minecraft:bedrock",
    "#c:hidden_from_recipe_viewers"
  ]
}
```

| Section | Value type | Meaning |
|---------|------------|---------|
| `values` | number, above 0 | Base value in U.M. of one unit. Zero and negative values are ignored, so the item is valued from its recipes. |
| `reference_stock` | integer, at least 1 | Per-item `r`: the stock at which the item costs its base value. Overrides `reference_stock` in the server config. |
| `initial_stock` | integer, at least 0 | Stock the item starts with. Overrides `initial_stock` mode in the server config. |
| `excluded` | array of strings | Items that never get a recipe-derived value and never get starting stock. `excluded` always wins: an item listed here is never traded, even if it also has a value in `values`. |

Keys that start with `_` (for example `_comment`) are ignored. Each key is an item id such as `minecraft:diamond`, or a
tag written with a leading `#`, such as `#minecraft:logs`.

**Default content.** The default file declares base values for common vanilla items, for example `#minecraft:logs`
at 4 U.M., `minecraft:diamond` at 64 U.M. and `minecraft:nether_star` at 1024 U.M. It sets `reference_stock` for
diamonds (200), nether stars (10) and netherite scrap (50), and excludes items such as bedrock, barriers, command
blocks, structure blocks, spawners, and the `#c:hidden_from_recipe_viewers` tag.

### Tags and priority

- A `#tag` key adds every item in that item tag. Tags are read from the game's item tag registry when the file loads,
  so tags from datapacks and mods work.
- An exact item id always beats a tag for the same item.
- If two tags list the same item, the tag that appears first in the file wins.
- An invalid JSON file is not applied. The error is logged and the previous values stay in use.

### Derived values from recipes

Items without a declared value (and not excluded) get a value from recipes. The solver:

1. Reads every recipe the server knows, with a result and ingredients, including crafting, smelting, stonecutting,
   smithing and recipes from mods that expose ingredients and results. Special recipes are skipped, and energy and
   fluids are ignored.
2. For each recipe, takes the cheapest alternative of every ingredient slot (a tag counts as any of its items), adds
   the cost, subtracts the items the recipe gives back (such as an empty bucket), and divides by the number of items
   the recipe makes.
3. Uses the cheapest recipe for each item. It repeats until no value changes, so cycles (for example ingot and block
   recipes) settle on the cheapest path.

Derived values are stored in the database. When a server starts and the database already has values, they are loaded
and not solved again.

### Labor

`labor_percent` (in `[market]`) adds a percentage on each crafting step. A chain of three steps with 10% labor costs
about 33% more than its raw ingredients (`1.1 * 1.1 * 1.1`).

### Recalc versus reload

| Command | What it does | Use it after |
|---------|--------------|--------------|
| `/freetrade reload` | Re-reads the server config and `values.json`. Declared values, reference stock, starting stock and excluded items are updated. Derived values already in the database are kept. | Changing the config, or editing declared values, reference stock or excluded items. |
| `/freetrade recalc` | Solves every derived value again from the current recipes and declared values, then stores them. Stock is kept. | Adding, removing or changing mods or recipes; changing `labor_percent`; using `setvalue`. |

`/freetrade setvalue <item> <value>` writes the value to `values.json` and reloads. It does not change derived values
of other items. Run `/freetrade recalc` to propagate it.

### Unpriced items

An item is **unpriced** when it has no declared value, no recipe-derived value, and is not excluded, and it is not a
money item. Unpriced items are not traded. List them with:

```
/freetrade unpriced [page]
```

Each page shows 15 items. To price one, add it to `values` and run `/freetrade reload`, exclude it, or install the
mod that provides its recipe and run `/freetrade recalc`.

## Price formula language

The `formula` key in `[market]` gives the price of **one unit** at a given stock.

### Variables, operators and functions

| Element | Meaning |
|---------|---------|
| `x` | Stock of the item (units). Clamped to at least 1. |
| `b` | Base value of the item in U.M. |
| `r` | Reference stock of the item. Clamped to at least 1. |
| `e`, `pi` | The constants e (2.71828...) and pi (3.14159...). |
| `+`, `-`, `*`, `/` | Arithmetic. Unary minus is allowed, as in `-x`. |
| `^` | Power. Right-associative, and the exponent can be negative, as in `2^-1`. |
| `( )` | Grouping. |
| `min(a, b)`, `max(a, b)`, `pow(a, b)` | Minimum, maximum and power. Two arguments. |
| `log(a)` | Base 10 logarithm. One argument. |
| `ln(a)` | Natural logarithm. One argument. |
| `sqrt(a)`, `abs(a)`, `exp(a)` | Square root, absolute value and e to the power `a`. One argument. |

Numbers can be written as `2`, `0.5` or `1e3`. Any other name is an error. The parser checks the formula when the
config loads, so a typo is caught before the market uses it.

### How a price is computed

- The formula gives the value of one unit when the stock is `x`. The result is clamped: a negative or NaN result
  becomes 0, a base value of 0 or less gives 0, and `price_ceiling` (when not `-1`) caps it.
- The result is never below `min_unit_price`.
- A trade is priced unit by unit. Selling `n` units at stock `S` pays the units valued at stock `S+1` to `S+n`.
  Buying `n` units at stock `S` charges the units valued at `S` down to `S-n+1`. Selling and buying back the same
  units therefore cost the same, and large trades move the price while they happen.
- Monetary amounts are stored in thousandths of a U.M.

### Worked examples

Assume `b = 10` U.M. and `r = 1000` (the defaults).

| Formula | Stock `x` | Price of one unit | Notes |
|---------|-----------|-------------------|-------|
| `b * (r / x)^1` | 1000 | 10.0 | At stock `r`, the price is the base value. |
| `b * (r / x)^1` | 500 | 20.0 | Half the stock doubles the price. |
| `b * (r / x)^1` | 2000 | 5.0 | Double the stock halves the price. |
| `b * (r / x)^2` | 500 | 40.0 | A steeper curve: half the stock quadruples the price. |
| `b * (r / x)^2` | 2000 | 2.5 | Double the stock divides the price by 4. |
| `b * (r / x)^2`, `price_ceiling = 25` | 250 | 25.0 | The formula gives 160, capped at the ceiling of 25 U.M. |
| `b * min(4, r / x)` | 100 | 40.0 | `min` caps the multiplier at 4, so the price never exceeds 4 times `b`. |

Selling 2 units at stock 1000 with the default formula values them at `x = 1001` and `x = 1002`, because each unit
is priced at the stock after it is added: `10 * 1000/1001 + 10 * 1000/1002`, about 19.97 U.M.
