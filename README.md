# GK Sketch Studio v1.2 — Advanced Offline Drawing App

A new, original sketching app for Android 10+ phones and tablets. **Developed By : Gaurav Kharva**.

## New in v1.2

- Independent brush, eraser, line, and shape-border thickness controls (1–300 px) with eight quick presets.
- Adjustable color palette, custom brush and line colors, 1–100% opacity, configurable colored shape fill.
- Eraser options: remove to transparency, erase to white, or paint with selected color.
- Eight line styles: solid, dashed, dotted, dash-dot, long dash, double, wavy, zigzag.
- 18 shapes: rectangle, rounded rectangle, ellipse, triangle, diamond, pentagon, hexagon, octagon, star, starburst, heart, cloud, speech bubble, arrow, chevron, parallelogram, cross, arc. Outline/fill/both modes.
- 34 *bundled, transparent PNG objects* organized by category (nature, symbols, everyday objects, arrows, frames). Tap to insert as a new movable layer; scale, flip, rotate, copy, export. Own PNG/JPEG image import remains available.

## Retained from v1.1

White drawing paper, 10 brush presets, stylus-pressure-aware strokes, layers with blend/opacity/visibility, rectangular selection, cut/copy/paste and transforms, multiline editable text layers, symmetry, grid, pan/zoom, undo/redo, local save, `.gkdraw` editable projects and PNG/JPEG export.

### Notes

This is a standalone offline drawing editor; the PNG object pack is bundled locally (no internet required). No remote APIs, no tracking, no network permission. APK is compiled using the GitHub Actions workflow. Device testing is separate from build verification. **Export editable `.gkdraw` projects before uninstalling a previous APK**; GitHub debug build signatures can vary between runs, potentially preventing an in-place update.

## Build

JDK 17 / Android Gradle Plugin 8.7.3 / Gradle 8.10.2, API 35; `gradle :app:assembleDebug`.
