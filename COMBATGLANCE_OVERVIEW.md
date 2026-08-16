# Combat Glance — Product Overview

## 1. Purpose

Combat Glance is a RuneLite PvM helper that offloads a small, high-stakes piece of working memory: **what am I attacking with, and what am I praying?**

During raids, slayer, and bossing, newer players look at the boss, the ground, their inventory, and the prayer book at once. The attack type on their weapon and the prayers they currently have active are easy to lose track of — especially after a panic switch, a weapon swap, or an overhead flick.

**Core promise:**

> One glanceable card that answers: melee / ranged / magic, which offensive prayer is on, and which overhead is on.

## 2. Problem

OSRS combat asks the player to keep several independent facts in mind at the same time:

1. The equipped weapon's **attack type** (melee, ranged, or magic) — which can change with the selected attack style, not only the item.
2. The **offensive prayer** currently boosting that attack (Piety, Rigour, Augury, or a lower-tier equivalent).
3. The **overhead prayer** currently protecting them (Protect from Melee / Missiles / Magic, or a Ruinous Dampen equivalent).

Existing RuneLite tools cover pieces of this, but not the working-memory job:

- Core **Attack Styles** shows Accurate / Aggressive / Defensive text and pure-skill warnings. It does not show melee vs ranged vs magic as a family, and it does not show prayers.
- Core **Prayer** shows drain, duration, and optional infoboxes for every active prayer. It is a dashboard, not a two-line combat readout.
- Hub plugins that show slash/stab/crush or draw overheads on the player still leave the player combining those facts themselves.

Combat Glance exists to put the three facts that matter mid-fight into one stable, noob-friendly card.

## 3. Product thesis

A player survives and deals damage more consistently when they do not have to remember their own setup.

The plugin should therefore present combat as two labeled sections:

```text
DEFENSE
  [Protect]

OFFENSE
  [Melee]   [Piety]
```

The player should be able to check this card the same way they check the prayer orb: quickly, without reading a manual, and without the plugin telling them what they *should* be using.

## 4. Target user

Primary user:

- Newer or returning PvM player who understands the three combat styles but still mixes them up under pressure.
- Someone who forgets whether Piety is still on after eating, running, or swapping weapons.
- Someone who flicks overheads and needs a larger, calmer confirmation than the tiny overhead icon on the player model.
- Someone who wants training wheels that stay useful even after they improve, because the card is small and quiet.

Combat Glance is not primarily designed for tick-perfect prayer flicking, speedrunning, encounter solvers, or recommending the “correct” prayer for a boss.

## 5. MVP experience

### 5.1 The card

A compact movable overlay with two stacked sections:

```text
┌─────────────────────────────┐
│  DEFENSE                    │
│   [🛡️ Protect]              │
│                             │
│  OFFENSE                    │
│   [⚔️ Melee]  [🙏 Piety]    │
└─────────────────────────────┘
```

Hierarchy:

1. Active overhead prayer — the prayer currently over the player's head, or a calm empty slot.
2. Attack type — always visible while logged in (Melee / Ranged / Magic).
3. Active offensive prayer — the prayer currently boosting damage/accuracy, or a calm empty slot.

The layout must stay stable. Empty slots keep their size so the eyes can park on the same spots every fight.

### 5.2 Attack type

Show the combat **family**, not the Attack Styles plugin's Accurate / Aggressive / Controlled labels:

- **Melee**
- **Ranged**
- **Magic**

Resolve this from the equipped weapon **and** the selected attack style, using the same client weapon-style enums the core Attack Styles plugin uses.

This matters because:

- A staff on Crush is melee; the same staff on autocast is magic.
- A bow is ranged even if the player also has melee gear.
- Unarmed is melee.
- The selected style on a mixed weapon is the truth, not the item name.

Use official Attack / Ranged / Magic skill icons. Short text labels sit under or beside the icon. Color accents the cell — melee warm/brown, ranged green, magic blue — as a cell border and a subtle tinted fill, but must not be the only signal (icon + label always carry the same information).

This is a fixed, always-on family palette, not a per-encounter judgement: the same three colors let the eye match "attack type" and "offensive prayer" cells at a glance (§5.3) without reading either label.

### 5.3 Offensive prayer

Show the currently active **offensive** prayer as its official prayer-book on-sprite.

Offensive means prayers whose primary PvM use is boosting attack, strength, ranged, or magic — including combined prayers:

- Melee combined: Piety, Chivalry
- Ranged combined: Rigour
- Magic combined: Augury
- Lower-tier style prayers (Ultimate Strength, Incredible Reflexes, Eagle Eye, Mystic Might, Deadeye, Mystic Vigour, and their weaker equivalents)
- Ruinous Powers equivalents that clearly boost a combat style (Ancient Strength / Sight / Will, Decimate / Annihilate / Vaporise, and similar)

When several offensive prayers are active at once (for example Ultimate Strength + Incredible Reflexes), show **one primary** prayer: the highest-priority combined or style-matching prayer. Do not turn the offense row into a full prayer-book dump.

When none are active, show a dimmed empty slot labeled **None**. Empty is information: “I am not praying offense.”

**Family color (always on, default behavior):** the offense-prayer cell's border and fill use the same melee/ranged/magic palette as the attack-type cell (§5.2), based on which style *that specific prayer* is designed for (Piety/Chivalry → melee, Rigour → ranged, Augury → magic, and so on). This is per-icon color-coding for fast recognition, not a comparison between the two cells — a Rigour icon is green because Rigour is a ranged prayer, regardless of what the player is currently attacking with. Ruinous Powers offense prayers have no verified style pairing (see §10) and render with the neutral, uncolored cell instead of guessing. The empty **None** slot is also neutral.

**Optional mismatch highlight (opt-in, off by default):** when enabled in config, the offensive-prayer cell's border tints red if the active offensive prayer is designed for a different combat style than the current attack type (e.g. Rigour active while meleeing). This is the one deliberate exception to §5.5/§7.2/§9's "no wrong-prayer warning" rule — see those sections for why it stays off by default and opt-in only. Ruinous Powers offense prayers are never flagged (no verified style pairing to check against; see implementation brief §4.5).

### 5.4 Overhead prayer (defense)

Show the currently active **overhead** prayer as its official on-sprite.

Overhead means prayers that appear over the player and protect or react in combat:

- Protect from Magic / Missiles / Melee
- Retribution, Redemption, Smite
- Ruinous Dampen Magic / Ranged / Melee
- Wrath

The sprite identifies which specific overhead is active, so the text label stays short: **Protect** (for any Protect from Magic/Missiles/Melee), **Dampen**, **Retrib**, **Redeem**, **Smite**, **Wrath** — never the full prayer name. The same short-label convention applies to the offensive slot (**Piety**, **Rigour**, **Augury**, **Strength**, …). Learn and Compact modes share these labels; only the section headers and optional under-icon labels differ between modes.

Only one overhead can be active at a time. When none is active, show a dimmed empty slot labeled **None**.

Do not put Steel Skin, Rapid Heal, Preserve, Protect Item, or other non-overhead prayers in the defense slot. Those are not what a PvM player means by “what am I praying overhead?”

### 5.5 What the card does not do

The card reports current state. It does not grade the player.

- No “wrong prayer” warning by default — the one opt-in exception is the mismatch highlight in §5.3, which is off unless the player turns it on
- No “you should pray magic here”
- No boss-specific overhead schedule
- No gear or style recommendations
- No tick metronome, attack-speed HUD, or action timeline by default — that is TickFlow's job. Three opt-in, off-by-default exceptions exist for players who want a taste of that without switching plugins: an attack-timer bar under the attack-style icon (§9.4-equivalent in the implementation brief), a generic tick-progress bar (§7.6), and a tick sound (§7.6/§11, config-panel-only — no overlay-embedded controls, unlike TickFlow). All three stay off unless explicitly enabled, and none is a substitute for TickFlow's full rolling timeline — see §15.

## 6. Modes

### 6.1 Learn Mode — default

Learn Mode is the noob-friendly layout:

- Visible **OFFENSE** and **DEFENSE** section headers
- Large icons
- Short labels under each slot (Melee, Piety, Protect, None)
- Stable two-row card

A new player should understand it in a few seconds without reading this document.

### 6.2 Compact Mode

Compact Mode removes headers and optional labels, keeping the same slot order:

```text
[Protect]
[Melee] [Piety]
```

Do not add a third “Feel” mode in MVP. Two layouts are enough.

## 7. Interaction and visual design principles

### 7.1 Intuitive before technical

Prefer official OSRS icons, two labeled rows, and a handful of words. Avoid varbit names, prayer IDs, and combat-style jargon in the default UI.

### 7.2 Calm and always readable

The overlay should feel like a status lamp, not an alert. It does not flash when prayers change. It simply updates.

Correct play is visually quiet. Missing an overhead is shown as an empty slot, not a red alarm. Empty can still be *clear* — dimmed icon, “None” label — without shaming the player. The one opt-in exception is the offense/style mismatch highlight (§5.3); it defaults to off precisely because a color-coded correctness signal cuts against "calm and always readable" for anyone who hasn't deliberately asked for it.

### 7.3 Show observations, not advice

Everything on the card is observed client state:

- Equipped weapon + selected attack style → attack type
- Active prayers → offensive slot and overhead slot

Never present a recommendation as if it were a fact.

### 7.4 Empty is a first-class state

An empty offensive or defense slot is not an error and not a missing texture. Reserve space for it. Players learn to glance at “None” the same way they glance at Piety.

### 7.5 Progressive disclosure

Default UI is the card. Debug diagnostics (raw prayer names, weapon category, style index, equipment fingerprint) stay behind a config toggle.

### 7.6 RuneLite-native appearance

Use RuneLite overlay conventions, fonts, spacing, and official sprites. No panel backing and no outer card border — just the cells themselves, floating over the game world for a minimal look. Restrained accents, nearest-neighbor scaling for pixel-art icons. Avoid a web-app or mobile-HUD aesthetic.

The card intentionally has no visual frame of its own; RuneLite's overlay-edit mode already draws its own highlight around any overlay's bounds when the player is repositioning it, so a self-drawn border added nothing but visual weight in the common case (not editing).

**Optional tick-progress bar (opt-in, off by default):** a horizontal amber-to-mint bar fills the otherwise-empty space beside the overhead-prayer cell, showing live progress through the current game tick. This is a generic tick heartbeat — it says nothing about prayers, attack style, or combat, and carries no correctness signal, so it does not fall under the "no wrong-prayer warning" family of exceptions in §5.5/§7.2. Visually it matches TickFlow's square-mode tick-pulse fill exactly (same track/marker colors, same gradient reveal) since that visual language already exists and works well — reusing it is not the same as reusing TickFlow's cadence-tracking logic.

**Optional attack-timer bar (opt-in, off by default):** a thin horizontal bar under the attack-style icon, same fill visual as the tick-progress bar above, showing progress through your current attack/cast/cooldown cycle, plus a bold centered number on the (dimmed) icon showing ticks remaining until the next attack is ready (0 = ready now). Unlike the tick-progress bar, this one *is* attack-specific and carries real (best-effort) information — see §5.3-equivalent detail in the implementation brief §4.2 for how it stays reliable without needing to reconfirm every individual attack, and `AttackAnimations` for the bounded, documented animation-ID allowlist that drives re-anchoring.

**Optional tick sound (opt-in, off by default):** a soft synthesized metronome blip once per game tick — the same sound TickFlow uses. The one deliberate difference from TickFlow: no mute/volume buttons are drawn on the card itself. Both the on/off toggle and the volume live only in the RuneLite config panel, so the card never grows a clickable surface — see §11 for why this matters for the "calm, passive readout" promise.

OSRS ships its skill and prayer sprites at one small native pixel-art size (roughly 25–30px) — the client has no separate high-resolution variant to request. "High-res and visually intuitive" therefore does not mean sourcing bigger art; it means:

- Always use the correct, largest official sprite for the icon in question (the full skill icon, not a tiny orb/side-panel icon; the standard prayer-book on-sprite, not a compressed thumbnail).
- Render every icon at a generous, glanceable on-screen size (see §5.1/§9), scaling up from native resolution with nearest-neighbor only — never bilinear/smooth scaling, which turns crisp pixel art into a blurry smear.
- Treat this as a deliberately larger read than other RuneLite overlays: this card should be legible at a glance while the player is watching the boss, not while they're leaning in to read a tooltip.

### 7.7 Working-memory layout

Eyes should not hunt. Fixed slot order:

```text
Defense       = overhead prayer
Offense left  = attack type
Offense right = offensive prayer
```

Do not reorder slots based on which prayers are active.

## 8. Required MVP scope

The first releasable prototype must include:

1. A standard RuneLite Plugin Hub-compatible Java project, following the same example-plugin / TickFlow repository pattern.
2. A plugin descriptor and a lean configuration panel.
3. A movable overlay card with Defense (top) and Offense (bottom).
4. Attack-type detection: Melee / Ranged / Magic from weapon category + selected style.
5. Offensive-prayer detection with a documented priority when several are active.
6. Overhead-prayer detection for the defense slot.
7. Official skill icons and official prayer-on sprites, with text fallback.
8. Calm empty states for missing offense prayer and missing overhead.
9. Clean handling of login, logout, world hop, death, weapon change, prayer change, plugin disable, and missing data.
10. Unit tests for style mapping, prayer classification, and snapshot behavior.
11. A README with local run, testing, limitations, and Plugin Hub submission steps.

## 9. Explicit exclusions

Do not implement any of the following in the initial plugin:

- Automated clicks, menu actions, prayer changes, movement, or combat actions
- Input modification or menu-entry swapping
- Boss-specific mechanic prediction or overhead schedules
- Prayer, gear, or attack-style recommendations
- “Wrong prayer” or “wrong style” warnings tied to an NPC (the opt-in, off-by-default offense/style mismatch highlight in §5.3 is unrelated — it compares prayer to attack style only, never to an NPC or encounter)
- Slash / stab / crush breakdown (that is a different plugin)
- A full tick timeline or metronome (TickFlow) — the opt-in attack-timer bar, tick-progress bar, and tick sound (§5.5/§7.6) are narrow, off-by-default exceptions, not a timeline or metronome feature
- Network calls, telemetry, accounts, cloud storage, or analytics
- Long-term performance scoring or DPS calculations
- External dependencies unless absolutely necessary
- Reflection, JNI, subprocess execution, runtime code downloads, or non-Java JVM languages

The plugin must remain a **passive visualization** of current combat setup.

## 10. Accuracy and honesty requirements

RuneLite observes client-side state, not every server decision. Therefore:

- Prayer state is sampled from client prayers/varbits; lag can delay the visual by a tick.
- Attack type is inferred from weapon-style enums and the selected style index. Unknown or exotic weapons must fall back conservatively (melee) rather than guessing.
- Ruinous Powers and future prayers must classify as offense, overhead, or ignored using an explicit allowlist — not a fuzzy name match that might mis-slot a new prayer.
- Multiple offensive prayers may be active; only the primary one is shown unless a later option expands this.

The plugin must fail gracefully:

- Show **Unknown** for attack type only when style truly cannot be resolved; prefer the conservative melee fallback when that is the honest default (unarmed, missing enums).
- Show **None** rather than inventing a prayer.
- Reset stale snapshots on logout, hop, death, and plugin disable.
- Provide an optional debug overlay/log for validation.

## 11. Configuration — lean default set

Required settings:

- Enable overlay
- Overlay mode: Learn / Compact
- Show section headers (Learn default on; Compact ignores this)
- Show text labels under icons
- Overlay scale
- Debug diagnostics

Optional only if simple:

- Hide overlay outside combat
- Hide overlay in banks / on login screen (normally hidden when not logged in anyway)
- Highlight offense/style mismatch (§5.3) — off by default, the one opt-in exception to the "no wrong-prayer warning" rule
- Show attack-timer bar (§9.4-equivalent) — off by default, best-effort cadence display
- Show tick-progress bar (§7.6) — off by default, generic tick heartbeat
- Tick sound + tick sound volume (§7.6) — off by default, config-panel-only (no overlay buttons, unlike TickFlow)

Avoid a large settings surface. Color pickers, per-prayer filters, and “warn me if…” checkboxes are out of MVP scope.

## 12. Success criteria

The MVP is successful when a player can:

1. Equip a scimitar, a bow, and a staff (including autocast vs melee style on the staff) and see the attack type change correctly.
2. Toggle Piety / Rigour / Augury and see the offense prayer slot update immediately.
3. Toggle Protect from Melee / Missiles / Magic and see the defense slot update immediately.
4. Turn all prayers off and see calm **None** slots rather than a broken or collapsed card.
5. Understand the interface without reading a manual.
6. Play a slayer task or low-risk boss for ten minutes without the overlay feeling noisy.
7. Use the card as working memory: they look at the boss, not the prayer book, to confirm their setup.

## 13. Validation scenarios

Use these scenarios before expanding scope:

### Scenario A — weapon families

- Unarmed → Melee
- Scimitar / whip / fang → Melee
- Shortbow / crossbow / blowpipe → Ranged
- Trident / powered staff → Magic
- Ancient staff or similar: Crush → Melee; autocast / casting style → Magic

### Scenario B — offensive prayer

- Activate Piety with a melee weapon → offense slot shows Piety.
- Switch to Rigour with a bow → offense slot shows Rigour.
- Deactivate → **None**.
- Activate Ultimate Strength + Incredible Reflexes (no Piety) → one primary prayer, documented by priority, not a random one.

### Scenario C — overhead prayer

- Protect from Magic / Missiles / Melee each appear in the defense slot with the correct sprite.
- Switching overheads replaces the previous icon; they never stack.
- No overhead → **None**.

### Scenario D — ignored prayers

- Steel Skin, Rapid Heal, Preserve, Protect Item do **not** occupy the defense slot.
- They do **not** occupy the offense slot either.

### Scenario E — state changes

- Weapon swap updates attack type without restarting the plugin.
- Prayer swap updates within the same fight.
- Logout, hop, death, and plugin disable clear or hide stale icons.

### Scenario F — readability

- Learn Mode headers make the two sections obvious.
- Compact Mode still preserves slot order.
- Icons remain sharp (nearest-neighbor), not blurry.

### Scenario G — mismatch highlight (opt-in)

- Setting off (default): offense cell border never turns red, regardless of prayer/style combination.
- Setting on, Rigour active while meleeing → offense cell border turns red.
- Setting on, Piety active while meleeing → no highlight (style matches).
- Setting on, offense slot empty (**None**) → no highlight (nothing to compare).
- Setting on, a Ruinous Powers offense prayer active → no highlight (no verified style pairing).

### Scenario H — attack-timer bar (opt-in)

- Setting off (default): no bar, attack-style icon renders exactly as before.
- Setting on, cadence not yet confidently known (e.g. just after login or a weapon swap, before combat starts) → no bar rather than a guessed value.
- Setting on, weapon attack speed resolved and combat with a target engaged → bar appears immediately and free-runs the cycle from that anchor point — it does **not** need to reconfirm every individual attack to keep showing (see implementation brief §4.2 for why the first version required exactly that, and why it barely worked).
- Setting turned on **while already fighting with a weapon equipped from before** → the weapon's known attack speed is resolved immediately on the toggle, not only on the next weapon swap. (Real bug found during development: attack-speed resolution was originally wired only to weapon-change events, so the bar could stay hidden the whole session unless the player re-equipped. Fixed by also resolving it the moment the setting turns on.)
- Bar stays visible and roughly in phase across many consecutive attacks with the same weapon — not just the first one. (Also a real bug in the first version: any single early-arriving observation discarded all learned cadence, so in practice the bar rarely survived past the first swing. Fixed by a redesign that free-runs from a known speed + anchor instead of requiring per-attack reconfirmation.)
- Attack detection is animation-based only (no menu-click classification, unlike TickFlow) — used only as a best-effort re-anchor/drift-correction signal now, not the sole trigger, so weapons whose attack animation doesn't reset between swings degrade to "slightly less precise" rather than "bar disappears."
- Setting on, target changes mid-fight → bar re-anchors to the new target rather than carrying over the old target's phase.
- Setting on, weapon swapped → bar clears and re-anchors on the next engagement rather than carrying over the old weapon's cadence.

### Scenario I — tick-progress bar (opt-in)

- Setting off (default): no bar; the space beside the overhead cell stays empty.
- Setting on: bar fills left-to-right (amber → mint) each game tick and resets at the tick boundary, independent of prayers, style, or combat state.
- Setting on, logged out or overlay hidden: bar does not render (same visibility rules as the rest of the card).

### Scenario J — tick sound (opt-in)

- Setting off (default): silent, exactly as before this toggle existed.
- Setting on: a soft blip plays once per game tick, same synthesized sound TickFlow uses, at the configured volume.
- No controls appear on the card itself for this — unlike TickFlow's overlay-embedded mute/volume buttons, both the on/off toggle and volume live only in the RuneLite config panel. This keeps the card a passive readout with zero clickable surface, consistent with §7 (it observes, it doesn't invite interaction).
- Turning the setting off, or disabling the plugin, stops the sound immediately — no lingering playback.

## 14. Product boundary for launch

A polished narrow plugin is preferable to a broad inaccurate plugin.

The launch version should do one thing exceptionally well:

> Show the player's current attack type, offensive prayer, and overhead prayer as a two-section glanceable card.

Everything beyond that — extra utility prayers, slash/stab/crush, boss warnings, infoboxes — must earn its complexity through real validation.

## 15. Distinct purpose vs existing plugins

Combat Glance is justified on Plugin Hub because it combines three mid-fight facts into one working-memory card. It is not:

- A replacement for Attack Styles (no Accurate/Aggressive labels, no XP-skill warnings).
- A replacement for the Prayer plugin (no drain rate, no duration, no every-prayer infobox).
- A replacement for TickFlow. By default there is no tick timeline, attack cadence, or sound at all. Three narrow, off-by-default toggles (an attack-timer bar, a generic tick-progress bar, a tick sound) exist for players who want a light taste of that without installing a second plugin — none has rolling history or per-action classification, and the tick sound specifically has no overlay-embedded controls the way TickFlow's does (config-panel-only, keeping the card itself a passive readout). If a player wants the real thing — a rolling window of past/now/next actions with inferred rhythm — that is what TickFlow is for, and this card should keep pointing them there in its README rather than growing into a second TickFlow.

If a reviewer asks “why not enable Attack Styles + Prayer infoboxes?”, the answer is: those tools scatter the facts; this plugin groups the three PvM working-memory facts into one calm card. If a reviewer asks "why not just use TickFlow for the attack timer", the answer is: the two opt-in toggles here are default-off conveniences for a card that is fundamentally about prayer/style state, not an attempt to replicate TickFlow's timeline.

## 16. Official references

Use these as source-of-truth starting points and verify APIs against the checked-out RuneLite version during implementation:

- RuneLite Developer Guide: https://github.com/runelite/runelite/wiki/Developer-Guide
- Official example plugin template: https://github.com/runelite/example-plugin
- Plugin Hub repository and submission guide: https://github.com/runelite/plugin-hub
- RuneLite source repository: https://github.com/runelite/runelite
- RuneLite API Javadocs: https://static.runelite.net/runelite-api/apidocs/
- RuneLite client Javadocs: https://static.runelite.net/runelite-client/apidocs/
- Core Attack Styles plugin (weapon-style enum resolution): `net.runelite.client.plugins.attackstyles`
- Jagex-account development login guide: https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts
- Rejected or rolled-back features: https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features

Known-good local reference (do not depend on it at compile time — copy the approach, not the artifact):

- TickFlow `CombatStyleResolver` / `CombatStyle` / `PrayerSprites` / icon loading in the sibling `plugin` (TickFlow) repository.
- TickFlow `AttackCycleTracker` (trimmed port powers the opt-in attack-timer bar) and `TickFlowOverlay#drawTickPulse`/`TickFlowLayout` (tick-progress bar fill visual, ported to match exactly) in the same repository.
