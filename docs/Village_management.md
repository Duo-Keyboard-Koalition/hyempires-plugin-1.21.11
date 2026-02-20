# Village Management (HyEmpires)

This document describes how villages and their boundaries work in HyEmpires. The design follows **natural Minecraft-style boundaries**: a location is part of a village if it is within range of that village’s bell(s). There is no artificial chunk claiming, pathfinding, or periodic territory updates.

For the **data structure** the plugin considers (POI Registry, Villager Brain, Gossip) and a **UML class diagram**, see [Village_data_structure.md](Village_data_structure.md).

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
- **Village bell + plain paper:** Get an **administration token** for that village (if the player doesn’t already have one). The administration token lets the player **see who lives in the village** (which villagers use that bell as their meeting point) and open the administration menu (population by type, villagers by profession).

### 2.3 Emerald on a bell (Trading token)

- **Village bell + emerald:** Get a **trading token** for that bell (if not already held).
- **Trading token:** Right-click air (or use from inventory) to open the **master trading menu** for that bell: one GUI showing **all villagers' trades** that use that bell as their meeting point. No need for a physical trading hall—one token gives access to every villager's offers under one bell.

### 2.4 Administrative paper (village admin token) on a bell

- **Blank bell + admin token:** **Add this bell** to the token’s village.
  - The bell is added as an additional bell; `effectiveRadius` is updated so the new bell is within range.
- **Village bell (same village as token) + admin token:** Open the **village administration menu** (who lives in the village, population by type, villagers by profession).
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

## 4. Base game only: village and membership

- **“What” is the village:** HyEmpires does **not** define villages beyond the bell(s) and radius. Village boundaries are radius-from-bells only (see §1). In the game, the **bell** is the anchor (meeting point); villagers store it in their Brain as **meeting_point**.
- **Membership of villagers:** Determined by the **base game** only. HyEmpires uses only Bukkit/Paper API:
  - **MemoryKey.HOME** – villager’s assigned bed.
  - **MemoryKey.JOB_SITE** – villager’s assigned workstation.
  - **Meeting point (bell)** – which bell the villager uses; “who lives in the village” = villagers that use that bell as meeting point (or whose HOME/JOB_SITE fall within the bell’s radius, depending on API).
- A villager is **linked** to a village if their HOME and/or JOB_SITE is within that village’s radius. **Residents (vassals)** = both HOME and JOB_SITE in the village. Peasant = bed in village only; Laborer = workstation in village only; Scout = neither assigned.
- HyEmpires adds **observability only**: the **administration token** shows **who lives in the village** (population by type, villagers by profession). The plugin does **not** track or display bed/workstation locations; it only tracks villagers and their professions (Minecraft predefined format).

## 5. Population and residents (observability)

- **Population** is the count of villagers who have both HOME and JOB_SITE (from base game) in that village.
- The **administration menu** shows population by type (Scout, Laborer, Peasant, Vassal) and **villagers by profession** (Farmer, Librarian, etc.). No bed or workstation location lists.

---

## 6. Tokens summary

| Token | Obtain | Use |
|-------|--------|-----|
| **Administration token** | Right-click bell with **paper** | See who lives in the village (meeting point = that bell). Open admin menu: population by type, villagers by profession, rename, info. Right-click air or non-workstation to open menu. |
| **Trading token** | Right-click bell with **emerald** | Open **master trading menu** for that bell: all villagers' trades in one GUI (no trading hall needed). |

## 7. Influence and administration

- Each village has an **influence** system (per-player influence, leader, etc.).
- **Administration token** (paper): right-click bell with **plain paper** to obtain. Right-click **air** or a **non-workstation block** to open the village administration menu (population, villagers by profession, rename, etc.).
- Building/destroying near a village center may require sufficient influence (and can grant small influence); exact rules are in the influence and admin listeners.

---

## 8. What is *not* used (performance / simplicity)

To keep behaviour natural and performance light, the following are **not** used for village boundaries or “who owns this block”:

- **Chunk claiming** – No 5×5 or path-based chunk claims for containment.
- **Pathfinding** – No search from beds/workstations to bells for territory.
- **Trail influence / road network for territory** – Not used to decide village containment.
- **Periodic tasks** – No recurring territory refresh, road building, or villager trail sampling for boundaries.

Containment is **only** “within radius of a bell.” Other systems (e.g. trail/road data) may still exist for optional or future features but do not define village boundaries.

---

## 9. Summary table

| Action | Result |
|--------|--------|
| Place bell | Bell is “blank”; no village yet. |
| Blank bell + plain paper | Create **new village** (name in chat). |
| Blank bell + admin token | **Add bell** to token’s village. |
| Village bell + plain paper | Get **administration token** (see who lives in village, admin menu). |
| Village bell + emerald | Get **trading token** (master trading menu for that bell). |
| Village bell + admin token (same village) | Open **administration menu**. |
| Village bell + token (other village, same owner) | **Merge** villages (new name in chat). |
| Village bell + token (other village, different owner) | Message: consent required (not implemented). |
| Bed/workstation within radius of bell | Part of that village; population updated for beds. |
| Bed/workstation outside radius | Not part of any village; no automatic claiming. |

---

## 10. Data and files

- Villages: `villages.nbt` (name, world, primary bell, additional bells, effectiveRadius, owner, population, etc.).
- Influence: `influence.nbt` (per-village, per-player).
- Chunk/trail/road data may still be persisted for compatibility or tools but **do not** define village boundaries; boundaries are radius-from-bells only.
