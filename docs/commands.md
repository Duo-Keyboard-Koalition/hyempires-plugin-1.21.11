# HyEmpires Plugin - Commands Reference

All HyEmpires commands start with **`/hyempires`**. You can also use block interactions (Bell and Campfire) for some actions.

## Chat output format (lines 31–32 and similar)

When the plugin sends messages in chat, it uses **Minecraft color codes**:

- **`§a`** = green (success)
- **`§e`** = yellow (labels)
- **`§6`** = gold (headers)
- **`§c`** = red (errors)
- **`§f`** = white (values)
- **`§7`** = gray (hints)

Example from village creation:
```text
§aVillage 'Village-X-Z' has been established!
§ePopulation: X villagers
```
- **`Village-X-Z`** is a placeholder for the actual village name (e.g. `Village-100-200` from block coordinates).
- **`X villagers`** is a placeholder for the real population count (e.g. `3 villagers`).

So in-game you might see: **Village 'Village-100-200' has been established!** and **Population: 3 villagers** (in green and yellow).

---

## Overview

| Type | Usage |
|------|--------|
| **Commands** | All start with `/hyempires` (see below) |
| **Block actions** | Bell = village, Campfire on grass = campsite |
| **Villagers** | Tracked automatically (no command) |

---

## /hyempires – Main command

**Usage:** `/hyempires <help|village|campsite|reload>`

### /hyempires help
Shows all HyEmpires commands and a short reminder about Bell/Campfire.

**Example:**
```text
/hyempires help
```

---

## Village commands

All village-related commands are under **`/hyempires village`**.

### /hyempires village list
Lists all active villages (name, population, coordinates). Your villages are marked with “(you)”.

**Example:**
```text
/hyempires village list
```

**Example output:**
```text
§6=== Villages === §7(2)
§fVillage-100-200 §7- Pop: 3 §7@ 100,64,200 §a(you)
§fVillage-300-400 §7- Pop: 1 §7@ 300,70,400
```

---

### /hyempires village info [name]
Shows detailed info for one village.

- **No argument:** You must be standing within 48 blocks of that village’s bell; info is shown for that village.
- **With name:** Shows info for the village with that exact name.

**Examples:**
```text
/hyempires village info
/hyempires village info Village-100-200
```

**Example output:** Same style as “Village Info” in the block section below (name, owner, population, location, status).

---

### /hyempires village refresh [name]
Recounts villagers for a village. Requires village owner or OP.

- **No argument:** Refreshes the village you’re standing in (within 48 blocks of bell).
- **With name:** Refreshes the village with that name.

**Examples:**
```text
/hyempires village refresh
/hyempires village refresh Village-100-200
```

**Example output:**
```text
§aPopulation updated: 5 villagers
```

---

## Campsite commands

All campsite-related commands are under **`/hyempires campsite`** (or **`/hyempires camp`**).

### /hyempires campsite list
### /hyempires camp list
Lists all active campsites (name and coordinates). Yours are marked with “(you)”.

**Examples:**
```text
/hyempires campsite list
/hyempires camp list
```

---

### /hyempires campsite info [name]
### /hyempires camp info [name]
Shows detailed info for one campsite.

- **No argument:** You must be standing on the campsite’s campfire block.
- **With name:** Shows info for the campsite with that name.

**Examples:**
```text
/hyempires campsite info
/hyempires campsite info Campsite-150-250
/hyempires camp info Campsite-150-250
```

---

## Reload command

### /hyempires reload
Rescans loaded chunks for villages and campsites and rescans all villagers. Requires **`hyempires.reload`** or OP.

**Example:**
```text
/hyempires reload
```

**Example output:**
```text
§aHyEmpires data rescanned.
```

---

## Block interactions (no /hyempires)

These actions do **not** use `/hyempires`; they use blocks only.

### Village (Bell)

| Action | Block | Click | Result |
|--------|--------|--------|--------|
| Create village | Place **Bell** | — | Village created; you see messages like §aVillage 'Village-X-Z' has been established! and §ePopulation: X villagers |
| View village info | **Bell** | Right-click | Same info as `/hyempires village info` |
| Refresh population | **Bell** | Shift + Right-click (owner/OP) | Same as `/hyempires village refresh` |
| Abandon village | **Bell** | Left-click (owner/OP) | Village abandoned (no command equivalent) |

### Campsite (Campfire)

| Action | Block | Click | Result |
|--------|--------|--------|--------|
| Create campsite | Place **Campfire** on grass/dirt/sand | — | Campsite created; you see §6New campsite established: Campsite-X-Z |
| View campsite info | **Campfire** | Right-click | Same info as `/hyempires campsite info` |
| Abandon campsite | **Campfire** | Left-click (owner/OP) | Campsite abandoned (no command equivalent) |

---

## Command summary (all under /hyempires)

| Command | Description |
|---------|-------------|
| `/hyempires help` | Show help and command list |
| `/hyempires village list` | List all villages |
| `/hyempires village info [name]` | Village details (stand in village or give name) |
| `/hyempires village refresh [name]` | Refresh population (owner/OP) |
| `/hyempires campsite list` | List all campsites |
| `/hyempires campsite info [name]` | Campsite details |
| `/hyempires camp list` | Same as campsite list |
| `/hyempires camp info [name]` | Same as campsite info |
| `/hyempires reload` | Rescan data (OP) |

---

## Tab completion

Typing `/hyempires ` and then pressing Tab will suggest:

- **First argument:** `help`, `village`, `campsite`, `camp`, `reload`
- **After `village`:** `list`, `info`, `refresh`
- **After `campsite` or `camp`:** `list`, `info`
- **After `info` or `refresh`:** Village or campsite names (if any)

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `hyempires.reload` | Use `/hyempires reload` | OP only |
| (others) | All other `/hyempires` commands | All players |

---

## Quick reference

**Commands (all start with `/hyempires`):**
```text
/hyempires help
/hyempires village list
/hyempires village info [name]
/hyempires village refresh [name]
/hyempires campsite list
/hyempires campsite info [name]
/hyempires camp list
/hyempires camp info [name]
/hyempires reload
```

**Block actions (no command):**
- Place **Bell** → create village (messages like §aVillage 'Village-X-Z' has been established! §ePopulation: X villagers).
- Right-click **Bell** → village info.
- Place **Campfire** on grass → create campsite (§6New campsite established: Campsite-X-Z).
- Right-click **Campfire** → campsite info.

For step-by-step testing, see **testing-guide.md**.
