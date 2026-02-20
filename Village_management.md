# Village Management (HyEmpires)

**Policy: Use only the base game (Bukkit/Paper) API.** No custom NMS, no artificial territory systems. Where the game provides data (e.g. villager bed/workstation), we use that API only.

---

## Base game API usage

- **Villager bed location:** `org.bukkit.entity.memory.MemoryKey.HOME` via `Villager.getMemory(MemoryKey.HOME)`.
- **Villager workstation (job site):** `org.bukkit.entity.memory.MemoryKey.JOB_SITE` via `Villager.getMemory(MemoryKey.JOB_SITE)`.
- **Village boundaries:** Simple distance from bell(s) (no chunk claiming, pathfinding, trail, or road logic for containment).
- **No new systems:** Do not add custom territory, POI scanning, or NMS; use base game API only.

---

## 1. Village boundaries (natural)

- **Rule:** A location is **in** a village if it is within **distance** of one of that village’s bells.
- **Primary bell:** `effectiveRadius` blocks (default 48, can grow when additional bells are added).
- **Additional bells:** 48 blocks from each extra bell.
- **Same world:** Only bells in the same world as the location count.
- No chunk claims, roads, or trail influence are used to decide “who owns this block.” Only distance from bells is used.

---

## 2. Bells

### 2.1 Every bell starts blank

- Placing a bell does **not** create a village.
- A bell becomes part of village logic only after a player interacts with it using **paper** or an **admin token**.

### 2.2 Plain paper on a bell

- **Blank bell + plain paper:** Start **new village**.
  - Player is asked to type a village name in chat (or `skip` for a default name).
  - New village is created with that bell as the primary (admin) location.
- **Village bell + plain paper:** Get an **administration token** for that village (if the player doesn’t already have one).

### 2.3 Administrative paper (village admin token) on a bell

- **Blank bell + admin token:** **Add this bell** to the token’s village.
  - The bell is added as an additional bell; `effectiveRadius` is updated so the new bell is within range.
- **Village bell (same village as token) + admin token:** Open the **village administration menu**.
- **Village bell (different village) + admin token:** **Merge** prompt:
  - **Same owner (both villages):** Player is asked to type a new name in chat; the two villages are merged into one with that name.
  - **Different owners:** Message that the other owner’s consent is required (consent flow not implemented yet).

---

## 3. Beds and workstations

### 3.1 When are they “in” a village?

- A **bed** or **workstation** is part of a village if its block location is **within that village’s boundary** (i.e. within `effectiveRadius` of the primary bell or within 48 blocks of any additional bell).
- No pathfinding or chunk claiming is used. Only the distance-from-bells rule above applies.

### 3.2 Bed placement

- If the bed is placed **within** a village (by radius): village population is updated (resident count: villagers with bed + workplace in that village).
- No search, no pathfinding, no chunk claiming.

### 3.3 Workstation placement

- If the workstation is placed **within** a village (by radius): it is already part of that village; nothing else happens.
- If placed **outside** any village: the player is told that it is outside village territory and that they should place it within a village’s radius of its bell to have it count as part of that village.

### 3.4 Right-click workstation with admin token

- If the block is a workstation and the player is holding an admin token:
  - **Already in token’s village:** Message that it is already part of that village (within X blocks of bell).
  - **In another village:** Message that it is part of the other village.
  - **Outside any village:** Message to place it within the village’s radius of its bell to be part of that village.
- No pathfinding or chunk claiming is performed.

---

## 4. Population and residents

- **Population** is derived from **residents**: villagers who have both a **bed** and a **workstation** that are **in that village** (by the radius rule).
- Population is updated when beds are placed or broken (and when resident count is refreshed).
- “Vassal” = bed + workstation in the same village; only vassals count toward population for this system.

---

## 5. Influence and administration

- Each village has an **influence** system (per-player influence, leader, etc.).
- **Village Administration Token** (paper with village name): obtained by right-clicking a village bell with **plain paper**.
- **With token:** Right-click **air** or a **non-workstation block** to open the village administration menu (population, bed locations, workspace locations, rename, etc.).
- Building/destroying near a village center may require sufficient influence (and can grant small influence); exact rules are in the influence and admin listeners.

---

## 6. What is *not* used (performance / simplicity)

To keep behaviour natural and performance light, the following are **not** used for village boundaries or “who owns this block”:

- **Chunk claiming** – No 5×5 or path-based chunk claims for containment.
- **Pathfinding** – No search from beds/workstations to bells for territory.
- **Trail influence / road network for territory** – Not used to decide village containment.
- **Periodic tasks** – No recurring territory refresh, road building, or villager trail sampling for boundaries.

Containment is **only** “within radius of a bell.” Other systems (e.g. trail/road data) may still exist for optional or future features but do not define village boundaries.

---

## 7. Summary table

| Action | Result |
|--------|--------|
| Place bell | Bell is “blank”; no village yet. |
| Blank bell + plain paper | Create **new village** (name in chat). |
| Blank bell + admin token | **Add bell** to token’s village. |
| Village bell + plain paper | Get **admin token** for that village. |
| Village bell + token (same village) | Open **administration menu**. |
| Village bell + token (other village, same owner) | **Merge** villages (new name in chat). |
| Village bell + token (other village, different owner) | Message: consent required (not implemented). |
| Bed/workstation within radius of bell | Part of that village; population updated for beds. |
| Bed/workstation outside radius | Not part of any village; no automatic claiming. |

---

## 8. Data and files

- Villages: `villages.nbt` (name, world, primary bell, additional bells, effectiveRadius, owner, population, etc.).
- Influence: `influence.nbt` (per-village, per-player).
- Chunk/trail/road data may still be persisted for compatibility or tools but **do not** define village boundaries; boundaries are radius-from-bells only.
