# Easy Culling

A personal Android app for culling photos fast: one-tap star ratings, a bin, filters, and bulk
share/move. Built for a Samsung Galaxy S26 Ultra, and works on any Android 13+ phone.

## Installing on the phone

1. Open the repository's **Releases** page on the phone (newest build at the top).
2. Download `EasyCulling-1.0.N.apk`.
3. Tap the downloaded file. The first time, Android asks you to allow installing apps from your
   browser or file manager. Allow it, then tap **Install**.
4. On first launch the app asks for **All files access**. This is needed to write ratings into
   the files, move photos, and empty the bin.

Later builds install over the old one and keep your sessions, because every build is signed
with the same key (`signing/`).

## How it works

- **Sessions**: create a session, then add photos either as whole folders or one by one. The
  session only references the files; nothing is copied.
- **RAW + JPEG**: a `.jpg` and `.dng` with the same name in the same folder count as one photo.
  Rating, bin, move and delete apply to both files. A `RAW` badge marks such photos.
- **Ratings are stored in the files** as standard XMP (`xmp:Rating`, `xmp:Label`), the same
  fields Lightroom/Bridge read. They go inside the JPEG and inside the DNG. Pixel data and other
  metadata stay unchanged, and the file's modification date is kept.
- **Gallery**: 3 columns of square thumbnails (6 in landscape), sorted by file name, or by
  capture time from the ⋮ menu. The Gallery and Bin tabs share the same grid.
- **Filter** (bottom bar): pick a star count. Tap the operator to cycle `≥` → `=` → `<`. You can
  also filter for unrated photos only or by colour label. The filter icon turns green while a
  filter is on.
- **Select**: tap *Select* or long-press a photo. Drag after a long-press to select a range.
  *All* selects only what the filter shows. Selected photos can be rated, labelled, shared,
  moved to another folder, compared (exactly 2), or sent to the bin.
- **Single photo view**: tap a star to set the rating, and tap the same star again to clear it.
  Pinch to zoom; double-tap zooms to 3× (adjustable) around the tapped spot, and double-tap again
  zooms back out. Swipe left/right to go to the next/previous photo. Tap the photo to hide or
  show the controls; this choice carries over to the next photo. 🎨 (bottom right) sets a colour
  label; ⚙️ (top bar) opens Settings.
  ⓘ shows file name, size and EXIF data. The bottom-left button undoes rating, label and bin
  actions one by one and jumps to the affected photo. Long-press it to switch to Redo, and
  long-press again to switch back.
- **Settings** (⚙️ on the sessions list and in the photo view, or ⋮ → Settings in the gallery):
  double-tap zoom (2×–5×, default 3×), auto-advance after rating, vibration on star taps, and
  grid columns (2–6, default 3; twice as many when the phone is horizontal).
- **Bin**: photos sent to the bin leave the gallery but stay on disk. Rating a photo inside the
  bin asks for confirmation, then brings it back to the gallery. *Delete all* permanently
  deletes the bin's files after a confirmation.

## Building

GitHub Actions builds the APK on every push (`.github/workflows/build.yml`) and publishes it as a
release. To build locally: `./gradlew assembleRelease` (needs the Android SDK).
