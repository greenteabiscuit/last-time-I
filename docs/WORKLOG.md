# Worklog

This file is the checked-in project memory.

Use it for short, factual entries so we can reconstruct why the project changed over time without digging through chat history.

## How To Use This

- Add a new dated entry when a meaningful milestone happens.
- Keep entries short.
- Record decisions, device test results, bugs, and next steps.
- Do not paste huge raw `logcat` dumps here. Save those under `logs/` locally and summarize the important part here.

## Entry Template

```text
## YYYY-MM-DD HH:MM
- What changed:
- Verified on:
- Decisions:
- Next:
```

## 2026-04-20 02:15
- What changed: Forked the checklist widget scaffold into `widget-20260420-last-time-i` so the new app could reuse the same Gradle wrapper, install scripts, logging workflow, and classic `RemoteViews` widget setup.
- Verified on: Local file-level scaffold copy on macOS.
- Decisions: Keep this as a separate standalone app instead of layering a second product idea into the checklist repo.
- Next: Replace the checklist model with timestamp-based tracking and build a 6-row widget with per-item refresh actions.

## 2026-04-20 02:30
- What changed: Implemented the `Last time I...` MVP with local timestamp storage, a 6-row widget that shows days-since plus per-item `Today` buttons, and a companion app CRUD screen for tracked items.
- Verified on: Pending first build/install for this new project.
- Decisions: Persist `lastRefreshedAtMillis` in `SharedPreferences`, keep list order simple, and show the first 6 items in the widget with `+N more in app` when the list grows.
- Next: Build, install, and verify per-item widget refresh plus add/edit/delete flows on the connected Pixel.

## 2026-04-20 02:40
- What changed: Built and installed the new `Last Time I...` app on the connected Pixel, launched it, captured filtered logs to `logs/logcat-20260420-012236.log`, and confirmed the seeded 6-item timestamp storage under `shared_prefs/last_time_i_storage.xml`.
- Verified on: Pixel 7 Pro over USB; `./scripts/build-debug.sh` and `./scripts/install-debug.sh` succeeded, saved logcat showed `MainActivity created` plus `Saved 6 last-time item(s): seed default last-time items` and `Opening add-item flow from widget`, and `run-as` showed the expected seeded titles with `lastRefreshedAtMillis` values.
- Decisions: Treat the deep-link launch as a good smoke test for widget-to-app entry points, while leaving real launcher-widget row-button interaction as a manual follow-up because shell-triggered broadcasts do not fully reproduce widget-host behavior.
- Next: Add the widget to the Pixel home screen and manually verify the per-row `Today` buttons, row-to-edit launch, and `+N more in app` behavior.

## 2026-04-20 01:38
- What changed: Added refresh-history persistence by extending each item JSON record with a `refreshHistoryMillis` array, migrated older single-timestamp records on read, and added an app-side `History` dialog that shows a compact 8-week calendar plus a recent-events list for each item.
- Verified on: Local `./scripts/build-debug.sh`; Pixel 7 Pro over USB via `./scripts/install-debug.sh`, `adb shell am start -W -n com.example.widget20260420lasttimei/.MainActivity`, `run-as com.example.widget20260420lasttimei cat shared_prefs/last_time_i_storage.xml`, and filtered logs saved to `logs/logcat-20260420-013519.log`.
- Decisions: Chose a recent-weeks calendar over a full month grid, heatmap, or pure timeline because it stays visually calendar-like while fitting the current single-screen app and keeping the raw local JSON easy to inspect; paired it with a recent-events list so exact timestamps remain visible for debugging.
- Next: Manually tap the new `History` button on an unlocked device to confirm the dialog layout feels good in-hand, then decide whether the next iteration should add month paging or a denser heatmap variant.

## 2026-04-20 01:45
- What changed: Switched refresh history from timestamp-level events to day-level `refreshHistoryDates` values like `2026-04-20`, updated parsing to preserve empty history when needed, and made the history dialog editable by tapping calendar days or using a `Pick Date` control for backfilling older days.
- Verified on: Local `./scripts/build-debug.sh`; Pixel 7 Pro over USB via `./scripts/install-debug.sh`, `adb shell am start -W -n com.example.widget20260420lasttimei/.MainActivity`, `run-as com.example.widget20260420lasttimei cat shared_prefs/last_time_i_storage.xml`, and filtered logs saved to `logs/logcat-20260420-014427.log`.
- Decisions: Store history as ISO local-date strings instead of millis because this app only tracks by day and the resulting JSON is easier to inspect during debugging; make calendar cells toggle days directly and keep a picker for dates outside the visible 8-week window.
- Next: Manually test day toggling and `Pick Date` on an unlocked device, especially the flow where a default seeded `today` entry is cleared and replaced with a retroactive day like last Sunday.

## 2026-06-19 14:33
- What changed: Added optional per-item expected intervals and overdue highlighting in the companion app rows and home-screen widget.
- Verified on: Local `./scripts/build-debug.sh` succeeded; Pixel 7 Pro over USB via `./scripts/install-debug.sh` succeeded; `adb shell am start -W -n com.example.widget20260420lasttimei/.MainActivity` returned `Status: ok`. UI inspection was blocked by the device keyguard.
- Decisions: Store the new interval as `intervalDays` in each item JSON record; missing or `0` means no interval and never overdue. An item is overdue only when `daysSince > intervalDays`.
- Next: Unlock the device and manually confirm overdue and no-interval rows render correctly in the app and on an active widget.

## 2026-09-13 14:56 JST
- What changed: Added per-item 7-day widget snooze, an early unsnooze control, and a visible expiry date. Persist `widgetSnoozedUntilMillis` without changing history; filter before selecting both widget sections and counts. Enabled 30-minute Android widget updates for automatic expiry.
- Verified on: Five Robolectric tests covering old records, persistence, exact expiry boundaries, both widget sections, multiple widget instances, undo, deleted items, and an unsnooze click after expiry. Debug APK built. Inspected native Android renders of available, snoozed, and all-snoozed states. No phone data changed or APK installed.
- Decisions: Snooze lasts exactly 7 × 24 hours; Android can delay the next refresh. Normal widget ordering and tag exclusions remain. Lint still reports the same 103 errors and 25 warnings as the untouched checkout; no new diagnostics.
- Next: Install the built APK on the Pixel when requested and verify the real launcher widget.

## 2026-09-13 — Pixel installation
- What changed: Installed the snooze build on the Pixel 7 Pro. A signing-key mismatch required a user-approved uninstall/reinstall; backed up the original APK and refreshed the data backup immediately before replacement.
- Verified on: Restored preferences matched the backup byte-for-byte. After launching successfully, all 22 item records still matched exactly. The device UI exposed an enabled `SNOOZE WIDGET FOR 7 DAYS` button.
- Next: Re-add the home-screen widget removed by uninstalling the previous app.

## 2026-09-13 — Direct widget snooze and eight rows
- What changed: Added a `Snooze 7d` action beside `Today` on each widget row, expanded both Latest and Oldest to four distinct items, and reduced fonts and spacing while retaining 48dp-tall action targets. Snooze refreshes every widget; item-specific pending intents prevent stale clicks from snoozing a replacement row.
- Verified on: Six Robolectric tests pass, including all eight snooze actions, both widget instances, history preservation, expiry, stale clicks, and Today. Inspected native renders at 363dp and 280dp widths and after snoozing. Debug build and normal Pixel update succeeded; all 22 records were unchanged and launcher logs confirmed existing widget 23 reloaded. Live launcher layout was not visually verified.
- Limitations: Lint retains 103 pre-existing API errors and reports a small-font advisory for the intentionally compact 10sp timestamp.

## 2026-11-22 — Native iPhone version

- Added `ios/` with a SwiftUI app, shared JSON model/storage, and an interactive WidgetKit extension (small, medium, large).
- Ported history editing back to 2010, tags/search/sort, overdue boundaries, soft delete/restore, and exact seven-day widget snoozing. Health-tagged items remain excluded from widgets.
- Generated and integrated a 1024px forest-green clock icon with ivory hands and an orange accent.
- Kept the original Android implementation. iOS starts with the original example titles unrecorded; no Android device data migration is performed.
- Installed and launched on the connected iPhone 15 Plus. In response to on-device feedback, replaced the large introduction/cards with an inline title and compact rows, removed filler copy, and moved Today beside each title.
- Added five model/storage tests and an end-to-end UI flow covering add, Today, snooze/unsnooze, delete/restore, relaunch persistence, and history navigation.

- Added a compact horizontal calendar beneath each item at user request. Dates scroll back to 2010 and toggle directly; removed “Not yet” from unrecorded list rows.

## 2026-11-22 — Connected-device transfer support

- Added a Debug-only in-app export/import bridge for devices restricting direct App Group filesystem access. Import validates IDs/titles/history/intervals, backs up current records before writing, saves atomically through shared storage, refreshes widgets, and exports a readback.
- Verified export and import on the connected iPhone, including exact field-by-field readback equality. Personal data and backups are kept outside the repository.

## 2026-11-22 — Delayed row movement

- Today and inline calendar taps persist immediately and update the date indicator in place. Keep the current row order for 0.5 seconds after the last tap, then animate into the selected sort order over 0.45 seconds.
- Rapid taps restart the delay; search, sort, trash navigation, and leaving the screen cancel pending movement. Respect Reduce Motion by applying the delayed reorder without animation.

## 2026-11-22 — Move deleted items into More options

- Removed the trash shortcut from the main navigation bar. Deleted items now opens from the ellipsis menu beside Sort, with a Back button on the deleted-items screen.
- Updated the UI flow to open the new menu before restoring a deleted item.

## 2026-11-28 — Remove widget title

- Removed the visible Last time I… title row from all iPhone widget sizes to leave more room for item names and days-since labels. Kept Add inline with the Latest section heading, or beside Today in the small widget.
