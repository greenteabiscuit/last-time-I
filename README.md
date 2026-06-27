# Last Time I...

This project is a standalone Android widget app for remembering the last time something happened.

The current MVP is built around a practical `RemoteViews` pattern:

- show 10 tracked items in a large home-screen widget
- show the number of days since each item was last refreshed
- optionally flag an item as overdue after its expected interval in days has passed
- let the widget reset a single item to today with one tap
- use the companion app for add, edit, and delete flows

## Product Shape

The seed list starts with these examples:

- Cut my nails
- Mopped the floor
- Changed my toothbrush
- Stayed at home the whole day
- Had my nose congested
- Had painful acne somewhere on my body

The app stores a timestamp and optional interval in days for each item, then turns that into a day count on both the widget and the companion screen. Items with an interval are highlighted as overdue when the days-since count is greater than the interval.

## What Is Here

- `app/` - the Android app and widget code
- `docs/LAST_TIME_I_WIDGET_PLAN.md` - concrete implementation plan for this product
- `docs/WORKLOG.md` - checked-in project memory for decisions and milestones
- `scripts/build-debug.sh` - builds the debug APK
- `scripts/install-debug.sh` - installs the debug APK onto a connected device
- `scripts/list-devices.sh` - shows devices visible to `adb`
- `scripts/logcat-widget.sh` - tails or saves filtered logs for this app
- `scripts/open-android-studio.sh` - opens this project in Android Studio

## Quick Start

1. Open the project in Android Studio once so it can sync Gradle:

   ```bash
   ./scripts/open-android-studio.sh
   ```

2. Confirm your phone is visible to `adb`:

   ```bash
   ./scripts/list-devices.sh
   ```

3. Build and install:

   ```bash
   ./scripts/build-debug.sh
   ./scripts/install-debug.sh
   ```

4. Add the widget from the launcher:

   - long-press the home screen
   - open the widget picker
   - find `Last Time I...`
   - drag the large widget onto the home screen

## Logging

Keep both of these log paths in use:

- `docs/WORKLOG.md` for short checked-in milestones
- `logs/` for saved raw `adb logcat` captures during testing

Capture filtered logs like this:

```bash
./scripts/logcat-widget.sh
./scripts/logcat-widget.sh --save
```

## Useful Commands

```bash
./scripts/build-debug.sh
./scripts/install-debug.sh
./scripts/list-devices.sh
./scripts/logcat-widget.sh
./scripts/logcat-widget.sh --save
./gradlew assembleDebug
```

## Notes

- The widget intentionally uses classic `RemoteViews`, not Glance/Compose, to keep the interaction model small and stable.
- `SharedPreferences` is enough for the first version because this app is tracking one small ordered list of personal reminders.
- Item JSON includes `intervalDays`; missing or `0` means no overdue highlighting.
- If the list grows beyond 10 items, the widget shows the first 10 and a `+N more in app` indicator.
