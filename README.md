# GK Sketch Studio v1.1 – Advanced Offline Sketchbook

Original Android drawing app with a **white paper canvas** and light interface. Developed By : Gaurav Kharva.

## Drawing and painting
- Ink, pencil, marker, airbrush, watercolor, neon, charcoal, pixel, calligraphy and eraser brushes.
- Brush size/opacity, color picker, color palette and pressure-aware strokes.
- Straight line, rectangle, ellipse and arrow tools, bucket fill and grid guides.
- Vertical, horizontal, both and radial symmetry; zoom/pan, stylus-only mode.

## Editing (new in 1.1)
- Rectangular selection with visible boundary and handles; Select All and Deselect.
- Copy, Cut, Paste (pasted pixels form a new layer), Duplicate, Delete selection.
- Move selected pixels or the entire active layer with the Move tool.
- Flip selection horizontally or vertically, rotate 90°, crop canvas to selection.
- Advanced edit toolbar always accessible; actions also available in tools panel and More menu.
- Editable multiline text: create a new text layer, later select the text layer and use Edit Text.
- Text color, font size, family, alignment, bold and italic; Move tool repositions text layers.
- Undo/redo for edits to layer pixels and text; clear active layer.

## Layers and files
- Up to 20 raster layers with visibility, opacity, blend mode, duplication and layer order.
- Auto-save in the offline gallery (IndexedDB), editable `.gkdraw` import/export including text metadata.
- Import images as layers; export PNG/JPEG with opaque white background.
- Android phone and tablet layouts, no INTERNET permission, no ads or tracking SDK.

## Install / build
Use Android Studio with JDK 17 and Android SDK 35, or the GitHub Actions build workflow. The CI builds a debug-signed APK.

**Important upgrading note:** CI debug builds may use different signing keys. If Android says "App not installed" while updating v1.0, first **export your .gkdraw project backups**, then uninstall the previous APK and install v1.1. Removing the old app can delete locally saved artwork.

**Current limits:** Rectangular selection (not lasso/magic wand); raster brush layers (text stays editable only until painted into); 12-state undo cap and 20 layers; no AI generation or PSD support. The source is independent of Sketchbook's proprietary app.
