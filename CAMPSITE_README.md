# HyEmpires Plugin - Campsite & Village Management

## Overview

This plugin adds Age of Empires-style settlement mechanics to Minecraft, allowing players to establish campsites and administer villages.

## Features

### 🏕️ Campsite System

**Creating a Campsite:**
- Place a **Campfire** on natural ground (grass, dirt, sand, snow, etc.)
- A campsite will be automatically registered
- The plugin will spawn camp structures around the campfire:
  - Tent (white wool structure)
  - Gray carpet flooring
  - Storage chest
  - Crafting table
  - Additional campfire

**Campsite Commands:**
- **Right-click** campfire: View campsite information
- **Left-click** campfire (owner only): Abandon the campsite
- **Break** campfire (owner only): Remove the campsite

**Campsite Protection:**
- Only the owner or OPs can break campsite blocks
- Campsites are saved to `campsites.csv`

### 🏰 Village Administration System

**Creating a Village:**
- Place a **Bell** block to serve as the Village Admin Block
- The village will be automatically registered with you as the owner
- The system tracks villager population within a 48-block radius

**Village Administration:**
- **Right-click** bell: View village information and statistics
- **Left-click** bell (owner only): Abandon village administration
- **Shift + Right-click** bell (owner only): Refresh population count

**Village Protection:**
- Non-owners cannot build or destroy within 16 blocks of the village center
- Only the owner or OPs can break the village bell
- Villages are saved to `villages.csv`

## Data Files

The plugin creates the following CSV files in the plugin data folder:

- `campsites.csv` - Stores all campsite locations and ownership
- `villages.csv` - Stores all village admin blocks and ownership
- `multiblocks.csv` - Stores multiblock structure locations (existing feature)
- `villager_jobs.csv` - Stores villager profession data (existing feature)

## Technical Details

### Campsite Block
- **Block Type:** Campfire
- **Placement:** Must be placed on natural ground (grass, dirt, sand, snow, podzol, mycelium, coarse dirt)
- **Structure Spawn:** Automatic tent and camp setup on placement

### Village Admin Block
- **Block Type:** Bell
- **Placement:** Can be placed anywhere
- **Village Radius:** 48 blocks for population counting
- **Protection Radius:** 16 blocks around the admin block

## Permissions

Currently, the plugin uses vanilla Minecraft permissions:
- Players can create their own campsites and villages
- OPs can manage any campsite or village
- Protection prevents non-owners from interfering

## Future Enhancements

Potential features for future versions:
- Custom crafting recipes for campsite/village blocks
- Village upgrade system
- Resource gathering mechanics
- Army recruitment from campsites
- Trade routes between villages
- Siege mechanics
