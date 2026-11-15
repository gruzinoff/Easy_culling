# Backlog

Nothing here is built yet. Items are written so they can be picked up directly.

**Build plan.** Several small builds, each tested on the phone before the next:
All of 1–8 done (builds 7–9). Item 9 is on hold.

**Terms used below**
- **Culled photo**: a photo moved out of its import folder by this app, to any folder: the
  default culled folder or any other.
- **Removed photo**: a photo whose files were permanently deleted from the bin.
- **Open photo**: a photo still in the session that is neither culled nor removed. It is either in
  the gallery or in the bin.

---

## 1. Culled photos stay in the gallery, clearly marked (DONE: build 9)

**Problem.** Moving photos currently removes them from the session. The gallery then shows
"No photos match the filter", which looks as if the photos are lost.

**Behaviour**
- After a move, the photos stay in the session as **culled**. The app records the new file
  location and keeps the rating and label.
- Culled photos stay in the gallery in their usual sort position.
- They can be opened in the single photo view, zoomed, swiped through and shared like any other
  photo.

**Marking. It must explain itself without a legend.**
- *Thumbnail*: a small green badge in the top-left corner showing a folder with a check mark,
  plus a thin green frame around the photo. The colour label dot moves next to the badge.
  The photo is **not** dimmed, because dimming reads as "disabled" or "rejected", and these are the
  best photos.
- *Single photo view*: under the file name, a green chip reads **"✓ Culled → Pictures/Culled"**.
  Tapping it opens that folder (see the note in item 5).
- *Selection mode*: culled photos can be selected. Moving them again moves them to a new folder.
  The record stays "culled" with the new location.

**Decided here (change if you disagree)**
- Rating or labelling a culled photo is allowed. The rating is written into the file in its new
  location.
- A culled photo cannot be sent to the bin (the bin button is disabled for it). This prevents
  deleting a keeper by accident. To get rid of one, use the phone's file manager.
- **Any move counts as culled**, whether to the default culled folder (item 6) or to another folder.
  The chip always shows the actual folder.
- **Photos added from the culled folder are culled.** When Add photos brings in photos that are
  in the default culled folder (item 6), they are marked culled automatically, and their ratings
  and labels are read back from the files. This also repairs the sessions culled before this
  feature existed: add the photos again from the culled folder.

---

## 2. "Culled" filter (DONE: build 9)

**Behaviour**
- The filter panel gets a new row, **Culled**, with three choices:
  **Show** (default) / **Only culled** / **Hide culled**.
- It combines with the star and label filters (all must match). Example: "≥ 3★ + Hide culled"
  means good photos that are not moved yet.
- The filter icon turns green whenever this row is not on "Show", the same as for the star filter.
- "Select all" respects this filter, as it already does for stars.

---

## 3. "Culled" count in the gallery header (DONE: build 9)

**Behaviour**
- The line under the session name becomes **"Rated 120 / 1000 · Culled 57"**.
- "Rated" counts all photos in the gallery (culled ones included). "Culled" counts the culled
  photos.
- The "· Culled N" part appears only when N > 0.
- The sessions list shows the same numbers on each session row.

---

## 4. Empty session: "Add photos" button on the gallery screen (DONE: build 7)

**Behaviour**
- A new session with no photos shows, in the middle of the gallery screen: **"No photos yet"**
  with an **Add photos** button directly under it.
- The text "Use ⋮ → Add photos" is removed. The ⋮ menu entry stays, for adding more photos later.

---

## 5. Ending a session: "Session finished" screen and stats (DONE: build 9)

**Purpose.** Make it visible that the job is done, and how much work it was.

**Two ways to finish**
1. **Suggested automatically.** When the last open photo is culled or permanently deleted
   (gallery has no open photos left and the bin is empty), the app shows a dialog:
   **"All photos processed 🎉 Finish this session?"** [Finish session] [Not yet].
   "Not yet" closes the dialog and leaves a slim banner at the top of the gallery with a
   **Finish** button, so the suggestion doesn't pop up again.
2. **Manually, at any time.** In the gallery: ⋮ → **End culling session**. A confirmation shows
   what is still open: "312 photos are not culled yet (40 rated, 272 unrated). Finish anyway?"
   - If the bin still has photos, the dialog has a checkbox **"Also delete the N photos in the
     bin"** (unchecked by default). If left unchecked, they stay on disk, untouched.
   - Ending a session never moves or deletes files on its own. Open photos simply stay where they
     are.

**Finished gallery screen.** Shown in place of the grid:
- a large **green tick** with **"Session finished"** and the finish date;
- **stats**:
  - **Processed X photos**: all photos ever added to the session;
  - **Culled Y**, with an **Open folder** button (one per folder if several were used);
  - **Removed Z**: permanently deleted;
  - only if the session was finished with photos still open, also **Left: N**, split into
    **Rated R** and **Unrated U**. The bin count is shown too if the bin wasn't emptied;
- buttons: **View culled photos** (grid filtered to "Only culled") and **View all photos**
  (normal grid, everything still viewable and rateable).

**Sessions list**
- A finished session shows a **green tick** next to its name and a short stats line:
  **"Processed 1638 photos · culled 214"**.
- Unfinished sessions keep the current line, plus "culled N" from item 3.

**Reopening.** ⋮ → **Reopen session** (or adding photos to it) makes it active again. The
finished state and tick disappear until it is finished again.

**Data changes**
- The session stores `finishedAt` and a count of removed photos. Removed photos are dropped from
  the session today and their number would otherwise be lost.

**Technical note: "Open folder".** Android has no single reliable way to open a folder in
another app. Built: try Samsung My Files first, then any app that can show a folder; if both
fail, a message shows the folder path. The in-app grid fallback was not built; add it only if
the phone can't open the folder.

---

## 6. Default culled folder + one-step "Move keepers" (DONE: build 9)

**Default culled folder**
- On first launch, after granting file access, the app asks you to **choose the folder for culled
  photos** (folder picker with a "New folder" button, for example `Pictures/Culled`). This folder
  should hold only culled photos.
- For the existing installation, the same question is asked **once on the first start after this
  update**. It can be skipped. If skipped, it is asked again the first time Move keepers is tapped.
- It can be changed later in **Settings → Culled folder**.
- The manual Select → Move flow opens with this folder pre-selected. Any other folder can still
  be chosen.

**Move keepers (batch, never automatic)**
- Rating **only marks** a photo. Nothing is moved while culling, however often the rating
  changes (2 → 3 → 2 → 3 does nothing to the files).
- When ready, tap **Move keepers** (gallery bottom bar or ⋮ menu). A confirmation shows:
  "Move 57 photos rated ≥ 3★ to Pictures/Culled?". The threshold (1–5★) can be changed in that
  dialog, defaults to 3★, and remembers the last value.
- On confirm, all matching photos that are not culled yet are moved in one batch (JPEG and its RAW
  together) and become **culled** (item 1).
- **Then the rest.** When the move is done, the app asks: **"Move the remaining 1424 photos to the
  bin?"** [Yes] [Not now]. "Remaining" means open photos in the gallery that are below the
  threshold. Yes sends them to the bin (undoable). Permanently deleting from the bin stays a
  separate, deliberate step ("Delete all" in the Bin tab). After that, the session is ready to
  finish (item 5).
- Moving out again is not automatic. If a culled photo is later re-rated below the threshold, it
  stays where it is. It is still marked culled, and you can move it back manually if wanted.

**Why batch and not instant.** On the same storage a move is only a rename: fast, with no extra
wear on the memory. But moving on every rating would:
- trigger Google Photos backup uploads and removals each time a rating crosses the threshold;
- move photos before the decision is final;
- make undo and the gallery confusing.

---

## 7. Track active culling time (DONE: build 9)

**Goal.** Show how long the culling actually took: time spent working on the session, not just
"finished minus started".

**What is counted**
- The session's creation date and time is already saved, and stays shown as "Started …".
- **Active time** is counted only while the app is on screen **and** one of this session's
  screens is open (gallery, single photo view, compare, add photos, move folder picker).
- Not counted: the app in the background, the screen off, the sessions list, Settings.
- **Idle cut-off (confirmed: 3 minutes).** If there is no touch for **3 minutes**, counting pauses until the next touch,
  and the idle minutes are not counted. This stops "phone left on the table with the app open"
  from inflating the time. Thinking about a photo for a minute or two still counts.
- Time keeps adding up across days and app restarts until the session is finished. Reopening a
  finished session continues the same counter.

**Where it is shown**
- **Finished gallery screen** (item 5), in the stats: **"Culling time 3h 12m"**, plus
  "Started 14 Nov 2026".
- **Sessions list**, on a finished session's line: **"1684 processed in 3h 12m · culled 214"**.
- Format: under 1 h → "47m"; otherwise "3h 12m".
- Sessions created before this feature have no recorded time. They show "—" for culling time and
  the sessions list shows "1684 processed" without "in …".

**Data change.** The session stores `activeMillis`. The app saves it every minute and whenever it
goes to the background, so a crash loses at most a minute.

---

## 8. New session without the naming step; rename by tapping the name (DONE: build 7)

**Behaviour**
- **New session** creates the session immediately with an automatic name: the creation date and
  time in ISO format, **`2026-11-15 04:20:33`** (`yyyy-MM-dd HH:mm:ss`, phone's local time). The name dialog is removed. It goes
  straight to Add photos, and then to the gallery.
- In the gallery, **tapping the session name** in the top bar turns it into an editable text
  field in place, with the keyboard open and the whole name selected so typing replaces it. No pen
  icon and no dialog.
  - Keyboard **Done** or tapping anywhere else saves.
  - Back cancels the edit.
  - An empty name is not saved; the previous name stays.
- **Leaving Add photos without adding anything** keeps the empty session. It opens the gallery
  with "No photos yet" and an Add photos button (item 4). It can be deleted from the sessions
  list.
- "Rename session" is removed from the gallery ⋮ menu. The sessions list keeps Rename in its ⋮
  menu.

---

## 9. Add photos: sort by date (newest first by default)

**Behaviour**
- In the Add photos browser, inside a folder, photos are sorted by **capture date**.
- A sort button in the top bar switches between **Newest first** (default) and **Oldest first**.
  The current order is shown in the button label or tooltip.
- The choice is remembered for next time.
- Ties (same second) are ordered by file name.
- The folder list keeps its current order: folders with the most recent photos first.
- Drag-to-select keeps working on whatever order is shown, so a range always means
  "what you see between the two photos".

---

## 10. Grid size by pinch gesture (remove it from Settings)

**Behaviour**
- In the **gallery** (Gallery and Bin tabs) and in the **Add photos** browser, a two-finger pinch
  changes the number of columns:
  - fingers **apart** (zoom in) → fewer, bigger photos;
  - fingers **together** (zoom out) → more, smaller photos.
- One column per step, from **2 to 6** columns (twice as many when the phone is horizontal).
  A short haptic tick on each step.
- The photo under your fingers stays in view while the grid changes.
- Pinching never selects or opens photos, and it does not interfere with scrolling,
  long-press drag-select or the fast-scroll handle.
- The size is **remembered separately** for the gallery and for the Add photos browser
  (small thumbnails are handy for picking hundreds of photos, bigger ones for judging).
  Default 3 columns for both.
- **Settings → Grid columns is removed.** The existing setting value becomes the gallery's
  starting size.

---

## 11. Describe photos with text, for finding them later (ON HOLD)

**Goal.** Attach a short piece of text to one photo (single photo view) or many at once (gallery
selection), so the photo can be found later, for example "Lisbon, Alfama, sunset" or "Masha's
birthday".

**Open decision: one description line or several tags?** Both are standard fields that
Lightroom/Bridge read and search:
- **Description (caption)**: one free line of text per photo (XMP `dc:description`). Simple to
  type. Search matches any word in it.
- **Keywords (tags)**: several separate short words per photo (XMP `dc:subject`), for example
  `Lisbon` `sunset` `family`. Better for adding or removing one tag on many photos, and for
  picking from previously used tags.

*Recommendation*: **description line**, because it matches "a short line of text describing the
photo". To keep it fast, the input offers recently used descriptions as one-tap suggestions.
Keywords can be added later if needed.

**Single photo view**
- With controls shown, a line above the stars shows the description, or a faint
  "Add description…" if there is none. Tap it to edit with the keyboard. Done saves.
- Recent descriptions appear above the keyboard as chips. One tap fills the field.
- Hidden together with the other controls.
- Changes go into the undo history, the same as ratings.

**Gallery (batch)**
- In selection mode, a new bottom-bar action **Describe** opens a dialog with the text field and
  recent suggestions.
- If some selected photos already have a different description, the dialog warns:
  "Replaces the description on 12 photos". Undo works from the snackbar.

**Finding photos**
- The gallery gets a **search** field (🔍 in the top bar). Typing filters the grid to photos whose
  description contains the text. It combines with the star/label/culled filters.
- Optional, later: search across all sessions from the sessions list.

**Storage**
- Saved in the app and written into the photo files (JPEG and DNG), the same way as ratings. It
  is preserved when photos are moved to the culled folder.
- To verify on the phone: whether Google Photos and Samsung Gallery show or search this field.
  Lightroom does. Google Photos and Samsung Gallery mostly use their own descriptions and may
  ignore it.
