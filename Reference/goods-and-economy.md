# Goods and economy

What is traded, what it costs, and every rule that moves a price.

Numbers come straight out of `Good`, `Faction`, `Warehouse`, `Loan` and `Captain` — see
[README](README.md) for how to refresh them.

## The ten goods

<!-- generated:goods -->
| Good    | Base price | Icon                        |
| ------- | ---------- | --------------------------- |
| Sugar   | 100g       | `sprites/goods/sugar.png`   |
| Rum     | 150g       | `sprites/goods/rum.png`     |
| Tobacco | 200g       | `sprites/goods/tobacco.png` |
| Cotton  | 120g       | `sprites/goods/cotton.png`  |
| Spices  | 300g       | `sprites/goods/spices.png`  |
| Wood    | 80g        | `sprites/goods/wood.png`    |
| Coffee  | 180g       | `sprites/goods/coffee.png`  |
| Cacao   | 160g       | `sprites/goods/cacao.png`   |
| Cloth   | 140g       | `sprites/goods/cloth.png`   |
| Silver  | 400g       | `sprites/goods/silver.png`  |
<!-- /generated:goods -->

Base price is the anchor only. What a port actually charges is

```
buy  = base x port bias x supply/demand x spread x buyPriceMultiplier(standing)
sell = base x port bias x supply/demand x spread x sellPriceMultiplier(standing)
```

where the port bias is fixed per port (some places are simply cheap for sugar), supply/demand
drifts over time and moves against the player's own trades — buying pushes a price up, selling
pushes it down — and the spread is what keeps buy above sell so round-tripping one good in one
port loses money. `MarketState` owns all of it and is a process-wide singleton, which matters
when writing tests.

Icons are painted art in `assets/sprites/goods/`, named after the enum constant. The path is
built at runtime, so `AssetIntegrityTest` cannot catch a missing one — the market screen will.

## Standing with the four powers

Reputation runs -100 to +100 per faction and does two things at once: it moves prices, and it
decides whether that faction's navy leaves the player alone.

<!-- generated:reputation -->
| Standing   | Range     | Buy price | Sell price | That navy         |
| ---------- | --------- | --------- | ---------- | ----------------- |
| Hostile    | -100..-50 | +20%      | -20%       | hunts you         |
| Unfriendly | -49..-15  | +10%      | -10%       | picks on the weak |
| Neutral    | -14..14   | base      | base       | ignores you       |
| Friendly   | 15..49    | -10%      | +10%       | ignores you       |
| Allied     | 50..100   | -20%      | +20%       | ignores you       |

Four powers: Spain, England, France, Netherlands.
<!-- /generated:reputation -->

Selling in a port raises standing with that port's owner and lowers it with the other three, at
one third of the gain each. That ratio is the reason the player cannot be friends with everyone:
at most three of the four can be climbing at any time. It is a shape decision, not a tuning knob
— `FactionConflictTest` guards it.

Pirates are deliberately not a faction: no home ports, no standing, always hostile.

## Warehouses

<!-- generated:warehouse -->
Buying one in a port costs 1500g and holds 100 units. Each of the 3 expansion levels adds 50, so a fully expanded warehouse holds 250.
<!-- /generated:warehouse -->

One per port, bought separately in each. Holds goods and gold; gold in a warehouse is out of
reach of a pirate who takes the ship, which is the whole point of putting it there.

## Loans

Taken from a commandant, one at a time.

<!-- generated:loans -->
| Borrow | Repay | Interest | Term         |
| ------ | ----- | -------- | ------------ |
| 1000g  | 1300g | 30%      | 5 game days  |
| 3000g  | 4200g | 40%      | 10 game days |
| 6000g  | 9000g | 50%      | 15 game days |
<!-- /generated:loans -->

Going overdue doubles the amount owed once, on the spot, and turns the lending faction's ships
hostile. The HUD shows the remaining term while a loan is outstanding.

## Captains

<!-- generated:captains -->
Hiring the Nth captain costs 2000g, 5500g, 15125g, 41593g, 114382g for the 1st through 5th.
<!-- /generated:captains -->

A captain needs an idle ship in the port where they are hired. They then run one route between
two ports, choosing what to carry themselves — the player sets the ship and the two ports, never
the cargo. A captain's own trade sense shifts every price they pay or receive, which is why any
test asserting on fleet gold has to build its captains by hand rather than rolling them.
