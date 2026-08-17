# Combat Glance

![Combat Glance](cover.png)

A glanceable PvM overlay: melee, ranged, or magic — which offensive prayer is on — which overhead is on. Nothing more.

## Features

- **Defense row** (top) — active overhead prayer (Protect from Melee/Missiles/Magic, Retribution, Redemption, Smite, Ruinous Dampen, Wrath), or a calm None.
- **Offense row** (bottom) — current attack style (Melee/Ranged/Magic), resolved from equipped weapon and selected attack style, plus active offensive prayer (Piety, Rigour, Augury, single-skill style prayers, and Ruinous Powers equivalents).
- Color-coded by style (brown/green/blue) on both the attack-type and offensive-prayer cells, so a matching pair reads at a glance.
- Learn Mode (headers + labels) and Compact Mode (slots only).
- Official OSRS sprites, upscaled with nearest-neighbor scaling to stay crisp.
- Optional, off by default:
  - Offense/style mismatch highlight — red border when your active offensive prayer doesn't match your attack style.
  - Attack-timer bar — swing/cast/cooldown progress under the attack-style icon, plus a centered ticks-remaining number. Best-effort; hidden when the cadence isn't confidently known.
  - Tick-progress bar — a generic per-tick heartbeat beside the overhead cell.
  - Tick sound — a soft metronome blip once per tick, config-panel-only (no on-overlay controls).
  - Debug diagnostics — raw prayer names, weapon category, style index, attack-timer state.
- Movable, scalable (80–140%), no gameplay automation.

## How it works

Combat Glance is a **passive observation** of client-side state — it never clicks, prays, moves, or recommends anything.

- Attack style is inferred from the equipped weapon and selected attack style, the same source the core Attack Styles plugin uses. Unknown weapons fall back to Melee.
- When multiple offensive prayers are active, only the primary one is shown, chosen by a deterministic priority (see `PrayerClassifier`).
- No "wrong prayer" warnings, no boss schedules, no gear recommendations — just current state.

## Installation

Search **Combat Glance** in the RuneLite Plugin Hub and install it.

### Local development

```powershell
.\gradlew.bat run
```

```bash
./gradlew run
```

Launches RuneLite with the plugin loaded in developer mode. Requires a [Jagex Account login](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) — treat `.runelite/credentials.properties` as a secret and never commit it.

```powershell
.\gradlew.bat clean test build
```

## Configuration

All settings live under **Combat Glance** in the RuneLite plugin panel:

| Setting | Default | Description |
|---|---|---|
| Enable overlay | On | Show or hide the card |
| Overlay mode | Learn | Learn (headers + labels) or Compact (slots only) |
| Show text labels | On | Short labels under each icon |
| Overlay scale % | 100 | 80–140% |
| Hide outside combat | Off | Hides the card after ~10s without an interacting target |
| Debug diagnostics | Off | Raw prayer names, weapon category, style index, attack-timer state |
| Highlight offense/style mismatch | Off | Red cell border when the offensive prayer doesn't match the current attack style |
| Show attack timer bar | Off | Horizontal swing/cast/cooldown progress bar under the attack-style icon |
| Show tick track bar | Off | Amber-to-mint tick-progress bar beside the overhead cell |
| Tick sound | Off | Soft metronome blip each game tick (config-panel-only; no overlay buttons) |
| Tick sound volume | 40 | 5–100% |

## Known limitations

- **`Client.isPrayerActive` is deprecated upstream** and mishandles the Deadeye/Eagle Eye and Mystic Vigour/Might tier pairs. Combat Glance mirrors core RuneLite's own workaround so the offense slot never shows a stale lower tier.
- **Icons are deliberately upscaled** from OSRS's native small pixel-art sprites using nearest-neighbor scaling to stay crisp rather than blurry.
- **No rolling history.** The card reflects only the current sample — that's TickFlow's job. The attack-timer bar, tick-progress bar, and tick sound borrow a bit of TickFlow's visual language but keep no history.
- **The attack-timer bar trades precision for reliability.** It needs the weapon's known attack speed and one anchor point before it shows anything, then free-runs the cycle from tick arithmetic. An observed attack (animation-based, best-effort) re-anchors to correct drift; a missed one no longer hides the bar, but other animations performed mid-fight (eating, drinking) can occasionally shift the phase by a tick.
- **Lag can delay the visual by a tick**, as with any client-side observation.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
