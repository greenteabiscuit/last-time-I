# Last Time I… for iPhone

Native SwiftUI app and interactive WidgetKit extension, iOS 17 or later. Open `LastTimeI.xcodeproj` and choose the LastTimeI scheme. No third-party dependencies.

## Features

- Compact opening list with inline Today controls, minimal headings, and no introductory text or unrecorded status filler.
- A horizontally swipeable calendar beneath every item. Starts at today, scrolls back to January 2010, and toggles a recorded day with one tap.
- Add and edit moments, comma-separated tags, and optional expected intervals.
- Record today once per calendar day; browse and toggle history back to January 2010.
- Search titles/tags and sort by latest, oldest, or name.
- Overdue means calendar days since the latest record **greater than** the expected interval.
- Soft-delete and restore with history preserved.
- Snooze widget eligibility for exactly 7 × 24 hours; unsnooze in the app.
- Small, medium, and large Home Screen widgets with Today actions; medium and large also offer snoozing. Large shows up to four latest plus four remaining oldest eligible items and an overflow count. Medium shows two latest and two oldest; selections can overlap when few items exist.
- Deleted, snoozed, and case-insensitive `health`-tagged items stay off widgets.
- Atomic local JSON storage in the shared App Group; widgets and app share records.
- Light and dark appearance, native accessibility labels, generated app icon.

The six original example titles are included but start unrecorded so that installing the app does not invent activity history. Newly added items can optionally record today. Existing Android on-device data is not automatically transferred.

## Build and install

The checked-in Xcode project is ready to open. If changing its structure, install XcodeGen and run `xcodegen generate` in this directory. The source of the project configuration is `project.yml`.

Select your Apple development team for both targets. App and extension must use the same App Group entitlement, `group.com.greenteabiscuit.lasttimei`; use a unique group and bundle IDs if building under a different Apple account.

```sh
xcodebuild -project LastTimeI.xcodeproj -scheme LastTimeI \
  -destination 'generic/platform=iOS' -derivedDataPath ../../../work/DerivedData \
  -allowProvisioningUpdates build
xcrun devicectl list devices
xcrun devicectl device install app --device YOUR_DEVICE \
  ../../../work/DerivedData/Build/Products/Debug-iphoneos/LastTimeI.app
xcrun devicectl device process launch --device YOUR_DEVICE com.greenteabiscuit.lasttimei
```

Add the widget by holding an empty area of the Home Screen, choosing Edit → Add Widget, and searching for Last Time I. iOS controls refresh timing; snoozed items become eligible at expiry and appear when iOS next refreshes the widget. There are no notification alarms.

## Verification

The LastTimeTests target covers duplicate recordings, history removal, overdue boundaries, exact snooze expiry, widget exclusions/selection, tag normalization, JSON round trips, and stable first-launch IDs shared with widget actions. `LastTimeUITests` exercises creation, Today, snoozing, deletion/restoration, persistence across relaunch, and history navigation.

```sh
xcodebuild -project LastTimeI.xcodeproj -scheme LastTimeI \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

## Icon

Generated with the built-in image generation tool. Final asset: `App/Assets.xcassets/AppIcon.appiconset/AppIcon.png` (1024 × 1024). Prompt: “Create a polished iOS app icon for Last Time I…, a quiet personal tracker remembering when everyday activities last happened. A single bold ivory clock face whose counterclockwise circular arrow ends in a small warm orange dot, on a rich forest green background. Subtle tactile enamel depth, very simple memorable silhouette, centered with generous safe padding, readable at tiny sizes. Square full bleed opaque background; no rounded outside corners, text, mockup, or watermark.”

## Connected-device data transfer (Debug builds)

When direct App Group access is restricted by iOS, the development app supports two launch arguments:

- `--export-transfer` exports the current records to `Documents/Transfer/current.json` in the app data container.
- `--import-transfer` reads `Documents/Transfer/incoming.json`, validates the records, saves a unique `before-UUID.json` backup, replaces the shared records, refreshes widgets, and exports a readback to `current.json`.

Copy files with `devicectl` using `appDataContainer` and bundle ID `com.greenteabiscuit.lasttimei`. Launch with `--terminate-existing` and exactly one transfer argument. Back up and compare existing records before choosing replacement versus merging. After verifying the readback, relaunch without arguments. Personal backup/import files belong outside the source repository.
