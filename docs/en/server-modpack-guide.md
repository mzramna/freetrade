# Free Trade: server and modpack guide

This guide is for server owners and modpack makers. It covers installing the mod, choosing a database, sharing one
economy between servers, seeding item values for a modpack, and the economy settings that operators can use. The
setting names and defaults are listed in the [configuration reference](configuration.md), and the commands are in the
[command reference](commands.md).

## Contents

- [Installing](#installing)
- [Server config in modpacks and worlds](#server-config-in-modpacks-and-worlds)
- [Choosing a database](#choosing-a-database)
- [Sharing one economy between servers](#sharing-one-economy-between-servers)
- [Seeding values for a modpack](#seeding-values-for-a-modpack)
- [Recalculating after adding mods](#recalculating-after-adding-mods)
- [Economy settings (tax, treasury, backoff)](#economy-settings-tax-treasury-backoff)
- [Stock and forced prices](#stock-and-forced-prices)
- [The tax account and operator commands](#the-tax-account-and-operator-commands)
- [The guide book and the catalog](#the-guide-book-and-the-catalog)
- [Triggers and command blocks](#triggers-and-command-blocks)
- [Backups](#backups)
- [Troubleshooting](#troubleshooting)

## Installing

Free Trade runs on NeoForge for Minecraft 1.21.1 (NeoForge 21.1.x, Java 21) and Minecraft 26.1.2 (NeoForge 26.1.x,
Java 25). On 26.1 only JEI is supported for drag-and-drop, because EMI has no 26.1 release yet.

| Where | What to install |
|-------|-----------------|
| Server | The `freetrade` jar. Required. |
| Clients | The same `freetrade` jar. Required, because the trade windows and the guide are client screens that use the mod's network messages. |
| Clients (optional) | JEI or EMI, for dragging items onto the Sell Point and Auto Buyer target slots, and for the price tooltips. Neither is needed on the server. |
| Server and clients (optional) | Lightman's Currency, for its coins as money and for the bank option. The mod works without it. |

Every server that shares an economy must run the same modpack, so that the recipes and values agree.

## Server config in modpacks and worlds

The server settings live in `config/freetrade-server.toml` in the instance folder. The instance is the server folder,
or the game folder for singleplayer. The file is created on first start, and it applies to every world of that
instance. To ship default settings in a modpack, include `config/freetrade-server.toml` in the pack.

To give one world its own settings, put a copy at `<world>/serverconfig/freetrade-server.toml`. NeoForge uses that copy
for that world instead of the instance file, and writes a `readme.txt` in the folder that explains this. Changes to the
instance file do not reach a world that has its own copy.

Put your `values.json` in the modpack's `config/freetrade/` folder. Free Trade loads it when the game starts, and it
does not overwrite a file that is already there. See [Seeding values for a modpack](#seeding-values-for-a-modpack).

## Choosing a database

| | SQLite (`type = "SQLITE"`) | PostgreSQL (`type = "POSTGRES"`) |
|---|---|---|
| Where the data lives | A file, `freetrade.db`, in the world folder | A PostgreSQL database on a server you run |
| Setup | None | Create a database and a user |
| Servers that can share it | One | Several, as one economy |
| Good for | Single player and small servers | Servers, especially when several share a market |
| Backups | Stop the server, then copy the file (see [Backups](#backups)) | Use `pg_dump` |

Use SQLite unless you want several servers to share one economy. PostgreSQL is the recommended choice for real
servers, because the database is separate from the world.

### PostgreSQL setup

1. Create a database named `freetrade` and a user with permission to create tables in it. The mod creates its own
   `ft_*` tables the first time it starts.
2. Put the connection details in the `[database]` section:

```toml
[database]
type = "POSTGRES"
postgres_url = "jdbc:postgresql://db.internal:5432/freetrade"
postgres_user = "freetrade"
postgres_password = "change-me"
server_id = "survival-1"
```

The JDBC driver is included in the mod's jar.

A minimal Docker Compose file for a database on the same network as the server could look like this. It is an example
only. Change the password, and do not publish the port to the internet.

```yaml
services:
  freetrade-db:
    image: postgres:16
    environment:
      POSTGRES_DB: freetrade
      POSTGRES_USER: freetrade
      POSTGRES_PASSWORD: change-me
    volumes:
      - freetrade-pg:/var/lib/postgresql/data

volumes:
  freetrade-pg:
```

If the database cannot be reached when the server starts, Free Trade logs an error and trading stays off until you
restart the server. Commands report "The market is offline right now."

### Developer note: testing against PostgreSQL

The repository has an integration test, `MarketPostgresTest`, that runs two market instances against one PostgreSQL
database, the way two servers share an economy. Run it with:

```
FT_PG_URL=jdbc:postgresql://host:port/db FT_PG_USER=... FT_PG_PASSWORD=... ./gradlew test
```

The test is skipped when `FT_PG_URL` is unset, so `./gradlew test` runs without a database.

## Sharing one economy between servers

Several servers can use the same PostgreSQL database. They then share stock, balances, the tax account, the treasury,
the transaction history and the triggers.

Rules to follow:

- **Same modpack everywhere.** Derived values are stored in the database. The first server to start with an empty
  database solves them. Others load them, so every server must have the same recipes.
- **Unique `server_id`.** Each server writes its name into the history. Use `survival-1`, `survival-2` and so on.
- **Same settings everywhere.** The formula, taxes, backoff and treasury are per server. If two servers use different
  values, they price the same stock differently.
- **Cache refresh.** With PostgreSQL, each server keeps a copy of stock, balances and triggers in memory and reloads
  it every `cache_refresh_seconds` (default 10). Prices shown on one server can lag by that long. Trades themselves run
  inside a database transaction that locks the stock row, so two servers cannot sell the same units twice.
- **Forced prices are shared.** A forced price (`/freetrade forceprice`) is stored in the database. The other servers
  pick it up at their next cache refresh.
- **Triggers fire once.** The trigger check is done inside a database transaction that locks the trigger row. Servers
  that share a database never fire the same trigger twice. The command runs on the server that won the lock.

## Seeding values for a modpack

`config/freetrade/values.json` decides the base value of each item, how fast its price moves, and the stock it starts
with. The full format is in the [configuration reference](configuration.md#valuesjson). A practical way to seed it:

1. **Declare the important items.** Give base values to items that should not depend on recipes, such as rare
   materials:

   ```jsonc
   {
     "values": {
       "minecraft:diamond": 64,
       "#minecraft:logs": 4,
       "#c:ingots": 8
     }
   }
   ```

   Tags are convenient for whole families. An exact id always beats a tag for the same item.

2. **Set how fast prices move.** `reference_stock` is the stock at which an item costs its base value. A smaller
   value makes the price rise faster when stock falls. A larger value makes it steadier.

   ```jsonc
   "reference_stock": { "minecraft:netherite_scrap": 50, "#c:gems": 200 }
   ```

3. **Set starting stock.** `initial_stock` sets the stock an item gets the first time the market sees it. The
   `initial_stock` mode in the server config (`REFERENCE`, `RANDOM` or `ZERO`) covers every other item:
   - `REFERENCE` (default): the stock equals `reference_stock`, so the price starts at the base value.
   - `RANDOM`: a random stock between `random_min` and `random_max` times the reference stock.
   - `ZERO`: no stock, so players must sell an item before anyone can buy it.

   A per-item `initial_stock` entry always wins over the mode.

4. **Exclude what must never be traded.** `excluded` keeps items out of recipe-derived values and gives them no
   starting stock. The default list already excludes bedrock, barriers, command blocks, structure blocks, spawners and
   similar items.

   > **Note:** `excluded` always wins. An item listed in `excluded` is never traded, even if it also has a value in `values`.

5. **Apply and check.** Run `/freetrade reload`, then `/freetrade price <item>` for the items you care about, and
   `/freetrade unpriced` to find items that have no price.

Starting stock applies only to items the market has not seen before. Changing `initial_stock` later does not change
the stock of items that already have stock. Use `/freetrade stock <item> set <amount>` for that (the older `/freetrade setstock` does the same).

## Recalculating after adding mods

Derived values come from recipes and are stored in the database. When you add or remove mods, or change recipes, run:

```
/freetrade recalc
```

Recalc re-solves every derived value from the current recipes and keeps the stock as it is. New items get their starting
stock according to the `initial_stock` settings. Then check `/freetrade unpriced` for items that still have no price.

`/freetrade reload` does **not** solve new recipes. It only re-applies declared values and settings. See
[Recalc versus reload](configuration.md#recalc-versus-reload).

## Economy settings (tax, treasury, backoff)

These settings are in the `[fees]` section of the server config. Their exact names and ranges are in the
[configuration reference](configuration.md#fees).

**Taxes.** `tax_on_sell_percent` takes a percentage of each sale to the association, and the seller receives the rest.
`tax_on_buy_percent` adds a percentage to each purchase. Both go to the tax account. Taxes are part of the price the
player sees, so they show in the catalog and in `/freetrade price`.

**Per-item taxes.** The operator can give one item its own sell or buy tax, with `/freetrade tax` or in the `taxes`
section of `values.json`. A per-item tax replaces the global percentage for that item and direction, so an item can
be taxed differently from the rest of the market. `0` exempts an item. See [Per-item taxes](configuration.md#per-item-taxes)
and the [command reference](commands.md#per-item-taxes).

**Treasury.** `treasury_initial = -1` (the default) makes the treasury infinite. Trades do not touch it, so the
association always pays. A finite treasury is the association's own cash:

- selling to the association is paid out of the treasury, and when the treasury cannot pay, the sale is reduced or
  refused with "The association has no money";
- buying from the association adds the price to the treasury;
- when the treasury is empty, the association stops buying from players, but players can always buy from it;
- `treasury_initial` sets the starting balance only when the database is first created. Later, operators can change
  the balance with `/freetrade treasury deposit` and `remove`.

**Backoff.** The anti-storage backoff is on by default. It counts a round trip when a player sells an item and buys it back within `backoff_window_seconds`, and it also counts a rounding gain (a trade where rounding to whole U.M. favoured the player). From `backoff_threshold` round trips on, each further round trip raises that player's buy price for that item by `backoff_percent` per level, and divides the payout for selling that item by the same factor, up to `backoff_max_level` levels. A level goes down after each full window without round trips. Only that player and that item are affected.

Examples of tuning:

| Goal | Settings |
|------|----------|
| Stronger protection against using the market as storage | `backoff_threshold = 2`, `backoff_percent = 20.0`, `backoff_window_seconds = 1800` |
| Turn it off | `backoff_enabled = false` |

**Other price settings** in `[market]`:

- `price_ceiling` caps the price of one unit.
- `min_unit_price` keeps abundant items from becoming free.
- `labor_percent` adds a percentage per crafting step, so crafted items cost more than their ingredients.
- `dynamic_propagation = false` stops derived items from following the current price of their ingredients.
- Whole U.M. and lots are not settings: every trade is rounded to whole U.M. (half up), and items worth less than 1 U.M. are traded in lots. See the [player guide](player-guide.md#whole-um-and-lots).

## Stock and forced prices

Use these commands to correct the market by hand. They change the live market at once.

- `/freetrade stock <item> add <amount>` and `remove <amount>` correct the stock by a known amount, and `set` puts it at a
  number. The stock never goes below 0.
- `/freetrade forceprice <item> set <value>` fixes the price of one unit. The price stays the same whatever the stock,
  so it does not fall when players sell or rise when they buy. Use it for a price that must not move, such as an event
  or a shop price. `/freetrade forceprice <item> clear` removes it.
- `/freetrade setvalue` is different: it changes the base value in `values.json`, and the price formula still applies.
  Use it when the item should keep following supply and demand.

Check the forced prices with `/freetrade forceprice list`. Forced prices survive `reload` and `recalc`. They are in the
database, so servers that share an economy share them (see above). Money items cannot be given a stock change or a
forced price.

## The tax account and operator commands

The tax account collects the sell and buy taxes. Operators use it for rewards and events:

```
/freetrade taxaccount balance
/freetrade taxaccount withdraw 50
/freetrade taxaccount give <player> 20
```

Taxes build up in the account as trades happen. Operators can take money out with `withdraw` (items go to the operator
who runs the command) or `give` (items go to the named player). A command block or the console has no player, so it must use `give`:
`withdraw` there fails and takes nothing. The full list is in the
[command reference](commands.md#accounts-treasury-and-tax).

## The guide book and the catalog

The Trade Association Guide book reads its settings from the running server each time it is opened. Its **How it works**
pages therefore show the real values of this server. After you change the config, run `/freetrade reload`, then reopen
the book (or use **Refresh** in its Catalog tab). See the [player guide](player-guide.md#the-trade-association-guide-book).

To hide the catalog from regular players, set `show_catalog` in the `[guide]` section of `freetrade-server.toml`:

```toml
[guide]
show_catalog = false
```

With `false`, the Catalog tab of the book is empty for regular players and shows a notice. Operators (permission level 2)
still see it. Trading with a Trade Representative is not affected, because its window always shows the catalog prices.
The key is described in the [configuration reference](configuration.md#guide).

## Triggers and command blocks

Triggers run a command when the treasury or tax account reaches a condition. A trigger can also spend money from the
same account before it runs. The command runs at permission level 4, so it can do anything an operator can.

The examples below can be typed as they are, in chat or in a command block.

**Comparator readout.** Put this in a command block, and connect a comparator to it. The command returns `1` when the
treasury holds at least 1000 U.M., and `0` otherwise:

```
/freetrade treasury query >= 1000
```

**One-time announcement.** Fires once, when the tax account reaches 500 U.M.:

```
/freetrade trigger add bonus tax >= 500 once run say Tax goal reached!
```

**Repeating reward that spends from the tax account.** When the tax account has at least 100 U.M., it can fire once
per hour. Each firing takes 20 U.M. from the tax account, and then runs the command, which here gives every online
player an emerald:

```
/freetrade trigger add reward tax >= 100 repeat 20 3600 give @a minecraft:emerald 1
```

The command can announce the firing instead, using the placeholders `{count}` (firings so far) and `{balance}` (the
balance after the cost):

```
/freetrade trigger add reward tax >= 100 repeat 20 3600 say Reward #{count}: {balance} U.M. left in tax
```

Notes:

- `once` triggers disable themselves after they fire. Use `/freetrade trigger reset <id>` to arm them again.
- `repeat` without a cooldown fires once each time the condition becomes true, and waits until it becomes false again
  before it can fire again.
- A cost is taken only if the balance is at least the cost. Otherwise the trigger does not fire.
- Command blocks need permission level 2 to run `/freetrade account` and `/freetrade trigger` commands.
- The `treasury` balance does not change with an infinite treasury (`treasury_initial = -1`), so triggers on the
  treasury need a finite treasury, or a manual deposit.

## Backups

**SQLite.** Stop the server before copying the database. Free Trade uses WAL mode, so copy the three files together:
`freetrade.db`, `freetrade.db-wal` and `freetrade.db-shm`. Restore them with the server stopped.

**PostgreSQL.** Back up the database with `pg_dump` on a schedule. Backups of the world do not include the market when
it lives in PostgreSQL.

**Both.** Keep the modpack and the `values.json` file under version control. Recalc recreates derived values, but it
needs the same recipes.

## Troubleshooting

| Symptom | Likely cause and fix |
|---------|----------------------|
| "The market is offline right now." | The database did not open at startup. Check the log for "could not open its database". Confirm the `type`, the URL, the user and the password, that PostgreSQL is reachable, and that the SQLite folder is writable. Trading stays off until a restart. |
| "Database error. Please try again." | A single transaction failed, often because the connection dropped. Free Trade reconnects by itself. If it repeats, check the database server. The log may show "Database connection lost, reconnecting". |
| An item says "is not traded" | It has no declared value and no recipe value, or it is excluded. Run `/freetrade unpriced`. Add a value and run `/freetrade reload`, or run `/freetrade recalc` after adding the mod. |
| A changed value did not reach derived items | Declared values apply on `reload`. Derived values need `/freetrade recalc`. |
| Two servers show different prices | Their `cache_refresh_seconds` or configs differ, or the database is shared but the modpacks differ. Compare the configs, and wait one refresh interval. |
| A trigger does not fire | Check `/freetrade trigger list` for `[off]`, check the account balance, and check the cost. A `once` trigger that already fired stays off until reset. |
| The Lightman's bank option does not appear | Lightman's Currency is not installed on the client and the server, or `lightmans_bank` is `false`. |
| Items keep their old price after a mod update | Run `/freetrade recalc`, then `/freetrade price <item>` to check the result. |
