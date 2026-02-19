# HyEmpires Plugin - In-Game Testing Guide

This guide provides step-by-step instructions for testing all features of the HyEmpires plugin in-game.

## Prerequisites

Before testing, ensure:
- ✅ Plugin JAR is installed in `plugins/` folder
- ✅ Server is running Minecraft 1.21.11 or higher
- ✅ Server has restarted after plugin installation
- ✅ You have operator (OP) permissions or are in creative mode
- ✅ You have access to the server console/logs

## Quick Verification Checklist

After server startup, verify:
- [ ] Plugin loads without errors in console
- [ ] Data folder created: `plugins/HyEmpires/`
- [ ] Console shows: "HyEmpires has been enabled!"
- [ ] Console shows: "Campsite and Village management systems active!"

---

## Test 1: Plugin Loading

### Steps:
1. Start your Minecraft server
2. Check the server console/logs

### Expected Results:
```
[INFO] [HyEmpires] HyEmpires has been enabled!
[INFO] [HyEmpires] Campsite and Village management systems active!
[INFO] [HyEmpires] Build your empire, one settlement at a time!
[INFO] [HyEmpires] Loaded X villages from disk
[INFO] [HyEmpires] Loaded X campsites from disk
```

### Verification:
- ✅ No errors in console
- ✅ Plugin folder created: `plugins/HyEmpires/`
- ✅ Data files may be created (if this is first run, they'll be empty)

---

## Test 2: Village Creation and Management

### Test 2.1: Create a Village

**Steps:**
1. Find a suitable location (preferably near villagers)
2. Obtain a **Bell** block (`/give @s bell`)
3. Place the bell on the ground
4. Wait 1-2 seconds

**Expected Results:**
- ✅ Chat message: `§aVillage 'Village-X-Z' has been established!`
- ✅ Chat message: `§ePopulation: X villagers`
- ✅ File created/updated: `plugins/HyEmpires/villages.csv`
- ✅ Village data appears in CSV file

**Verify in CSV:**
Open `plugins/HyEmpires/villages.csv` and check:
- VillageName column has entry
- AdminX, AdminY, AdminZ match bell location
- Owner column has your UUID
- Population shows villager count
- Active is `true`

---

### Test 2.2: View Village Information

**Steps:**
1. Right-click the bell you just placed

**Expected Results:**
- ✅ Chat displays:
  ```
  §6=== Village Info ===
  §eName: §fVillage-X-Z
  §eOwner: §f<your-uuid>
  §ePopulation: §fX villagers
  §eLocation: §fX, Y, Z
  §eStatus: §aActive
  ```
- ✅ If you're the owner, additional message:
  ```
  §7=== Administration Options ===
  §e[Left-click] §7Abandon village
  §e[Shift+Right-click] §7Refresh population count
  ```

---

### Test 2.3: Refresh Village Population

**Steps:**
1. Ensure you're the village owner
2. Stand near the bell
3. Hold **Shift** and **Right-click** the bell

**Expected Results:**
- ✅ Chat message: `§aPopulation updated: X villagers`
- ✅ Population count in CSV file is updated
- ✅ Count reflects actual villagers within 48-block radius

**Test Variation:**
- Spawn villagers near the village: `/summon villager ~ ~ ~`
- Refresh population again
- Verify count increases

---

### Test 2.4: Village Protection

**Steps:**
1. Create a village (place a bell)
2. Have another player (or use a second account) try to:
   - Place blocks within 16 blocks of the bell
   - Break blocks within 16 blocks of the bell

**Expected Results:**
- ✅ Non-owner receives message: `§cYou cannot build near another player's village center!`
- ✅ Non-owner receives message: `§cYou cannot destroy near another player's village center!`
- ✅ Block placement/breaking is cancelled
- ✅ Owner can build/destroy freely
- ✅ OP players can build/destroy freely

---

### Test 2.5: Abandon Village

**Steps:**
1. Stand near your village bell
2. **Left-click** the bell (punch it)

**Expected Results:**
- ✅ Chat message: `§cVillage administration has been abandoned.`
- ✅ Nearby players see: `§cVillage <name> has been abandoned`
- ✅ CSV file updated: `Active` column set to `false`
- ✅ Bell can now be broken by anyone
- ✅ Protection removed (others can build/destroy)

---

### Test 2.6: Break Village Bell (Non-Owner)

**Steps:**
1. Create a village
2. Have another player try to break the bell

**Expected Results:**
- ✅ Non-owner receives: `§cYou can only break your own village admin blocks!`
- ✅ Block break is cancelled
- ✅ Bell remains intact

---

## Test 3: Campsite Creation and Management

### Test 3.1: Create a Campsite

**Steps:**
1. Find an open area with natural ground (grass, dirt, sand, etc.)
2. Obtain a **Campfire** (`/give @s campfire`)
3. Place the campfire on natural ground
4. Wait 1-2 seconds

**Expected Results:**
- ✅ Chat message: `§6New campsite established: Campsite-X-Z`
- ✅ Structures automatically generated:
  - **Tent**: White wool pyramid structure (5x5 base, gray carpet floor)
  - **Chest**: 3 blocks west of campfire
  - **Crafting Table**: 3 blocks north of campfire
  - **Additional Campfire**: 3 blocks east of campfire (if space available)
- ✅ File created/updated: `plugins/HyEmpires/campsites.csv`

**Verify Structure:**
- Check tent has pyramid shape (white wool)
- Check gray carpet floor (5x5 area)
- Verify chest and crafting table positions

---

### Test 3.2: View Campsite Information

**Steps:**
1. Right-click the campfire you placed

**Expected Results:**
- ✅ Chat displays:
  ```
  §6=== Campsite Info ===
  §eName: §fCampsite-X-Z
  §eOwner: §f<your-uuid>
  §eLocation: §fX, Y, Z
  ```
- ✅ If you're the owner:
  ```
  §a[Left-click to abandon campsite]
  ```

---

### Test 3.3: Campsite Protection

**Steps:**
1. Create a campsite
2. Have another player try to break the campfire

**Expected Results:**
- ✅ Non-owner receives: `§cYou can only break your own campsites!`
- ✅ Block break is cancelled
- ✅ Campfire remains intact
- ✅ Owner can break freely
- ✅ OP players can break freely

---

### Test 3.4: Abandon Campsite

**Steps:**
1. Stand near your campsite campfire
2. **Left-click** the campfire (punch it)

**Expected Results:**
- ✅ Chat message: `§cCampsite has been abandoned.`
- ✅ Nearby players see: `§cCampsite <name> has been abandoned`
- ✅ CSV file updated: `Active` column set to `false`
- ✅ Structures remain but campsite is inactive
- ✅ Campfire can now be broken by anyone

---

### Test 3.5: Campsite on Different Ground Types

**Steps:**
Test placing campfires on different ground types:
1. Grass Block
2. Dirt
3. Coarse Dirt
4. Podzol
5. Mycelium
6. Sand
7. Snow Block

**Expected Results:**
- ✅ All natural ground types should work
- ✅ Campsite created successfully
- ✅ Structures generated properly

**Test Invalid Placement:**
- Place campfire on stone, wood, or other non-natural blocks
- Should NOT create campsite (no message, no structures)

---

## Test 4: Villager Management

### Test 4.1: Villager Auto-Naming

**Steps:**
1. Spawn a villager: `/summon villager ~ ~ ~`
2. Wait 1-2 seconds
3. Look at the villager

**Expected Results:**
- ✅ Villager has a custom name above head (yellow text)
- ✅ Name format: `§6<FirstName>` (e.g., "Aldrich", "Beatrice")
- ✅ Name is visible from a distance
- ✅ File created/updated: `plugins/HyEmpires/villager_jobs.csv`

**Verify in CSV:**
- VillagerName column has the name
- UUID column has villager's UUID
- Status is `ALIVE`

---

### Test 4.2: Villager Profession Tracking

**Steps:**
1. Spawn a villager
2. Place a workstation block near the villager (e.g., Composter for Farmer)
3. Wait for villager to claim it (they should pathfind to it)
4. Check CSV file after 60 seconds (scan interval)

**Expected Results:**
- ✅ Villager changes profession to match workstation
- ✅ CSV file updated with profession name
- ✅ Profession column shows correct type (e.g., "minecraft:farmer")

**Test Different Professions:**
- Farmer → Composter
- Librarian → Lectern
- Smith → Smithing Table
- Armorer → Blast Furnace
- Butcher → Smoker
- etc.

---

### Test 4.3: Villager Workstation Tracking

**Steps:**
1. Place a workstation block (e.g., Composter)
2. Spawn a villager nearby
3. Wait for villager to claim workstation
4. Check CSV file after scan (60 seconds)

**Expected Results:**
- ✅ CSV file has JobsiteX, JobsiteY, JobsiteZ coordinates
- ✅ Coordinates match workstation block location
- ✅ Villager paths to workstation during work hours

---

### Test 4.4: Villager Bed Tracking

**Steps:**
1. Place a bed near a villager
2. Wait for villager to claim bed (at night or when sleeping)
3. Check CSV file after scan

**Expected Results:**
- ✅ CSV file has BedX, BedY, BedZ coordinates
- ✅ Coordinates match bed location
- ✅ Villager returns to bed at night

---

### Test 4.5: Villager Death Tracking

**Steps:**
1. Spawn a villager
2. Note the villager's UUID from CSV
3. Kill the villager: `/kill @e[type=villager,limit=1]`
4. Wait for scan (60 seconds) or check CSV immediately

**Expected Results:**
- ✅ CSV file updated: Status column changed to `DEAD`
- ✅ Villager removed from world
- ✅ Data persists in CSV for record-keeping

---

### Test 4.6: Multiple Villagers

**Steps:**
1. Spawn 5-10 villagers in an area
2. Place various workstations
3. Place beds
4. Wait for scan cycle (60 seconds)

**Expected Results:**
- ✅ All villagers get unique names
- ✅ Each villager tracked in CSV
- ✅ Each villager has workstation and bed data
- ✅ No duplicate entries
- ✅ All villagers have different UUIDs

---

## Test 5: Data File Verification

### Test 5.1: CSV File Creation

**Steps:**
1. After creating villages, campsites, and spawning villagers
2. Check `plugins/HyEmpires/` folder

**Expected Files:**
- ✅ `villages.csv` - Village data
- ✅ `campsites.csv` - Campsite data
- ✅ `villager_jobs.csv` - Villager tracking data

**File Format Check:**
- All files have headers
- Data rows are comma-separated
- Files are readable in Excel/Google Sheets

---

### Test 5.2: Data Persistence

**Steps:**
1. Create a village and campsite
2. Spawn some villagers
3. Stop the server
4. Restart the server

**Expected Results:**
- ✅ Console shows: "Loaded X villages from disk"
- ✅ Console shows: "Loaded X campsites from disk"
- ✅ Villages and campsites still exist
- ✅ Data persists across server restarts

---

### Test 5.3: CSV File Structure

**Verify `villages.csv`:**
```
VillageName,World,AdminX,AdminY,AdminZ,Owner,CreatedDate,Population,Active
Village-100-200,world,100,64,200,uuid-here,1234567890,5,true
```

**Verify `campsites.csv`:**
```
CampsiteName,World,X,Y,Z,Owner,CreatedDate,Active
Campsite-150-250,world,150,65,250,uuid-here,1234567890,true
```

**Verify `villager_jobs.csv`:**
```
VillagerName,UUID,JobsiteX,JobsiteY,JobsiteZ,Profession,BedX,BedY,BedZ,Status
Aldrich,uuid-here,105,64,205,minecraft:farmer,110,64,210,ALIVE
```

---

## Test 6: Edge Cases and Error Handling

### Test 6.1: Multiple Villages Close Together

**Steps:**
1. Place two bells more than 48 blocks apart
2. Place two bells less than 48 blocks apart

**Expected Results:**
- ✅ Bells far apart: Both villages created successfully
- ✅ Bells close together: May overlap, but both should work
- ✅ Population counts may overlap

---

### Test 6.2: Campsite Without Space

**Steps:**
1. Place campfire in a tight space (surrounded by blocks)
2. Try to create campsite

**Expected Results:**
- ✅ Campsite still created
- ✅ Structures placed where space allows
- ✅ Some structures may not generate if blocked

---

### Test 6.3: Breaking Blocks During Creation

**Steps:**
1. Place a bell
2. Immediately break it before village is created

**Expected Results:**
- ✅ Village may not be created
- ✅ No error messages
- ✅ No orphaned data

---

### Test 6.4: Server Restart During Operations

**Steps:**
1. Create villages/campsites
2. Restart server mid-operation
3. Check data integrity

**Expected Results:**
- ✅ No data corruption
- ✅ All data persists correctly
- ✅ Plugin reloads successfully

---

## Test 7: Performance Testing

### Test 7.1: Many Villages

**Steps:**
1. Create 10+ villages across the world
2. Monitor server performance

**Expected Results:**
- ✅ No lag spikes
- ✅ Server performance stable
- ✅ All villages tracked correctly

---

### Test 7.2: Many Villagers

**Steps:**
1. Spawn 50+ villagers
2. Monitor server performance
3. Check scan performance

**Expected Results:**
- ✅ Scan completes within reasonable time
- ✅ No server lag
- ✅ All villagers tracked

---

### Test 7.3: Chunk Loading/Unloading

**Steps:**
1. Create villages in different chunks
2. Travel between chunks
3. Monitor data loading

**Expected Results:**
- ✅ Villages load when chunks load
- ✅ No errors on chunk unload
- ✅ Data persists correctly

---

## Troubleshooting Common Issues

### Issue: Plugin doesn't load
**Check:**
- Server version matches (1.21.11+)
- Java version is 21+
- Check console for errors
- Verify JAR file is not corrupted

### Issue: Villages not creating
**Check:**
- Bell is placed correctly
- No errors in console
- Check CSV file permissions
- Verify you have permission to place blocks

### Issue: Villagers not getting names
**Check:**
- Wait a few seconds after spawning
- Check if villager has custom name already
- Verify plugin is enabled
- Check console for errors

### Issue: CSV files not updating
**Check:**
- File permissions (server needs write access)
- Check console for file errors
- Verify plugin data folder exists
- Check disk space

---

## Quick Test Command Reference

### Console Commands (for testing):
```bash
# Give yourself items
/give @s bell
/give @s campfire

# Spawn villagers
/summon villager ~ ~ ~
/summon villager ~ ~ ~ {Profession:0}  # Farmer
/summon villager ~ ~ ~ {Profession:1}  # Librarian

# Teleport to test locations
/tp @s 100 64 200

# Check plugin status
/plugins
```

### Expected Chat Messages:
- Village created: `§aVillage 'X' has been established!`
- Campsite created: `§6New campsite established: X`
- Village info: `§6=== Village Info ===`
- Campsite info: `§6=== Campsite Info ===`
- Protection: `§cYou cannot build near another player's village center!`

---

## Test Report Template

Use this template to document your testing:

```
Test Date: ___________
Minecraft Version: ___________
Plugin Version: ___________
Tester: ___________

[ ] Test 1: Plugin Loading - PASS/FAIL
[ ] Test 2: Village Creation - PASS/FAIL
[ ] Test 3: Campsite Creation - PASS/FAIL
[ ] Test 4: Villager Management - PASS/FAIL
[ ] Test 5: Data Files - PASS/FAIL
[ ] Test 6: Edge Cases - PASS/FAIL
[ ] Test 7: Performance - PASS/FAIL

Notes:
_______________________________________
_______________________________________
_______________________________________
```

---

## Additional Resources

- See `README.md` for feature overview
- See `RELEASING.md` for build instructions
- Check server logs: `logs/latest.log`
- Plugin data: `plugins/HyEmpires/`

---

**Happy Testing!** 🎮
