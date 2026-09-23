# 📡 سند مشخصات «دریافت منابع داده» — نبض بازار

این سند **خودکفا** است: همه‌چیزهایی که برای انتقال لایه‌ی دریافت قیمت از منابع مختلف (JSON، HTML، بورس تهران) به هر اپلیکیشن دیگری — با هر زبان و پلتفرمی — لازم داری. کد نمونه به‌صورت شبه‌کد/JSON است و منطق، مسیرها و آدرس‌ها دقیقاً همان چیزی است که در این اپ کار می‌کند.

> مرجع کد در همین مخزن:
> `Fetcher.kt` (موتور fetch) • `SourceCatalog.kt` (منابع آماده) • `JsonPath.kt` (مسیریابی JSON) • `Num.kt` (پاک‌سازی اعداد فارسی) • `TseModel.kt` (بورس تهران) • `QuoteRepo.kt` (کش و تاریخچه) • `Model.kt` (مدل داده)

---

## ۱) معماری در یک نگاه

```
SourceDef (تعریف منبع: آدرس + مسیرها + نمادها)
      │
      ▼
  Fetcher.fetchAll(source, symbols)
      │
      ├─ kind = JSON_REST  ──►  درخواست گروهی (batchTemplate) یا تکی، پارس JSON با JsonPath
      ├─ kind = HTML_CSS   ──►  دانلود صفحه، انتخاب اولین المنط مطابق سلکتور CSS
      └─ kind = TSE_TSETMC ──►  جریان چندلایه‌ی مخصوص بورس تهران (جستجو ← قیمت پایانی)
      │
      ▼
  List<Quote>  (قیمت، درصد تغییر، حجم، سری نمودار، خطا)
      │
      ▼
  کش «آخرین مقدار سالم» + تاریخچه‌ی قیمت روی دستگاه (برای نمودار)
```

قواعد کلی:

- **هر منبع با کم‌ترین تعداد درخواست** خوانده می‌شود: اگر `batchTemplate` داشت همه‌ی نمادها با یک HTTP؛ وگرنه هر نماد یک درخواست (موازی).
- اگر درخواست گروهی شکست خورد، به‌صورت خودکار به **درخواست تکی** برمی‌گردد.
- هر نماد مستقل از بقیه خطا می‌خورد؛ خطای یک نماد بقیه را خراب نمی‌کند.
- **داده‌ی سالم هرگز پاک نمی‌شود**: اگر خواندن شکست بخورد، آخرین مقدار سالم با پرچم `stale` نمایش داده می‌شود.

---

## ۲) مدل داده

### SourceDef — تعریف یک منبع

| فیلد | نوع | توضیح |
|---|---|---|
| `id` | string | شناسه‌ی یکتا (مثلاً `crypto_coingecko`) |
| `title` | string | نام نمایشی |
| `subtitle` | string | توضیح کوتاه |
| `kind` | enum | `JSON_REST` \| `HTML_CSS` \| `TSE_TSETMC` |
| `urlTemplate` | string | آدرس هر نماد؛ جای `{symbol}` با کدِ URL-encoded نماد عوض می‌شود |
| `batchTemplate` | string? | آدرس گروهی؛ جای `{symbols}` با کدها (جدا با `,` و URL-encoded) عوض می‌شود |
| `pricePath` | string? | مسیر قیمت در JSON (زیربخش ۴) |
| `changePath` | string? | مسیر «تغییر» — معنایش با `changeMode` تعیین می‌شود |
| `changeMode` | enum | `PERCENT` \| `ABSOLUTE` \| `PREV_CLOSE` \| `NONE` |
| `sparkPath` | string? | مسیر آرایه‌ی اعداد برای نمودار مینیاتوری |
| `volumePath` | string? | مسیر حجم (عدد یا آرایه — آرایه جمع زده می‌شود) |
| `cssSelector` | string? | برای `HTML_CSS`: سلکتور CSS (می‌تواند چند سلکتور با `,` باشد — اولین تطبیق) |
| `cssAttr` | string? | اگر پر باشد مقدار از attribute خوانده می‌شود، وگرنه text المنط |
| `scale` | double | ضریب قیمت/حجم/نمودار (پیش‌فرض `1.0`) |
| `unit` | string | واحد نمایشی (تومان، ریال، $، …) |
| `symbols` | SymbolDef[] | نمادهای آماده‌ی منبع |
| `headers` | map | هدرهای HTTP اضافه برای این منبع |
| `builtIn` | bool | منبع آماده‌ی اپ یا ساخته‌ی کاربر |

### SymbolDef — یک نماد

| فیلد | نوع | توضیح |
|---|---|---|
| `code` | string | کد فنی نماد در منبع (برای TSE: نام نماد یا `insCode`) |
| `label` | string | نام نمایشی (فارسی) |
| `sourceId` | string | منبع والد (در فرمت جدید همیشه پر است) |

### Quote — یک نتیجه‌ی خوانده‌شده

| فیلد | نوع | توضیح |
|---|---|---|
| `code`, `label` | string | همان نماد |
| `price` | double? | قیمت × `scale` — `null` یعنی نتوانست بخواند |
| `changePct` | double? | درصد تغییر |
| `volume` | double? | حجم معاملات / حجم ۲۴ ساعت |
| `unit` | string | واحد منبع |
| `error` | string? | پیام خطا (حداکثر ~۸۰ کاراکتر) — فقط وقتی قیمت هم نیامده نمایش داده می‌شود |
| `ts` | long | زمان شروع درخواست (میلی‌ثانیه) |
| `stale` | bool | قیمت، آخرین مقدار سالم است ولی تازه نشده (چراغ قرمز) |
| `spark` | double[] | سری نمودار (اگر منبع بدهد؛ وگرنه از تاریخچه‌ی محلی) |
| `sourceId` | string | منبع |

### نمونه‌ی JSON منبع دلخواه (فرمت ذخیره‌سازی همین اپ — برای سازگاری بین دو اپ)

```json
{
  "id": "my_source",
  "title": "منبع من",
  "subtitle": "قیمت لحظه‌ای",
  "kind": "JSON_REST",
  "urlTemplate": "https://api.example.com/price/{symbol}",
  "batchTemplate": "https://api.example.com/price?symbols={symbols}",
  "pricePath": "data.price",
  "changePath": "data.changePct",
  "changeMode": "PERCENT",
  "sparkPath": "data.history",
  "volumePath": "data.volume",
  "cssSelector": null,
  "cssAttr": null,
  "scale": 1.0,
  "unit": "تومان",
  "symbols": [
    { "code": "btc", "label": "بیت‌کوین", "sourceId": "my_source" }
  ],
  "headers": { },
  "builtIn": false
}
```

---

## ۳) تنظیمات HTTP

| مورد | مقدار |
|---|---|
| User-Agent (عمومی) | `Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36` |
| User-Agent (بورس تهران) | `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36` |
| `Accept-Language` | `fa,en;q=0.8` |
| `Accept` (JSON) | `application/json, text/plain, */*` |
| `Accept` (HTML) | `text/html,application/xhtml+xml,*/*` |
| Timeout | اتصال ۱۲ ثانیه • خواندن ۱۵ • کل فراخوانی ۲۵ (بورس تهران: ۱۰/۱۰) |
| Retry | در شکست اتصال، دوباره تلاش شود |
| کدگذاری نماد در URL | `URLEncoder.encode(code, "UTF-8")` و تبدیل `+` به `%20` |

نکته: بعضی سایت‌های ایرانی هنوز روی HTTP سرو می‌شوند؛ در اندروید باید `cleartextTrafficPermitted="true"` در network-security-config فعال باشد (یا فقط برای هاست‌های مشخص).

---

## ۴) زبان مسیریابی JSON (JsonPath)

مسیر کوتاهِ نقطه‌ای با پشتیبانی از ایندکس آرایه، جای‌گذاری نماد و مسیر جایگزین:

```
closingPriceInfo.pClosing
chart.result[0].meta.regularMarketPrice
[0].lastValue
result[0][2]                    ← چند ایندکس پشت‌سرهم
{symbol}.usd                    ← {symbol} با کد نماد عوض می‌شود
[0].lastValue | indexB1LastAll[0].lastValue | lastValue   ← مسیرهای جایگزین با |
```

قواعد:

1. مسیر با `.` به بخش‌ها تقسیم می‌شود؛ هر بخش = نام فیلد + صفر یا چند `[عدد]`.
2. اگر بخشی نام داشته باشد ولی مقدار فعلی آرایه‌ی خام باشد، نام نادیده گرفته می‌شود (برای پاسخ‌هایی که گاهی wrapper دارند و گاهی نه).
3. مسیرهای جایگزین با `|` جدا می‌شوند؛ **اولین مقدار غیر-null** برمی‌گردد.
4. خواندن عدد: اگر مقدار Number بود همان؛ اگر String بود با قواعد `Num` (زیربخش ۵) پارس می‌شود.
5. خواندن آرایه‌ی عددی (نمودار/حجم): اعضای null یا غیرعددی **نادیده گرفته می‌شوند**.

---

## ۵) پاک‌سازی اعداد (Num)

سایت‌ها عدد را به شکل‌های مختلف می‌دهند؛ این قواعد همه را به `double` تبدیل می‌کند:

1. ارقام فارسی `۰-۹` و عربی `٠-٩` به لاتین.
2. حذف جداکننده‌ی هزارگان و فاصله‌ها: `,` `٬` `،` نیم‌فاصله (`\u200F`, `\u200E`) فاصله‌ی نشکن (`\u00A0`) و فاصله‌ی معمولی.
3. حذف هر کاراکتر غیرعددی باقی‌مانده (واحد پول، حروف و…) — فقط `0-9 . + - e E` می‌ماند.
4. `toDouble()`؛ اگر نشد → `null`.

نمونه: `"۱۲٬۴۵۰ تومان"` ← `12450.0`

---

## ۶) الگوریتم دریافت

### ۶.۱) JSON_REST

```
برای هر نماد (یا همه با batch):
    url = (batch ? batchTemplate["{symbols}" ← join(",", urlencode(code))]
                  : urlTemplate["{symbol}" ← urlencode(code)])
    body = GET(url, headers)
    json = parse(body)                     // JSONObject یا JSONArray

    rawPrice = readDouble(json, pricePath["{symbol}" ← code])
    price    = rawPrice * scale
    change   = تبدیل تغییر طبق changeMode (زیربخش ۶.۳)
    volume   = readDouble(volumePath) * scale  یا  sum(readDoubleList(volumePath)) * scale
    spark    = readDoubleList(sparkPath) * scale    // فقط اگر ≥۳ نقطه داشت؛ نگه‌داشتن ۲۴۰ نقطه‌ی آخر

    error = (price == null) ? "قیمت در پاسخ پیدا نشد" : null
```

fallback گروهی: اگر درخواست batch با هر خطایی شکست خورد → همه‌ی نمادها تک‌تک fetch شوند.

### ۶.۲) HTML_CSS

```
body = GET(urlTemplate["{symbol}" ← code])
doc  = parseHTML(body, baseUrl)
el   = اولین المنط مطابق cssSelector (چند سلکتور با , مجاز است)
raw  = cssAttr پر بود؟ el.attr(cssAttr) : el.text()
num  = Num.parse(raw) * scale
error = (num == null) ? "«raw» عدد نبود" : (سلکتور پیدا نشد؟ "سلکتور در صفحه پیدا نشد")
```

### ۶.۳) حالت‌های «تغییر» (changeMode)

| حالت | معنی مقدار changePath | فرمول درصد |
|---|---|---|
| `PERCENT` | خود سایت درصد داده | `pct = raw` |
| `ABSOLUTE` | تغییر مطلق (مثلاً ریال) | `base = (price/scale) − raw` → `pct = raw / base × 100` |
| `PREV_CLOSE` | قیمت بسته‌ی قبلی | `pct = ((price/scale − raw) / raw) × 100` |
| `NONE` | — | `null` (درصد نمایش داده نمی‌شود) |

### ۶.۴) مدیریت خطا

- خطای HTTP: پیام = `HTTP {code} — {۶۰ کاراکتر اول بدنه}`.
- هر نماد مستقل خطا می‌خورد؛ خروجی همیشه یک Quote با `error` پر (و `price = null`) است، نه exception.
- خطا فقط وقتی به کاربر نشان داده می‌شود که **هیچ قیمتی** برای نمایش نمانده باشد؛ در غیر این صورت مقدار قبلی + پرچم `stale` می‌ماند.

---

## ۷) منابع آماده (کاتالوگ)

### ۷.۱) کریپتو — CoinGecko

| مورد | مقدار |
|---|---|
| kind | `JSON_REST` |
| تکی | `https://api.coingecko.com/api/v3/simple/price?ids={symbol}&vs_currencies=usd&include_24hr_change=true&include_24hr_vol=true` |
| گروهی | همان آدرس با `ids={symbols}` (کدها با `,`) |
| pricePath | `{symbol}.usd` |
| changePath / حالت | `{symbol}.usd_24h_change` / `PERCENT` |
| volumePath | `{symbol}.usd_24h_vol` |
| unit | `$` |
| نمادها | `bitcoin` بیت‌کوین، `ethereum` اتریوم، `tether` تتر، `binancecoin` بایننس‌کوین، `solana` سولانا، `ripple` ریپل، `dogecoin` دوج‌کوین، `toncoin` تون‌کوین |
| جستجوی نماد | `GET https://api.coingecko.com/api/v3/search?query={q}` → `coins[] {id, name, symbol}` |

پاسخ نمونه:

```json
{ "bitcoin": { "usd": 64210, "usd_24h_change": 2.31, "usd_24h_vol": 28400000000 } }
```

### ۷.۲) بورس تهران — TSETMC

| مورد | مقدار |
|---|---|
| kind | `TSE_TSETMC` (جریان مخصوص — زیربخش ۸) |
| unit | ریال |
| نمادهای پیش‌فرض | فولاد، خودرو، خساپا، شپنا، وبملت، فملی، شستا، خگستر |

### ۷.۳) شاخص کل بورس

| مورد | مقدار |
|---|---|
| kind | `JSON_REST` |
| آدرس | `https://cdn.tsetmc.com/api/Index/GetIndexB1LastAll/0` |
| pricePath | `[0].lastValue \| indexB1LastAll[0].lastValue \| lastValue` |
| changePath / حالت | `[0].indexChange \| indexB1LastAll[0].indexChange \| indexChange` / `ABSOLUTE` |
| unit | واحد |

### ۷.۴) ارز — Navasan (آینه‌ی GitHub)

| مورد | مقدار |
|---|---|
| kind | `JSON_REST` |
| تکی/گروهی | `https://raw.githubusercontent.com/HosseinOdd/Navasan-API/main/data/fiat.json` |
| pricePath | `{symbol}.value` |
| changePath / حالت | `{symbol}.change_pct` / `PERCENT` |
| unit | تومان |
| نمادها | `usd` دلار، `eur` یورو، `gbp` پوند، `aed` درهم، `try` لیر، `jpy` ین، `chf` فرانک، `cny` یوان |

### ۷.۵) طلا و سکه — Navasan (آینه‌ی GitHub)

| مورد | مقدار |
|---|---|
| آدرس | `https://raw.githubusercontent.com/HosseinOdd/Navasan-API/main/data/gold.json` |
| بقیه | مثل ارز (pricePath: `{symbol}.value`، change: `{symbol}.change_pct` / `PERCENT`، unit: تومان) |
| نمادها | `18ayar` طلای ۱۸ عیار، `gerami` مثقال، `sekkeh` سکه، `bahar` بهار آزادی، `nim` نیم، `rob` ربع |

پاسخ نمونه (هر دو):

```json
{ "usd": { "value": 61500, "change_pct": 0.4 } }
```

### ۷.۶) سهام آمریکا — Yahoo Finance

| مورد | مقدار |
|---|---|
| kind | `JSON_REST` |
| آدرس | `https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=5m&range=1d&includePrePost=false` |
| pricePath | `chart.result[0].meta.regularMarketPrice` |
| changePath / حالت | `chart.result[0].meta.chartPreviousClose` / `PREV_CLOSE` |
| sparkPath | `chart.result[0].indicators.quote[0].close` |
| volumePath | `chart.result[0].indicators.quote[0].volume` (آرایه — جمع زده می‌شود) |
| unit | `$` |
| نمادها | `AAPL`, `TSLA`, `MSFT`, `NVDA`, `GOOGL`, `AMZN`, `META`, `BTC-USD` |
| جستجوی نماد | `GET https://query1.finance.yahoo.com/v1/finance/search?q={q}&quotesCount=15&newsCount=0` → `quotes[] {symbol, shortname, quoteType}` — فقط `quoteType` های `EQUITY/ETF/CRYPTOCURRENCY/INDEX/CURRENCY` |

### ۷.۷) نمونه‌ی اسکرپ وب — TGJU

| مورد | مقدار |
|---|---|
| kind | `HTML_CSS` |
| آدرس | `https://www.tgju.org/profile/{symbol}` |
| cssSelector | `span[data-col='info-last-trade'], .price, .info-price .value` |
| unit | تومان |
| نمادها | `price_dollar_rl` دلار آزاد، `geram18` طلای ۱۸ عیار، `sekeb` سکه بهار |

---

## ۸) جریان مخصوص بورس تهران (TSETMC)

### ۸.۱) API های TSETMC

| کار | آدرس | نکته |
|---|---|---|
| جستجوی نماد | `GET https://cdn.tsetmc.com/api/Instrument/GetInstrumentSearch/{query-urlencoded}` | پاسخ: `instrumentSearch[]` |
| قیمت پایانی | `GET https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/{insCode}` | پاسخ: `closingPriceInfo{}` |
| مشخصات نماد | `GET https://cdn.tsetmc.com/api/Instrument/GetInstrumentInfo/{insCode}` | پاسخ: `instrumentInfo{}` |

فیلدهای مهم:

| فیلد | معنی |
|---|---|
| `insCode` | کد یکتای عددی نماد (۱۵ تا ۲۰ رقم) |
| `lVal18AFC` / `l18` | نماد فارسی کوتاه (فولاد) |
| `lVal30` | نام کامل (فولاد مبارکه اصفهان) |
| `pClosing` | **قیمت پایانی** (اولویت اول) |
| `pDrCotVal` | آخرین قیمت معامله (اولویت دوم) |
| `priceYesterday` | قیمت دیروز |
| `priceChange` | تغییر مطلق |
| `qTotTran5J` | حجم معاملات (تعداد سهم) |

### ۸.۲) محاسبات

```
price     = pClosing ?: pDrCotVal
changePct = (pClosing − priceYesterday) / priceYesterday × 100
            (اگر pClosing نبود: priceChange / priceYesterday × 100)
volume    = qTotTran5J
```

### ۸.۳) لایه‌های fetchQuote(code) — به‌ترتیب، اولین موفق برمی‌گردد

1. **insCode مستقیم:** اگر `code` عدد ۸ تا ۲۰ رقمی بود → `GetClosingPriceInfo` (نام از `GetInstrumentInfo` یا کاتالوگ داخلی).
2. **کاتالوگ داخلی:** جستجو در فهرست آماده‌ی نمادهای پرمعامله (آفلاین، سریع — برای resiliency) → قیمت با `GetClosingPriceInfo`.
3. **جستجوی آنلاین:** `GetInstrumentSearch?{code}` → اولین نتیجه‌ی با نمادِ منطبق (یا نتیجه‌ی اول) → قیمت با `GetClosingPriceInfo`.
4. هیچ‌کدام نشد → حداقل نام نماد برگردد (نه خطا).

### ۸.۴) پارس لینک/ورودی کاربر (TseUrlParser)

- **insCode:** الگوی `(?:i=|/instInfo/|/Instrument/|^)(\d{15,20})` — مثلاً `loader.aspx?ParTree=151311&i=46348633615832441`.
- **ISIN:** `(?:/instInfo/|^)(IRO[0-9A-Z]{9})` — مثلاً `IRO1FOLD0001`.
- **لینک دیگر:** آخرین segment مسیر (قبل از `?`).
- متن معمولی → جستجوی متنی.

---

## ۹) کش، تاب‌آوری و تاریخچه (استراتژی نمایش)

این بخش برای این است که رفتار اپ مقصد مثل همین اپ «مقاوم» باشد:

1. **کش فقط با داده‌ی سالم نوشته می‌شود** — نتیجه‌ی موفق (`price != null`) روی کش قبلی merge می‌شود؛ خطا هیچ‌وقت مقدار قبلی را بازنویسی نمی‌کند.
2. **قانون نمایش:** نماد تازه آمده → همان؛ نیامده → آخرین مقدار کش با `stale = true` (در UI: LED قرمز، عدد می‌ماند).
3. **زمان‌سنجی:** یک `ts` سراسری برای آخرین به‌روزرسانی موفق نگه داشته می‌شود (نمایش «آخرین به‌روزرسانی HH:MM»).
4. **تاریخچه‌ی نمودار روی دستگاه:** با هر خواندن سالم — حتی بدون تغییر قیمت — یک نقطه به سریِ نماد اضافه می‌شود (کلید: `sourceId|code`، سقف ۲۴۰ نقطه). اگر منبع `sparkPath` نداشت (مثل بورس)، این سری محلی منبع نمودار است. تعداد نقاطِ «نمایش» را UI/کاربر تعیین می‌کند (۶ تا ۶۰ از انتهای سری).
5. **جلوگیری از درخواست تکراری:** اگر چند ویجت/صفحه نماد یکسان را بخواهند، نماد فقط یک بار از شبکه خوانده می‌شود (تجمیع خواسته‌ها بر اساس منبع + کد).

---

## ۱۰) چک‌لیست پیاده‌سازی در اپ مقصد

- [ ] مدل `SourceDef / SymbolDef / Quote` (زیربخش ۲)
- [ ] HTTP با هدرها و timeout های زیربخش ۳ + URL-encode نماد
- [ ] JsonPath کوچک: `.`، `[i]`، `{symbol}`، `|` جایگزین (زیربخش ۴)
- [ ] پارس عدد فارسی/عربی/جدادار (زیربخش ۵)
- [ ] سه نوع منبع: JSON (تکی/گروهی + fallback)، HTML، بورس تهران (زیربخش‌های ۶ و ۸)
- [ ] چهار حالت تغییر: PERCENT / ABSOLUTE / PREV_CLOSE / NONE (زیربخش ۶.۳)
- [ ] خطای per-symbol بدون exception + پیام کوتاه (زیربخش ۶.۴)
- [ ] merge کش فقط با داده‌ی سالم + پرچم stale (زیربخش ۹)
- [ ] تاریخچه‌ی محلی برای نمودار منابع بدون سری (زیربخش ۹)
- [ ] (اختیاری) سازگاری فرمت JSON منابع دلخواه برای import/export بین دو اپ (زیربخش ۲)

---

*ساخته‌شده برای پروژه‌ی «نبض بازار» — نسخه‌ی سند: ۱٫۵ (هم‌گام با نسخه‌ی ۱٫۵ اپ)*
