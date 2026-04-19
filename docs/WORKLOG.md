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
