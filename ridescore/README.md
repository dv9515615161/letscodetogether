# RideScore

An advisory Android app for Indian bike-taxi drivers. It reads the offer screen
of **Rapido Captain** and **Uber Driver**, works out what the offer is actually
worth after fuel, and shows a small floating card:

```
┌─────────────────────┐
│ 🟢 ACCEPT           │
│ ₹60                 │
│ 7.7 km • 19 min     │
│ ₹112 net/hr         │
│ ₹4.59 net/km        │
└─────────────────────┘
```

The driver has a few seconds to decide, so everything happens on the phone and
finishes in under a millisecond.

> **RideScore never accepts, declines, taps, swipes or scrolls anything.** It
> reads and advises. The driver decides. This is enforced structurally: the
> accessibility service does not declare `canPerformGestures`, and there is no
> code path anywhere in the app that calls `performAction` or dispatches a
> gesture to another app.

---

## Contents

- [Build and run](#build-and-run)
- [First run on the phone](#first-run-on-the-phone)
- [How the decision is made](#how-the-decision-is-made)
- [Settings](#settings)
- [Project layout](#project-layout)
- [How the screen is read](#how-the-screen-is-read)
- [Performance design](#performance-design)
- [Privacy](#privacy)
- [Tests](#tests)
- [Adding Ola](#adding-ola)
- [Platform limits, honestly](#platform-limits-honestly)

---

## Build and run

The Android project is the `ridescore/` directory of this repository. (The
repository root holds an unrelated Java collections exercise; the two do not
interact.)

**Requirements**

| | |
|---|---|
| Android Studio | Ladybug (2024.2) or newer |
| JDK | 17 (bundled with Android Studio) |
| Android Gradle Plugin | 8.7.3 (declared in `gradle/libs.versions.toml`) |
| Gradle | 8.11.1 (via the committed wrapper) |
| compileSdk / targetSdk | 35 |
| minSdk | 26 (Android 8.0) |

**Steps**

1. `git clone` this repository.
2. Android Studio → **File ▸ Open** → select the **`ridescore`** folder (not the
   repository root — the root is a Maven project).
3. Let Gradle sync. It will download the AGP, Compose and ML Kit artifacts on
   first run.
4. If Studio asks for an SDK location it writes `ridescore/local.properties`
   itself. To create it by hand:
   ```properties
   sdk.dir=/Users/you/Library/Android/sdk
   ```
5. Plug in a phone with USB debugging on (or start an emulator, though the real
   value only shows with Rapido/Uber installed).
6. Press **Run ▸ Run 'app'**.

**From the command line**

```bash
cd ridescore
./gradlew :app:assembleDebug          # build the APK
./gradlew :app:installDebug           # build and install on a connected device
./gradlew :app:testDebugUnitTest      # run the unit tests
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

---

## First run on the phone

The Home tab shows each permission with a button next to it.

1. **Accessibility service** → opens Android Settings ▸ Accessibility ▸
   *RideScore offer reader* → turn on. This is what lets RideScore read the
   offer text. Android shows its own warning screen; that warning is generic to
   all accessibility services.
2. **Draw over other apps** → needed for the floating card.
3. **Notifications** → only used by the optional OCR fallback, which must run as
   a foreground service. Skip it if you leave OCR off.
4. Press **Analyse the sample offer** to see the card without waiting for a real
   ride offer.
5. Open Rapido or Uber and drive. The card appears when an offer does.

### Offers that arrive while the phone is locked

Rapido can show an offer over the lock screen because it is an *activity*, and
activities may declare `showWhenLocked`. A floating overlay may not: Android
hides overlay windows behind the keyguard, for every app, with no opt-in.

So on the lock screen RideScore still reads the offer and still scores it, but
the card cannot be drawn. Two things do get through, and both are on by default
or one switch away:

- **A notification.** The verdict is posted as a silent, high-priority
  notification whenever the card cannot be drawn - the one channel Android does
  let through to the lock screen. Allow notifications on the Home tab, and keep
  *Notify on the lock screen* on in Settings.
- **Voice.** Turn it on in Settings and the verdict is spoken, which is the
  safer way to take it on a bike anyway.

RideScore deliberately does not use a full-screen intent to force itself in
front of the offer. That would cover the screen the driver is trying to read.

### Everything comes out as MAYBE

Usually the per-km rule. ACCEPT requires the hourly rate **and** the net ₹/km
floor to pass, and ₹/km is the harder of the two on short bike rides: at
₹3.20/km of fuel, a ₹9/km *net* floor needs roughly ₹12.20/km gross, which many
bike-taxi fares never reach. Either lower **Minimum net per km** to something
your city's fares actually pay, or switch off **Require both metrics** and let
the hourly rate decide alone. The detailed card names this rule when it is the
one holding an offer back.

The other cause is confidence: a read below the accept threshold is capped at
MAYBE on purpose. The Home tab shows the confidence of the last read.

### Google Play Protect blocks the install

Expected, and not a sign of a bad build. RideScore declares an accessibility
service, and Play Protect hard-blocks any sideloaded app that requests one,
because that permission is the one Android banking malware abuses most. The
block is about the category, not about anything found in this app.

To install anyway:

1. Play Store → profile picture → **Play Protect** → **⚙️ gear** → turn off
   *Scan apps with Play Protect*.
2. Open the APK again and install it.
3. Turn Play Protect back on. If it then offers to uninstall RideScore, keep it.

### The Accessibility switch is greyed out

Android 13 and newer put accessibility behind *restricted settings* for any app
that did not come from an app store. Clear it once:

**Settings ▸ Apps ▸ RideScore ▸ ⋮ (top right) ▸ Allow restricted settings**

The switch in Settings ▸ Accessibility then works normally.

Both of these are Android protections working as designed. The way to avoid them
entirely is to ship through the Play Store, which requires a developer account
and a review that specifically covers accessibility use.

Two things worth doing on most Indian phones: exempt RideScore from battery
optimisation (Settings ▸ Apps ▸ RideScore ▸ Battery ▸ Unrestricted), and check
the Home tab's **Status** section, which names the last foreground package it
saw. If your Rapido build has a package name other than `com.rapido.rider`, that
is where you will see it — add it to `SourceApp` and rebuild.

---

## How the decision is made

Everything below runs locally, deterministically, in a few microseconds.

### The numbers

```
fuelCostPerKm   = petrolPrice / mileage                 ₹120 / 37.5   = ₹3.20/km
totalDistance   = pickupKm + tripKm                     1.8 + 5.9     = 7.7 km
pickupMinutes   = ceil(pickupKm / pickupSpeed × 60)     1.8 @ 17 km/h = 7 min
totalTime       = pickupMinutes + tripMinutes           7 + 12        = 19 min
gross           = totalFare                             ₹45 + ₹15     = ₹60
fuelCost        = totalDistance × fuelCostPerKm         7.7 × 3.20    = ₹24.64
maintenance     = totalDistance × maintenancePerKm      (off by default)
platformFee     = gross × feePercent / 100              (off by default)
net             = gross − fuel − maintenance − fee      60 − 24.64    = ₹35.36
netPerHour      = net / (totalTime / 60)                35.36 / 0.317 = ₹111.66
netPerKm        = net / totalDistance                   35.36 / 7.7   = ₹4.59
grossPerHour    = gross / (totalTime / 60)                            = ₹189.47
grossPerKm      = gross / totalDistance                               = ₹7.79
```

Gross, fuel and net are kept separate and shown separately. **With the optional
costs off — which is the default — "net" means "after fuel", and nothing more
is claimed.** Maintenance and platform commission are subtracted only if the
driver switches them on.

Estimated pickup time is rounded **up** to the whole minute, which is what the
driver would see on their own clock: 1.8 km at 17 km/h is 6.35 minutes, counted
as 7. That reproduces the worked example exactly (19 minutes total, ₹111.66
net/hour) and the second example too (1.4 km → 5 min, 22 minutes total).

### The traffic light

| Condition (defaults in brackets) | Result |
|---|---|
| Fare, distance or time could not be read | ⚪ CHECK |
| Read confidence below the usable floor [50%] | ⚪ CHECK |
| net/hour below the maybe threshold [₹120] | 🔴 REJECT |
| net/hour at or above accept [₹150] **and** net/km at or above floor [₹9] | 🟢 ACCEPT |
| Anything in between | 🟡 MAYBE |
| Would be ACCEPT but confidence is below [75%] | 🟡 MAYBE (never a confident accept) |

Both metrics matter, which is the point of using them together: a long highway
run can clear ₹150/hour while paying ₹4/km, and a short hop can look fine per km
while burning the hour. If you prefer the hourly rule alone, turn off *Require
both metrics*.

### Several offers at once

Rapido regularly stacks two or three. All of them are parsed, analysed and
ranked by net ₹/hour (net ₹/km breaks ties, then confidence). The card shows the
best one in full and up to two more as one-liners. If none of them is worth
taking, the header reads **🔴 NO GOOD ORDER**.

---

## Settings

Every rule the engine uses is editable in the Settings tab.

| Setting | Default |
|---|---|
| Vehicle | Bike — Bajaj Pulsar 150 |
| Mileage | 37.5 km/L (presets 35, 36, 37, 37.5, 38, 39, 40, plus custom) |
| Petrol price | ₹120/L (custom) |
| Accept threshold | ₹150 net/hour |
| Maybe threshold | ₹120 net/hour |
| Minimum net per km | ₹9/km |
| Require both metrics | On |
| Pickup speed | 17 km/h |
| Include pickup distance | On |
| Include pickup time | On |
| Maintenance cost | **Off** (₹1.50/km when enabled) |
| Platform fee | **Off** (10% when enabled) |
| Overlay | On, Quick mode |
| Voice | Off |
| Apps watched | Both |
| OCR fallback | **Off** (needs screen-capture consent) |
| Accept needs confidence above | 75% |
| Usable confidence floor | 50% |

Destinations: RideScore displays the destination it read (`→ Nallagandla`). It
does **not** say an area is in high demand, because it has no demand data. The
settings model already carries a `preferredDestinations` list so historical
destination scoring can be added later without reshaping anything.

---

## Project layout

```
ridescore/
├── app/src/main/java/com/ridescore/app/
│   ├── domain/model/          RideOffer, RideAnalysis, ScreenSnapshot, Decision, SourceApp
│   ├── domain/settings/       RideScoreSettings — every configurable rule
│   ├── data/settings/         DataStore persistence + a lock-free settings cache
│   ├── parser/                TextNormalizer, Extractors, OfferSegmenter,
│   │                          RideOfferParser, BaseOfferParser, RapidoParser,
│   │                          UberParser, ParserRegistry
│   ├── calculator/            FareCalculator — fuel, net, per-hour, per-km
│   ├── decision/              DecisionEngine, OfferRanker
│   ├── engine/                RideScoreEngine (screen → ranked decisions),
│   │                          OfferPipeline (conflation, dedupe, cache)
│   ├── accessibility/         RideScoreAccessibilityService, NodeTextExtractor
│   ├── ocr/                   MlKitOcrProvider, ScreenCaptureManager/Service
│   ├── overlay/               OverlayPresenter (pure), OverlayController (window)
│   ├── tts/                   VoicePhrases (pure), VoiceAnnouncer
│   ├── ui/                    Compose home + settings screens
│   └── util/                  Format, Diagnostics
└── app/src/test/java/…        83 unit tests
```

The design rule: **everything from screen text to decision is pure Kotlin with
no Android types.** `ScreenSnapshot` in, `ScreenAnalysis` out. That is why the
parsing, the arithmetic, the ranking, the card wording, the spoken phrases and
the pipeline's staleness behaviour are all unit-tested on the JVM, with no
emulator and no Robolectric.

---

## How the screen is read

**Accessibility text first, always.** `NodeTextExtractor` walks the accessibility
tree of the foreground window and pulls out the visible text. It is exact, it is
free, and on a normal offer screen it is all RideScore needs. While walking, it
also works out where each offer card begins and ends: a node counts as a card
when its subtree contains both a fare and a distance and no descendant of it
does — the smallest self-contained offer. That is what makes two stacked Rapido
offers rank correctly instead of merging into one.

**OCR only as a fallback.** If — and only if — the driver has enabled it, a
supported app is in front, and the accessibility parse came back without a
usable fare, `MlKitOcrProvider` grabs one frame and runs ML Kit's on-device Latin
recogniser over it, at most once every 1.2 seconds. On a screen whose text is
exposed properly, OCR never runs at all. Nothing is uploaded: the model is
bundled and runs locally.

**Parsing is layout-independent.** Four strategies, in order of trust:

1. **Labelled lines** — `Pickup 1.8 km`, `23 mins (9.4 km) trip`.
2. **Sum expressions** — `₹45 + ₹15` yields base, bonus and total at once.
3. **Positional fallback** — both apps show the pickup leg before the trip leg.
   Used when nothing is labelled, and it costs confidence.
4. **Reconciliation** — a printed total that disagrees with base + bonus lowers
   confidence and is recorded as a note rather than silently picked.

Formats handled: `₹45`, `Rs. 45`, `Rs 45`, `INR 45`, `₹ 45 + ₹ 15`, `45 + 15`,
`₹1,250`, `₹128.55`, Devanagari digits, non-breaking spaces, `1.8 km`, `1.8km`,
`1.8 KMs`, `800 m`, `12 min`, `12 mins`, `25 minutes`, `1 hr 5 min`.

**Confidence, and never inventing a number.** Every field carries a confidence
weight; a missing field is left `null` rather than filled in. A low-confidence
read can never be shown as ACCEPT, and an unreadable fare shows
**⚪ CHECK — Could not read fare** instead of a recommendation. OCR text is
additionally repaired for the classic confusions (`l.8 km` → `1.8 km`,
`₹4S` → `₹45`) but only where a number is expected and only when a real digit
anchors the run — `₹lS` is left unreadable rather than guessed into `₹15`.

---

## Performance design

Target: the card is up as soon as the offer is, and a stale offer is never shown.

- **Debounced at the source.** The first screen change after a quiet moment is
  read immediately; a burst (a ticking countdown, an animating card) is collapsed
  into one read 100 ms later. The framework also throttles at
  `notificationTimeout=100` before events reach the process.
- **Never reads unsupported apps.** The package name on the event is checked
  before the window content is requested.
- **Conflated inbox.** The analysis queue is a `Channel.CONFLATED`: a snapshot
  arriving while another is being analysed *replaces* anything waiting. Work
  never piles up and stale offers are dropped, not analysed late.
- **Duplicate suppression.** Identical screen text is recognised by signature and
  skipped.
- **Result cache.** A 16-entry LRU keyed by screen signature and settings serves
  repeat screens without re-parsing.
- **Cheap analysis.** Parse plus arithmetic plus ranking for a two-offer screen
  is a few hundred microseconds on a JVM; the test suite fails if it exceeds
  2 ms.
- **Overlay updates are text sets on existing views** — no re-inflation, no
  recomposition. That is why the card is built from Views rather than Compose,
  while the app's own screens are Compose.

---

## Privacy

- No `INTERNET` permission is declared. The app **cannot** phone home.
- No analytics, no crash reporting, no account, no cloud, no AI API. The decision
  engine is a few dozen lines of arithmetic and comparisons.
- Screenshots are never stored — a captured frame is converted, read, and
  recycled in the same function.
- Screen text never leaves the process.
- Settings live in a local DataStore file.
- **The ride log, when the driver switches it on, is the only other thing
  written to disk.** It is off by default. It lives in the app's private
  storage, which no other app can read, it is capped at 2 MB and trimmed to the
  most recent rows after that, and it leaves the phone only when the driver
  shares it themselves through Android's share sheet. Nothing uploads it, and
  it can be deleted from Settings at any time.

---

## Tests

```bash
cd ridescore
./gradlew :app:testDebugUnitTest
```

83 tests, all on the JVM. Coverage against the brief's checklist:

| Requested | Where |
|---|---|
| 1. ₹45 + ₹15 = ₹60 | `RapidoParserTest`, `ExtractorsTest` |
| 2. 1.8 + 5.9 = 7.7 km | `FareCalculatorTest` |
| 3. 7.7 km at 37.5 km/L and ₹120/L | `FareCalculatorTest` (fuel ₹24.64) |
| 4. 19 minutes total | `FareCalculatorTest`, `RideScoreEngineTest` |
| 5. net/hour | `FareCalculatorTest` (₹111.66), `DecisionEngineTest` |
| 6. Rapido parser | `RapidoParserTest` (9 cases) |
| 7. Uber parser | `UberParserTest` (4 cases) |
| 8. Multiple offers | `MultipleOffersTest`, `OverlayPresenterTest` |
| 9. Missing pickup time | `FareCalculatorTest`, `RapidoParserTest` |
| 10. Missing bonus | `RapidoParserTest` |
| 11. OCR errors | `TextNormalizerTest`, `RapidoParserTest` |
| 12. ₹ formatting variants | `TextNormalizerTest`, `ExtractorsTest`, `RapidoParserTest` |
| 13. Decimal distances | `ExtractorsTest` |
| 14. Duplicate accessibility events | `OfferPipelineTest` |
| 15. Slow device | `OfferPipelineTest` (25 ms analyses, no backlog) |
| 16. Rapid screen changes | `OfferPipelineTest` (10 offers → newest wins) |
| Performance | `RideScoreEngineTest` (< 2 ms per analysis) |
| Card wording | `OverlayPresenterTest` |
| Voice wording | `VoicePhrasesTest` |
| Decision thresholds | `DecisionEngineTest` |

---

## The ride log

Switch on **Settings ▸ Ride log ▸ Keep a log of offers** and every offer
RideScore sees is appended to `ride-log.csv` in the app's private storage. Tap
**Export** to send it anywhere - email, Drive, a chat - and open it in any
spreadsheet.

One row per offer, with `screen_id` grouping the offers that appeared together
and `rank` recording how they were ordered:

| | |
|---|---|
| When | `logged_at`, `date`, `time`, `hour`, `weekday` |
| Offer | `app`, `ride_type`, `base_fare`, `bonus_fare`, `total_fare` |
| Journey | `pickup_km`, `trip_km`, `total_km`, `pickup_min`, `trip_min`, `total_min` |
| Money | `fuel_cost`, `maintenance_cost`, `platform_fee`, `net_earning` |
| Rates | `gross_per_hour`, `net_per_hour`, `gross_per_km`, `net_per_km` |
| Verdict | `decision`, `confidence`, `offers_on_screen`, `rank` |
| Where | `pickup_location`, `destination` |
| Assumptions | `mileage_kmpl`, `petrol_price`, `accept_per_hour`, `min_net_per_km` |

Every row carries the settings that were in force when it was written, because
a row whose mileage and petrol price are unknown stops meaning anything the
moment those change. A field that could not be read is left **empty**, never
zero - so a blank fare in the log is a failed read, not a free ride.

The identical offer seen again within five minutes is the same offer, not a new
one, and is not written twice.

This is what makes the questions worth asking answerable: which hours actually
pay, whether the ₹/km floor is set somewhere realistic for your city, which
destinations lead to a good next ride, and how often RideScore's advice matched
what the shift was really worth.

## Adding Ola

The architecture is already split for it. Three steps, no changes anywhere else:

1. `SourceApp.OLA` already exists with its package name.
2. Write the parser:
   ```kotlin
   class OlaParser : BaseOfferParser(SourceApp.OLA) {
       override val pickupWords = Keywords.PICKUP + listOf("pickup distance")
       override val dropWords = Keywords.DROP + listOf("drop distance")
   }
   ```
3. Add it to `ParserRegistry.DEFAULT_PARSERS`, and add an `AppMode` entry if you
   want it separately selectable.

The calculator, decision engine, ranker, pipeline and overlay are app-agnostic
and need no changes.

---

## Platform limits, honestly

Everything here uses public, documented Android APIs. No root, no reverse
engineering, no modification of Rapido or Uber, nothing that touches platform
security. A few consequences of staying inside those rules are worth stating
plainly:

- **The driver must enable the accessibility service by hand**, and Android shows
  a warning screen when they do. There is no way for an app to grant this to
  itself, and RideScore does not try.
- **Accessibility text is only available for text the app draws as views.**
  Anything rendered onto a canvas, a map surface or an image — which some offer
  cards do — is invisible to the accessibility tree. That is exactly the gap the
  OCR fallback fills, and it is why the fallback exists rather than being an
  optimisation.
- **OCR needs `MediaProjection`**, which requires an explicit consent dialog and
  keeps Android's screen-recording indicator visible the whole time. Both are
  enforced by the OS. This is why OCR is off by default and opt-in.
- **Package names are best-effort.** They are `com.rapido.rider` and
  `com.ubercab.driver` here. Regional or updated builds can differ; the Home tab
  shows the last foreground package so a mismatch is visible in seconds, and
  `SourceApp` is a one-line change.
- **Parser tuning is expected.** The parsers are written against the layouts in
  the brief and the common variants of them, with several independent strategies
  and no hardcoded line positions. Real screens still drift. When a screen does
  not parse cleanly the app says ⚪ CHECK rather than guessing — that is the
  intended failure mode.
- **Android may stop the service.** Aggressive battery managers on Xiaomi, Oppo,
  Vivo and Samsung devices can kill accessibility services; exempting RideScore
  from battery optimisation is the documented remedy.
- **Auto-accepting is not a limitation, it is a deliberate exclusion.** It is
  technically expressible with an accessibility service, and RideScore refuses to
  do it: no gesture capability is declared, and the driver stays in control.

---

## Safety

The card is meant to be read **before** accepting, while stopped. It is small,
draggable, and positioned clear of the Accept button both apps place at the
bottom of the screen. The optional voice announcement exists precisely so the
driver does not have to look at the phone. RideScore never asks for a tap, never
counts down, and never nags.
