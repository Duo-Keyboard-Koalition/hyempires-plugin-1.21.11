# Quick Test Checklist

A quick reference for testing HyEmpires plugin features.

## Pre-Flight Check ✅

- [ ] Server running Minecraft 1.21.11+
- [ ] Plugin JAR in `plugins/` folder
- [ ] Server restarted
- [ ] Console shows: "HyEmpires has been enabled!"
- [ ] Data folder exists: `plugins/HyEmpires/`

---

## Village Testing (5 minutes)

### Basic Village Test
- [ ] Place bell → Get success message
- [ ] Right-click bell → See village info
- [ ] Check CSV file → Village data present
- [ ] Shift+Right-click bell → Population refreshes
- [ ] Left-click bell → Village abandoned

### Protection Test
- [ ] Non-owner tries to build near bell → Blocked
- [ ] Non-owner tries to break bell → Blocked
- [ ] Owner can build/break freely → Works

---

## Campsite Testing (5 minutes)

### Basic Campsite Test
- [ ] Place campfire on grass → Get success message
- [ ] Structures generated (tent, chest, crafting table)
- [ ] Right-click campfire → See campsite info
- [ ] Check CSV file → Campsite data present
- [ ] Left-click campfire → Campsite abandoned

### Protection Test
- [ ] Non-owner tries to break campfire → Blocked
- [ ] Owner can break freely → Works

---

## Villager Testing (5 minutes)

### Auto-Naming Test
- [ ] Spawn villager → Gets name automatically
- [ ] Name visible above head (yellow)
- [ ] Check CSV → Villager data present

### Profession Test
- [ ] Place composter → Villager becomes farmer
- [ ] Place lectern → Villager becomes librarian
- [ ] Check CSV after 60s → Profession updated

### Bed Test
- [ ] Place bed → Villager claims it
- [ ] Check CSV after 60s → Bed coordinates present

---

## Data File Verification (2 minutes)

- [ ] `villages.csv` exists and has data
- [ ] `campsites.csv` exists and has data
- [ ] `villager_jobs.csv` exists and has data
- [ ] Files readable in Excel/Sheets
- [ ] Data persists after server restart

---

## Quick Commands for Testing

```bash
# Get items
/give @s bell
/give @s campfire
/give @s composter
/give @s lectern

# Spawn villagers
/summon villager ~ ~ ~

# Teleport
/tp @s 100 64 200

# Check plugins
/plugins
```

---

## Expected Chat Messages

✅ **Success Messages:**
- `§aVillage 'X' has been established!`
- `§6New campsite established: X`
- `§aPopulation updated: X villagers`

❌ **Error Messages:**
- `§cYou cannot build near another player's village center!`
- `§cYou can only break your own village admin blocks!`
- `§cYou can only break your own campsites!`

ℹ️ **Info Messages:**
- `§6=== Village Info ===`
- `§6=== Campsite Info ===`

---

## Test Duration

- **Quick Test:** 15 minutes (all basic features)
- **Full Test:** 30-45 minutes (includes edge cases)
- **Performance Test:** 1+ hour (stress testing)

---

## Pass/Fail Criteria

### ✅ Plugin Works If:
- All basic interactions work
- Data files created and updated
- No console errors
- Protection works correctly
- Villagers tracked properly

### ❌ Plugin Fails If:
- Console errors on startup
- Interactions don't work
- Data files not created
- Crashes or lag issues
- Protection not working

---

**See `testing-guide.md` for detailed procedures**
