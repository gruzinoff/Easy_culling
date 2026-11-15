# Backlog

Nothing here is built yet. Items are written so they can be picked up directly.

**Terms used below**
- **Culled photo**: a keeper that was moved to another folder from the session (for example, from
  the camera import folder to a "Culled" folder).
- **Removed photo**: a photo whose files were permanently deleted from the bin.
- **Open photo**: a photo still in the session that is neither culled nor removed. It is either in
  the gallery or in the bin.

---

## 1. Culled photos stay in the gallery, clearly marked

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

---

## 2. "Culled" filter

**Behaviour**
- The filter panel gets a new row, **Culled**, with three choices:
  **Show** (default) / **Only culled** / **Hide culled**.
- It combines with the star and label filters (all must match). Example: "≥ 3★ + Hide culled"
  means good photos that are not moved yet.
- The filter icon turns green whenever this row is not on "Show", the same as for the star filter.
- "Select all" respects this filter, as it already does for stars.

---

## 3. "Culled" count in the gallery header

**Behaviour**
- The line under the session name becomes **"Rated 120 / 1000 · Culled 57"**.
- "Rated" counts all photos in the gallery (culled ones included). "Culled" counts the culled
  photos.
- The "· Culled N" part appears only when N > 0.
- The sessions list shows the same numbers on each session row.

---

## 4. Empty session: "Add photos" button on the gallery screen

**Behaviour**
- A new session with no photos shows, in the middle of the gallery screen: **"No photos yet"**
  with an **Add photos** button directly under it.
- The text "Use ⋮ → Add photos" is removed. The ⋮ menu entry stays, for adding more photos later.

---

## 5. Finished session: summary screen

**When it appears.** When the session has no open photos left: everything was either culled or
removed, and the bin is empty.

**Gallery screen in this state.** It shows a summary in place of the grid:
- **Processed X photos**: all photos ever added to the session.
- **Culled Y photos**, with an **Open folder** button. If photos were moved to several folders,
  each folder is listed with its count and its own button.
- **Removed Z photos**: permanently deleted from the bin.
- A **View culled photos** button that shows the grid filtered to "Only culled".

The sessions list marks the session **"Finished"** and shows the same three numbers.

**Data change.** The session needs a count of removed photos, because removed photos are dropped
from the session today and their number would otherwise be lost.

**Technical note: "Open folder".** Android has no single reliable way to open a folder in
another app. The plan is to try to open it in the phone's file manager (Samsung My Files) and, if
that fails, show the folder's photos in an in-app grid. This needs testing on the phone.

**Open questions**
- Should there be a manual **"Finish session"** button (for example, when you leave some photos
  neither moved nor deleted on purpose)? If yes, the leftovers would count as "Left in import
  folder: N".
- Can a finished session be reopened by adding more photos? Proposal: yes. Adding photos makes
  it active again.

---

## 6. Default culled folder + one-step "Move keepers"

**Default culled folder**
- On first launch, after granting file access, the app asks you to **choose the folder for culled
  photos** (folder picker with a "New folder" button, for example `Pictures/Culled`). This folder
  should hold only culled photos.
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
- Moving out again is not automatic. If a culled photo is later re-rated below the threshold, it
  stays where it is. It is still marked culled, and you can move it back manually if wanted.

**Why batch and not instant.** On the same storage a move is only a rename: fast, with no extra
wear on the memory. But moving on every rating would:
- trigger Google Photos backup uploads and removals each time a rating crosses the threshold;
- move photos before the decision is final;
- make undo and the gallery confusing.
