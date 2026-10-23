# MG5 Metro Dark — Approved Implementation Specification
Status: APPROVED USER DESIGN DECISIONS, not an implemented APK.
Date: 2026-10-23. Branch: mg5-metro-dark-v1 (separate from stable mg5-dashboard-v1).

## Hardware and constraints
Topway TS10S UIS7862A Android 12 (API31), landscape 1280x720, density 160. Build a normal Android launcher, not a flashable ROM. Preserve DoFun and Topway AC (com.tw.ac), radio (com.tw.radio), Bluetooth, reverse camera, CANBUS and steering-wheel buttons. RTL Arabic; all displayed numerals Latin 0-9; large legible controls and offline graceful degradation.

## Approved UI/UX
- **Metro / Windows 8 Start** style: dark navy base, muted but distinct tile colors, near-square corners, square and rectangular tiles of multiple sizes, compact consistent gutters. Avoid oversized dashboard cards and duplicated bottom navigation dock.
- **Maps tile**: shortcut launches installed Google Maps. No embedded live map, no MapLibre dependency needed in first version.
- **Illustrated backgrounds**: artistic Metro-style scenes, NOT photos. Change subtly based on sunrise/day/sunset/night and optionally rain/dust only with valid weather data. Provide offline time-of-day fallback.
- **Clock, Gregorian/Hijri date, and weather in THREE SEPARATE LIVE TILES**. Show every date line fully; fix cropped date from Alpha6. Weather data from actual service with caching; never fake temperature.
- **Long press tiles** to enter editing; drag/reorder and resize (1x1, 2x1, 2x2 recommended), persist tile arrangement/size across restarts. No edits while driving.
- **Minimum motion**. No auto-flipping, pulsing, bouncing, attention-stealing animations. Only gentle transitions on changing values/backgrounds, ideally restrained during driving.
- **Back** appears in app drawer only, not on home. **Settings** tile visible on home. **Wi-Fi** tile visible on home and indicates actual connectivity; tapping can open Wi-Fi settings (Android 12 blocks unprivileged direct Wi-Fi toggling).
- **Air-conditioner** compact tile, opens factory app. Only display actual AC setpoint/fan/power if validated read-only OEM integration exists; never synthesize values.
- Optional **GPS speed** tile marks speed explicitly as GPS reading, not a vehicle/CANBUS reading. Real media metadata only with MediaSession permission/integration.
- **Horizontal paging** provisionally supported only after testing: first driving page contains essential tiles, second apps/entertainment. Avoid accidental swipes while driving.
- Configurable pages/groups; important live information must not require navigation while driving.

## Suggested composition
Driving: separate clock, separate complete date, separate weather, large Maps shortcut, compact AC, Wi-Fi, Settings, speed, music, radio, apps.
Apps page: installed apps/favorites with drag/resize edit mode, easy back button within drawer, factory DoFun return link.

## Technical stages
1. New UI shell/tile grid with dark Metro styling, strict date sizing, separate live time/weather/date, Maps/AC/Settings/Wi-Fi shortcuts.
2. Long-press edit mode, drag-and-drop, resize and persist; page swipe as performance permits.
3. Implement verified MediaSession, GPS speed, time/weather illustrated scene changes, proper lifecycle updates and offline fallback.
4. Explore read-only Topway OEM AC integration separately; never change factory services.

## Acceptance checks
- 1280x720 renders without cropped text; Latin digits only, appropriate RTL.
- Separate live clock/date/weather tiles; full Gregorian/Hijri dates visible.
- Maps launches Google Maps; Wi-Fi reflects real connection and opens controls.
- No back button on home; back in drawer; Settings on home.
- Compact AC tile, no fake climate info.
- Long press edit drag/resize works and persists; minimal animation.
- No interference with DoFun, camera, CANBUS, SWC, or Topway services.
- New test APK separately evaluated before changing default launcher.


## Iteration accepted 2026-10-23 (Preview 2)
- Back inside the app drawer must be fixed at the **bottom-left**, close to the hand.
- Maps tile opens an on-screen picker for **installed** navigation applications, not just Google Maps.
- Clock is 12-hour with Arabic AM/PM marker and Latin digits.
- Favorite app shortcuts live directly on Metro home: Thmanyah, a separately pinned YouTube alternative (auto-detected if installed; never assume the exact name), and one customizable app favorite tile. Show app's actual name/icon and permit choosing/replacing installed package from long-press edit.
- Keep weather, Wi-Fi, GPS speed live; music should read actual MediaSession metadata only when notification access is granted, no mock tracks.
- Retain original Alpha6 and Preview1 APKs; no modifications to OEM systems.
