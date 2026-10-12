# Roadmap – done

Shipped features, newest first.

- 1.26.0 – New local diagnostics log (Settings → Diagnostics log): records detailed entries for GPS track recording when enabled, crashes always; view and share from Settings. Entries are redacted (coordinates, amounts, plates, real file paths) before they could ever leave the device
- 1.25.1 – No user-visible change; added debug logging to the GPS track recording service to investigate reports of missing points
- 1.25.0 – Tours and repairs can have an exact start/end time, not just a date, with a "Now" quick-fill setting both at once; home address history with a validity period per address, managed in Settings, so a new tour's "starts at home" station uses whichever address was valid back then; a quick-save checkmark leaves an autosaving form immediately without the discard/keep dialog; the GPS-track section moved right after the tour overview and stays expanded; the recording notification shows a live duration; the tour map draws individual track points with time labels, decluttered by zoom level, not just a line
- 1.24.1 – "Open in maps" on a tour now uses its first located station or track point instead of an empty text search; countries are detected from the GPS track too when no station has coordinates; the map button moved from the stations heading into the top app bar so it's easier to find
- 1.24.0 – All forms (tours, repairs, stations, diary, vehicles, documents) now autosave as you type; pinned checklist templates with a Tours-tab quick start and a packing list template; per-axle vehicle tyre size and seller/buyer names on purchase/sale; workshop name and GPS coordinate on repairs; time, odometer and weather on diary entries; GPS track recording drops its motion-triggered relocation churn and writes points immediately instead of buffering; the recording notification shows live points/distance/duration; the tour map now also opens for a track-only tour without a located station
- 1.23.2 – Fixed saving a new tour with "Starts at home" enabled when no vehicle exists yet (the home station was created with an invalid vehicle reference, failing the save)

- 1.23.1 – A scheduled GPS-track auto-resume now survives a device restart; the tutorial shows which vehicle a new tour is assigned to even with just one vehicle; cleared up a misdiagnosed "DatePicker doesn't open during a tutorial" report (it was a test bug, not an app bug)

- 1.23.0 – The "create your first tour" tutorial now walks through every field step by step (14 real steps instead of 3): vehicle selection with inline vehicle creation if needed, period, GPS-track and home-location switches with location permission requested inline, other costs, slug, saving, adding a first station, and the finished track section with map

- 1.22.0 – Pausing a GPS track recording can now schedule an automatic resume after a chosen duration (30 min / 1 h / 3 h / 8 h / 1 day), no new permission needed

- 1.21.0 – Tutorial includes an auto-generated, clearly marked sample tour (own demo vehicle, 3 stations, simulated GPS track) to explore GPS tracking and the map before creating a real tour; sample data is swept automatically and never appears in backups/exports

- 1.20.1 – Device-testing fixes: sold vehicles no longer pre-selected for new tours, app name in the track notification, the "create your first tour" tutorial no longer gets stuck, tutorials moved up into Settings, "Today" button on the tour start date, automatic GPS recovery after a detected reboot, fixed a rare ghost GPS recording pointing at a deleted tour

- 1.20.0 – Guided in-app tours: a walkthrough of the main screens and a step-by-step "create your first tour" guide, both repeatable from About CamperLog

- 1.19.0 – Tour list and detail screen reworked (tinted header card, grouped form, configurable list metrics), rough distance estimate when a tour has no GPS track, tour-list search also matches the name, fixed travel-day count for ongoing tours in the list
- 1.18.0 – Home location auto-fills start/end stations at tour start, clearer data/backup icon, vehicle dimensions in cm with PS/kW and optional displacement & transmission, odometer at sale, GPS track recording detects a reboot instead of silently resuming and restarts itself automatically afterward (new permission: RECEIVE_BOOT_COMPLETED)
- 1.17.1 – Travel days in the tour list, donate button moved into Settings, GPS track recording redesigned: pause/resume, a persistent status bar while recording, a switch directly on the tour (and at tour creation), clearer feedback when a recording fails to start
- 1.17.0 – Optional tour destination with name and export slug, configurable accent color, camper-icon station rating (incl. food), odometer and temperature with one-tap weather lookup on stations, heart-tap favorite, LTE smileys, photos at station creation, link field for overnight spots, per-vehicle required energy types filtering tank and fuel-station fields
- 1.16.0 – Ongoing tours with automatic days, nights and GPS distance; finish action in tour cards and detail; clearer tour/stops/track introduction
- 1.15.2 – Fixes from a security/quality review: backups with long attachment names import again, device transfer includes attachment files, capped network responses, lost attachments reported on import, verified Gradle dependencies and SHA-pinned CI actions
- 1.15.1 – Opt-in GPS track recording (foreground service, no background permission), track on the tour map, track length, GPX track export, Gradle 9.8.1 (tag v1.15.0 was never released: its CI failed on the lint check for the newer Gradle)
- 1.14.0 – Place search via Nominatim (behind the Weather & map switch), vignette expiry warning on tours
- 1.13.0 – Daily diary per tour day, checklists with templates (per tour or vehicle), full-text search from every tab
- 1.12.0 – Countries per tour detected offline (Natural Earth, coast/border tolerance), countries in overview, richer tour share text, tour export ZIP (HTML, Markdown, GPX, CSV, photos)
- 1.11.1 – Photos (with location) on stops, repairs and logbook; document wallet with expiry reminders; ZIP backups with readable folders; older backups import with missing fields
- 1.10.0 – Costs on stops by category, electricity billing variants, toll/vignette stops, ferry booking reference, cost breakdowns
- 1.9.0 – Optional notifications (daily check, one per due item), backup reminder, backup folder with auto-backup; unused WorkManager permissions removed
- 1.8.0 – Own OSM map per tour/stops (disk cache, attribution, accessible list)
- 1.7.0 – Optional weather on stops (Open-Meteo, rounded coordinates)
- 1.6.0 – Leak test date with reminder
- 1.5.0 – Optional one-shot location, "Where am I?" in the breakdown card
- 1.4.0 – Stops: types, timeline, Stops tab, geo: links, logbook link, gas bottle tile
- 1.3.0 – Breakdown & accident card, measured empty weight and payload
- 1.2.x – Several vehicles, logbook, maintenance/repairs, in-app reminders, backup v2, About, intro tour, Keep Android Open, new icon, new signing key
- Research round (2026-10-06) – user needs from camper forums and comparable apps feed the roadmap
