---
name: veha
description: Produce the pre-implementation decision sheet ("лист решений") for a roadmap milestone, before any code is written. Use when the user starts a milestone ("начинаем M14", "делаем M11b", "/veha M12"), or asks for a decision sheet / лист решений for an upcoming piece of work.
---

# Лист решений перед вехой

Goal: surface every decision that would otherwise be silently invented during implementation,
in ONE message the user can answer in ONE reply. The sheet is written in Russian (the design
docs are Russian; this file is English only to match `CLAUDE.md`).

Why this exists: a paragraph of design prose leaves roughly seven open decisions. Guessing the
*shape* of a rule wrong (per-transaction vs per-unit, cumulative vs one-off) costs a rewrite;
guessing a *number* wrong costs one line at M15. The sheet separates those two so the user
spends attention only where it's expensive.

## Read before writing a single line of the sheet

1. The milestone entry in `README.md`'s roadmap and in `Project design/06-roadmap-integration.md`.
2. The design doc it references (`Project design/NN-*.md`) plus the matching section of
   `Project design/10-game-vision.md` (its §13 table maps sections to milestones).
3. **The actual code the milestone touches** — data classes, the screens involved. Never propose
   a rule the current data structures cannot express without saying so outright, naming the file
   and what would have to change. (Worked example: a faction-conflict penalty needs to know where
   a good was bought, but `Player.cargo` is a plain `Map<Good, Int>` with no provenance, and so is
   `Warehouse.goods` — that is a data-structure change, not a tweak, and the user must learn it
   from the sheet rather than from a mid-implementation surprise.)

## Format — six sections, in this order

1. **Правила** — the shape of every formula/rule. The most important section.
2. **Числа** — concrete values. Placeholders are fine and should be labelled as such.
3. **Краевые случаи** — collisions between states, what happens at limits, interaction with
   already-shipped systems (fleet captains, warehouses, saves).
4. **Место в UI** — which *existing* screen/component is reused. Any new screen must be
   justified in one line; it is the single most expensive thing in this project.
5. **Не делать в этой вехе** — explicit boundaries. This is what stops an M10a-style doubling.
6. **Готово когда** — what gets demonstrated, and what the user verifies by actually playing.

Rules for every item:

- Sequential numbering across the whole sheet (so the reply can be "3 — нет, надо X; 7 — ок").
- State a **decision**, never a question, followed by the rejected alternative in one line —
  a decision cannot be judged without seeing what else was on the table.
- Tag each item `[форма]` (wrong ⇒ rewrite; the user should think here) or `[константа]`
  (wrong ⇒ one-line fix at M15; a glance is enough).
- Anything requiring a data-structure or save-format change gets flagged explicitly.

Then, and only then, a final **«Требует вашего решения»** block: choices that are genuinely the
user's alone, each with 2-3 concrete options. Keep it to a handful — if most of the sheet ends
up there, the sheet failed at its job.

Target: one page. If it does not fit, the milestone is too big — say so and propose a split
instead of writing a longer sheet.

## Hard rules

- **No code, no file edits** until the user has replied. The sheet is the entire deliverable.
- **Never use AskUserQuestion here.** The whole point is one screen the user reads as a whole,
  with full context visible; sequential prompting is what this workflow exists to replace.
- One message, then stop and wait.

## After the user replies

Fold in their edits, restate **only** the changed items (not the whole sheet again), then save
the agreed version to `Project design/decisions/<MILESTONE>.md` so a fresh session can pick the
work up without re-deriving it. Delete that file when the milestone ships — from then on the
code and `CHANGELOG.md` are the record, and a stale sheet is worse than none.
