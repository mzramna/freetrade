# Free Trade: commands

Every command starts with `/freetrade`, or with the short alias `/ft`. The two are the same. For the meaning of the
settings these commands change, see the [configuration reference](configuration.md).

## Contents

- [Who can run what](#who-can-run-what)
- [Player command](#player-command)
- [Admin commands](#admin-commands)
- [Accounts: treasury and tax](#accounts-treasury-and-tax)
- [Triggers](#triggers)
- [Examples](#examples)

## Who can run what

| Who | Commands |
|-----|----------|
| Any player | `price` |
| Operators (permission level 2 or higher), and command blocks at their default level | Everything else: `recalc`, `reload`, `setstock`, `setvalue`, `unpriced`, `history`, `account`, `treasury`, `taxaccount`, `trigger` |

Commands that change money print the new balance. Commands that return a number use it as the command result, which
command blocks can read (see [Accounts](#accounts-treasury-and-tax)).

## Player command

| Command | Description |
|---------|-------------|
| `/freetrade price` | Shows the price of the item in your main hand. |
| `/freetrade price <item>` | Shows the price of an item. The answer shows the stock, the sell price per unit (after sell tax), the buy price per unit (with buy tax), the lot size when one unit is worth less than 1 U.M., and the base value with its source (declared or from recipes). Money items show their value. |

## Admin commands

| Command | Description |
|---------|-------------|
| `/freetrade recalc` | Solves every derived value again from the current recipes and declared values, and stores them. The reply reports how many items are derived and unpriced, and how long it took. Runs in the background. |
| `/freetrade reload` | Re-applies the server config and re-reads `values.json`. Derived values already stored are kept. |
| `/freetrade setstock <item> <amount>` | Sets the stock of an item. The amount is a whole number, zero or more. |
| `/freetrade setvalue <item> <value>` | Writes the item's base value to `values.json` and reloads. A value of `0` removes the declared value. Derived values of other items are not changed until `recalc`. |
| `/freetrade unpriced [page]` | Lists items that have no price, 15 per page. Page 1 is the default. |
| `/freetrade history [item]` | Shows the last 10 transactions, optionally only for one item. Each line shows the server id, time, player, quantity, item, amount, tax and the stock after the trade. |

## Accounts: treasury and tax

The association has two accounts:

- **treasury**: the association's own money. Selling to the association is paid from it, and buying from it adds to
  it. When the treasury is infinite (`treasury_initial = -1`), trades do not touch it. An empty finite treasury stops buying from players; players can always buy from it.
- **tax** (the operators' tax account): receives the sell and buy taxes. Its name in commands is `tax`.

### Syntax

```
/freetrade account <account> balance
/freetrade account <account> query
/freetrade account <account> query <operator> <value>
/freetrade account <account> deposit <amount>
/freetrade account <account> remove <amount>
/freetrade account <account> withdraw <amount>
/freetrade account <account> withdraw <amount> <money-item>
/freetrade account <account> give <player> <amount>
```

Shortcuts for the two accounts:

| Shortcut | Same as |
|----------|---------|
| `/freetrade treasury ...` | `/freetrade account treasury ...` |
| `/freetrade taxaccount ...` | `/freetrade account tax ...` |

Amounts are in U.M., and may contain decimals (for example, `12.5`). The minimum is 0.001. The account name comes
before the subcommand: `account treasury query`, not `account query treasury`.

| Subcommand | What it does |
|------------|--------------|
| `balance` | Shows the balance. The command result is the balance in whole U.M. The treasury note appears when it is infinite. |
| `query` | Without an operator, behaves like `balance`. With `<operator> <value>`, compares the balance with the value. It replies "Condition true" and returns `1`, or "Condition false" and returns `0`. Operators are `>=`, `>`, `<=`, `<`, `=`. |
| `deposit <amount>` | Adds U.M. to the account. No items are taken from you. |
| `remove <amount>` | Subtracts U.M. from the account. Fails if the account does not hold enough. No items are given to you. |
| `withdraw <amount>` | Takes U.M. out of the account and gives you money items for it, largest first. The amount is rounded down to what the money items can make. The command result is the whole U.M. taken. |
| `withdraw <amount> <money-item>` | Same, but gives only that money item. The amount is rounded down to a multiple of the item's value. For example, `withdraw 50 minecraft:emerald_block` takes 45 U.M. and gives 5 emerald blocks. |
| `give <player> <amount>` | Same as `withdraw`, but the money items go to the named player. |

Any other account name is accepted, but it is a separate account that the market does not use.

**Command blocks and the console.** `withdraw` without `give` hands the items to the player running the command. A
command block or the console has no player, so the command **fails with a message and takes no money**. In
automation, use `give <player> <amount>`.

## Triggers

A trigger runs a command when an account reaches a condition. Triggers are saved in the database, and they are checked
after every trade or account change, and periodically (see `check_interval_ticks`).

### Syntax

```
/freetrade trigger list
/freetrade trigger add <id> <account> <operator> <value> once run <command>
/freetrade trigger add <id> <account> <operator> <value> repeat run <command>
/freetrade trigger add <id> <account> <operator> <value> once <cost> <cooldown> <command>
/freetrade trigger add <id> <account> <operator> <value> repeat <cost> <cooldown> <command>
/freetrade trigger remove <id>
/freetrade trigger reset <id>
/freetrade trigger enable <id>
/freetrade trigger disable <id>
```

| Part | Meaning |
|------|---------|
| `<id>` | A unique name for the trigger, one word. Adding an existing id replaces that trigger. |
| `<account>` | `treasury` or `tax`. |
| `<operator>` | `>=`, `>`, `<=`, `<` or `=`. |
| `<value>` | The threshold, in U.M. |
| `once` | The trigger fires one time. After it fires, it is disabled. `reset` enables it again. |
| `repeat` | The trigger fires again each time the condition becomes true after being false. With a cooldown, it fires at most once per cooldown while the condition stays true. |
| `<cost>` | U.M. taken from the **same account** when the trigger fires, before the command runs. The trigger does not fire if the balance is below the cost. The cost is taken in the same database transaction as the check. |
| `<cooldown>` | Seconds between two firings of a `repeat` trigger. `0` means no cooldown. |
| `<command>` | The command to run, without the leading `/`. It takes the rest of the line. It runs with the highest permission level (4). |

The command can use these placeholders, which are replaced before it runs:

| Placeholder | Replaced with |
|-------------|---------------|
| `{id}` | The trigger id. |
| `{account}` | The account name. |
| `{balance}` | The account balance after the cost was taken. |
| `{cost}` | The cost of this firing. |
| `{count}` | How many times the trigger has fired, including this firing. |

`trigger list` shows each trigger as `id: account operator threshold | mode cost=... cooldown=...s fired=N -> /command`.
Disabled triggers are marked `[off]`.

`trigger reset <id>` sets the fired count back to 0, arms the trigger and enables it. `enable` and `disable` change
only the enabled state.

## Examples

```
/ft price                                 (the item in your hand)
/ft price minecraft:oak_log               (a specific item)
/ft setvalue minecraft:diamond 32         (declare a new base value)
/ft reload                                (apply it)
/ft recalc                                (propagate it to derived items)
/ft setstock minecraft:iron_ingot 5000    (set the stock)
/ft unpriced 2                            (second page of unpriced items)
/ft history minecraft:diamond             (recent diamond trades)
/ft treasury deposit 500                  (add 500 U.M. to the treasury)
/ft taxaccount balance                    (show the tax account)
/ft taxaccount withdraw 50 minecraft:emerald_block
/ft taxaccount give Alex 20               (pay 20 U.M. from tax to a player)
/ft account treasury query                (returns the balance in whole U.M.)
/ft account treasury query >= 1000        (1 if the treasury has at least 1000 U.M., otherwise 0)
/ft trigger add bonus tax >= 500 once run say Tax goal reached!
```

Trigger examples, with explanations, are in the [server and modpack guide](server-modpack-guide.md#triggers-and-command-blocks).
