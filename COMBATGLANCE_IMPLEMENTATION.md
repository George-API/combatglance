# Combat Glance — One-Shot Implementation Brief

## 0. Agent instruction

You are the senior Java/RuneLite engineer responsible for delivering a polished, runnable Combat Glance plugin in this repository.

Read `COMBATGLANCE_OVERVIEW.md` first and treat it as the product contract. Then inspect this repository, the official RuneLite example plugin, current RuneLite API/source, Plugin Hub requirements, and the sibling TickFlow repository (`../plugin`) before changing code.

Your task is to implement the complete MVP, test it, document it, and leave the repository ready for local gameplay validation and eventual Plugin Hub submission.

Do not stop after scaffolding, pseudocode, or a partial overlay. Make reasonable decisions independently. Prefer a smaller reliable implementation over speculative complexity.

Before coding, briefly record your implementation plan in the working log or final response. Then execute it without waiting for confirmation.

## 1. Source-of-truth links

Verify all implementation details against current official sources:

- Developer guide: https://github.com/runelite/runelite/wiki/Developer-Guide
- Example plugin template: https://github.com/runelite/example-plugin
- Plugin Hub setup/submission requirements: https://github.com/runelite/plugin-hub
- RuneLite source: https://github.com/runelite/runelite
- RuneLite API Javadocs: https://static.runelite.net/runelite-api/apidocs/
- RuneLite client Javadocs: https://static.runelite.net/runelite-client/apidocs/
- Core Attack Styles plugin: `net.runelite.client.plugins.attackstyles` in RuneLite source
- Jagex-account development login: https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts
- Rejected/rolled-back features: https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features

Local pattern reference (copy approach, do not add a compile dependency):

- Sibling TickFlow repo: `../plugin`
- Especially `CombatStyleResolver`, `CombatStyle`, `PrayerSprites`, `TickFlowIcons`, `build.gradle`, `runelite-plugin.properties`, overlay lifecycle, and tests
- For the two opt-in, off-by-default extras approved after MVP (see §4.2/§9.3/§10): `AttackCycleTracker` (trim — no `CycleFeedback`, no public phase enum, this plugin doesn't need either) powers the attack-timer bar; `TickFlowOverlay#drawTickPulse` + `TickFlowLayout`'s `PROGRESS_START`/`PROGRESS_END` power the tick-progress bar, ported to match the fill visual exactly

When documentation and checked-out source disagree, use the version resolved by the project build and note the discrepancy.

## 2. Non-negotiable constraints

- Java only.
- Passive observation and rendering only.
- Never click, move, pray, attack, swap menus, modify input, or automate gameplay.
- No boss-specific helpers, predictions, or “you should pray X” warnings.
- No tile, prayer, gear, or attack recommendations.
- No tick timeline, attack-speed inference, or metronome by default. Three narrow, off-by-default toggles are the approved exception (see §4.2/§9.3/§10): an attack-timer bar, a generic tick-progress bar, and a tick sound (copied from TickFlow, but config-panel-only — no overlay-embedded mute/volume buttons). None has rolling history, none is on unless the player opts in, and none should grow into a timeline — that scope stays TickFlow's.
- No external network calls or telemetry.
- No new third-party dependencies unless unavoidable.
- No reflection, JNI, subprocesses, runtime downloads, or bundled executable code.
- Keep the project compatible with standard Plugin Hub build expectations.
- Use `build=standard` unless the repository genuinely requires otherwise.
- Keep all state bounded and resettable.
- Do not block the client thread or perform heavy work in render methods.

## 3. Deliverables

Produce a complete repository containing at minimum:

```text
src/main/java/com/combatglance/
  CombatGlancePlugin.java
  CombatGlanceConfig.java
  CombatGlanceOverlay.java
  CombatGlanceState.java
  CombatGlanceSnapshot.java
  CombatStyle.java
  CombatStyleResolver.java
  OverlayMode.java
  PrayerKind.java
  PrayerClassifier.java
  PrayerSprites.java
  CombatGlanceIcons.java
  AttackCycleTracker.java       # opt-in attack-timer bar only — free-running tick-anchor model, see §4.2/§9.3
  AttackAnimations.java         # opt-in attack-timer bar only — bounded animation-ID allowlist that drives re-anchoring, see §4.2
  TickMetronome.java            # opt-in tick sound only — copied verbatim from TickFlow, see §10/§11

src/test/java/com/combatglance/
  CombatGlancePluginTest.java   # run harness: ExternalPluginManager.loadBuiltin
  CombatStyleTest.java
  PrayerClassifierTest.java
  CombatGlanceStateTest.java
  CombatGlanceSnapshotTest.java
  PrayerSpritesTest.java
  AttackCycleTrackerTest.java
  AttackAnimationsTest.java

src/test/resources/
  logback-test.xml

README.md
LICENSE
runelite-plugin.properties
build.gradle
settings.gradle
COMBATGLANCE_OVERVIEW.md
COMBATGLANCE_IMPLEMENTATION.md
```

Class names may be consolidated when doing so genuinely improves clarity, but avoid a monolithic plugin class.

Also update or add:

- Correct `pluginMainClass` in `build.gradle` (`com.combatglance.CombatGlancePluginTest`)
- Correct package names (`com.combatglance`)
- Plugin metadata
- Useful README screenshots placeholder section
- Manual validation checklist
- Known limitations

This repository already contains Gradle wrapper files, LICENSE, `.gitignore`, and these two briefs. Keep them. Replace the starter README with the full user-facing README specified below.

## 4. Recommended architecture

This plugin is much smaller than TickFlow. There is no rolling history and no state machine. The model is a **current snapshot** sampled from client state and rendered as a two-section card.

### 4.1 `CombatGlancePlugin`

Responsibilities:

- RuneLite lifecycle
- Dependency injection
- Overlay registration/removal
- Event subscriptions
- Forward normalized observations into `CombatGlanceState`
- Reset / hide on invalidating game transitions

Keep business logic out of event handlers. Handlers should read client state and call state methods.

Likely injected dependencies:

- `Client`
- `OverlayManager`
- `CombatGlanceOverlay`
- `CombatGlanceConfig`
- `SpriteManager` (via icons helper)
- `ItemManager` — resolves weapon attack speed (`ItemEquipmentStats#getAspeed`) for the opt-in attack-timer bar; not needed if that toggle is skipped
- `ConfigManager`

Use the current official APIs and imports resolved by the project.

### 4.2 `CombatGlanceState`

Owns the current session model:

- Whether the local player is in a renderable logged-in state
- Current `CombatStyle` (MELEE / RANGED / MAGIC)
- Current primary offensive `Prayer` or none
- Current overhead `Prayer` or none
- Optional debug fields: weapon category, style index, equipment fingerprint, full active-prayer mask
- Optional (opt-in attack-timer bar only): an internal `AttackCycleTracker` plus a local tick counter

Required operations:

- `refresh(Client client)` — sample and replace the snapshot
- `reset(reason)`
- immutable `CombatGlanceSnapshot snapshot()` for rendering

Additional operations for the opt-in attack-timer bar — keep these as thin pass-throughs to `AttackCycleTracker`, called only from the plugin's event handlers, never from `refresh` itself:

- `onGameTick()` — advance the local tick counter and the tracker's inactivity check; call once per real `GameTick`, before `refresh`
- `onAttackObserved()` — best-effort, animation-based re-anchor signal; safe to call more than once per tick or not at all (no dedup needed — see redesign note below)
- `onWeaponSpeedResolved(int aspeed)` — called from the plugin's equipment-cache refresh on an actual weapon change, **and also from a `ConfigChanged` handler** the moment `showAttackTimer` turns on (see bug note below)

`refresh` itself calls `AttackCycleTracker.noteEngagement(tickIndex, targetIdentity)` / `noteDisengaged()` directly (not a separate `CombatGlanceState` pass-through) whenever it samples `local.getInteracting()`, since that's already where target identity is computed.

Do not expose mutable collections to the overlay. Do not update state from `render`.

**Redesigned after QA — this replaced a fragile first version, read before touching this again:** the original port required `onCredibleAttack(tickIndex)` — a *fresh, correctly-timed* observation of literally every attack — to keep the ring (this feature was a ring at the time) showing anything, matching TickFlow's rolling-timeline tracker it was ported from. In practice this barely worked: `AnimationChanged` doesn't reliably fire once per attack (many weapons don't return to an idle animation between swings), and a single observation that arrived earlier than the previous prediction discarded *all* learned state back to "unknown" (an "impossible early attack" safety check that made sense for TickFlow's tick-perfect feedback badges, but was far too strict for a simple readiness bar). The result: the bar (then a ring) stayed hidden for most of a fight — exactly the "doesn't always/consistently display" bug report that triggered this rewrite.

The current model needs only two things to show something: a known `speedTicks` (from `ItemEquipmentStats#getAspeed`) and one anchor tick (when combat with the current target began). From there `AttackCycleTracker.ticksUntilReady`/`elapsedFraction` are pure modulo arithmetic against that anchor — no drift, no per-attack reconfirmation. `onAttackObserved` still exists and still re-anchors when it fires, correcting phase error opportunistically, but a missed or mistimed observation no longer discards anything. See `AttackCycleTracker`'s own class javadoc for the full rationale before changing this again.

**Real bug found in QA, now fixed — read before touching this again:** weapon attack speed was originally only pushed into the tracker on a weapon-*change* event (`ItemContainerChanged` detecting a different weapon id). That means enabling `showAttackTimer` mid-session, with a weapon already equipped from before the toggle was flipped, left the tracker with no weapon-speed knowledge at all until the next swap. This is exactly the "toggle a setting on while already fighting" scenario a real user will hit first. Fix: subscribe to `ConfigChanged`, and when `showAttackTimer` becomes enabled, immediately resolve and push the *currently* cached weapon's attack speed (via `clientThread.invoke`, since this touches client-thread-confined state) rather than waiting for a swap that may never come.

**Real bug found in QA, now fixed — read before touching this again:** `onAnimationChanged` originally re-anchored on *any* animation change while the player had an interacting target (`local.getAnimation() > 0`). That's far too loose — eating, fletching, most emotes, and plenty of other non-attack animations happen mid-fight and were each nuking the cycle's phase to the wrong tick, which is exactly why the bar looked like it "didn't restart precisely after every completed attack": it was restarting on the wrong things. Fixed by checking each animation ID against `AttackAnimations.isAttack(int)` — a bounded, hand-curated allowlist of real attack animation IDs (melee stances, bows/crossbows/blowpipe/thrown ammo, standard-book spellcasting) cross-checked against a real published reference plugin (`ngraves95/attacktimer`'s `AnimationData.java`) and verified to exist in this project's resolved `net.runelite.api.gameval.AnimationID` via `javap`. Same "explicit list beats a guess" philosophy as `PrayerClassifier` — not exhaustive (no per-boss or per-special-attack entries), but under-covering is safe here since a missed animation just skips one re-anchor opportunity and the free-running cycle keeps the bar showing regardless (see the redesign note above). The attack-style cell also now shows a bold centered ticks-remaining number (icon dimmed behind it) alongside the bar, so the countdown is legible without needing to read bar-fill length by eye.

**Real bug found in QA, now fixed — read before touching this again:** `AttackCycleTracker.elapsedFraction`/`ticksUntilReady` originally treated `intoCycle` as 0-indexed (`intoCycle / speedTicks`), so across a `speedTicks`-length cycle the bar only ever reached `(speedTicks-1)/speedTicks` (e.g. 75% for a 4-tick weapon) before snapping back to empty at the wrap — it never visibly touched the right edge, which is what "the final tick fills out the bar to the end, currently it ends just before the end" was reporting. The overlay had a `ticksUntilReady == 0 ? 1.0 : elapsedFraction` special case trying to patch this at the single boundary instant, but that instant is also where a real re-anchor typically lands, so the full-bar frame rarely rendered. Fixed by making both methods 1-indexed (`(intoCycle + 1) / speedTicks`, `speedTicks - 1 - intoCycle`): the current tick counts as fully elapsed for its whole ~0.6s render duration, so the *last waiting tick* — not just a boundary instant — reads a completely full bar and `ticksUntilReady == 0`. The overlay's special case was removed since it's no longer needed. See `AttackCycleTracker#elapsedFraction`'s javadoc for the full reasoning before changing this again.

**Known, accepted limitation — do not try to silently "fix" this without discussing scope first:** attack detection here is `AnimationChanged`-only (`local.getAnimation() > 0 && local.getInteracting() != null`), unlike TickFlow's primary signal (classifying "Attack" menu clicks via `MenuOptionClicked`, with animation only as corroboration). Two consequences, both inherent to this simpler design, not implementation bugs:
1. Weapons whose attack animation doesn't reset to an idle value between swings may not re-fire `AnimationChanged` for every attack, since the event only fires on an actual value *change*. Polling `getAnimation()` every tick instead of subscribing to the event would not help — RuneLite's event already fires on exactly that same tick-to-tick value change, so polling can only ever see what the event already reports.
2. Any positive-animation action performed while an interacting target happens to be set (eating, drinking a potion) can be misread as an attack, since there is no allowlist of genuine attack-animation IDs and building one would be exactly the kind of "giant speculative mapping" the quality bar (§17) warns against.
Building a proper fix means porting (a trimmed version of) TickFlow's menu-click classifier — a real scope increase, not a bug fix. If this needs to happen, treat it as a product decision (bigger opt-in feature, more code to review for Plugin Hub), not something to slip in quietly.

### 4.3 `CombatGlanceSnapshot`

Immutable render model:

```text
loggedIn
combatStyle
offensivePrayer          # nullable
overheadPrayer           # nullable
debugWeaponCategory      # optional
debugStyleIndex          # optional
ticksUntilAttackReady    # opt-in attack-timer bar only; -1 when not confidently known
attackCycleFraction      # opt-in attack-timer bar only; -1 when not confidently known, else [0,1]
```

Expose `isAttackTimerKnown()` as a derived convenience (`ticksUntilAttackReady >= 0 && attackCycleFraction >= 0`) rather than making the overlay check both raw fields. The overlay reads only this snapshot — never invents a countdown number when unknown.

### 4.4 `CombatStyle` + `CombatStyleResolver`

Copy and adapt TickFlow's known-good approach.

`CombatStyle`: `MELEE`, `RANGED`, `MAGIC`, plus `fromAttackStyleName(String)` mapping:

- `RANGING`, `LONGRANGE` → RANGED
- `CASTING`, `DEFENSIVE_CASTING` → MAGIC
- everything else (Accurate, Aggressive, Controlled, Defensive, Crush, …) → MELEE
- null/empty → MELEE

`CombatStyleResolver.resolve(Client)`:

1. Read `VarbitID.COMBAT_WEAPON_CATEGORY`
2. Read `VarPlayerID.COM_MODE` (selected style index)
3. Read `VarbitID.AUTOCAST_DEFMODE` for staff defensive-casting slot offset
4. Resolve style name from `EnumID.WEAPON_STYLES` + style structs (`ParamID.ATTACK_STYLE_NAME`), matching core Attack Styles / TickFlow
5. Map the name through `CombatStyle.fromAttackStyleName`
6. On failure, fall back conservatively to MELEE (or `ItemEquipmentStats` strongest bonus if that path is already implemented and tested)

Keep fallbacks for weapon categories missing from the enum (TickFlow currently special-cases types 22 and 30). Re-verify against current RuneLite source; do not blindly copy stale IDs without checking.

### 4.5 `PrayerKind` + `PrayerClassifier`

`PrayerKind`:

```text
OFFENSE
OVERHEAD
OTHER
```

`PrayerClassifier` is a pure, allowlisted mapping from `net.runelite.api.Prayer` to `PrayerKind`. No string matching against prayer names for classification. Future prayers that are not in the allowlist are `OTHER` and stay off the card.

**OVERHEAD** (defense slot) — standard book:

- `PROTECT_FROM_MAGIC`
- `PROTECT_FROM_MISSILES`
- `PROTECT_FROM_MELEE`
- `RETRIBUTION`
- `REDEMPTION`
- `SMITE`

**OVERHEAD** — Ruinous Powers (verify exact enum names against the resolved API):

- `RP_DAMPEN_MAGIC`
- `RP_DAMPEN_RANGED`
- `RP_DAMPEN_MELEE`
- `RP_WRATH`

**OFFENSE** — combined / high-tier:

- `PIETY`, `CHIVALRY`
- `RIGOUR`
- `AUGURY`
- `DEADEYE`, `MYSTIC_VIGOUR` if present in the API

**OFFENSE** — style prayers:

- Strength: `BURST_OF_STRENGTH`, `SUPERHUMAN_STRENGTH`, `ULTIMATE_STRENGTH`
- Attack: `CLARITY_OF_THOUGHT`, `IMPROVED_REFLEXES`, `INCREDIBLE_REFLEXES`
- Ranged: `SHARP_EYE`, `HAWK_EYE`, `EAGLE_EYE`
- Magic: `MYSTIC_WILL`, `MYSTIC_LORE`, `MYSTIC_MIGHT`

**OFFENSE** — Ruinous (verify names):

- `RP_ANCIENT_STRENGTH`, `RP_ANCIENT_SIGHT`, `RP_ANCIENT_WILL`
- `RP_DECIMATE`, `RP_ANNIHILATE`, `RP_VAPORISE`
- Include other clearly offensive Ruinous combat prayers only if the API enum is unambiguous. When unsure, classify as `OTHER`.

**OTHER** (never occupies offense or defense slots):

- Skin prayers: `THICK_SKIN`, `ROCK_SKIN`, `STEEL_SKIN`
- `RAPID_RESTORE`, `RAPID_HEAL`, `PROTECT_ITEM`, `PRESERVE`
- Ruinous utility: `RP_PROTECT_ITEM`, `RP_REJUVENATION`, `RP_RUINOUS_GRACE`, `RP_BERSERKER`, `RP_METABOLISE`, vows, etc. unless clearly overhead or offensive
- Anything unknown

Primary offensive prayer selection when several OFFENSE prayers are active:

```text
1. Combined prayer matching current CombatStyle
   melee  → Piety, then Chivalry
   ranged → Rigour, then Deadeye, then Eagle Eye
   magic  → Augury, then Mystic Vigour, then Mystic Might
2. Any remaining combined prayer (Piety / Chivalry / Rigour / Augury)
3. Style-matching single-skill prayer (strength/attack for melee, ranged prayers, magic prayers)
4. Any other active OFFENSE prayer, stable enum-order tie-break
```

The function must be deterministic. Unit-test it.

**Optional style-mismatch check (opt-in, §9.3/§10):** a separate pure function, `styleOf(Prayer)`, maps a standard-book OFFENSE prayer to the single `CombatStyle` it's designed for (melee/ranged/magic), returning null for Ruinous Powers offense prayers (no verified style pairing — same reasoning as their absence from the priority chains above) and for non-offense prayers. This is used only to render the optional mismatch highlight; it must never feed into `primaryOffense` selection itself.

Overhead selection: the single active OVERHEAD prayer, or none. If the client somehow reports two (it should not), pick by enum order and note it in debug.

Sampling: iterate `Prayer.values()` and `client.isPrayerActive(prayer)` (or the current equivalent). Do not invent a bit-mask protocol unless you also test it against `Prayer.ordinal()` limits.

**Resolved-source discrepancy (§1):** `Client.isPrayerActive` is `@Deprecated` as of the checked-out RuneLite version, with a javadoc note that it "does not properly handle deadeye/eagle eye or mystic vigour/might" — those pairs share one prayer-book slot once the higher tier is unlocked, so the raw boolean can read Eagle Eye/Mystic Might as active even after Deadeye/Mystic Vigour replaces them. There is no non-deprecated replacement method on `Client`. Core RuneLite's own Prayer plugin still calls the deprecated method too, but gates the ambiguous pairs with an extra check (`PrayerType#isEnabled`, reading the `PRAYER_DEADEYE_UNLOCKED` / `PRAYER_MYSTIC_VIGOUR_UNLOCKED` varbits and `BR_INGAME` to exclude LMS). Mirror that exact gate when sampling: treat `EAGLE_EYE`/`MYSTIC_MIGHT` as inactive whenever the corresponding higher tier is unlocked (and not in LMS), even if the deprecated call reports them active.

### 4.6 `PrayerSprites`

Map each relevant `Prayer` to its official on-state sprite (`SpriteID.Prayeron.*` and Ruinous `SpriteID.IconPrayerZaros01_30x30.*`).

Copy and trim TickFlow's `PrayerSprites` mapping. Keep:

- `spriteId(Prayer)` with a generic prayer-orb fallback
- `shortLabel(Prayer)` compact enough for a card slot (Protect, Piety, Rigour, None)
- Do **not** require `matchFromText` unless you have a real use for menu text

### 4.7 `CombatGlanceIcons`

Load and cache:

- Combat family icons: `SpriteID.Staticons.ATTACK`, `.RANGED`, `.MAGIC` (the full skill icons — do not substitute `TinyCombatStaticons` or another compact variant)
- Prayer-on sprites via `PrayerSprites.spriteId` (`SpriteID.Prayeron.*` / `SpriteID.IconPrayerZaros01_30x30.*` / `02_30x30.*`, already the largest official on-sprite RuneLite exposes for each prayer)
- A dimmed/empty fallback for None slots (generic prayer orb at low alpha, or a simple drawn placeholder)

Use `SpriteManager.getSpriteAsync` like TickFlow. Scale with nearest-neighbor only (never `SCALE_SMOOTH`/bilinear) so pixel art stays sharp.

**Important divergence from TickFlow:** TickFlow's `crispFit` helper deliberately never upscales past native sprite size, because its 24px timeline icons already sit close to native resolution and are meant to read as a small inline log. Combat Glance's card is the opposite: it must be glanceable from a distance while the player watches the boss, and OSRS's native skill/prayer sprites (~25–30px) are the largest source art the client offers — there is no higher-resolution asset to fetch instead. So the icon helper here must support clean nearest-neighbor **upscaling**, not just downscaling:

- Primary slot icons: target ~36–40px (larger than TickFlow's 24px timeline icons), scaling *up* from native sprite size with nearest-neighbor when the source is smaller than the target — this is intentional and expected here, not a bug to avoid.
- Re-derive the cached bitmap when `overlayScale` (§10, 80–140%) changes, so enlarging the overlay enlarges icons crisply at the new pixel size instead of stretching an already-rasterized image with the Swing/AWT default (bilinear) transform.
- Prefer integer or near-integer scale factors from native size where practical (e.g. 1x/2x) to avoid uneven nearest-neighbor artifacts at odd scale ratios; document the chosen target sizes rather than leaving them implicit.

Clear caches on plugin shutdown.

### 4.8 `CombatGlanceOverlay`

RuneLite-native movable overlay. Prefer a custom `Overlay` (TickFlow pattern) if you need two large icon cells with section headers. `OverlayPanel` is acceptable only if the result still looks like a card, not a stack of title strings.

Responsibilities:

- Render Defense section on top: overhead-prayer cell
- Render Offense section below: attack-type cell + offensive-prayer cell
- Respect Learn vs Compact (headers / labels)
- Respect scale
- Hide when overlay disabled, not logged in, or optional auto-hide applies
- Remain allocation-light and side-effect free

Default position: near the top-left or above-chatbox-right without covering inventory. Let the user move it. Use standard overlay priority/layer conventions.

Visual style:

- No panel background and no outer card border — just the cells; RuneLite's own overlay-edit-mode highlight already outlines the bounds while dragging, so a self-drawn frame is redundant
- Section headers in small muted caps: `DEFENSE` / `OFFENSE`
- Three cells with consistent size
- Attack-type cell: skill icon + Melee/Ranged/Magic label; melee/ranged/magic accent applied as both cell border and a subtle tinted fill (see §9.3)
- Prayer cells: official on-sprite + short label; offense-prayer cell uses the same accent palette, keyed off which style *that prayer* is for (§4.5 `styleOf`)
- None cells: dimmed, labeled `None`, same size as filled cells
- No flashing, no combat-style “warning red” by default. Two narrow, opt-in exceptions exist — the offense/style mismatch border and the attack-timer bar/tick-progress bar — all off by default and documented in §9.3/§9.4/§10

## 5. RuneLite events and observations

Do not blindly assume these exact events remain sufficient. Inspect current API/source and choose the smallest robust set.

Primary candidates:

- `GameTick` — periodic full refresh of style + prayers (authoritative, cheap); also the tick clock for both opt-in extras (attack-cycle ticks, tick-progress bar's tick-start timestamp)
- `VarbitChanged` — immediate refresh when prayer or weapon-category / autocast varbits change, so overhead flicks do not wait a tick
- `ItemContainerChanged` — equipment container; refresh style after weapon swap, and (opt-in attack-timer bar only) re-resolve weapon attack speed
- `GameStateChanged` — reset/hide on login screen, hopping, connection lost, loading
- `ActorDeath` — local-player death; refresh (prayers drop) or reset
- `AnimationChanged` (opt-in attack-timer bar only) — best-effort attack observation; gate the whole handler on `showAttackTimer` being enabled so it costs nothing when the toggle is off
- `ConfigChanged` (opt-in attack-timer bar only) — not needed for the rest of the card, since `enabledOverlay`/`mode`/`overlayScale`/etc. are all read live at render time and need no push. The one real use: when `showAttackTimer` flips on, immediately resolve the already-equipped weapon's attack speed instead of waiting for the next weapon swap (see §4.2 for the bug this fixes)

Potential client APIs:

- `client.isPrayerActive(Prayer)`
- `VarbitID.COMBAT_WEAPON_CATEGORY`
- `VarPlayerID.COM_MODE`
- `VarbitID.AUTOCAST_DEFMODE`
- `EnumID.WEAPON_STYLES` + struct `ParamID.ATTACK_STYLE_NAME`
- Equipment container `InventoryID.WORN` / current equipment inventory ID
- `SpriteManager` / `SpriteID`

Immediate varbit updates are desirable for PvM working memory, but a `GameTick` refresh alone is an acceptable MVP if varbit filtering is messy — document the choice. Prefer both: tick as backstop, varbit as snappy path. Debounce by just calling `state.refresh(client)`; it is cheap.

Document the final event mapping in the plugin class javadoc and README.

## 6. Refresh model

Unlike TickFlow, there is no tick-assignment problem.

Recommended approach:

1. On each relevant event, if `GameState` is `LOGGED_IN` and local player exists, sample:
   - combat style via `CombatStyleResolver`
   - all active prayers via `client.isPrayerActive`
   - classify and pick primary offense + overhead
2. Store an immutable snapshot.
3. If not logged in / hopping / no local player, `reset` and let the overlay render nothing.
4. Overlay reads the snapshot only.

Do not buffer menu clicks. Do not infer future prayers. Do not keep history.

## 7. Classification rules

### 7.1 Attack type

A resolved family from weapon category + selected style. Never show slash/stab/crush. Never show Accurate/Aggressive.

If enums are missing, MELEE is the honest unarmed/default — not MAGIC.

### 7.2 Offensive prayer

Only `PrayerKind.OFFENSE`. Primary selection uses the documented priority. Do not show Steel Skin here.

### 7.3 Overhead prayer

Only `PrayerKind.OVERHEAD`. Do not show Protect Item here (it is not an overhead).

### 7.4 Other prayers

Observed internally for debug if useful. Never rendered on the card in MVP.

## 8. Reset and invalidation rules

Reset or hide on:

- Login/logout/world hop/loading/connection-lost transitions
- Local-player absence
- Plugin disable
- Configuration changes that toggle the overlay off

Weapon and prayer changes are **refreshes**, not full session resets. Do not flash or rebuild the overlay object on every prayer toggle.

On plugin shutdown: unregister overlay, clear icon caches, clear snapshot.

## 9. Overlay specification

### 9.1 Placement

Movable overlay, default `TOP_LEFT` or `ABOVE_CHATBOX_RIGHT`. User repositions with the usual RuneLite overlay drag.

### 9.2 Layout

Learn Mode:

```text
DEFENSE
 [ overhead icon ]
  Protect

OFFENSE
 [ style icon ] [ offense prayer icon ]
  Melee           Piety
```

Defense on top, Offense below — chosen so the overhead (the higher-stakes, more time-critical fact mid-fight) reads first.

Compact Mode: same cells, no `OFFENSE`/`DEFENSE` headers; labels optional via config.

Defense is a full-width row with a single cell aligned to the left (or centered under the pair — pick one and keep it consistent). Left-aligned under the style cell is slightly more “status panel”; centered is slightly more “emblem”. Prefer **left-aligned under the style cell** so slot positions never shift.

The Defense row's single cell leaves the same width empty to its right as the Offense row's second cell below it (both rows are laid out to the same two-cell content width). The opt-in tick-progress bar (§9.3/§10) renders in exactly that empty space — it does not change the panel's width or add a new row.

### 9.3 Visual style

- RuneLite-native dark panel
- Neutral whites/grays for labels
- One restrained accent per attack-style family (melee/ranged/magic), applied as both a cell border and a subtle tinted fill — not a neon fill
- The same family palette applies to the offense-prayer cell, keyed off `PrayerClassifier.styleOf(offensivePrayer)` (§4.5) rather than the current `CombatStyle` — this is default, always-on color-coding for fast per-icon recognition, not a comparison between cells. Prayers with no verified style pairing (Ruinous Powers offense prayers) and the empty **None** slot render with the neutral cell colors instead of guessing.
- No constant flashing
- Clear icons with text fallback
- Accessible without relying solely on color
- Exception: when `highlightPrayerMismatch` (§10) is enabled, the offense-prayer cell's *border* additionally switches to a red mismatch color if `PrayerClassifier.styleOf(offensivePrayer)` is non-null and differs from the current `CombatStyle` — the fill still shows the prayer's own family color underneath, so the two signals layer rather than replace each other. Off by default; this is the one deliberate, opt-in departure from "no warning red" elsewhere in this section and in the overview.
- Exception: when `showAttackTimer` (§10) is enabled and the cadence is confidently known (`CombatGlanceSnapshot#isAttackTimerKnown`), a thin horizontal progress bar renders along the bottom inside edge of the attack-type cell, inset a couple px from the cell border. Same shared bar-drawing routine as the tick-progress bar below (see §4.7-equivalent visual note), not a separate visual language. The attack-style icon is **not** dimmed for this — a bar under the icon doesn't compete with it the way an earlier full-icon-overlay ring design did. Hidden — not a guessed value — whenever confidence isn't there yet. Off by default. (An earlier version of this was a clockwise-sweep ring with a tick-countdown number, drawn over a dimmed icon; replaced for a cleaner look per direct product feedback — do not resurrect the ring/number treatment without discussing it first.)
- Exception: when `showTickPulse` (§10) is enabled, a horizontal amber-to-mint bar renders in the empty space beside the overhead cell (see §9.2), filling left-to-right through the current game tick and resetting at each tick boundary. This is a generic tick heartbeat, not attack-specific, and carries no correctness signal — it is not part of the "warning red" family of exceptions. Match TickFlow's `drawTickPulse` fill visual exactly: dark track (`40,44,48`), white leading-edge marker, `GradientPaint` reveal from amber (`210,175,85`) to mint (`0,220,165`). Off by default. The attack-timer bar above reuses this exact same drawing routine and colors — one shared helper, not two near-duplicates.
- Audio exception (not visual): when `tickSound` (§10) is enabled, a soft synthesized blip plays once per game tick via `TickMetronome` (copied from TickFlow). No overlay-drawn controls for this — see §11.

If custom icons are included, place them in `src/main/resources` and load through `getResourceAsStream`-compatible methods. Prefer official sprites.

### 9.4 Debug mode

Optional debug section under the card:

```text
Style: MELEE
Weapon category: N
COM_MODE: N
Offense: Piety
Overhead: Protect from Melee
Active: Piety, Steel Skin, Protect Item
Attack timer: 3 ticks   # or "unknown" — only meaningful once showAttackTimer is on
```

Off by default.

## 10. Configuration interface

Implement a concise `Config` interface using RuneLite `@ConfigItem` conventions. Group: `combatglance`.

Recommended keys:

```text
enabledOverlay             # default true
mode                       # LEARN / COMPACT, default LEARN
showLabels                 # default true
overlayScale               # 80–140, default 100
autoHideOutsideCombat      # default false; hide after a short inactivity if easy
debugMode                  # default false
highlightPrayerMismatch    # default false; see §9.3
showAttackTimer            # default false; see §9.3/§4.2 — attack-timer bar
showTickPulse              # default false; see §9.2/§9.3 — generic tick-progress bar
tickSound                  # default false; see §11 — config-panel-only, no overlay buttons
tickSoundVolume            # default 40, range 5-100; only meaningful when tickSound is on
```

`showHeaders` may be implied by LEARN vs COMPACT rather than a separate toggle.

**Amended after MVP:** the original brief said "optional tick audio must not be added — this is not a metronome," full stop. That's no longer accurate — `tickSound`/`tickSoundVolume` were added, explicitly reviewed and approved as a third narrow, off-by-default exception alongside `showAttackTimer`/`showTickPulse` (see §2). The one line that's still true without qualification: no overlay-embedded controls. TickFlow draws clickable mute/volume buttons directly on its overlay; this plugin deliberately does not — both settings live only in the config panel, so the card never grows a clickable surface. Do not add overlay buttons for this without treating it as a product decision first.

## 11. Threading and performance

- Treat subscribed client events as the state-update path.
- Do not use background loops.
- Do not sleep.
- Do not poll continuously outside RuneLite events.
- Keep render work allocation-light.
- Cache sprites and scaled images.
- Do not mutate shared state from the overlay.
- Use immutable snapshot transfer or client-thread-confined state.

`refresh` on GameTick plus occasional varbits is cheap. Do not iterate huge tables. The prayer enum is small; iterating it each tick is fine.

**Found and fixed in QA — a real example of what "allocation-light" means in practice, since `render()` runs every client frame, not every game tick:**
- The outer panel frame was a fresh `RoundRectangle2D.Float` every call; removed along with the panel border (§9.3).
- An earlier ring-shaped version of the attack-timer bar freshly constructed a `BasicStroke` and `Arc2D` every call; both the ring and that particular allocation problem are gone now that the feature is a plain bar sharing the tick-progress bar's drawing routine.
- `onVarbitChanged` fires for every varbit change client-wide, the large majority of them irrelevant to this plugin (minimap, run energy, interface widgets, …). The prayer-varbit membership check originally used a `Set<Integer>`, which autoboxes the queried `int` on every single irrelevant call just to say "no". Switched to a sorted `int[]` checked via `Arrays.binarySearch` — no boxing, no allocation on the hot path.

**Tick sound and threading:** `TickMetronome.play()` is called synchronously from the `GameTick` handler, on the client thread — not a background thread, no violation of "do not use background loops." `AudioPlayer.play()` (the RuneLite API this goes through) handles actual playback off-thread internally; this plugin never touches `javax.sound` directly, matching TickFlow's approach and the Plugin Hub's audio requirements.

## 12. Testing requirements

### 12.1 Unit tests

`CombatStyleTest` must cover:

1. Accurate / Aggressive / Controlled / Defensive → MELEE
2. Ranging / Longrange → RANGED
3. Casting / Defensive casting → MAGIC
4. null / empty → MELEE

`PrayerClassifierTest` must cover:

1. Piety, Rigour, Augury are OFFENSE
2. Protect from Melee / Missiles / Magic are OVERHEAD
3. Dampen prayers and Wrath are OVERHEAD if present
4. Steel Skin, Rapid Heal, Preserve, Protect Item are OTHER
5. Primary offense with Piety + Ultimate Strength → Piety
6. Primary offense with Ultimate Strength + Incredible Reflexes (no combined) is deterministic
7. Primary offense prefers Rigour when style is RANGED even if a melee prayer is somehow also active
8. No active offense → null
9. No active overhead → null
10. Unknown / unlisted enum values (if you can simulate) → OTHER

`CombatGlanceStateTest` must cover:

1. Snapshot is immutable / defensive
2. Reset clears prayers and logged-in flag
3. Refresh path can be tested with a small fake or by testing classifier+style in isolation if Client is too heavy to mock — do not write brittle Mockito theater. Prefer pure functions.

`PrayerSpritesTest` must cover:

1. Protect from Melee maps to the on-sprite
2. Null prayer uses generic orb
3. Short labels fit a slot (Protect, Piety, None)

`CombatGlanceSnapshotTest` must cover the derived `isOffenseStyleMismatch`/`isAttackTimerKnown` logic directly (construct snapshots with the package-private constructor; no `Client` needed):

1. No offense prayer → never a mismatch
2. Offense prayer matching current style → no mismatch
3. Offense prayer for a different style → mismatch
4. Ruinous Powers offense prayer → never a mismatch (no verified style pairing)
5. Negative ticks-until-ready / fraction → attack timer reported unknown

`AttackCycleTrackerTest` (opt-in attack-timer bar only) must cover, at minimum:

1. Unknown until both a weapon speed *and* an engagement anchor are set — neither alone is enough
2. Free-runs correctly from the anchor at the known speed (`ticksUntilReady` counts down and wraps)
3. Does **not** require reconfirming every individual attack — this is the whole point of the redesign; assert the cycle stays correct across many ticks with no further `onAttackObserved` calls at all
4. An observed attack re-anchors (self-corrects drift) rather than being rejected when it disagrees with the previous prediction
5. A weapon-speed change clears the anchor until the next engagement; the *same* speed pushed again does not
6. A target change re-anchors cleanly
7. Unknown speed hides readiness rather than guessing
8. Inactivity beyond the timeout clears both speed and anchor
9. `elapsedFraction` tracks progress through the cycle correctly, including immediately after wrapping, and reads exactly 1.0 (fully filled) on the last waiting tick before the next attack — not just short of it

Avoid tests that simply mirror implementation details.

### 12.2 Build checks

Run the repository's official tasks, at minimum:

```bash
./gradlew clean test
./gradlew build
```

On Windows, use:

```powershell
.\gradlew.bat clean test
.\gradlew.bat build
```

Also run the template's development task, normally:

```bash
./gradlew run
```

Confirm the plugin appears in RuneLite configuration and can be enabled/disabled without errors.

### 12.3 Manual gameplay test checklist

Document and perform as much as possible:

- Login and enable plugin
- Overlay appears and is movable
- Unarmed / scimitar / bow / staff (melee style vs autocast) show the correct family
- Piety / Rigour / Augury occupy the offense slot
- Protect from Melee / Missiles / Magic occupy the defense slot
- Turning prayers off shows None without collapsing the card
- Steel Skin / Protect Item / Preserve do not steal a slot
- Weapon swap updates attack type
- Overhead swap updates immediately enough to be useful in PvM
- Logout / hop / death / plugin disable clears or hides the card
- Learn vs Compact modes
- Attack-timer bar (opt-in): off by default shows nothing extra; on, bar appears once cadence is confidently known, stays visible across many consecutive attacks (not just the first), and clears cleanly on weapon swap / target change / logout
- Tick-progress bar (opt-in): off by default shows nothing extra; on, bar fills and resets every game tick beside the overhead cell
- Tick sound (opt-in): off by default, silent; on, a soft blip plays every game tick with no overlay-drawn controls, and stops immediately when disabled or on plugin shutdown
- No meaningful FPS degradation
- No exceptions in logs

If interactive login is unavailable, complete all non-login tests and state exactly what remains for the human tester.

## 13. Development login and local run instructions

Use the official example-plugin workflow, same as TickFlow.

1. Ensure the project is based on or compatible with `https://github.com/runelite/example-plugin`.
2. Use the Java version currently required by the template/plugin-hub repository. Do not rely on stale prose; verify the build files and CI. TickFlow currently uses Java 11 (`options.release.set(11)`) and `latest.release` RuneLite.
3. Run the Gradle `run` task.
4. For a Jagex Account, follow:
   `https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts`
5. Treat `.runelite/credentials.properties` as sensitive. Never print, commit, copy, or expose it.
6. Delete/invalidate development credentials after testing if desired, following the official guide.

Add these steps to README with Windows-friendly commands.

## 14. Plugin metadata

Set polished metadata, adapting author/package placeholders to the repository owner (TickFlow uses `author=Noob` and package `com.tickflow` — match the same author unless the owner prefers otherwise):

```properties
displayName=Combat Glance
author=Noob
description=Glanceable PvM card of your attack type, offensive prayer, and overhead prayer.
tags=pvm,combat,prayer,melee,ranged,magic,overhead,overlay
plugins=com.combatglance.CombatGlancePlugin
version=1.0.0
build=standard
```

Use a concise `@PluginDescriptor`:

```text
name=Combat Glance
description=Glanceable attack type, offensive prayer, and overhead prayer for PvM.
tags={"pvm", "combat", "prayer", "overlay", "melee", "ranged", "magic"}
```

Add an optional `icon.png` at repository root only if a suitable final icon exists, respecting current Plugin Hub dimensions (TickFlow: 48×48). A simple card-with-swords/prayer motif is enough; do not block MVP on a custom icon.

## 15. README requirements

Write a user-facing README containing:

1. One-sentence value proposition
2. Current feature list
3. Screenshot/GIF placeholder
4. Accuracy disclaimer (client-side observation, not advice)
5. Installation for local development
6. Build/test commands
7. Jagex Account login link and credential warning
8. Configuration summary
9. Manual validation checklist
10. Known limitations
11. Plugin Hub submission/update steps
12. License

Keep it concise and credible. Do not market unimplemented capabilities. Do not claim it recommends prayers.

## 16. Plugin Hub readiness

Follow the current official guide at `https://github.com/runelite/plugin-hub`.

Before calling the repository launch-ready:

- Repository is public-ready and contains a BSD 2-Clause license.
- No forbidden language/features.
- No unnecessary dependencies.
- `runelite-plugin.properties` is complete.
- Build uses the current RuneLite release configuration expected by the template.
- Tests pass.
- README is accurate.
- Plugin does not duplicate an existing plugin without a clear distinct purpose (see overview §15).
- Review the rejected/rolled-back feature list.
- Create a Plugin Hub manifest file containing the repository HTTPS URL and exact 40-character commit hash when submitting.
- Confirm Plugin Hub CI and requested changes.

Suggested GitHub repo name: `combatglance` (or `combat-glance`) under the same owner as TickFlow (`George-API`).

Do not automatically submit or push unless repository credentials and explicit permission are already available. Prepare exact commands/instructions instead. `gh` may not be logged in on this machine.

## 17. Quality bar

The result should feel like a finished narrow plugin, not an AI-generated prototype.

Required qualities:

- Compiles cleanly
- Tests pass
- No obvious dead code
- No giant speculative mappings beyond the prayer allowlist
- Clear naming
- Small methods
- Documented classification rules
- Graceful unknown/empty states
- Overlay polished at default scale
- Settings concise
- Lifecycle cleanup correct
- No misleading claims or “wrong prayer” language

Run formatting/checkstyle tasks provided by the repository and fix all failures.

Reuse TickFlow patterns where they already solve the problem (style resolver, prayer sprites, sprite caching, overlay chrome, Gradle `run` harness). Do not copy TickFlow's rolling timeline, metronome, or action classifier — those are the tools that make TickFlow TickFlow.

**Amended after MVP:** an `AttackCycleTracker` (redesigned from a naive TickFlow port into its own simpler free-running model — see §4.2), TickFlow's tick-pulse fill visual, and TickFlow's `TickMetronome` sound (copied verbatim) were added, each behind its own off-by-default config toggle (`showAttackTimer`, `showTickPulse`, `tickSound` — §10), each reviewed and explicitly approved as a narrow exception rather than scope creep. None gained a rolling history, per-action classification, or overlay-embedded controls — the parts of TickFlow this plugin still does not become. If a future change to any of these toggles starts requiring one of those, stop and treat it as a product decision, not an implementation detail.

## 18. Execution sequence

Use this order:

### Phase 1 — inspect and align

- Inspect this repository and TickFlow / example-plugin versions.
- Verify current `Prayer` enum names, varbit IDs, and sprite IDs against the resolved RuneLite client.
- Identify reusable official utilities (Attack Styles enums, SpriteManager).
- Record a concise architecture decision.

### Phase 2 — pure model

- Implement `CombatStyle`, `CombatStyleResolver` (pure parts), `PrayerKind`, `PrayerClassifier`, snapshot.
- Write unit tests first or alongside implementation.

### Phase 3 — event integration

- Wire plugin lifecycle, `GameTick`, varbit/equipment refreshes, resets.
- Add debug diagnostics.

### Phase 4 — polished overlay

- Implement Learn Mode card with official icons.
- Add Compact Mode.
- Tune dimensions, empty states, and lifecycle.

### Phase 5 — verify and document

- Run all tests/build/checks.
- Resolve warnings/errors.
- Write README and known limitations.
- Provide exact local test steps.

## 19. Completion response

At completion, report:

1. What was implemented
2. Files created/changed
3. Exact build/test commands run and results
4. What was validated automatically
5. What requires live OSRS testing
6. Known limitations
7. Exact next prompt or human steps to run the development client and validate
8. Plugin Hub readiness gaps, if any

Do not claim live gameplay validation unless it actually occurred.
