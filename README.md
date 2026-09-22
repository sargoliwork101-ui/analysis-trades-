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
- **Sparkline for every symbol** — local price history is recorded on each successful update, so even TSE symbols gradually get a trend chart.
- **Stale-data resilience** — on update failure the last good value stays; a small **blinking LED** per row tells the status (green = fresh, red blink = stale, gray = no data). The header shows the last-update time.
- **Tehran market status** — "بورس: باز/بسته" next to the clock, computed in Asia/Tehran time (Sat–Wed, 9:00–12:30). Toggleable.
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

---

## 🔁 Releases & versioning
- Every meaningful change ships with a **version bump** (`versionName`) and a GitHub Release (`v*` tag) so the in-app updater can pick it up.
- CI (GitHub Actions) builds the APK on every push: see the [Actions tab](https://github.com/sargoliwork101-ui/analysis-trades-/actions).

## 🛠 Build
```bash
./gradlew assembleDebug    # APK → app/build/outputs/apk/debug/
```
Kotlin 2.0 · AGP 8.5 · minSdk 26 · Gradle wrapper included.

## 🔐 Notes
Settings are stored in DataStore/SharedPreferences only (no account, no keys in code). Custom sources may fetch plain HTTP by explicit user configuration.

## 👤 Developer
**Hamed Sargoli** — [hamedsargoli.ir](https://hamedsargoli.ir) — +98 912 636 8924
