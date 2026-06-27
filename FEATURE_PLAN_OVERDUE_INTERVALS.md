# Feature Plan: Per-item interval + overdue highlighting

## Summary
Let each "Last Time I..." item have an optional **expected interval** in days
(e.g. "Cut my nails" every 14 days). When the number of days since the last
refresh exceeds the interval, the item is visually flagged as **overdue** on both
the widget and the companion app.

## Why this is a good addition
- Today the app shows "days since" for each item but gives no signal about which
  items actually need attention.
- An interval + overdue highlight turns the list into an actionable "what's due"
  view, which is the whole point of remembering the last time something happened.

## User-facing behavior
- In the companion app, the add/edit flow gains an optional **interval (days)**
  field per item. Empty/0 means "no interval" (never flagged), preserving current
  behavior for existing items.
- An item is **overdue** when `daysSince > intervalDays` (and `intervalDays > 0`).
- Overdue items are highlighted:
  - Widget: a clear marker (e.g. a warning glyph and/or red day-count color).
  - App: matching highlight on the row.
- Existing stored items (no interval) load unchanged and are never flagged.

## Data model
- Add an optional `intervalDays` (int) field to `LastTimeItem`:
  - New JSON key, e.g. `intervalDays`.
  - `fromJson` defaults to `0` when the key is absent (back-compat).
  - `toJson` writes it.
  - Add getter/setter; keep the class's existing refresh-history logic intact.
- Compute "days since" using the existing `LastTimeFormatter` helpers; add an
  `isOverdue()`/`getDaysSince()` helper if not already present.

## Implementation steps
1. Read `LastTimeItem.java`, `LastTimeStorage.java`, `LastTimeFormatter.java`,
   `LastTimeIWidgetProvider.java`, and `MainActivity.java`.
2. Extend `LastTimeItem` with `intervalDays` (JSON back-compat as above).
3. Add the interval input to the app's add/edit UI and persist it via
   `LastTimeStorage`.
4. Add overdue highlighting in the app row rendering.
5. Add overdue highlighting in the widget `RemoteViews` (color and/or a glyph).
   Keep within RemoteViews-safe view operations.
6. Update `docs/WORKLOG.md` with the decision and the new JSON key.

## Files likely to touch
- `app/src/main/java/com/example/widget20260420lasttimei/LastTimeItem.java`
- `app/src/main/java/com/example/widget20260420lasttimei/LastTimeStorage.java`
- `app/src/main/java/com/example/widget20260420lasttimei/LastTimeIWidgetProvider.java`
- `app/src/main/java/com/example/widget20260420lasttimei/MainActivity.java`
- `app/src/main/res/layout/*` (widget + app rows), `values/strings.xml`,
  `values/colors.xml`
- `docs/WORKLOG.md`

## Out of scope
- No notifications/alarms (highlighting only).
- No automatic reordering of items by urgency (keep existing order).

## Verification
1. `./scripts/build-debug.sh`
2. `./scripts/install-debug.sh` (Pixel 7 Pro is connected via adb)
3. Manually: set a short interval (e.g. 0–1 day) on an item, confirm it shows as
   overdue in app + widget; confirm items without an interval are never flagged;
   confirm existing items still load.
