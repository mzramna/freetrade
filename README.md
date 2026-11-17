<img src="docs/icon.png" width="128" alt="Free Trade icon" align="right">

# Free Trade

**English** | [Português (Brasil)](README.pt-BR.md)

A NeoForge economy mod for modpacks and servers. A global **Trade Association** keeps the stock of every item,
and prices follow supply: the more stock, the lower the price. Items with no declared value get one derived from
their recipes, so modded items are priced automatically.

## Downloads
Grab the jar for your Minecraft version from [Releases](https://github.com/mzramna/freetrade/releases/latest):

| Jar                           | Minecraft | NeoForge | Packs |
|-------------------------------|-----------|----------|-------|
| `freetrade-1.21.1-<ver>.jar`  | 1.21.1    | 21.1.x   | ATM10 family |
| `freetrade-26.1.2-<ver>.jar`  | 26.1.2    | 26.1.x   | ATM11 |

Install it on **both server and clients**. All servers using the same economy must run the same modpack.

## Concepts
- **Trade Association:** the server-wide market. Stock and prices are stored in the database.
- **U.M. (unidade monetária):** the currency. By default 1 emerald = 1 U.M. and 1 emerald block = 9 U.M.
  Money items are set by `currency_items`. Lightman's Currency coins and bank accounts work too when that mod is installed.
- **Price formula:** `y = b * (r / x)^1`, where `x` is the current stock, `b` the base value and `r` the reference stock.
  Lower stock means higher price. The default `b * (r / x)^1` has no ceiling (`max_price = -1`).
  The formula is configurable, and each item can override `r`.
- **Slippage:** large trades are priced unit by unit, so the price moves during the transaction.
- **Whole U.M. lots:** every payment is a whole number of U.M., rounded in the association's favor.
  Cheap items are traded in lots, e.g. 2 sticks = 1 U.M.
- **Initial stock** starts at the reference stock `r`, so the first seller does not get the maximum price.

## Recipe-derived values
Items without a declared value get one from the cheapest recipe that makes them: crafting, smelting, stonecutting,
smithing and machines from mods (energy and fluids are ignored). Labor cost is a percentage (default 0%).
Values are computed on first start or with `/freetrade recalc`, and are then saved to the database.

Prices **propagate**: a base value is the current price of the ingredients divided by the recipe yield, and then
the formula applies the item's own stock. Selling many logs makes planks and sticks cheaper too. Items declared
in the config use their own value and do not inherit from ingredients. Items that have no recipe and no value
stay out of the market and show up in `/freetrade unpriced`.

## Values file
`config/freetrade/values.json` holds base values, reference stock and initial stock per item.

```jsonc
{
  "values": { "minecraft:diamond": 16, "#minecraft:logs": 4 },   // item id or #tag
  "reference_stock": { "minecraft:cobblestone": 4096 },          // r override per item
  "initial_stock": { "minecraft:iron_ingot": 256 },              // starting stock
  "excluded": ["minecraft:bedrock", "#c:ores/netherite"]          // never traded
}
```

## Trade Representative (villager)
An unemployed villager becomes a **Trade Representative** when it finds an **Auto Buyer** block (its workstation).
- **Sell:** a grid with one item type at a time. Put in any number of stacks of the same item and see the value.
- **Buy:** choose an item from the searchable catalog, set the quantity, pay with money items, and receive change
  in the largest denominations possible.

## Sell Point
A block that keeps one item, chosen with a ghost slot (drag from JEI, EMI or REI).
- Capacity: 54 stacks x (1 + number of adjacent chests/barrels), with up to 5 barrels. The upgrade is virtual: if a
  barrel is removed, the block stops accepting items until the stock fits again.
- With a **redstone signal**, it sells its contents to the association.
- Its GUI shows the stored amount, the item, the price per unit, the balance, and money arrows (↑/↓) to withdraw.
- Money items inserted into it become balance, so it works as a change machine. They are not sold as goods.
- A hopper or other automation can only take the selected money item, with redstone on and enough balance.

## Auto Buyer
A block with a ghost slot for the **target item**. Money items inserted into it become balance, and it buys the
target while there is balance, stock in the association and space. Its capacity works like the Sell Point.
Automation only extracts bought items while redstone is on. Only the owner and OPs can open its GUI.

## Trade Guide
Crafted with a book and an emerald. Right-click it to open a two-tab screen:
1. **How it works:** pages about the formula, U.M., villagers, blocks, tax, backoff and treasury.
2. **Catalog:** searchable by name or id, with sell and buy price per unit, lot size for 1 U.M., stock,
   and a filter for all / in stock / out of stock.

## Tax, backoff and treasury
- `tax_on_sell_percent` and `tax_on_buy_percent` send a share of each transaction to the OP **tax account**
  (`/freetrade taxaccount`).
- **Anti-storage backoff:** if a player sells and buys back the same item too many times in a short period, the
  buy-back costs `backoff_percent` more per level. The level decays over time and is tracked per player and item.
- **Treasury:** the association's money. The default `-1` means infinite.

## Database
- **SQLite** (default): a file in the world folder. Good for single-player and small servers.
- **Postgres** (recommended for servers): can be **shared by several servers**, so they form one economy.
  Each transaction is atomic in the database, and the history records a `server_id`.

## Commands
Alias: `/ft`. Operators (level 2) for admin commands.

| Command | Description |
|---------|-------------|
| `/freetrade price [item]` | Price and stock of an item (the held item by default). |
| `/freetrade recalc` | Recalculate derived values from recipes. |
| `/freetrade reload` | Reload the configuration. |
| `/freetrade setstock <item> <n>` | Set the stock of an item. |
| `/freetrade setvalue <item> <v>` | Set the base value of an item (run `recalc` to propagate). |
| `/freetrade unpriced [page]` | List items with no price. |
| `/freetrade history [item]` | Recent transactions. |
| `/freetrade account <treasury\|tax> balance` | Show the balance. |
| `/freetrade account <treasury\|tax> query [op value]` | Balance, or 1/0 when compared to a value. |
| `/freetrade account <treasury\|tax> deposit` | Deposit the money items held in hand. |
| `/freetrade account <treasury\|tax> withdraw <amount> [item]` | Withdraw U.M. as money items. |
| `/freetrade account <treasury\|tax> remove` | Remove the account's money items from the player. |
| `/freetrade account <treasury\|tax> give <player> <amount>` | Give U.M. from the account to a player. |
| `/freetrade trigger add <id> <account> <op> <value> <once\|repeat> run <command>` | Register a trigger. |
| `/freetrade trigger add <id> <account> <op> <value> <mode> <cost> <cooldownSeconds> <command>` | Trigger with a cost and cooldown. |
| `/freetrade trigger list \| remove <id> \| reset <id> \| enable <id> \| disable <id>` | Manage triggers. |

`treasury ...` and `taxaccount ...` are aliases for `account treasury ...` and `account tax ...`.

Trigger placeholders in the command: `{id}`, `{account}`, `{balance}`, `{cost}`, `{count}`.

### Command block examples
- A comparator on `/freetrade account query treasury >= 1000` gives a signal when the treasury has at least 1000 U.M.
- `/freetrade trigger add rich treasury >= 1000 repeat run say Treasury is full: {balance} U.M.` announces every time
  the treasury crosses 1000 going up.
- `/freetrade trigger add bonus treasury >= 500 once 100 0 run give @a minecraft:diamond` runs once, and spends 100 U.M.
  from the treasury when it fires.

## Configuration
- Server config: `serverconfig/freetrade-server.toml` in the world (or `defaultconfigs/` in a modpack). It holds the
  formula, tax, database, backoff and treasury settings.
- Values: `config/freetrade/values.json` (see above).

## More documentation
- Player guide: [docs/en/player-guide.md](docs/en/player-guide.md)
- Server and modpack guide: [docs/en/server-modpack-guide.md](docs/en/server-modpack-guide.md)
- Commands: [docs/en/commands.md](docs/en/commands.md)
- Configuration: [docs/en/configuration.md](docs/en/configuration.md)
- Future regional markets (design note, not implemented): [docs/regions.md](docs/regions.md)

## Building
| Branch   | Java |
|----------|------|
| `1.21.1` | 21   |
| `26.1`   | 25   |

```bash
./gradlew build              # jar in build/libs
./gradlew runGameTestServer  # automated GameTests
```

CI runs build + GameTests on every push and pull request. Pushing a `v*` tag, or pushing to the `release` branch,
builds and tests both branches and publishes one release with both jars.

## License
[MIT](LICENSE)
