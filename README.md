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
- Conditions: above / below / %gain / %loss / **volume spike versus the previous sample**; per-alert schedule and anti-spam cooldown.
- **Alert history** — up to 200 delivered events with timestamp and observed value, stored locally and included in backups.
- **Snooze** — silence alerts for 15/30/60 minutes with one tap.
- Notifications use a **single subtle vibration** (70 ms).

### Crypto pumps 🔥 (hideable)
- **Education** — what a pump is, how pump-and-dump schemes work, and the red flags.
- **Live scanner** — top CoinGecko coins are scored (24h change + 2× 1h change + volume/market-cap turnover) to surface coins currently being pumped; scan range (top 50/100/250) and threshold (3–25%) are configurable.
- **Explainable, cautious suggestion** for every result, based on risk, 1h momentum, 24h change and volume/market-cap turnover. It only says watch, wait or avoid chasing — never buy.
- **Opt-in pump alerts** with a configurable anti-spam cooldown; background scans are at least 15 minutes apart, each scan is evaluated once, and notifications include the suggestion and its reason.
- **Optional AI second opinion** — the user supplies any OpenAI-compatible endpoint, model name and API key; AI reviews the built-in suggestion with a reason and confidence, and requests linked related news when the provider supports web search. It runs only when the user taps a coin and never replaces the cautious built-in analysis.
- **Risk badge** per coin and a “widget” button to add that coin to your widget and watch it.
- **Caution rules** on the page — this is a monitor, not a buy signal.

### Refresh & data usage ⏱
- Live foreground service with per-widget interval (5–120 s) + WorkManager fallback every 15 min.
- **Scheduled refresh window** — updates (and mobile data) only during chosen hours; manual refresh always works. Overnight windows supported.

### Settings UI
- The Persian interface is forced to **RTL** regardless of the phone language.
- Clean sectioned design (landing menu → each section its own page), live widget preview, color-swatch theme picker and unified dialogs.
- **Named watchlists** — save the current symbol/source set by name and apply it to another widget.
- **Backup & restore** — export/import configuration, watchlists and alert history as one JSON file.
- **In-app updater** — checks the latest GitHub release, shows the **APK SHA-256 fingerprint**, and installs the new APK *over* the current app (nothing is wiped).

### Data sources
- **Source health & automatic fallback** — view last success/error, latency, active host and fallback use; a failed endpoint temporarily moves to the back of the queue and is retried later.
- **Abnormal-price detection** — an unexplained 40%+ jump is quarantined for one sample while the last healthy price remains visible; a second matching sample confirms it.
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
- **Every change**, including small fixes and documentation updates, must bump both `versionCode` and `versionName`; no change set is recorded without a new version. Published versions use a GitHub Release (`v*` tag) so the in-app updater can pick them up.
- CI builds the APK on every push: [Actions tab](https://github.com/sargoliwork101-ui/analysis-trades-/actions).
  - The installable file lands in that run's **Artifacts** as `PulseMarket-Android-APK` (containing `PulseMarket-vX.Y.apk`).
  - Since 1.21 CI builds the optimized **release APK**, runs unit tests and release lint, and verifies its signature. Since 1.23 an unreadable signature is a hard failure, and since 1.25 APKs larger than 2 MiB are rejected to preserve the roughly 90% size reduction.
- **Every version is signed with one stable key** (`app/ci-debug.keystore`) so a new APK installs *over* the existing app and the in-app updater works. This is a debug/sideload key, not a Play Store key.
- ⚠️ Versions 1.0–1.3 were signed with the CI runner's throwaway key; installing **1.4** over them needs one uninstall/reinstall. After 1.4 that is no longer needed.

**Version history**
- **1.47** — fixes an Int/Float type error in the candlestick pan-step calculation so chart zoom and drag compile and work.
- **1.46** — trading fees in the simulator (0.2% per side by default, editable) with net profit, break-even price and total fees; the main chart is now candlesticks with selectable ranges (1 day hourly, 7 days, 30 days, 3 months), pinch zoom and pan, and the Ichimoku cloud drawn on those candles; the chart button opens TradingView (CoinGecko stays as a secondary link); the AI timeout is now 180s; and the pump score is graded with an explanation (calm under 15, moderate 15-35, strong 35-70, overheated above 70).
- **1.45** — fixes a floating-point edge in the trade simulator: hitting the take-profit or stop-loss level exactly now closes the position (110.00000000000001 used to block it).
- **1.44** — paper-trade take-profit/stop-loss levels are now also evaluated against the cached prices when the page opens, not only after a fresh scan.
- **1.43** — adds a paper-trading simulator: record a simulated buy with amount, take-profit and stop-loss percentages, sell manually, auto-close positions when a later scan crosses those levels, and review a wallet card with realized profit, open profit, win rate and history; each coin detail also reports whether it trades on Nobitex (cached for 24h).
- **1.42** — the AI prompt is now expert-grade: numeric technical read, entry/stop/target/timeframe/invalidation levels, project backing, team and tokenomics, news catalysts and a short expert verdict; the 7-day chart gained an Ichimoku cloud (tenkan, kijun, cloud) whose state is also fed to the model; adds an LLMsRelay (Claude) preset that sends the Anthropic headers.
- **1.41** — keeps both Gemini routes side by side: requests go through the native path first and automatically repeat over Google OpenAI-compatible path when the answer comes back empty (e.g. MALFORMED_FUNCTION_CALL); the OpenAI-compatible Gemini preset is back, the connection test reports which route answered, and key/quota/region errors no longer retry pointlessly.
- **1.40** — fixes Gemini's `MALFORMED_FUNCTION_CALL` failure: the review now requests structured output (`responseMimeType` + `responseSchema`), automatically retries once in plain-text mode when it still happens, and shows a clear Persian explanation.
- **1.39** — fixes the empty-answer failure on Google thinking models: thought parts are skipped, the output budget grew to 4096 (512 for the connection test), and the vague message is replaced by the real cause (MAX_TOKENS, safety filter, missing candidate, OpenAI-style refusal).
- **1.38** — supports Gemini's native path (`/v1beta/models/{model}:generateContent` with the `X-goog-api-key` header) so Google AI Studio keys work directly; the Google preset now uses `gemini-flash-latest`, and the connection test uses the same path.
- **1.37** — AI service errors are now parsed out of the JSON body and explained in Persian (Gemini geo-restriction, invalid/expired key, wrong model name, quota, permission), the captured server message grew from 60 to 240 characters, and the Google preset hint documents the regional block.
- **1.36** — audit pass: fixes BOOT_COMPLETED never reaching the boot receiver (it must be exported, otherwise widgets stayed stale after a reboot), guards the live service against ForegroundServiceStartNotAllowedException crashes, null-guards NotificationManager in the alert engine and live service, creates the notification channel only once, and trims the pump cache so sparklines are stored for the top 60 coins only.
- **1.35** — adds an "AI connection test" button with a precise result message, actionable error texts (401/403/404/429, DNS, TLS, timeout, blocked network) that include a short redacted server reply, raises the AI request timeout to 90s, and sends Anthropic's native headers alongside Bearer.
- **1.34** — subsets the Vazirmatn fonts to the Persian/Latin characters the app actually uses, cutting about 170 KB from the APK and bringing it back under the 2 MiB budget.
- **1.33** — adds a per-coin pump detail sheet (tap any coin): 7-day sparkline, 1h/24h/7d/30d changes, volume, market cap and turnover, 24h high/low with price position, ATH distance, supply, a "pump stage" indicator, thin-market warning, pump-score breakdown, comparison with the previous scan, the AI second opinion with linked news, and add-to-widget / open-CoinGecko actions.
- **1.32** — adds ready-made AI provider presets (Gemini, Claude, OpenAI, OpenRouter) to the pumps section plus a provider web-search toggle, supports Google's `/v1beta/openai` compatible path, fixes the API key being lost while migrating from 1.18, and null-guards `NotificationManager` in pump alerts.
- **1.31** — replaces API-24 `Iterable.forEach` call sites with equivalent ordered `for` loops, so Android 6 compatibility no longer depends on the standard stream desugaring bundle; UI output and execution order are unchanged.
- **1.30** — preserves full custom CSS-selector semantics with deterministic non-Stream DOM traversal, allowing the Android 6 build to use the minimal function/Optional desugaring flavor instead of bundling unused stream implementations.
- **1.29** — removes unnecessary annotation/inner-class retention from the app's R8 rules and packages only Persian plus English fallback resources, reducing metadata/locales without changing RTL presentation or runtime behavior.
- **1.28** — replaced an over-conservative raw DEX-string check (which also matched harmless backport descriptors) with a regression test for the exact HTML parsing/CSS-selector path; release lint, minSdk 23 and standard stream/function desugaring remain enforced.
- **1.27** — routes HTML strings directly through jsoup's parser, preserving custom CSS-selector sources without calling its unused file/Path entry points.
- **1.26** — kept patched jsoup on Android 6 while switching from the unused NIO filesystem backport to standard stream/function desugaring; introduced a conservative DEX descriptor experiment that was refined in 1.28.
- **1.25** — restored precise R8 shrinking by removing a package-wide keep rule and tightened CI's APK limit to 2 MiB so dependency growth cannot undo the roughly 90% footprint reduction; no UI or product behavior changed.
- **1.24** — recorded the completed release-build, unit-test, release-lint, size, artifact and stable-signature verification for the audit; no runtime behavior or UI design changed from 1.23.
- **1.23** — full reliability/security audit: fixed settings delete/import write races, isolated per-source refresh failures, collision-safe cache/history identifiers, per-widget alert-history IDs and stale-state cleanup, clock-rollback-safe pump evaluation, complete source-health reporting, strict custom-URL parsing and hardened CI signature verification; updated Kotlin, jsoup and DataStore without changing the UI design.
- **1.21** — lightweight audit: optimized R8/resource-shrunk release APK, unused UI dependencies removed, bounded active-symbol cache, stale-history cleanup and throttled disk writes in live mode.
- **1.20** — 1h/1d/1w/1m pump changes, period sorting, five-result preview with “show more”, clearer in-app help, responsive spacing, watchlist guidance, bulk TSE fetching and Android 6+ support.
- **1.19** — security/reliability audit: API 36, Android-Keystore encryption for the AI key, no credentials over HTTP/redirects, per-widget alert isolation, clock-rollback handling and input/error sanitization.
- **1.18** — optional AI second opinion for pumps using a user-selected API/model, with reason, confidence and provider-powered related-news search.
- **1.17** — added the mandatory “every change gets a version” rule to the repository rules and architecture checklist.
- **1.16** — anti-spam pump alerts plus an explainable cautionary suggestion and reason for every detected pump and notification.
- **1.15** — forced RTL Persian UI, alert history, volume-spike alerts, source health with smarter fallback, abnormal-price quarantine, and named watchlists.
- **1.14** — security/engineering audit (internal widget token, response size caps, URL validation, stricter updater) + global gold + TradingView source + crypto-pumps section + unit tests in CI. Details: [AUDIT_fa.md](AUDIT_fa.md)
- **1.13** — five edge cases around source deletion + periodic-worker battery leak.
- **1.12** — only healthy built-in sources + gold/FX from TGJU's official API.
- **1.11** — live service only with a real live widget + alerts always fresh.

## 🛠 Build
```bash
./gradlew assembleRelease  # optimized APK → app/build/outputs/apk/release/
./gradlew test lintRelease # JVM unit tests + release lint
```
Kotlin 2.4.20 · AGP 8.9 · compile/target API 36 · minSdk 23 (Android 6+) · Gradle wrapper included.

## 🔐 Notes
- Settings live only on the device (DataStore/SharedPreferences); no accounts, no secrets in code.
- Plain-HTTP custom sources are fetched only when the user explicitly configures them, and the dialog warns about the risk.
- All network reads go through `Http`: HTTP(S) only, hard response-size caps, bounded redirects, and sensitive headers stripped on cleartext or cross-origin hops.
- The AI API key is AES-GCM encrypted with Android Keystore and is never sent over HTTP.
- Widget-internal broadcasts are signed with a private token so other apps cannot force refreshes or toggle live mode.
- The remaining signing-key recommendation is described in [AUDIT_fa.md](AUDIT_fa.md).

## 👤 Developer
**Hamed Sargoli** — [hamedsargoli.ir](https://hamedsargoli.ir) — +98 912 636 8924
