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

## Publishing

Sideloading is why Play Protect blocks the install and why the accessibility
switch starts greyed out. Both go away when the app is installed from Google
Play, and only then. [`RELEASING.md`](RELEASING.md) covers the whole path -
developer account, signing key, the accessibility declaration that is the real
gate, and what a subscription needs - along with the risks worth knowing before
spending money on any of it. Store copy is drafted in
[`PLAY_LISTING.md`](PLAY_LISTING.md).

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

### The fare on the offer is what the *customer* pays

On Rapido that matters, because captains choose between two plans and they are
completely different arithmetic:

- **Commission plan** — Rapido keeps a percentage of every fare, plus the taxes
  line below.
- **Earnings plan** — a fixed fee per day, and then the fare is the driver's,
  less the same taxes line. Rapido's rate card shows 0% commission on this one.

Set it in the first-run setup or under **Settings ▸ Your plan**; the rate card
inside Rapido shows which you are on, under *Rapido's Commission*. On the
commission plan every figure RideScore shows is about a fifth too high until
this is set, so it asks rather than assuming.

**0% commission does not mean the fare is yours.** Rapido's payout screens show
a *Government Taxes and Other Fees* line that comes off on either plan, even
when the commission is zero. Six real orders from one day on the earnings plan:

| Customer fare | Taxes and other fees | Reached the driver |
|---|---|---|
| ₹43 | −₹4.86 | ₹38.14 |
| ₹51 | −₹5.24 | ₹45.76 |
| ₹56 | −₹5.48 | ₹50.52 |
| ₹73 | −₹6.29 | ₹66.71 |
| ₹74 | −₹6.33 | ₹67.67 |
| ₹89 + ₹10 extra | −₹7.46 | ₹95.54 |

Across a 2.3× range of fares those fit **₹2.87 + 4.65%** to within two paise,
and the last one shows the fee tracks a customer extra as well as the fare.
That is about a tenth of a small order, gone before any petrol is bought,
which a driver reading "0% commission" would never guess. So the taxes
percentage and the flat per-order fee apply on **both** plans, and only the
commission is plan-dependent.

**The commission is not charged on the whole fare, though the screen says it
is.** Three order details from the commission plan, same driver, same city:

| Customer fare | Taxes and other fees | Commission | Total earning |
|---|---|---|---|
| ₹40 | −₹5.46 | −₹6.00 | ₹28.54 |
| ₹82 | −₹8.29 | −₹12.72 | ₹60.99 |
| ₹60 | −₹6.81 | −₹9.20 | ₹43.99 |

Every commission line is headed *"16.00% of Customer Fare"*, and not one of
them is. 16% of ₹40 is ₹6.40; the charge was ₹6.00. Of ₹82, ₹13.12; charged
₹12.72. Of ₹60, ₹9.60; charged ₹9.20. Short by **₹0.40 every time**, on fares
twice apart in size — and ₹0.40 is 16% of ₹2.50. Some fixed ₹2.50 of each fare
carries no commission, so the charge is 16% of the rest. Settings calls it
*fare that carries no commission*; set it to 0 if your own payout screens show
the flat percentage.

**And that commission line carries no GST.** It is the bare 16%. Rapido bills
the GST inside *Government Taxes and Other Fees* — which is why that line is
6.74% + ₹2.76 on the commission plan against 4.65% + ₹2.87 on the earnings
plan. So the *GST on that commission* field defaults to **0**: a driver who
copies their taxes line off a payout screen has already paid it once, and
entering it again would charge it twice. RideScore used to do exactly that,
and overstated the platform's cut by ₹1.55 to ₹2.76 an order.

**Parcel orders pay no tax.** On the same payout screens a parcel order's fare
and its earning are the same number — ₹57 paid ₹57, ₹130 paid ₹130. RideScore
reads the ride type off the offer and skips the taxes and the flat fee on
parcel and delivery work; the behaviour can be switched off if a platform
starts charging on it.

Commission is *not* skipped. Both parcels seen were on the plan that charges
no commission, so they say nothing about how commission behaves on a parcel —
and an advisory tool that guesses a deduction away overstates what the work
pays, which is the error that talks a driver into a bad ride.

### An estimated time is not a promise

When an offer prints no duration RideScore estimates one — and an estimate made
at the average speed is a promise the traffic may not keep. From 172 offers
that printed both a distance and a duration:

| Hour | Median speed |
|---|---|
| 07:00 | 30.2 km/h |
| 08:00 | 20.6 km/h |
| **09:00** | **14.2 km/h** |

At 09:00 — the busiest hour in that log — the road runs at **half** the daily
average. A 12 km trip takes 51 minutes, not 30, so an offer sold at ₹150 an
hour pays ₹88. No single speed can be right for both ends of that.

Two things follow, and both are in the app:

**Speed depends on the trip's length.** Under 2 km the measured median is
14.9 km/h; from 2 to 5 km, 22.0; over 5 km, 32.6. Short hops are slow per km —
the lights, the turn into the lane, the last hundred metres looking for a gate
— and they are the majority: 100 of those 172 were under 5 km. One average
across all of them understates a short trip's minutes by half.

**ACCEPT has to survive the slow case.** When the duration was estimated, the
offer is scored twice: once at the usual speed and once at 60% of it, which is
that 09:00 road. ACCEPT is shown only if it clears your hourly target *both*
times. Anything that clears only on a good run is a MAYBE, with the second
figure printed underneath — "₹139/hr in traffic" — so the risk is visible
rather than hidden inside a green light. A duration the app actually printed is
a fact and is never stress-tested.

Replaying the 1,179 scoreable offers in that log through this: **201 of the 239
ACCEPTs are withdrawn**, nearly all to MAYBE. Those were green lights that
depended on the road being empty.

Both parts are adjustable under **Settings ▸ Riding speeds**, and the stress
test can be switched off if you would rather see the optimistic answer.

### Live traffic, without a traffic API

The speeds above are only a starting point, because the app does not have to
guess for long. **When Rapido prints "5.96 km · 13.97 min" it has already asked
its own routing engine what that road costs at this moment.** That is a live,
traffic-aware estimate for the exact street the driver is on — and RideScore is
already reading that screen.

So it learns from them. Every offer that prints both a distance and a duration
becomes one reading of how fast the roads are. Asked for a speed, the app
answers in this order:

1. **The road right now** — the median of readings from the last 45 minutes,
   once there are at least three. Replaying a real log, 68% of the offers that
   needed an estimate had three or more.
2. **This hour of the day** — learned over previous days. In that log 05:00
   averaged 27.3 km/h and 09:00 averaged 17.7, so the hour matters even when
   the last half hour was quiet. Early morning really is faster.
3. **The shipped default** — only until something has been learned.

A reading is three numbers: a speed, an hour, a timestamp. No coordinates, no
addresses, no fares, no ride identifiers. It is kept in a small text file in
the app's own storage and **is never uploaded**. Readings outside 3–70 km/h are
discarded, so a misparse cannot teach the app that the roads are empty.

**Why not the Google Maps traffic API?** It was considered and rejected on
four counts, any one of which is disqualifying:

- **Privacy.** Asking Google how long a trip takes means sending Google the
  pickup and drop of every offer. RideScore promises that ride and location
  data never leave the phone, and that promise is worth more than a slightly
  better estimate.
- **Cost.** A traffic-aware route request is billed per call. One driver in
  this log saw ~470 offers a day; at that rate a free app would be running a
  five-figure monthly request bill in rupees, per driver.
- **Latency.** An offer card lives for seconds. A network round trip on mobile
  data is not something to put on that path.
- **Key safety.** An API key shipped inside a free APK is extracted and abused,
  and the bill lands on whoever published it.

The platforms' own printed times cost nothing, arrive instantly, describe the
same road at the same moment, and never leave the device. Where they exist they
are simply better. Where they do not, the hour-of-day profile and the traffic
stress test cover the gap.

Turn it off with **Settings ▸ Learn road speed from the apps** if you would
rather the app used only the fixed number you set.

### What actually happened: the completed-rides log

Everything else in RideScore is a forecast. When a ride finishes, Rapido shows
an order-details screen that is not:

```
Bike Order Details
Your Earning  ₹28.54
1.17 km · 4.72 min
Customer Fare                       ₹40
Government Taxes and Other Fees  −₹5.46
Commission (16.00% of Customer Fare) −₹6
Total Earning                    ₹28.54
```

Those minutes are measured, not estimated, and that ₹28.54 is what really
arrived. With **Settings ▸ Completed rides** on, RideScore reads that screen
and appends a row to a second local file, `rides.csv`: real distance, real
minutes, real payout, the deduction and what percentage it came to, plus the
speed the ride actually ran at.

Put beside the offer log it answers the questions no amount of reasoning can —
whether the estimated minutes were right, whether the deduction settings match
the real payout, and what an hour of accepted work actually paid. It also turns
a nuisance into data: before this, receipt screens leaked into the offer log as
phantom offers with fares like ₹197.04 and ₹105.86, and one receipt was parsed
as *two* offers at once.

Same rules as the offer log: off by default, private to the app, capped in
size, and it leaves the phone only when you share it.

Two receipt layouts exist and both are read. A commission-plan receipt ends
with a "Total Earning" row; a subscription-plan one has no such row at all —
the amount sits at the top under "Your Earning" — and adds a **Customer Extra**
row. The extra matters, because the platform's fee is charged on it: a ₹52 fare
with a ₹10 extra was billed ₹5.76, which is 9.29% of ₹62, not of ₹52. The
payment table is two columns, and depending on how the screen is walked its
text arrives either interleaved or as every label followed by every amount, so
the parser matches the rows up either way.

That seventh receipt is also the best evidence yet for the deduction formula:
₹2.87 + 4.65% of ₹62 is ₹5.75 against the ₹5.76 actually taken — one paisa,
from a formula fitted on six earlier payouts, on the other plan.

### Screens RideScore stays off

A plan page, the subscription page, a rate card and a finished order's receipt
all carry rupee figures, and the parser used to lift them: in one log of 1,894
offers, 153 rows carried fares no ride ever paid — ₹40,000, ₹5,400, ₹750,
₹491.66 — scraped off pages like those. The card then covered the very text
the driver had opened the page to read.

The test is the **affordance, not the words**: a screen with something to
accept is an offer screen, whatever else is printed on it. That matters,
because Rapido's home screen shows a live offer and a "Low Balance — Orders
will be blocked" banner at the same time, and a rule that went by words alone
would have swallowed a real ₹45 delivery.

Read your own numbers off any completed order's payment breakdown rather than
copying the ones above; they will differ by city and by category. The settings
screen shows what your figures come to on a ₹70 fare so you can check them
against a real payout in seconds.

The default is nothing deducted at all — so RideScore never invents a cut the
driver did not tell it about.

**The daily plan fee is deliberately not subtracted from individual offers.**
Once the day's fee is paid it is spent whichever order comes next, so it has no
bearing on whether *this* offer is worth taking. It belongs to the decision
about whether to go out at all, and burying it in a per-offer rate would make
every offer look worse than the choice actually is.

A **handling fee per order** is separate and is subtracted, because it is
charged per order. It is flat, which is exactly what makes it bite hardest on
small ones: ₹5 is 13% of a ₹38 order and 3.5% of a ₹142 one.

### Trip-count bonuses

Both apps run them - "12 trips today for ₹300" - and while one is live an offer
is not worth only its fare. Set the bonus and the target on the **Home** tab and
tap **+1 trip** as you finish each one. Each remaining offer is then valued at
its fare plus its share of the bonus:

```
share per trip = bonus remaining / trips still needed
```

The share grows as the target approaches, which is exactly how the decision
should change: ₹300 over 12 trips is ₹25 an offer, but on the last trip that
same ₹300 rides on one order, and a ₹30 fare that is otherwise a plain reject
at ₹32/hour becomes ₹932/hour and worth taking. The card shows how much of the
total is bonus, so it is never a mystery why a poor-looking offer went green.

**A count quest does not lift every offer equally, and that is the useful
part.** The bonus per trip is fixed no matter how big the trip is, so its
effect on the *hourly* rate is largest on the shortest ride. From one real
shift, with ₹250 for 15 trips running:

| Offer | Alone | With the quest | |
|---|---|---|---|
| ₹38, 3.7 km, 14 min | ₹113/hr | **₹184/hr** | 🔴 → 🟢 |
| ₹69, 8.9 km, 27 min | ₹89/hr | ₹126/hr | 🔴 → 🔴 |
| ₹142, 17.3 km, 45 min | ₹116/hr | ₹138/hr | 🔴 → 🟡 |

The same ₹16.67 rides on all three, but spread over 14 minutes instead of 45.
The cheapest, shortest offer is the best one to take, which is the opposite of
the instinct to grab the ₹142 fare.

Two limits worth knowing. **The trip count is yours to keep** - RideScore reads
offer screens and has no way to know a ride finished, so it will not guess.
And it assumes you will actually reach the target: if the bonus is out of reach
with the time left, the share it adds is fiction, and the honest move is to
switch it off. Commission, when enabled, is charged on the fare only, not on
the bonus.

### A card appeared over a trip already under way

A navigation screen carries a fare, a distance and a duration exactly like an
offer does, so it used to parse as one. RideScore now recognises a trip in
progress and stays out of the way.

What distinguishes them is not the wording but the affordance: an offer has
something to accept. That matters because both apps put a new offer on top of
an active trip when they have one, and those must still be scored - a screen
showing "Navigate" **and** an accept button is a real offer.

### A long trip scored green and then there was no order back

The default calculation scores the ride it was shown: fare, distance, time. It
knows nothing about where the drop leaves you. A 30 km drop for ₹280 is ₹157
net an hour on the paid leg and green under most thresholds - and if there is
no order back, the ride home is 30 km of unpaid fuel and an unpaid hour, which
turns the same offer into **₹38 net an hour**.

Switch on **Settings ▸ The ride back ▸ Assume you ride back empty** and trips
over the threshold (10 km by default) are scored on the round trip: the return
kilometres cost fuel and time, and earn nothing. The card marks those offers
`incl. ride back` so it is never applied silently.

This assumes nothing about demand. It does not claim the drop area is quiet -
only that *if* no order comes, this is what the offer was really worth. Whether
that is the right assumption for your city and your hour is a judgement the
ride log will eventually answer, since it records every destination you were
offered.

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

1. On the "App blocked" dialog, tap **More details** → **Install anyway**. On
   most builds this is all that is needed and Play Protect stays on.
2. If there is no such option: Play Store → profile picture → **Play Protect**
   → **⚙️ gear** → turn off *Scan apps with Play Protect*, install, turn it
   back on. If it then offers to uninstall RideScore, keep it.

### The Play Protect switch itself is greyed out

Common on 2024-and-later phones, and usually **not** Play Protect at all —
something else is holding it down. In rough order of likelihood:

- **Samsung: Auto Blocker.** On One UI 6.1 and newer this is *on by default*,
  blocks sideloading outright, and greys out the controls that would let you
  allow it. **Settings ▸ Security and privacy ▸ Auto Blocker ▸ off.** This is
  the answer on most newer Samsung devices.
- **Xiaomi / Redmi / POCO.** *Settings ▸ Passwords & security ▸ System
  security* — turn off **Scan before installing** and, on HyperOS, **Enhanced
  security**. MIUI also re-enables these after some updates.
- **Realme / OPPO / vivo / OnePlus.** *Settings ▸ Security / Security check* —
  turn off **Payment protection** or **App security check**.
- **A managed or supervised phone.** A work profile, an employer's MDM, or
  Family Link supervision locks the toggle and no OEM setting will free it.
  Check **Settings ▸ Security ▸ Device admin apps**. If something is listed,
  that is the cause, and the account holder has to allow the install.
- **An out-of-date Play Store.** Play Store ▸ profile ▸ Settings ▸ About ▸
  *Update Play Store*, then reboot. A stale Play Services can grey the switch.

Two routes that avoid the fight entirely:

- **Install over USB from the Mac.** `adb install -r ridescore.apk` goes
  through a different installer path and is usually not blocked. It needs
  Developer options ▸ USB debugging on the phone, and the platform-tools
  package on the Mac.
- **Ship it through Play internal testing.** A one-time developer account fee,
  and then testers install from the Play Store itself, so Play Protect never
  objects on any device. See [RELEASING.md](RELEASING.md). This is the only
  approach that scales past your own phone: every new device is another round
  of this, and on a managed phone there is no round to win.

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

**A printed pickup time always wins over an estimate.** Uber gives both legs -
`4 min (0.6 km)` to the pickup and `10 mins (3.1 km)` for the trip - so its
totals are exact and the pickup-speed setting never comes into it. Rapido gives
only the trip, so the pickup leg is worked out there. The card marks the
difference: a total containing an estimate is shown with a tilde (`~19 min`),
an exact one without, and the detailed card breaks out the two legs
(`4 min to pickup + 10 min trip`).

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
- **The rate is the headline.** Net ₹/hour is the biggest thing on the card in
  both modes, because it is what the decision is made on and what a rider
  glancing at a handlebar has time to read. Card size is adjustable up to 1.8×.
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
