<img src="docs/icon.png" width="128" alt="Free Trade icon" align="right">

# Free Trade

**English** | [Português (Brasil)](README.pt-BR.md)

A NeoForge economy mod for modpacks and servers. A global **Trade Association** keeps the stock of every item, and
prices follow supply: the more stock, the lower the price. Items with no declared value get one derived from their
recipes, so modded items are priced automatically.

## Downloads

Get the jar from [Releases](https://github.com/mzramna/freetrade/releases/latest):

| Jar                          | Minecraft | NeoForge | Notes |
|------------------------------|-----------|----------|-------|
| `freetrade-1.21.1-<ver>.jar` | 1.21.1    | 21.1.x   | Java 21 |
| `freetrade-26.1.2-<ver>.jar` | 26.1.2    | 26.1.x   | Java 25. EMI is not available for 26.1 yet, so only JEI drag-and-drop is supported. |

Install it on **both the server and the clients**. Every server that shares an economy must run the same modpack.

## Features

- **Supply-based prices.** Each item has a base value and a formula that moves its price with stock. The default
  formula is `b * (r / x)^1`, and the server can change it, add a ceiling, or use a steeper curve.
- **Values from recipes.** Items without a declared value get one from the cheapest recipe that makes them, and the
  price follows its ingredients.
- **Slippage.** Large trades are priced unit by unit, so the price moves while you trade.
- **Whole-U.M. sales to the Trade Representative.** Only the units that earn whole U.M. are taken, and the rest is
  returned.
- **Trade Representative.** An unemployed villager becomes a representative at an Auto Buyer. Sell one item type or
  buy from the catalog in its window.
- **Sell Point and Auto Buyer.** Machines that sell an item on a redstone signal, and buy an item from money you put
  in them. They have capacity that grows with adjacent chests and barrels.
- **Trade Association Guide.** A book with a "How it works" tour and a searchable catalog of buy and sell prices, stock
  and a stock filter.
- **Taxes, backoff and treasury.** Optional sell and buy taxes into an operator account, an anti-storage backoff for
  round trips, and an optional finite treasury.
- **Money.** Emeralds and emerald blocks by default, any items the server chooses, and Lightman's Currency coins and
  bank accounts when that mod is installed.
- **Database.** SQLite for a single world, or PostgreSQL, which several servers can share as one economy.
- **Triggers.** Commands that run when the treasury or tax account reaches a condition, optionally spending money
  first. Operators set them up; see the [command reference](docs/en/commands.md#triggers) for the syntax.

## Quick start

**Players.** Craft a **Trade Association Guide** from a book and an emerald, and right-click it to read the market.
To trade, place an **Auto Buyer** near an unemployed villager, then right-click the new Trade Representative.
The [player guide](docs/en/player-guide.md) covers selling, buying, the Sell Point and the Auto Buyer.

**Server owners and modpack makers.** Install the jar on the server and on every client. Set the settings in
`serverconfig/freetrade-server.toml` (or `defaultconfigs/` in a modpack), and declare item values in
`config/freetrade/values.json`. After adding mods, run `/freetrade recalc`. The
[server and modpack guide](docs/en/server-modpack-guide.md) explains the database choice, sharing an economy, and
backups.

## Documentation

- [Player guide](docs/en/player-guide.md): how to trade, and how the market works.
- [Server and modpack guide](docs/en/server-modpack-guide.md): install, databases, shared economies, modpack seeding,
  economy settings and troubleshooting.
- [Configuration reference](docs/en/configuration.md): every server setting, the `values.json` format, and the price
  formula language.
- [Command reference](docs/en/commands.md): every `/freetrade` command, with permissions and examples.
- [Regional markets](docs/regions.md): a design note for a future version. It is not implemented.

## Translations

Free Trade is available in English and Brazilian Portuguese. To add a language, the game text and the documentation
can both be translated. See [TRANSLATING.md](TRANSLATING.md).

## Building

| Branch   | Java |
|----------|------|
| `1.21.1` | 21   |

```bash
./gradlew build              # jar in build/libs
./gradlew runGameTestServer  # automated GameTests
```

CI runs build and GameTests on every push and pull request. Pushing a `v*` tag, or pushing to the `release` branch,
publishes a release with the built jars.

## License

[MIT](LICENSE)
