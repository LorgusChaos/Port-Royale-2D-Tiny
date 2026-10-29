# Ships

Every hull in the game: what it costs, what it does, and what it looks like.

Numbers come straight out of `ShipTypes` / `ShipCondition` — see [README](README.md) for how to
refresh them. The prose around them is hand-written; the generator never touches it.

## The five purchasable classes

One progression, tier 1 to 5, not five unrelated hulls. The Sloop is what a new game starts with
and cannot be bought back; everything else is sold at any shipyard.

<!-- generated:hulls -->
| Class      | Price | Cargo | Speed | Hull | Damage | Manoeuvre | Sprite            |
| ---------- | ----- | ----- | ----- | ---- | ------ | --------- | ----------------- |
| Sloop      | start | 50    | 13    | 100  | 15-25  | 70        | ship2_white_1.png |
| Cutter     | 900g  | 30    | 16.5  | 70   | 8-16   | 95        | ship2_white_2.png |
| Barque     | 1800g | 75    | 12    | 130  | 18-28  | 58        | ship2_white_3.png |
| Brigantine | 2500g | 90    | 11.5  | 150  | 20-32  | 50        | ship2_white_4.png |
| Galleon    | 6000g | 160   | 9.5   | 220  | 28-45  | 30        | ship2_white_5.png |
<!-- /generated:hulls -->

Speed is world units per second before fouling and sail upgrades; manoeuvre feeds flee and
evasion rolls in combat. `Cutter` is the odd one out on purpose — it is the only step that trades
cargo and hull *down* for speed and manoeuvre, so tier does not simply mean better.

## The longboat

<!-- generated:longboat -->
`Longboat` - cargo 0, speed 5, hull 30, damage 0-0, manoeuvre 20, sprite `dinghyLarge1.png` drawn at 0.5x. Not in `ShipTypes.ALL`, so no shipyard sells it.
<!-- /generated:longboat -->

What a lost battle leaves. No hold, no guns, the slowest thing afloat — and no NPC will attack a
player sailing it. It is the bottom of the game and is meant to be reachable.

## Upgrades, wear and repair

Three upgrades, three levels each, reset whenever a different hull is bought — they belong to the
ship, not to the player.

<!-- generated:upgrades -->
| Upgrade     | Per level    | to L1 | to L2 | to L3 |
| ----------- | ------------ | ----- | ----- | ----- |
| Cannons     | +4 damage    | 900g  | 1800g | 2700g |
| Extra Oars  | +8 manoeuvre | 500g  | 1000g | 1500g |
| Extra Sails | +10 speed    | 600g  | 1200g | 1800g |

Fully fouled costs 35% of speed; cleaning is 150g flat, and repair is 4g per hull point.
<!-- /generated:upgrades -->

Fouling accumulates with time at sea (about fifteen minutes of sailing from clean to fully
fouled) and is bought off in one payment at a shipyard.

## What they look like

All ship art is **pixel art**: hard one-pixel outline, flat fill, a small palette, no
anti-aliasing. Viewed straight from above with the bow pointing up. The hull is warm ochre
planking with deck hatches, a row of dark gun ports along each bulwark, and a gilded gallery at
the stern. Sails are seen from above as rounded panels threaded down the mast one behind another,
with yards as thin cross-bars and rigging as pale threads to each side. One mast runs down the
centre like a spine, with a top at the bow end.

Going up the tiers adds a tier of sails, widens the hull, adds gun ports and enlarges the stern
gallery. The Cutter is the only one to add a bowsprit with a fore-and-aft jib.

| Class | Sprite size | What changes |
| --- | --- | --- |
| Sloop | 58x133 | two sail panels, narrow hull, no gun ports |
| Cutter | 84x145 | bowsprit and jib appear |
| Barque | 89x160 | three square tiers, gun ports appear |
| Brigantine | 101x176 | four tiers, noticeably wider hull |
| Galleon | 112x207 | five tiers, widest hull, largest gallery |

The longboat is a different object altogether: a bare brown hull with thwarts, 20x38, no mast and
no sails, and drawn at half scale so it reads as a boat among ships without a label.

### Faction and pirate hulls

The same five hulls, recoloured by sail: cream for Spain, red for England, blue for France, green
for the Netherlands, black for pirates — and pirate sails carry a white skull and crossbones.
Colour is reserved for this; the player always sails under white sails.

| Role | Tier used | Sprite |
| --- | --- | --- |
| Trader | 2 | `ship2_<colour>_2.png` |
| Navy | 4 | `ship2_<colour>_4.png` |
| Pirate | any of 1-5 | `ship2_black_<tier>.png` |

### Known problem: the ships are out of step with everything else

The hulls are pixel art 58-112 px wide. Everything drawn since — port frames, HUD panels, icons,
goods — is painted illustration authored at 1024 px and up, downscaled with mipmaps. Hard
aliased outline against soft brushwork, flat palette against modelled light.

It is worse than a straight style clash because ship sprites are **magnified** on screen (sprite
scale up to 1.65, times camera zoom), so their pixels grow while everything around them stays
smooth. Tolerable at a distance, obvious close in. Replacing them is an open task.
