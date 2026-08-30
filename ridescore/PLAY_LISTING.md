# Play Store listing content

Draft copy for the Play Console. Written to be honest about what the app does,
partly because that is right and partly because overclaiming is what gets
accessibility apps rejected.

---

## App name (30 characters max)

```
RideScore: Ride Offer Check
```

## Short description (80 characters max)

```
See what a bike-taxi offer really pays per hour, after petrol. Before you accept.
```

## Full description (4000 characters max)

```
RideScore reads the ride offer on your screen and tells you what it is actually
worth — per hour and per kilometre, after petrol — in the few seconds you have
to decide.

WHAT IT SHOWS

A small card appears next to the offer:

  ACCEPT / MAYBE / REJECT
  ₹184/hr
  ₹38 · 3.7 km · 14 min

It works out the fuel cost from your bike's mileage and today's petrol price,
subtracts it from the fare, and divides by the time the whole job takes —
including riding to the pickup, which most drivers forget to count.

WHAT IT DOES NOT DO

RideScore never accepts, declines, taps or swipes anything. It cannot: the app
is built without the ability to act inside Rapido or Uber. It shows you the
number. The decision stays yours.

BUILT FOR INDIAN BIKE TAXIS

• Defaults set for a Bajaj Pulsar 150 at 37.5 km/L — change to your bike
• Petrol price you set yourself
• Handles ₹45 + ₹15 bonus fares, and multiple offers on one screen
• Ranks them and shows you the best one
• Works with Rapido Captain and Uber Driver

THINGS THAT COST YOU MONEY QUIETLY

• The ride back. A 30 km drop can pay ₹157/hour and still be a bad job once you
  count riding home empty. Switch it on and long trips are scored on the round
  trip.
• Trip bonuses. On a "15 trips for ₹250" quest, every trip carries part of that
  bonus — and because the bonus is the same whatever the trip's size, the short
  cheap offers become the good ones. RideScore does that arithmetic for you.

YOUR DATA STAYS ON YOUR PHONE

RideScore has no internet permission. It cannot send your data anywhere, even
by accident. No account, no servers, no analytics, no location, no screenshots
saved. Everything is calculated on the device.

An optional ride log saves offers to a file on your phone so you can look at
patterns later. It stays on the phone unless you share it yourself.

SAFETY

Read the card before you accept, while you are stopped. Turn on the voice
option and you do not need to look at the phone at all. Never read it while
riding.

NOTE ON PERMISSIONS

RideScore uses Android's accessibility service to read the fare, distance and
time from Rapido and Uber offer screens. That is its core function and there is
no other way to do it. It reads those two apps only, and nothing it reads
leaves your phone. You turn the permission on yourself and can turn it off at
any time.
```

## Accessibility API declaration (Play Console → App content)

```
RideScore's core function is to read the fare, distance and estimated time from
ride-offer screens in Rapido Captain and Uber Driver, so the driver can see what
an offer pays per hour and per kilometre after fuel before deciding whether to
accept it. Drivers have only a few seconds to make this decision.

The accessibility API is the only way to obtain this information. The data is
displayed on the offer screen by another app; there is no API, export or
intent that exposes it.

The service is strictly read-only. It does not declare canPerformGestures, does
not use flagRequestFilterKeyEvents, and contains no call to performAction on
any node in another application. It cannot accept, decline, tap, swipe or
scroll anything. This is enforced by the service configuration, not only by
policy.

The service checks the foreground package before requesting any window content,
so no application other than the two supported driver apps is ever read.

No data collected through the accessibility API is transmitted. The application
does not request the INTERNET permission. All calculation happens on the device.

The app is not an accessibility tool for users with disabilities and does not
set isAccessibilityTool. Users are shown an in-app disclosure describing what is
read and where it goes, and must accept it before the app offers to enable the
service.
```

## Data safety answers

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | No data is shared. |
| Data collected | Only if the user enables the optional ride log: "App activity — other actions", stored on-device only. |
| Is data encrypted in transit? | Not applicable — no data is transmitted. |
| Can users request data deletion? | Yes — Settings → Ride log → Delete log, and uninstalling removes everything. |
| Is all collected data optional? | Yes. |

## Content rating

Everyone. No user-generated content, no ads, no data sharing.

## Assets still needed

- App icon, 512×512 PNG
- Feature graphic, 1024×500
- At least two phone screenshots — include the disclosure screen and the card
  over a real offer
- A short screen recording for the reviewer showing the card appearing and the
  driver deciding, which shortens accessibility review
