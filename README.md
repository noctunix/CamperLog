<p align="center"><img src="logo/icon.svg" width="96" alt="CamperLog app icon"></p>

# CamperLog

CamperLog is an Android app for recording camper trips, their costs, and the currencies they were paid in, alongside a vehicle data sheet, maintenance, and a logbook. It is built with Kotlin, Jetpack Compose, and Room. The app uses English by default and German on devices configured for German. CSV export column headers and values follow the app language; German keeps the earlier format for compatibility with existing exports.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_tours.png" width="200" alt="Tour list">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_detail.png" width="200" alt="Tour with its stops">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7_stops.png" width="200" alt="Stops">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5_vehicle.png" width="200" alt="Vehicle data sheet">
</p>

## Features

CamperLog is organized into four tabs: **Tours**, **Stops**, **Logbook**, and **Vehicle**.

- **Tour log:** destination, dates, tour type (day trip, weekend, vacation), vehicle, distance, overnight stays, and costs; searchable and filterable by year. Each tour shows its stops as a timeline, a diary with one entry per travel day, and the countries it passed through, detected offline from the stops' coordinates (bundled Natural Earth borders); countries can be added or removed by hand. Sharing a tour sends a text summary with costs per category, countries, and the stops you would return to.
- **Stops:** overnight places, water & waste, fuel & charging, tolls and vignettes, sightseeing, food, ferries and more, with or without a tour. Overnight stops keep the pitch details (site kind, pitch assigned, electricity, mobile reception, slope, levelling blocks) and a "would return" flag. Each stop can carry costs by category (pitch, electricity, toll, ferry, fuel, …); electricity is entered the way the site bills it (flat per night or stay, metered, base fee plus metered, coin meter) and the app works out kWh and cost. Stop costs add up in tour totals and the overview. Places are described in text or by pasting coordinates and map links (OsmAnd, Organic Maps, OpenStreetMap, Google Maps), parsed offline; `geo:` links from map apps open a new stop. With **Location** turned on in Settings, a tap on "Use current location" fills in coordinates on the spot. With **Weather & map** turned on, a tap on "Get weather" fetches a one-time snapshot from Open-Meteo for today's stops, and a tour's or the Stops tab's map shows its located stops on an OpenStreetMap background with dashed connections in order. The Stops tab lists all stops with search and filters and exports them as CSV.
- **Multiple vehicles:** a data sheet per vehicle (general data, purchase & sale, insurance & tax, dimensions & weight, engine, tyres, tanks, energy, notes), a "Breakdown & accident" card with tap-to-dial numbers for breakdown assistance, travel protection and the insurer, "Where am I?" for your current coordinates (with Location on), and the remaining payload from a measured empty weight; a current vehicle is used for new tours and logbook entries, a switcher appears once you have more than one, and an "All vehicles" view covers tours and the overview.
- **Logbook:** one tap for "Today" records the current vehicle's cassette and grey water emptying, gas bottle swap or diesel/gas heater run; other dates can be logged too, with history and undo. Ticking cassette, grey water or gas on a stop records the logbook entry for you, without duplicates.
- **Photos and documents:** photos on stops, repairs and logbook entries (stored on the device with their location; gallery photos come without location, so a stop's location can be applied), and a document wallet per vehicle (registration, insurance, warranties) with expiry reminders.
- **Maintenance and repairs:** next inspection (MOT/TÜV), gas check and leak test dates, last oil change with its odometer reading, and a repair log with date, description, mileage, and cost.
- **Reminders:** cards on the Vehicle tab and a badge flag upcoming maintenance; lead time and the oil-change interval are configurable in Settings. Notifications are optional (off by default, Settings → Reminders): turning them on runs a daily background check for due maintenance and a backup reminder, with no exact alarms.
- **Checklists:** your own templates (suggestions for departure, arrival, winterizing and spring check can be added with one tap), copied into a checklist per tour or per vehicle and ticked off there; later template edits leave existing checklists unchanged.
- **Search:** one search across tours, stops, diary, logbook, repairs, checklists, documents and vehicles, from every tab; ignores case and accents ("muritz" finds "Müritz").
- **Multiple currencies:** costs keep their original currency and precision (e.g. `1450.00 NOK`, `3500 ISK`); amounts are entered in your device's number format.
- **Overview:** tours, distance, travel days, overnight stays, countries, and costs per currency, in total and per year, for one vehicle or all of them; optionally converted into a main currency using exchange rates you maintain yourself.
- **Export:** tours and stops as CSV for spreadsheet apps, including the vehicle, protected against formula injection. A single tour exports as a ZIP that works without the app: `Tour.html` (self-contained, prints to PDF from any browser), `Tour.md`, `Tour.gpx` (stops as waypoints for OsmAnd or Organic Maps), `Stops.csv`, and `Photos/` with the original files.
- **Backup and restore:** a ZIP file with all photos and documents in readable folders plus a JSON file with all tours, stops, vehicles, repairs, logbook entries, exchange rates, diary entries, checklists and templates, and the main currency; the import shows a preview before changing anything, and older backups stay importable. A backup folder (chosen once via the system file picker) can hold one-tap or, if enabled, automatic backups, with an optional reminder (Data screen) if you have not backed up in a while.
- **About:** feedback and support links, source code and licenses, and a replayable introduction tour.
- **Light and dark theme**, following the system by default.

## Getting started

1. Download the latest `CamperLog-*.apk` from [Releases](https://github.com/noctunix/CamperLog/releases) and optionally compare it with the published SHA-256 checksum. Release APKs are built for **arm64** devices running **Android 8.0 or newer**.
2. Open the APK on your phone and allow installing apps from this source when Android asks. Later releases install as updates and keep your data.
3. In the **Vehicle** tab, name your camper (or add more vehicles) and fill in its data sheet and maintenance dates if you like; the **Logbook** tab records cassette, grey water, and heater use with one tap. Tap **New tour** in **Tours** to log your first trip, and set a main currency with exchange rates under **Settings → Manage exchange rates** if you want converted totals.

**Updating from an earlier install:** 1.2.0 is the first release published from this repository and is signed with a new key, so Android cannot install it as an update over a version installed from elsewhere. If that applies to you, back up your data first (**Data → Backup**), uninstall the old app, install 1.2.0, then restore the backup (**Data → Restore**).

**Privacy:** CamperLog works completely offline and needs no account. Three optional features are off by default: **Location** reads your position once when you tap "Use current location" or "Where am I?" (location permission, never in the background), **Weather & map** fetches a one-time weather snapshot from Open-Meteo for today's stops and loads map tiles from OpenStreetMap for the area you are viewing (internet permission, used only when turned on), and **Notifications** (Settings → Reminders) run a daily background check for due maintenance and the backup reminder entirely on your device; turning them on asks for the notification permission; for the check the app also holds two permissions granted at installation without a prompt (starting again after a restart, briefly keeping the device awake), used only while notifications are on. Both Open-Meteo and OpenStreetMap see your IP address when used, and a weather request also sends the stop's coordinates, rounded to about 1 km; nothing from the background check is ever sent anywhere. All data stays on your device until you export or share it yourself. Uninstalling the app deletes its data, so save a backup regularly (**Data → Backup**), for example before switching phones.

## Development

Requirements: JDK 17, Android SDK (platform 37), and an Android device or emulator to run the app. The Gradle wrapper is included.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Pushes to `master` and pull requests run these checks in [GitHub Actions](.github/workflows/ci.yml). Debug APKs are not published as releases.

`scripts/update-screenshots.sh` regenerates the README and F-Droid screenshots in `fastlane/metadata/android/en-US/images/phoneScreenshots/` from sample data (Robolectric, no device needed). Run it after visible UI changes; the screenshot test is skipped in normal test runs.

## Versions and releases

The version is declared in `app/build.gradle.kts` as `appVersion` plus the matching `appVersionCode` (`1.2.3` → `10203`); the build fails if they disagree. Release regularly, e.g. after each completed set of user-visible changes, so the published APK stays current. For each release, increment both values (SemVer; `MINOR` and `PATCH` must each be below 100), add `fastlane/metadata/android/en-US/changelogs/<appVersionCode>.txt` and its `de-DE` counterpart (used by F-Droid), commit the changes, and tag the **commit on `master`** with `v<version>`. Push the branch before the tag:

```sh
git push github master
git tag -a v1.0.4 -m 'CamperLog 1.0.4' # Example: after bumping to the next unused version
git push github v1.0.4
```

The [release workflow](.github/workflows/release.yml) verifies the version, tag, and branch, runs tests and lint, builds a signed arm64 APK, verifies its signature, and publishes it with a SHA-256 checksum. A release fails if signing secrets are missing or invalid. Treat published tags as immutable: increment the version for fixes rather than moving a tag.

### Signed APK (arm64)

For local builds, follow `keystore.properties.example` and **keep a permanent, secure backup of the keystore**. `scripts/build-release-arm64.sh --no-install` builds a signed APK without installing it. Replacing the signing key prevents updating existing installations while retaining their data.

To publish an installable APK, configure both repository secrets under **Settings → Secrets and variables → Actions**:

- `RELEASE_KEYSTORE_BASE64`: Base64-encoded content of the permanently backed-up release keystore.
- `RELEASE_PROPERTIES_BASE64`: Base64-encoded `keystore.properties` with `storeFile=release.jks`, `storePassword`, `keyAlias`, and `keyPassword` for that keystore.

On Linux, use `base64 -w0 camperlog-release.jks` and `base64 -w0 keystore.properties`, then enter the respective outputs directly as secrets. The properties file encoded for GitHub **must** contain `storeFile=release.jks`. Never commit the keystore or passwords.

## License

Apache License 2.0; see [LICENSE](LICENSE).
