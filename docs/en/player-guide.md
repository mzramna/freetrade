# Free Trade: player guide

This guide is for players. It explains how the Trade Association works, how to sell and buy, and how to use the
Sell Point and the Auto Buyer. For server settings, see the [configuration reference](configuration.md) and the
[server and modpack guide](server-modpack-guide.md).

## Contents

- [What is the Trade Association](#what-is-the-trade-association)
- [Money: U.M. and money items](#money-um-and-money-items)
- [Whole U.M. and lots](#whole-um-and-lots)
- [The Trade Association Guide book](#the-trade-association-guide-book)
- [Finding or creating a Trade Representative](#finding-or-creating-a-trade-representative)
- [Selling to the Trade Representative](#selling-to-the-trade-representative)
- [Buying from the Trade Representative](#buying-from-the-trade-representative)
- [Sell Point](#sell-point)
- [Auto Buyer](#auto-buyer)
- [Why prices change](#why-prices-change)
- [Anti-storage backoff](#anti-storage-backoff)
- [Checking a price with /freetrade price](#checking-a-price-with-freetrade-price)
- [Troubleshooting](#troubleshooting)

## What is the Trade Association

The Trade Association is a single market shared by everyone on the server. It keeps a **stock** of every item it
trades. Each item has a price that depends on that stock:

- when the association has a lot of an item, the item is cheap;
- when it has little, the item is expensive;
- every item has a **base value** (its worth in U.M.). Items that nobody declared get their base value from the
  recipes that make them.

You can sell items to the association and buy items from it. Buying and selling use the same prices, so buying back
exactly what you sold costs nothing extra, except for taxes and the [backoff](#anti-storage-backoff).

There are three ways to trade:

| Way | What it does |
|-----|--------------|
| Trade Representative (villager) | Sell a stack of one item type, or buy from the catalog, in a window. |
| Sell Point (block) | Stores one item and sells it while it has a redstone signal. |
| Auto Buyer (block) | Buys one item for you from the money you put in it. |

## Money: U.M. and money items

The currency is the **U.M.** (unit of money). By default:

| Money item | Value |
|------------|-------|
| Emerald | 1 U.M. |
| Emerald block | 9 U.M. |

The server can change this list. If Lightman's Currency is installed, its coins can count as money too, and you can
use your Lightman's bank account (see [Buying from the Trade Representative](#buying-from-the-trade-representative)).

Money is never traded as goods. You cannot sell emeralds to the association.

When you receive change, it comes back as money items, largest first. For example, 10 U.M. of change with the default
money is one emerald block (9 U.M.) and one emerald (1 U.M.).

## Whole U.M. and lots

Prices are always whole U.M. Every trade works the same way, whether you buy or sell:

1. The association first computes the **real total** of the trade: the value of every unit (including slippage), the sell or buy tax, the durability of a damaged item, and your personal backoff.
2. Only then is that total rounded to whole U.M., **half up**: .5 or more rounds up, and less than .5 rounds down.

For example, 3 items worth 1.5 U.M. each cost 4.5 U.M. in total, so you pay 5 U.M., not 3 x 2 = 6. One item worth 1.4 U.M. costs 1 U.M., and two of them (2.8 U.M.) cost 3 U.M.

Items worth less than 1 U.M. are traded only in **lots**. The lot size is the nearest whole number of units worth about 1 U.M., and never less than 2. A stick worth 0.5 U.M. is traded 2 at a time, never one alone.

- Prices shown for such items are per lot.
- Selling takes only whole lots. Units that do not make a whole lot go back to you (at the Trade Representative) or wait in storage (at a Sell Point).
- Buying rounds the quantity up to whole lots, so buying 1 stick gives you 2.
- The lot size follows the current price, so it can change when the stock changes.

Rounding can also be used against the rules. Trading so that the rounding favours you, for example selling one at a time an item worth 1.5 U.M. to get 2 U.M. each time, counts as a round trip for the [backoff](#anti-storage-backoff).

## The Trade Association Guide book

The Trade Association Guide is a book that shows the market. Craft it with a **book and an emerald** (in any
arrangement). Right-click it to open the guide. It has two tabs.

**How it works** has seven short pages:

1. The Trade Association and the U.M.
2. Prices and supply.
3. Values from recipes.
4. The Trade Representative.
5. Sell Point.
6. Auto Buyer.
7. Taxes, backoff and the treasury. The current rates are shown in the header.

**Catalog** lists every item the association trades:

- a search box (by name or item id);
- the sell price and the buy price for one unit, or for one lot when the item is traded in lots (the buy price shows "Out of stock" when there is no stock);
- the lot size, when one unit is worth less than 1 U.M. (for example, "2 units = 1 U.M.");
- the current stock;
- a filter for **All**, **In stock** and **Out of stock**;
- a **Refresh** button that asks the server for current prices and stock.

After you have opened the guide or a trade window, hovering over an item in your inventory also shows its price. Hold
Shift to see the lot size.

The catalog shows the buy price without your personal backoff. If you have a backoff penalty, the real price is
higher. See [Anti-storage backoff](#anti-storage-backoff).

## Finding or creating a Trade Representative

A **Trade Representative** is a villager with its own profession. You get one by placing an **Auto Buyer** block:

- an unemployed villager that finds an Auto Buyer as its job site becomes a Trade Representative;
- each Auto Buyer can hold one representative;
- a villager that already has a job does not switch.

To make the Auto Buyer, craft it like this (P = any planks, E = emerald, B = barrel, H = hopper):

```
P E P
P B P
P H P
```

Place the Auto Buyer, then wait near it for an unemployed villager to take it. Right-click the Trade Representative
to open the trade window. Any player can use the representative, not only the player who placed the Auto Buyer.

A baby villager does not open the trade window.

## Selling to the Trade Representative

The left side of the window is a sell grid:

- it accepts **one kind of item at a time**, up to **27 stacks** (the server can lower this limit);
- money items cannot be placed in the grid;
- enchanted, renamed or otherwise modified items are refused, and the message tells you so. Any stack that carries
  extra data (enchantments, names, contents and similar) is refused;
- damaged tools and armor pay in proportion to their remaining durability. The server can refuse damaged items
  altogether;
- items that cannot be traded are refused.

When you press **Sell**, the association takes your units in whole lots, works out their real total, and pays that total rounded to whole U.M. (see [Whole U.M. and lots](#whole-um-and-lots)). Units that do not make a whole lot are given back. For example, sticks are worth 0.5 U.M. each and are traded 2 at a time. If you offer 3 sticks, the association takes 2 sticks, pays 1 U.M., and returns the third stick.

The payout is given as money items, largest first. If the Lightman's bank option is on, the payout goes into your
bank account instead.

## Buying from the Trade Representative

The right side of the window is the catalog:

1. Search or filter the list and select an item.
2. Set the quantity with **-64**, **-1**, **+1** and **+64**. The **-1** and **+1** buttons move by one lot, and **-64** and **+64** move by 64 lots. For items worth 1 U.M. or more, a lot is one unit. You can buy up to 2,304 units at once.
3. Check the estimated cost, then press **Buy**.

The quantity is rounded up to whole lots. For example, buying 1 stick, when sticks are traded 2 at a time, gives you 2.

Payment rules:

- the price is paid in money items from your inventory. The window uses your smallest money items first and then
  gives back the change;
- a purchase is **all or nothing**. If you do not have enough money for the full quantity, nothing is bought and
  the message tells you how much you need;
- if the price changed between your click and the purchase, you will see a message and can try again;
- buy prices include the buy tax and, when it applies to you, your personal backoff;
- prices are whole U.M., so with emeralds as money no change is lost. If the server uses money items worth a fraction of a U.M., a change amount smaller than the smallest money item cannot be given back. Keep exact change when you can.

**Bank** button: only shown when Lightman's Currency is installed and the server allows it. When it is on, the purchase
is paid from your Lightman's bank account, and the change is deposited back into it. If the bank cannot pay, the
purchase is refused.

## Sell Point

The Sell Point stores one item and sells it to the association while it has a **redstone signal** (any redstone
power next to the block).

To make it, craft (P = any planks, E = emerald, C = a wooden chest, H = hopper):

```
P E P
P C P
P H P
```

**Setting the item.** Open the block and use the **Target** ghost slot. Click it while holding an item, or drag an
item from JEI or EMI onto it. Click it with an empty cursor to clear it. You can change the item only when no item of
the old kind is stored.

**Storing items.** Put items of the target kind into the input slot, or let a hopper insert them. Items are added to
the storage, not sold right away.

**Capacity.** The storage holds 54 stacks of the target item (a stack is the item's normal stack size). Each chest or
barrel touching the block adds one more set of 54 stacks, up to five containers, so the maximum is 6 times the base
capacity. Chests include trapped chests. If you remove a container and the stored items no longer fit, the block
stops accepting items until they fit again.

**Selling.** While the block is powered, it sells up to 64 units about once a second (the server can change the speed and batch size). It sells whole lots only: a leftover smaller than a lot stays in storage until more arrives. The money goes into the block's balance, in whole U.M. when the money items are whole U.M.

**Money in the block.**

- Use the **^** and **v** buttons to choose which money item the block gives out.
- Left-click the money slot to take one money item. Right-click, or Shift-click, to take up to one stack (64).
- **All** takes the whole balance as change.
- **Take** takes up to 64 stored items back to your inventory.
- Money you put into the input slot becomes balance, so the block can work as a change machine.
- Money items are never sold as goods.

**Hoppers and automation.** A hopper can put target items and money into the block. A hopper can only take the
selected money item, and only while the block is powered and has enough balance.

**Ownership.** The player who places the block is its owner. Only the owner and server operators can open the block
or break it. A block placed without a player (for example, by a command) has no owner, and anyone can use it.

Status messages in the GUI tell you why the block is not selling, for example "Waiting for redstone signal", "Out of stock", "Storage full" or "Waiting for a full lot".

## Auto Buyer

The Auto Buyer buys one **target item** for you, paid from money you put into it.

To make it, see [Finding or creating a Trade Representative](#finding-or-creating-a-trade-representative).

**Setting the target.** Use the same ghost slot as the Sell Point. You can change it only when no items are stored.

**Money.** Money items you put into the block become its balance. The block buys the target while it has balance, the
association has stock, and there is space in the storage. It buys whole lots, up to 64 units per cycle, at the catalog buy price,
and it does **not** need a redstone signal to buy.

**Taking items.** The owner can take bought items and the leftover balance back by hand. Hoppers can take bought items
only while the block is powered.

**Capacity and ownership** work the same way as the Sell Point. The block is also the job site of a Trade
Representative.

**Backoff.** Purchases made by the Auto Buyer count against its owner's backoff, the same as the owner's own trades.

## Why prices change

- **Supply.** Selling an item raises its stock, which lowers its price. Buying lowers the stock, which raises its
  price. The price follows the formula `b * (r / x)^1` by default, where `x` is the stock, `b` the base value and `r` the
  reference stock. Doubling the stock halves the price.
- **Slippage.** A large trade is priced unit by unit. Each unit you sell lowers the price of the next one, and each
  unit you buy raises the price of the next one.
- **Propagation.** The base value of a crafted item follows the current prices of its ingredients. If many people sell
  logs, the logs get cheaper, and so do planks and sticks, since they are made from logs.
- **Values from recipes.** Items nobody declared get a value from the cheapest recipe that makes them. Modded items
  are priced this way too.
- **Ceiling.** The server can cap the price of one unit, so a very scarce item cannot cost more than the cap.
- **Taxes.** The server can add a percentage to sales and purchases. Taxes go to the operators' tax account, not to
  the treasury.

The guide and the `/freetrade price` command always show the current numbers.

## Anti-storage backoff

The association is not a warehouse. If you sell an item and buy it back within the **backoff window** (default 600 seconds, which is 10 minutes), that counts as a **round trip**. A **rounding gain** also counts as a round trip: a trade where rounding to whole U.M. favoured you, such as selling one at a time an item worth 1.5 U.M. to get 2 U.M. each time (see [Whole U.M. and lots](#whole-um-and-lots)). Once you have made the **threshold** number of round trips (default 3), each further round trip raises your penalty level for that item by one, up to a maximum of 20 levels.

The penalty level applies to that item only, and it has two effects:

- your buy price for the item rises by **10% per level** (+200% at level 20);
- your payout for selling the item is divided by 1 + 10% x level. At level 2 (+20%) you receive about 83% of the normal payout (1 / 1.2), and at level 20 you receive one third.

Your penalty goes down by one level for every full window without round trips. The penalty is tracked per player and per item, and it does not affect anyone else. Server operators can change all of these values; see the [configuration reference](configuration.md#fees).

## Checking a price with /freetrade price

```
/freetrade price            (the item in your main hand)
/freetrade price <item>     (any item, by id or name)
```

Anyone can run it. The answer shows:

- the stock in the association;
- what the association pays you per unit, after sell tax (per lot for items traded in lots);
- what the association charges per unit, with buy tax, at the current stock (per lot for items traded in lots);
- how many units make 1 U.M., when one unit is worth less than 1 U.M.;
- the base value and whether it was declared by the server or derived from recipes.

Money items show their value. Items that are not traded say "is not traded".

## Troubleshooting

| Message or problem | What it means and what to do |
|--------------------|------------------------------|
| "The market is offline right now." | The server could not open its database. Ask an operator to check the server log. |
| "Database error. Please try again." | A single trade failed. Try again. If it keeps happening, tell an operator. |
| "X is not traded." | The item has no value and no recipe. Operators can list such items with `/freetrade unpriced`. |
| "The association has no stock of X." | The stock is zero. Wait for someone to sell it, or buy another item. |
| "You need N U.M. for this." | You do not have enough money in your inventory (or bank). The purchase was cancelled. |
| "The association has no money." | The treasury is finite and empty, so it cannot pay sales. |
| "Enchanted, renamed or modified items are refused." | Stacks with extra data cannot be sold. Use plain items. |
| "Only the owner (name) and operators can use this block." | Someone else placed the block. Ask the owner, or an operator. |
| "The price changed. Please try again." | The price moved between your click and the purchase. Try again. |
| The Sell Point does not sell. | Check that it has a redstone signal and that the GUI shows a target and stored items. |
| The Auto Buyer does not buy. | Check the balance, the target, the stock and the free space in the GUI. |
| The Sell Point shows "Waiting for a full lot". | The stored amount is smaller than one lot of the item, so it waits until more arrives. |
