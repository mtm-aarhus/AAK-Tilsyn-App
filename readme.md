# TilsynsApp

TilsynsApp is an internal Android application developed for Teknik og Miljø, Aarhus Kommune. It streamlines field inspections of permits (tilladelser), site notices (henstillinger), and ad-hoc reports (indmeldte tilsyn), as well as giving inspectors access to trigger route optimizations via RegelRytteren.

## Features

* Secure email-based login (token flow)
* View and filter pending inspections by type:
    * Tilladelser (Ny / Færdig)
    * Henstillinger
    * Indmeldte tilsyn (ad-hoc, manually created)
* Interactive map (OSM) with color-coded markers per type
* Create ad-hoc inspections directly from the map (tap to place) or via address search (Datafordeler & Adressevælger, limited to Aarhus Kommune)
* Create ad-hoc inspections from the Tilsyn list via a dedicated form with address autocomplete
* Inspect items with comments, photos (camera + gallery), and status updates
* Henstilling workflow: register m2, set end date, send to invoicing, or dismiss
* Permissions left off the weekly list in some months: parklets from October to March by rule (TilsynAPI), and any permission via "Skjul i bestemte måneder" in the inspection dialog. Permissions left off this month show in the history under "Vis skjulte tilsyn", where their months can be changed
* Indmeldt workflow: comment-based inspection, hide/show
* Full inspection history with expandable detail cards
* Pull-to-refresh on all list screens
* RegelRytteren: select inspectors, transport mode, and which types to include (tilladelser, henstillinger, indmeldte) for automated route generation
* Full dark mode support
* Back-gesture handling on sub-screens

## Architecture

* Single-activity Compose UI with screen-state navigation
* `TilsynViewModel` manages all data state (tasks, history, map selection)
* `ApiHelper` handles all network calls (TilsynAPI, Datafordeler, Adressevælger)
* Data source: Azure Cosmos DB via TilsynAPI (`https://mtmtilsyn.aarhuskommune.dk`, `BuildConfig.API_URL`)
* Address lookup & reverse geocoding: Datafordeler GraphQL (`DAR_Adressepunkt`, `DAR_Husnummer`) and Adressevælger (`adressevaelger.dk`), filtered to kommunekode 0751 (Aarhus)
* Map: osmdroid with OpenStreetMap tiles

## API Endpoints Used

| Endpoint | Method | Purpose |
|---|---|---|
| `/tilsyn/tasks` | GET | Fetch active inspections |
| `/tilsyn/history` | GET | Fetch completed/hidden inspections |
| `/tilsyn/inspect` | POST | Register an inspection |
| `/tilsyn/indmeldt` | POST | Create ad-hoc inspection (server assigns case number) |
| `/tilsyn/upload-image` | POST | Upload inspection photos |
| `/tilsyn/register-token` | POST | Register the phone for push notifications |
| `/tilsyn/regelrytteren` | POST | Queue RegelRytteren route generation (TilsynAPI creates the queue item) |
| `/tilsyn/hidden-months` | POST | Choose the months a permission is left off the weekly list |
| `/tilsyn/auth/request-link` | POST | Request login link |
| `/tilsyn/auth/check` | POST | Poll login authorization |
| `/tilsyn/app/version` | GET | App version check |

The key the app gets at login only opens TilsynAPI. A session saved by an app
version that used another server is wiped at start-up, so the user logs in once
more (`SecurePrefs.isSessionFor`).

## Privacy & Security

* All network communication uses HTTPS
* API keys are stored in SharedPreferences (EncryptedSharedPreferences) and never hardcoded
* Location access is optional, used for map centering and navigation
* Address search is limited to Aarhus Kommune (kommunekode 0751)
* Data is not shared with third parties
* Data is not sensitive or personally attributable

## Build & Publish

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. Build signed release APK: Build > Generate Signed Bundle / APK
3. Upload to Google Play Console (internal testing track)
4. After rollout, update `min_version` in the API version endpoint to enforce the upgrade
   (TilsynAPI's `app/sections/tilsyn.py`; for the release that moved to TilsynAPI, the
   old app's `/api/tilsynapp/version` too - that is what old phones ask)

## Maintainers

* Jakob Terkelsen (Digital udvikling, Teknik og Miljø, Aarhus Kommune)
