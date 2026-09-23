# Pulse Market — نبض بازار 📈

An Android home-screen widget that shows **live market prices** — Tehran Stock Exchange (TSE), crypto, gold, and forex — in your own style. Written in Kotlin with Jetpack Compose (settings) + RemoteViews (widgets).

**[📥 Download latest release](https://github.com/sargoliwork101-ui/analysis-trades-/releases/latest)** · Persian UI: see [README_fa.md](README_fa.md)

---

## ✨ Features

### Widgets (small / medium / large)
- **Per-widget independent settings** — every widget has its own sources, symbols, values and theme; each can be edited by tapping it.
- **5 themes** — Dark, Light, Glass, Aurora, Neon — with zebra row separation.
- **Dynamic sizing** — fonts and content adapt to the real widget size plus a user font-scale (×0.75–×1.5).
- **Live values per symbol** — price **with its unit right next to it** (unit moves under the price on narrow widgets), change badge, volume, sparkline.
- **Sparkline for every symbol, every widget size** — all-or-nothing: enabled for all sizes (small/medium/large) or none. Price history is stored locally on the phone, so even TSE symbols get a chart; the number of displayed points (6–60) is user-configurable.
- **Offline & update-off resilience** — with no internet or auto-update disabled, the widget never goes blank: the last prices stay on screen, each row's **blinking LED** turns red/green (green = fresh, red = stale, gray = no data), and the bottom bar says what state it's in. The header shows the last-update time.
- **Status of the widget's own markets** — next to the clock, open/closed for each market active in that widget: «بورس / کریپتو (۲۴/۷) / آمریکا / طلا و ارز». Toggleable.
- **Smart sorting** — display symbols manually, by biggest daily change, or alphabetically.
- **Row count control** — choose how many symbols (1–6) each widget shows.

### Alerts 🔔
- Conditions: above / below / %gain / %loss; per-alert schedule (time window + days of week, overnight windows supported) and anti-spam cooldown.
- **Snooze** — silence all alert notifications for 15/30/60 minutes (meetings, sleep) with one tap.
- Notifications use a **single subtle vibration** (70 ms), not the long default buzz.

### Refresh & data usage ⏱
- Live foreground service with per-widget interval (5–120 s) + WorkManager fallback every 15 min.
- **Scheduled refresh window** — set "from/to" hours so updates (and mobile data) only happen during those hours; manual refresh always works. Overnight windows supported.

### Settings UI
- Clean sectioned design (landing menu → each section its own page), live widget preview, color-swatch theme picker, unified dialogs (TSE symbol search, add alert, add source).
- **Backup & restore** — export/import the full configuration (widgets, template, custom sources, symbols, alerts) as one JSON file.
- **In-app updater** — checks the latest GitHub release; a newer APK installs *over* the current one (nothing is wiped). Test dial auto-hides after 3 s.

### Data sources
- Built-ins: **TSE (TSETMC)**, CoinGecko, Yahoo Finance, TGJU (gold/forex), Navasan mirror.
- **Custom sources** — any JSON API (dot-path, batch templates, scale/unit) or HTML page (CSS selector) — and TSE symbol search by name or TSETMC page link.
- 📡 Porting the source-fetching layer to another app? See **[SOURCES_SPEC_fa.md](SOURCES_SPEC_fa.md)** (Persian) — full spec of the data model, JSON path language, TSETMC APIs, and caching strategy.
- 🏗 Contributing/Extending? See **[ARCHITECTURE_fa.md](ARCHITECTURE_fa.md)** (Persian) — module map and the golden rules (no source-id string switches, all price/unit text via `QuoteText`).

---

## 🔁 Releases & versioning
- Every meaningful change ships with a **version bump** (`versionName`) and a GitHub Release (`v*` tag) so the in-app updater can pick it up.
- CI (GitHub Actions) builds the APK on every push: see the [Actions tab](https://github.com/sargoliwork101-ui/analysis-trades-/actions).
  - The installable file is always uploaded to that run's **Artifacts** as `PulseMarket-Android-APK` (containing `PulseMarket-vX.Y.apk`).
- **Every version is signed with one stable key** (`app/ci-debug.keystore`) so a new APK installs *over the existing app* and the in-app updater works. A per-build key would make Android reject the update (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). This is a debug/sideload key, not a Play Store key.
- ⚠️ Versions 1.0–1.3 were signed with the CI runner's throwaway key; installing **1.4** over them needs one uninstall/reinstall. After 1.4 that is no longer needed.

## 🛠 Build
```bash
./gradlew assembleDebug    # APK → app/build/outputs/apk/debug/
```
Kotlin 2.0 · AGP 8.5 · minSdk 26 · Gradle wrapper included.

## 🔐 Notes
Settings are stored in DataStore/SharedPreferences only (no account, no keys in code). Custom sources may fetch plain HTTP by explicit user configuration.

## 👤 Developer
**Hamed Sargoli** — [hamedsargoli.ir](https://hamedsargoli.ir) — +98 912 636 8924
