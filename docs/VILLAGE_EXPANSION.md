# Village Expansion System

## Overview

Villages in HyEmpires can **expand organically** by placing additional bells within their territory. Instead of creating a new village, placing a bell within an existing village's territory will **expand and connect** to that village.

## How It Works

### Creating a New Village
- Place a **Bell** block in an area **not** within any existing village's territory
- A new village is created with a 48-block radius
- You become the founder with 100 influence points

### Expanding an Existing Village
- Place a **Bell** block **within** an existing village's territory (within effective radius)
- The village **expands** instead of creating a new one
- The new bell becomes an **additional bell** for the village
- The village's **effective radius** increases to include the new bell
- You gain **+10 influence** for expanding the village

## Expansion Mechanics

### Effective Radius
- Starts at **48 blocks** from the primary bell
- Expands automatically when new bells are added
- New radius = distance from primary bell to new bell + 48 blocks
- Ensures all bells are connected within the village territory

### Additional Bells
- Each village can have **multiple bells**
- Primary bell: The original bell that created the village
- Additional bells: Bells placed within the village territory
- All bells function identically (right-click for info, etc.)
- Population counts villagers around **all bells** (no double counting)

### Territory Calculation
- Village territory includes:
  - Area within `effectiveRadius` of the primary bell
  - Area within 48 blocks of each additional bell
- Overlapping areas are part of the same village
- New bells placed in overlapping territory expand the village

## Examples

### Example 1: Simple Expansion
1. Player A places Bell #1 at (100, 64, 200) → Creates "Village-100-200"
2. Village territory: 48-block radius around (100, 64, 200)
3. Player B places Bell #2 at (120, 64, 210) → Within territory!
4. Village expands:
   - Bell #2 becomes additional bell
   - Effective radius expands to ~58 blocks (distance + 48)
   - Player B gains +10 influence
   - Population recalculated around both bells

### Example 2: Chain Expansion
1. Bell #1 at (0, 64, 0) → Village created
2. Bell #2 at (40, 64, 0) → Expands village (within 48 blocks)
3. Bell #3 at (80, 64, 0) → Expands village (within 48 blocks of Bell #2)
4. Result: One large village spanning from (0,0) to (80,0)

### Example 3: Multiple Players Expanding
1. Player A creates village with Bell #1
2. Player B places Bell #2 → Expands village, gains influence
3. Player C places Bell #3 → Expands village, gains influence
4. All three players have influence in the same village
5. Leadership determined by total influence

## Benefits of Expansion

### For Players
- **Gain Influence**: +10 influence per expansion
- **Connect Areas**: Link distant parts of your settlement
- **Increase Population**: More area = more villagers counted
- **Collaborative Building**: Multiple players can expand the same village

### For Villages
- **Larger Territory**: More space for buildings and villagers
- **Better Coverage**: Multiple bells provide more admin points
- **Organic Growth**: Villages grow naturally as players build

## Commands

### View Village Info
```
/hyempires village info [name]
```
Shows:
- Primary bell location
- Effective radius
- Number of additional bells
- Population (counted around all bells)

### Refresh Population
```
/hyempires village refresh [name]
```
Recalculates population around all bells (primary + additional).

## Technical Details

### Data Storage
- Additional bells stored in CSV: `x1,y1,z1;x2,y2,z2;...`
- Effective radius stored per village
- Backward compatible with old village data (defaults to 48 radius)

### Population Counting
- Uses a Set to avoid double-counting villagers
- Counts around primary bell (effective radius)
- Counts around each additional bell (48-block radius each)
- Total = unique villagers found

### Territory Checks
- `getVillageContaining()` checks all bells
- `getVillageAt()` checks all bells
- Expansion checks happen before creation

## Tips

1. **Plan Your Expansion**: Place bells strategically to maximize coverage
2. **Gain Influence**: Expanding villages is a great way to gain influence
3. **Connect Settlements**: Use bells to connect distant parts of your build
4. **Collaborate**: Multiple players can expand the same village
5. **Check Territory**: Use `/hyempires village info` to see current radius

## Limitations

- Bells cannot be placed at the exact same location (prevents duplicates)
- Maximum effective radius is not capped (but very large villages may impact performance)
- Breaking a bell removes it from the village (if it's an additional bell, village shrinks)

---

**Remember**: Villages grow organically! Place bells strategically to expand your territory and gain influence! 🏰
