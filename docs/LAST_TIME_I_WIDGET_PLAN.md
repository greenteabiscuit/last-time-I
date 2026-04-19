# Last Time I Widget Plan

## Product Goal

Build a large Android home-screen widget that helps track the last time a small set of personal events happened.

Examples:

- Cut my nails
- Mopped the floor
- Changed my toothbrush
- Stayed at home the whole day
- Had my nose congested
- Had painful acne somewhere on my body

## Core Interaction

Each item stores a timestamp for when it most recently happened.

The widget should show:

- the item title
- the number of days since it last happened
- a one-tap button that resets the item to today

## Android Constraint

Standard home-screen widgets use `RemoteViews`, which are intentionally limited.

That means:

- one-tap actions are a good fit
- opening the app for add/edit/delete is a good fit
- full freeform CRUD directly inside the widget is not a good fit

So the MVP is:

- quick `Today` actions in the widget
- add/edit/delete in the companion app

## Recommended MVP

### Widget

- large widget layout sized for 6 visible rows
- each row shows:
  - item title
  - day-count label
  - `Today` button
- footer actions:
  - `Add`
  - `Open App`
- if there are more than 6 items, show `+N more in app`

### App

- one simple list screen
- supports:
  - create item
  - edit item title
  - delete item
  - reset an item to today

## Storage Choice

Use `SharedPreferences` with a small JSON payload.

Why:

- enough for one ordered list of personal reminders
- easy to inspect with `run-as` during debugging
- keeps the first version tiny

Each item stores:

- stable ID
- title
- `lastRefreshedAtMillis`

## Build Order

1. Create the timestamp-based item model.
2. Add local persistence with defaults for the initial 6 examples.
3. Replace the widget layout with 6 timestamp rows.
4. Add per-item `Today` actions in the widget.
5. Build the simple CRUD companion app.
6. Test on the connected Pixel.

## Testing Notes

- Use the connected Pixel as the primary test device.
- Capture raw logs with `./scripts/logcat-widget.sh --save`.
- Summarize important results in `docs/WORKLOG.md`.
