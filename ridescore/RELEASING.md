# Releasing RideScore on Google Play

Everything here is about getting from a sideloaded APK to a free app anyone can
install from the Play Store without a Play Protect warning. **Publishing on
Play is the only way to make the warning go away** — it is triggered by
sideloading an app that declares an accessibility service, and no code change
avoids it.

Read the [Risks](#risks-worth-knowing-before-you-spend-money) section before
spending money on any of this.

---

## 1. Google Play developer account

- Sign up at <https://play.google.com/console> — **US$25, one time**.
- Choose the account type carefully:

| | Personal account | Organisation account |
|---|---|---|
| Needs | ID verification | ID + a D-U-N-S number for your business |
| Closed testing before launch | **12 testers for 14 continuous days** | Not required |
| Time to first release | ~3 weeks minimum | Days |

**The 12-testers-for-14-days rule is the long pole for a personal account.** If
you want to launch sooner, register as an organisation. Either way, start this
step first — everything else can be done while it runs.

## 2. Create a release signing key

This key *is* your app's identity on Play. If you lose it you cannot update
your own app. Generate it once and back it up somewhere you will still have in
five years.

```bash
keytool -genkeypair -v \
  -keystore ridescore-release.jks \
  -alias ridescore \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then create `ridescore/keystore.properties` — **never commit this file**, it is
already in `.gitignore`:

```properties
storeFile=/absolute/path/to/ridescore-release.jks
storePassword=...
keyAlias=ridescore
keyPassword=...
```

Enrol in **Play App Signing** when you upload (the default). Google then holds
the signing key and your upload key can be replaced if lost.

## 3. Build the bundle

Play takes an `.aab`, not an `.apk`:

```bash
cd ridescore
./gradlew :app:bundleRelease \
  -PridescoreVersionCode=2 -PridescoreVersionName=1.1
```

Output: `app/build/outputs/bundle/release/app-release.aab`.

`versionCode` must increase with every upload. The build is signed only if
`keystore.properties` (or the `RIDESCORE_KEYSTORE*` environment variables) are
present; without them it still builds, unsigned.

## 4. The accessibility declaration — the real gate

This is where apps like this one get rejected, so treat it as the main task
rather than paperwork.

In Play Console, under **App content → Permissions → Accessibility API**, you
must justify the use. What matters:

- **Do not** set `android:isAccessibilityTool="true"`. That flag is for tools
  built for users with disabilities. RideScore is not one, and claiming it is
  gets an app removed.
- Describe the core function plainly: *reads the fare, distance and time from
  ride-offer screens in Rapido Captain and Uber Driver, and displays what the
  offer is worth per hour and per kilometre. Read-only. It cannot accept,
  decline or interact with those apps; the service declares no gesture
  capability.*
- Point at the in-app disclosure (the first screen the app shows) and at the
  privacy policy.
- Expect questions. Answer them with the specific technical constraint — that
  the service is configured without `canPerformGestures` — rather than a
  promise, because it is checkable.

A **screen recording** of the app in use, including the disclosure screen and
the card appearing over a real offer, makes review go faster.

## 5. Store listing and data safety

- **Privacy policy URL** is mandatory. `docs/privacy.html` in this repository is
  ready to serve; turn on GitHub Pages (Settings → Pages → deploy from `main`,
  folder `/docs`) and the URL is
  `https://<user>.github.io/<repo>/privacy.html`. It is already wired into the
  app's disclosure screen — update `MainActivity.PRIVACY_POLICY_URL` if you host
  it elsewhere.
- **Data safety form**: RideScore collects nothing and transmits nothing, so
  every "data shared" answer is No. Say yes to *data collected* only if the ride
  log is on, and describe it as stored on-device and never transmitted.
- Listing text is in [`PLAY_LISTING.md`](PLAY_LISTING.md).

## 6. Money

RideScore ships **free, with no ads and no in-app purchases**. That is a
product decision, and it also removes a pile of work: no Play Billing, no
merchant account, no subscription products to configure, no purchase
verification, and a much simpler data safety form and review.

If that ever changes, the constraint to know is that digital subscriptions
inside a Play app **must** use Google Play Billing — you cannot take UPI or a
bank transfer for in-app features — and Google takes 15% of the first US$1M a
year. Nothing about the current code forecloses adding it later.

---

## Risks worth knowing before you spend money

**Play may reject the accessibility declaration.** Reviewers are strict with
apps that read other apps' screens. Being read-only and having a clear
disclosure helps a great deal, and similar driver tools do exist on Play, but
approval is not guaranteed. Do not build a business plan that assumes a green
light on the first submission.

**Rapido's and Uber's terms are a separate question from Play's.** Those
platforms can and do object to third-party tools that interact with their
driver apps, and enforcement lands on the *driver's* account, not on yours. An
advisory read-only tool is a far weaker case than an auto-accepter, but if you
are going to charge drivers money, you owe them a plain warning in the listing
about that risk, and you should read both platforms' current driver terms
yourself before launching.

**Parsing will break.** Rapido and Uber redesign their offer screens without
warning, and when they do, the app stops reading fares for every paying user at
once. A subscription business needs a way to notice that fast and ship a fix in
days — the "Last screen read" diagnostic exists for exactly this, but you will
want a support channel and a quick release process behind it.

**A free app still costs you.** No revenue does not mean no obligations: a
listed app needs updates when Play raises the target SDK each year, replies to
reviews, and a fix when a parser breaks. Free removes the billing work, not the
maintenance.

**Support load is real.** Battery-optimisation killing the service, OEM quirks
on Xiaomi and Vivo, accessibility being switched off by system updates — these
generate support requests out of proportion to the price of the app.

None of these is a reason not to do it. They are the things to have an answer
for before taking the first payment.
