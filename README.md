============
sonic-engine
============
>This project is a work in progress, for the current state please see the latest version in the Releases section of this
document

Introduction
============
This Java-based Sonic the Hedgehog game engine aims to fully and faithfully recreate the original physics of the Sonic
The Hedgehog games for Sega Mega Drive (Genesis). It will be capable of loading the game data from the original ROMs of
the games and providing a pixel-perfect gameplay experience of the original games. It will also provide more modern features,
such as a level editor, and an open framework allowing for modding and customisation.

Configuration
=============
The engine currently makes limited use of config.json to hold some basic configurations. Change these at your own risk.

Controls
========
>Currently, only Keyboard controls are supported.

Player Controls
- D-Pad Up/Down/Left/Right - Movement Controls
- Space - Jump
- Z - Cycle Acts
- X - Cycle Zones

Debug Controls
- F1 - Show/Hide Debug Overlay (Includes text and bounding boxes)
- F2 - Show/Hide Shortcuts Overlay
- F3 - Show/Hide Player Panel
- F4 - Show/Hide Sensor Labels
- F5 - Show/Hide Object Labels
- F6 - Show/Hide Camera Bounds
- F7 - Show/Hide Player Bounds
- F9 - Show/Hide Ring Bounds
- F10 - Show/Hide Plane Switchers
- F11 - Show/Hide Touch Response
- F12 - Show/Hide Art Viewer

Releases
========
V0.01 (Pre-Alpha) (Unreleased) - A moving black box. This version will be complete when we have an unskinned box that can traverse
terrain in the same way Sonic would in the original game.

V0.05 - Little more than a tech demo. Sonic is able to run and jump and collide with terrain in a reasonably correct way. No
graphics have yet been implemented so it's a moving white box on a black background.

v0.1.20260110 - Now vaguely resembles the actual Sonic 2 game. Real collision and graphics data is loaded from the Sonic 2 ROM and rendered
on screen. The majority of the physics are in place, although it is far from perfect. A system for loading game objects has
been created, along with an implementation for most of the objects and Badniks in Emerald Hill Zone. Rings are implemented, life and score
tracking is implemented. SoundFX and music are implemented. Everything has room for improvement, but this now resembles a playable
game.

v0.2.20260117 - Improvements and fixes across the board. Special stages are now implemented, feature complete with a few known issues.
Physics have been improved, parallax backgrounds implemented and complete for EHZ, CPZ, ARZ and MCZ. Some sound improvements, title cards,
level 'outros' etc.

v0.3.20260206 - A massive release covering 366 commits across every major subsystem. The engine has been refactored to support
multiple games via a provider-based abstraction layer, with initial Sonic 1 ROM support alongside the existing Sonic 2 support.
The physics engine has been completely rewritten to match ROM behaviour. Boss fights are implemented for 5 zones (EHZ, CPZ, HTZ,
CNZ, ARZ), along with 15+ new badniks and 50+ new game objects spanning all implemented zones. A full water system with drowning
mechanics is in place for CPZ and ARZ. The graphics backend has been migrated from JOGL to LWJGL with a GPU-accelerated rendering
pipeline (pattern atlas, tilemap shader, instanced sprite batching, priority FBOs). The audio engine has seen major accuracy
improvements to YM2612 FM synthesis (based on Genesis-Plus-GX reference) and the SMPS driver. New infrastructure includes a level
select screen, HeadlessTestRunner for physics integration testing, visual and audio regression test suites, GraalVM native build
support, and significant performance optimisations throughout. See CHANGELOG.md for full details.