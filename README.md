# Pulse Market — نبض بازار 📈

An Android home-screen widget that shows **live market prices** — Tehran Stock Exchange (TSE), crypto, gold & FX, and global markets — in your own style. Written in Kotlin with Jetpack Compose (settings) + RemoteViews (widgets).

**[📥 Download latest release](https://github.com/sargoliwork101-ui/analysis-trades-/releases/latest)** · Persian UI: see [README_fa.md](README_fa.md)

---

## ✨ Features

### Widgets (small / medium / large)
- **Per-widget independent settings** — every widget has its own sources, symbols, values and theme; each can be edited by tapping it.
- **5 themes** — Dark, Light, Glass, Aurora, Neon — with zebra row separation.
- **Dynamic sizing** — fonts and content adapt to the real widget size plus a user font-scale (×0.75–×1.5).
- **Price with its unit right under it** — Toman/Rial/$ for every source and widget size, including a **per-symbol unit** (the global gold ounce is in USD while a gold coin is in Toman). Change badge, volume and sparkline live in the same row.
- **Sparkline for every symbol, every widget size** — all-or-nothing. Price history is stored locally, so even TSE symbols get a chart; the number of displayed points (6–60) is user-configurable.
- **Offline & update-off resilience** — with no internet or auto-update disabled the widget never goes blank: the last prices stay, each row's **blinking LED** turns red/green (green = fresh, red = stale, gray = no data), and the bottom bar explains the state. The header shows the last-update time.
- **Status of the widget's own markets** — next to the clock: TSE / crypto (24-7) / global / gold & FX — toggleable.
- **Smart sorting** — manual, biggest daily change, or alphabetical.
- **Row count control** — how many symbols (1–6) each widget shows.

### Alerts 🔔
- Conditions: above / below / %gain / %loss; per-alert schedule (time window + weekdays, overnight windows supported) and anti-spam cooldown.
- **Snooze** — silence alerts for 15/30/60 minutes with one tap.
- Notifications use a **single subtle vibration** (70 ms).

### Crypto pumps 🔥 (hideable)
- **Education** — what a pump is, how pump-and-dump schemes work, and the red flags.
- **Live scanner** — top CoinGecko coins are scored (24h change + 2× 1h change + volume/market-cap turnover) to surface coins currently being pumped; scan range (top 50/100/250) and threshold (3–25%) are configurable.
- **Risk badge** per coin and a “widget” button to add that coin to your widget and watch it.
- **Caution rules** on the page — this is a monitor, not a buy signal.

### Refresh & data usage ⏱
- Live foreground service with per-widget interval (5–120 s) + WorkManager fallback every 15 min.
- **Scheduled refresh window** — updates (and mobile data) only during chosen hours; manual refresh always works. Overnight windows supported.

### Settings UI
- Clean sectioned design (landing menu → each section its own page), live widget preview, color-swatch theme picker, unified dialogs (TSE symbol search, add alert, add source).
- **Backup & restore** — export/import the full configuration as one JSON file.
- **In-app updater** — checks the latest GitHub release, shows the **APK SHA-256 fingerprint**, and installs the new APK *over* the current app (nothing is wiped).

### Data sources
- Built-ins: **TSE (TSETMC)** + market index, **CoinGecko** (crypto), **TGJU** (free-market USD, gold & coins, plus the **global gold/silver ounce**), and **TradingView** (gold, silver, oil, dollar index, crypto).
- **Custom sources** — any JSON API (dot-path, batch template, scale/unit — plus a per-symbol unit with `@`, e.g. `ons:Gold@$`) or an HTML page (CSS selector); TSE symbol search by name or TSETMC page link.
- 📡 Porting the fetching layer elsewhere? See **[SOURCES_SPEC_fa.md](SOURCES_SPEC_fa.md)** (Persian).
- 🏗 Module map & golden rules: **[ARCHITECTURE_fa.md](ARCHITECTURE_fa.md)** (Persian).
- 🔍 Security & engineering audit: **[AUDIT_fa.md](AUDIT_fa.md)** (Persian).

---

## 🥇 Global gold price — how to get it

Two built-in ways; both give the **USD** price of one troy ounce:

**Option 1 — TGJU (works on Iranian ISPs, no VPN)**
1. Widget settings → **Data sources** → enable “طلا و ارز — TGJU”.
2. **Symbols** → “Search or add symbol” → pick the TGJU source → tap **«انس طلا (جهانی)»**
   (silver: «انس نقره (جهانی)»).
3. Done — it shows on the widget with the `$` unit.

**Option 2 — TradingView (official scanner feed, 24×5)**
1. Enable “بازارهای جهانی — TradingView” in **Data sources**.
2. In **Symbols** pick **«طلا — انس جهانی»** (`OANDA:XAUUSD`).
3. If your network blocks TradingView, use option 1 (or add a custom source with the
   `TVC:GOLD` code).

> Domestic gold (18k gram, mesghal, coins) is available from the same TGJU source, so you
> can show the global ounce and the Emami coin side by side — each with its own unit.

---

## 🔁 Releases & versioning
- Every meaningful change ships with a **version bump** and a GitHub Release (`v*` tag) so the in-app updater can pick it up.
- CI builds the APK on every push: [Actions tab](https://github.com/sargoliwork101-ui/analysis-trades-/actions).
  - The installable file lands in that run's **Artifacts** as `PulseMarket-Android-APK` (containing `PulseMarket-vX.Y.apk`).
  - Since 1.14 CI also **runs unit tests** (`./gradlew test`) and verifies the APK signature.
- **Every version is signed with one stable key** (`app/ci-debug.keystore`) so a new APK installs *over* the existing app and the in-app updater works. This is a debug/sideload key, not a Play Store key.
- ⚠️ Versions 1.0–1.3 were signed with the CI runner's throwaway key; installing **1.4** over them needs one uninstall/reinstall. After 1.4 that is no longer needed.

**Version history**
- **1.14** — security/engineering audit (internal widget token, response size caps, URL validation, stricter updater) + global gold + TradingView source + crypto-pumps section + unit tests in CI. Details: [AUDIT_fa.md](AUDIT_fa.md)
- **1.13** — five edge cases around source deletion + periodic-worker battery leak.
- **1.12** — only healthy built-in sources + gold/FX from TGJU's official API.
- **1.11** — live service only with a real live widget + alerts always fresh.

## 🛠 Build
```bash
./gradlew assembleDebug    # APK → app/build/outputs/apk/debug/
./gradlew test             # JVM unit tests (no device needed)
```
Kotlin 2.0 · AGP 8.5 · minSdk 26 · Gradle wrapper included.

## 🔐 Notes
- Settings live only on the device (DataStore/SharedPreferences); no accounts, no secrets in code.
- Plain-HTTP custom sources are fetched only when the user explicitly configures them, and the dialog warns about the risk.
- All network reads go through the `Http` module: http/https only, with a hard response-size cap.
- Widget-internal broadcasts are signed with a private token so other apps cannot force refreshes or toggle live mode.
- The remaining signing-key recommendation is described in [AUDIT_fa.md](AUDIT_fa.md).

## 👤 Developer
**Hamed Sargoli** — [hamedsargoli.ir](https://hamedsargoli.ir) — +98 912 636 8924
