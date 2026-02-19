# HyEmpires Influence System

## Overview

HyEmpires uses a **dynamic influence system** where village ownership is temporary and earned through active participation. This creates a sandbox-like experience where players must actively maintain their influence or risk losing leadership to more active players.

## Core Concepts

### Influence Points
- Players earn **influence points** by participating in village activities
- Influence determines who can administer a village
- Influence **decays over time** when players are inactive
- Multiple players can have influence in the same village

### Founder Status
- The player who creates a village becomes the **founder**
- Founders start with 100 influence points
- If a founder is inactive for **7 days**, leadership can be usurped
- The player with the highest influence becomes the new founder

### Leadership
- The player with the **highest influence** is the current leader
- Leaders can administer the village (refresh population, etc.)
- Players within **80% of the leader's influence** can also administer
- Leadership can change as influence shifts

---

## Gaining Influence

### Villager Trades
- **+2.0 influence** per trade
- Must trade with villagers within the village (48-block radius)
- Updates your activity timestamp

### Building
- **+0.5 influence** per block placed near village center
- Only applies within 16 blocks of the bell
- Must have sufficient influence to build (creates a feedback loop)

### Future: Quests
- Completing village quests will grant influence (planned feature)
- Different quest types will grant different amounts

### Future: Other Activities
- Protecting villagers from raids
- Improving village infrastructure
- Recruiting new villagers

---

## Losing Influence

### Decay System
- Influence decays at **0.5 points per hour** of inactivity
- Decay only applies when you haven't been active
- Minimum influence is **0.0** (can't go negative)
- Maximum influence is **1000.0**

### Example Decay
- If you have 100 influence and are inactive for 10 hours: **100 - (10 × 0.5) = 95 influence**
- If you're inactive for 7 days: **100 - (168 × 0.5) = -16 → 0 influence** (minimum)

---

## Usurpation

### When Founder Disappears
- If the founder is inactive for **7 days**, the village becomes open for usurpation
- The player with the highest influence automatically becomes the new founder
- Old founder loses founder status but keeps their influence

### Becoming Leader
- Work to gain influence through trades, building, and quests
- Maintain activity to prevent decay
- Once you exceed the current leader's influence, you become the new leader
- You can administer the village once you reach 80% of the leader's influence

---

## Commands

### View Your Influence
```
/hyempires village info [name]
```
Shows your influence in the village and the current leader.

### View Influence Ranking
```
/hyempires village influence [name]
```
Shows the top 10 players by influence in a village.

### Refresh Population
```
/hyempires village refresh [name]
```
Requires sufficient influence (80% of leader's) or OP.

---

## Protection System

### Village Center Protection
- Only players with sufficient influence can build/destroy within 16 blocks of the bell
- Must have at least 80% of the leader's influence
- OP players bypass this restriction

### Building Near Center
- If you have sufficient influence, you can build
- Building grants +0.5 influence (reward for contributing)
- Creates a positive feedback loop for active players

---

## Data Storage

### Influence Data File
`plugins/HyEmpires/village_influence.csv`

**Columns:**
- `VillageName` - Name of the village
- `PlayerUUID` - Player's unique ID
- `Influence` - Current influence points
- `LastActivity` - Timestamp of last activity
- `IsFounder` - Whether player is the founder

### Example Entry
```
Village-100-200,550e8400-e29b-41d4-a716-446655440000,150.5,1708123456789,true
```

---

## Examples

### Scenario 1: New Player Joins Village
1. Player A creates village → 100 influence (founder)
2. Player B trades with villagers → gains 2 influence per trade
3. Player B builds near center → gains 0.5 influence per block
4. After 20 trades + 10 blocks: Player B has ~50 influence
5. Player B can now build near center (50 > 100 × 0.8 = 80? No, but close)
6. Player B continues trading → reaches 80+ influence → can administer

### Scenario 2: Founder Goes Inactive
1. Player A (founder) has 100 influence
2. Player A stops playing for 7 days
3. Influence decays: 100 - (168 × 0.5) = 16 influence
4. Player B has been active, now has 120 influence
5. System detects founder inactive → Player B becomes new founder
6. Player A's influence stays at 16 (not reset)

### Scenario 3: Competing for Leadership
1. Player A (founder): 100 influence
2. Player B: 90 influence (active trader)
3. Player C: 85 influence (active builder)
4. All three can administer (all > 100 × 0.8 = 80)
5. Player A goes inactive → influence decays
6. Player B and C compete → highest becomes new leader

---

## Configuration

### Current Settings
- **Inactivity Threshold**: 7 days (7 × 24 × 60 × 60 × 1000 ms)
- **Decay Rate**: 0.5 points per hour
- **Min Influence**: 0.0
- **Max Influence**: 1000.0
- **Admin Threshold**: 80% of leader's influence

### Trade Rewards
- **Villager Trade**: +2.0 influence

### Building Rewards
- **Block Placed**: +0.5 influence (near village center only)

---

## Tips for Players

1. **Stay Active**: Regular activity prevents decay
2. **Trade Often**: Villager trades are the easiest way to gain influence
3. **Build Strategically**: Building near the center grants influence
4. **Monitor Competition**: Check `/hyempires village influence` regularly
5. **Don't Worry About Abandoning**: You can't abandon villages - just let influence decay naturally

---

## For Server Admins

### Removing Villages
Only OP players can remove villages:
- Break the bell block (as OP)
- Village is removed from system
- All influence data is cleared

### Monitoring
- Check `village_influence.csv` for influence data
- Check server logs for usurpation messages
- Use `/hyempires reload` to rescan data

---

## Future Enhancements

Planned features:
- Quest system integration
- Raid protection rewards
- Villager recruitment bonuses
- Infrastructure improvement rewards
- Influence multipliers for certain activities
- Configurable decay rates per server

---

**Remember**: In HyEmpires, ownership is temporary. The sandbox belongs to those who actively participate! 🏰
