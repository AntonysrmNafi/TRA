# Tracker Remover (Android)

Standalone Android version of the Tracker Remover / Link Cleaner project.
Same core idea as the Telegram bot, no server: everything runs on your
own device.

Package name: `com.blockveil.tracker.remover`

## What it does

- Paste a link, or **Share** a link into the app from any other app
  (Telegram, Chrome, WhatsApp, etc.) using the system share sheet.
- Follows shortener redirects (bit.ly, t.co, amzn.to, youtu.be, ...) so
  you see the real destination.
- Strips known tracking parameters (utm_*, fbclid, gclid, igshid, and
  platform-specific ones for YouTube/Instagram/Facebook/Twitter/Amazon/
  TikTok/LinkedIn).
- Optional safety checks, each toggled independently in **Settings**:
  - Google Safe Browsing (needs your own free API key)
  - VirusTotal (needs your own free API key)
  - Domain age via RDAP (no key needed, flags domains under 30 days old)
- A plain-language explanation of what the removed trackers would have
  revealed (grouped as ad-click IDs, campaign tags, cross-site
  analytics, or share-tracing codes), same wording as the bot's
  "Optional Description" line.
- **Instant share-and-reshare**: pick "Tracker Remover" from any app's
  Share sheet (e.g. sharing a YouTube video) and it cleans the link
  and immediately reopens the Share sheet with the cleaned link, so
  you pick the final recipient right after — no extra taps in between.
- Local history of cleaned links (Room database on-device, nothing
  leaves the phone). Can be turned off or cleared in Settings.

## Project structure

```
TrackerRemoverApp/
├── .github/workflows/build.yml        GitHub Actions: builds a debug + release APK on every push
├── app/
│   ├── build.gradle                   Module config, dependencies
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/blockveil/tracker/remover/
│       │   ├── TrackerRemoverApp.kt   Application class (DB + settings singletons)
│       │   ├── cleaner/
│       │   │   ├── TrackingRules.kt   All tracking-param / shortener domain lists
│       │   │   └── LinkCleaner.kt     Offline URL parsing + cleaning
│       │   ├── network/
│       │   │   ├── HttpClientProvider.kt
│       │   │   └── UrlResolver.kt     Follows shortener redirects, one hop at a time
│       │   ├── safety/
│       │   │   ├── SafetyModels.kt
│       │   │   ├── SafeBrowsingClient.kt
│       │   │   ├── VirusTotalClient.kt
│       │   │   ├── DomainAgeClient.kt
│       │   │   └── SafetyChecker.kt   Combines whichever checks are enabled
│       │   ├── data/
│       │   │   ├── CleanedLinkEntity.kt
│       │   │   ├── CleanedLinkDao.kt
│       │   │   ├── AppDatabase.kt     Room database (history)
│       │   │   └── SettingsRepository.kt  SharedPreferences wrapper
│       │   ├── util/
│       │   │   └── LinkProcessor.kt   Ties cleaner + resolver + safety + storage together
│       │   └── ui/
│       │       ├── MainActivity.kt
│       │       ├── SettingsActivity.kt
│       │       ├── HistoryActivity.kt
│       │       └── HistoryAdapter.kt
│       └── res/                       Layouts, strings, colors, launcher icon
├── build.gradle                       Root Gradle config
├── settings.gradle
└── gradle.properties
```

## Building it

### Option A: GitHub Actions (what you asked for)

1. Push this whole folder as a new GitHub repo.
2. The workflow at `.github/workflows/build.yml` runs automatically on
   every push to `main` (and can be triggered manually from the
   Actions tab too).
3. Open the finished run under the **Actions** tab, scroll to
   **Artifacts**, and download:
   - `tracker-remover-debug` — installable straight away (debug-signed).
   - `tracker-remover-release-unsigned` — release build, but
     **unsigned**, so Android will refuse to install it until it's
     signed. See "Signing a release build" below when you're ready
     for that.
4. No `gradlew` file is committed to the repo; the workflow installs
   Gradle itself and generates the wrapper on the runner, so you don't
   need Android Studio at all for this path.

### Option B: Android Studio (if you ever want it)

Open the `TrackerRemoverApp/` folder in Android Studio, let it sync,
then Run. Studio will generate its own `gradlew` the first time you
sync, same as the CI does.

## Settings → API keys

Both are free tiers, no card required:

- **Safe Browsing**: Google Cloud Console → enable "Safe Browsing
  API" → create an API key.
- **VirusTotal**: virustotal.com → sign up → your profile → API key.

Keys are stored in plain `SharedPreferences` on-device (not encrypted
at rest). That's fine for a personal-use API key with a free-tier
quota; if you ever want it hardened, swap `SettingsRepository`'s
backing store for `androidx.security:security-crypto`'s
`EncryptedSharedPreferences` — same get/set shape, just a different
constructor.

## What was intentionally left out of this first version

The bot's Python `link_cleaner.py` also did AMP-page unwrapping and
canonical-link extraction (fetching a page's HTML to find its real
`<link rel="canonical">`). That's left out here to keep the first
version simple and mobile-friendly (no background HTML parsing on top
of everything else) — the redirect-following and tracker-stripping,
which cover the vast majority of real-world links, are both in. Happy
to add AMP/canonical support in a follow-up if you want it.

## Signing a release build (for later, not needed yet)

When you're ready to distribute a signed release APK:
1. Generate a keystore: `keytool -genkey -v -keystore release.keystore -alias tracker-remover -keyalg RSA -keysize 2048 -validity 10000`
2. Add a `signingConfigs { release { ... } }` block in `app/build.gradle`
   pointing at it, reading the keystore path/password from environment
   variables (never commit the keystore or its password to the repo).
3. In the GitHub Actions workflow, store the keystore as a base64
   repo secret, decode it into a file in a step before the build, and
   pass the passwords in as `env:` from repo secrets.

Ask any time and this can be wired up fully.
