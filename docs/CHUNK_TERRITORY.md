# Chunk-Based Territory System

## Overview

HyEmpires uses a **chunk-based territory system** where villages must claim chunks to expand. Villagers can only claim job sites within claimed chunks, creating strategic territory management.

## Core Concepts

### Chunks
- Minecraft worlds are divided into **16x16 block chunks**
- Villages claim chunks, not just radius-based areas
- Each chunk must be explicitly claimed using the boundary tool

### Village Power
- **Power** determines how many chunks a village can claim
- Power = Population + (Total Influence / 10)
- Example: 10 villagers + 100 influence = 10 + 10 = 20 power
- Each power point allows claiming 1 chunk (plus 1 base chunk)

### Territory Expansion
- Villages start with **1 free chunk** (the chunk containing the bell)
- To expand, villages need **power** to claim additional chunks
- Placing a bell within existing territory expands the village (if power allows)
- Players can manually claim chunks using the boundary tool

---

## Power System

### Calculating Power
```
Power = Population + (Total Influence / 10)
```

**Examples:**
- 5 villagers + 50 influence = 5 + 5 = **10 power** → Can claim 11 chunks (1 base + 10)
- 20 villagers + 200 influence = 20 + 20 = **40 power** → Can claim 41 chunks
- 1 villager + 0 influence = 1 + 0 = **1 power** → Can claim 2 chunks

### Increasing Power
1. **Increase Population**: More villagers = more power
2. **Increase Influence**: More active players = more power
3. **Both**: Best strategy is to balance both

---

## Boundary Drawing Tool

### Getting the Tool
```
/hyempires tool
```
or
```
/hyempires boundary
```

Gives you a **Stick** with special properties.

### Using the Tool

#### Claim a Chunk
- **Right-click** any block in an unclaimed chunk
- Must be within village territory (for expansion) or at village edge
- Requires sufficient village power
- Requires influence to administer (80% of leader's influence)

#### Unclaim a Chunk
- **Left-click** any block in a claimed chunk
- Cannot unclaim the chunk containing the primary bell
- Requires influence to administer

#### View Chunk Info
- **Shift + Right-click** any block
- Shows chunk coordinates, ownership, and village power info

---

## Village Expansion Rules

### Placing Bells
1. **Outside any village**: Creates new village (claims 1 chunk)
2. **Within existing village territory**: Expands village (claims chunk if power allows)
3. **Insufficient power**: Expansion fails, bell can still be placed but chunk not claimed

### Power Requirements
- Each new chunk requires 1 power point
- Base chunk is free (the one with the bell)
- Expansion fails if village doesn't have enough power

### Example Expansion
1. Village has 10 power → Can claim 11 chunks
2. Currently has 5 chunks claimed
3. Player places bell → Tries to claim chunk
4. If power allows → Chunk claimed, village expands
5. If not → Expansion fails, player notified

---

## Villager Job Site Restrictions

### The Rule
**Villagers can ONLY claim job sites within village-claimed chunks.**

### How It Works
1. Player places workstation block (Composter, Lectern, etc.)
2. If outside village chunks → Warning message
3. Villager tries to claim job site
4. System checks if job site chunk is claimed
5. If not claimed → Job site is broken/removed
6. Villager cannot claim it

### Workstation Blocks
- Composter (Farmer)
- Lectern (Librarian)
- Smithing Table (Smith/Toolsmith)
- Fletching Table (Fletcher)
- Cartography Table (Cartographer)
- Brewing Stand (Cleric)
- Blast Furnace (Armorer)
- Smoker (Butcher)
- Cauldron (Leatherworker)
- Stonecutter (Mason)
- Loom (Shepherd)
- Grindstone (Weaponsmith)
- Barrel (Fisherman)

### Enforcement
- **Placement Warning**: Warns when placing outside territory
- **Periodic Scan**: Every 60 seconds, removes job sites outside territory
- **Career Change Event**: Breaks job sites when villagers try to claim outside territory

---

## Commands

### Get Boundary Tool
```
/hyempires tool
```

### View Village Info (Shows Power & Chunks)
```
/hyempires village info [name]
```
Shows:
- Village Power
- Claimed Chunks (X/Y Max)
- Chunk count

### View Influence Ranking
```
/hyempires village influence [name]
```
Shows players contributing to village power.

---

## Strategic Gameplay

### Early Game
- Start with 1 chunk (free)
- Focus on population growth (spawn villagers)
- Build workstations within claimed chunk
- Trade with villagers to gain influence

### Mid Game
- Expand chunks strategically
- Claim chunks with valuable resources
- Connect distant areas with additional bells
- Balance population and influence for power

### Late Game
- Large villages with many chunks
- Multiple bells connecting territory
- Strategic chunk placement for defense
- Power management becomes critical

---

## Examples

### Example 1: Starting a Village
1. Place bell at (100, 64, 200)
2. Village created, claims chunk (6, 12) automatically
3. Power: 1 (1 villager + 0 influence)
4. Can claim: 2 chunks total (1 base + 1 power)

### Example 2: Expanding with Power
1. Village has 5 villagers + 50 influence
2. Power: 5 + 5 = 10
3. Can claim: 11 chunks total
4. Currently has 3 chunks
5. Player places bell → Claims new chunk (if within range)
6. Now has 4/11 chunks claimed

### Example 3: Manual Chunk Claiming
1. Village has 10 power (can claim 11 chunks)
2. Currently has 5 chunks
3. Player uses boundary tool
4. Right-clicks block in adjacent chunk
5. Chunk claimed! (6/11 chunks)

### Example 4: Job Site Restriction
1. Player places Composter at (200, 64, 300)
2. Chunk (12, 18) is NOT claimed by any village
3. Warning: "This workstation is outside village territory!"
4. Villager tries to claim it
5. System detects it's outside territory
6. Composter is broken/removed
7. Villager cannot claim it

---

## Data Storage

### Chunk Territory File
`plugins/HyEmpires/village_chunks.csv`

**Columns:**
- `VillageName` - Village that claims the chunk
- `World` - World name
- `ChunkX` - Chunk X coordinate
- `ChunkZ` - Chunk Z coordinate

### Example Entry
```
Village-100-200,world,6,12
Village-100-200,world,7,12
Village-100-200,world,6,13
```

---

## Tips

1. **Plan Your Chunks**: Claim chunks strategically around resources
2. **Power Management**: Balance population and influence for maximum power
3. **Boundary Tool**: Always carry one for territory management
4. **Workstation Placement**: Only place workstations in claimed chunks
5. **Expansion Strategy**: Claim chunks before placing bells for expansion
6. **Chunk Efficiency**: Claim chunks that maximize villager coverage

---

## Troubleshooting

### "Not enough power to claim chunk"
- Increase village population (spawn more villagers)
- Increase influence (trade, build, complete quests)
- Check current power: `/hyempires village info`

### "Villagers not claiming workstations"
- Check if workstation is in a claimed chunk
- Use `/hyempires village info` to see claimed chunks
- Claim the chunk first using boundary tool

### "Cannot unclaim chunk"
- You cannot unclaim the chunk containing the primary bell
- Make sure you have sufficient influence to administer

---

**Remember**: Territory is power! Claim chunks strategically and manage your village's power to expand! 🗺️
