# HyEmpires

An Age of Empires-like Minecraft plugin that allows you to manage villagers, establish villages and campsites, and build your own empire!

## ?? Table of Contents

- [Feature Overview](#feature-overview)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Villager Management](#villager-management)
- [Village Management](#village-management)
- [Campsite Management](#campsite-management)
- [Data Storage](#data-storage)
- [Technical Details](#technical-details)
- [FAQ](#faq)

## ?? Feature Overview

HyEmpires is an empire-building plugin that provides the following core features:

- **Villager Management**: Automatically track and manage each villager's workstation, bed location, and profession
- **Village System**: Establish and manage villages by placing village administration blocks
- **Campsite System**: Quickly establish temporary campsites with auto-generated structures
- **Automatic Scanning**: The plugin automatically scans and updates villager status
- **Data Persistence**: All data is saved in CSV files for easy management and backup

## ?? Installation

### Requirements

- Minecraft 1.21.11 or higher
- Spigot/Paper server
- Java 21 or higher

### Installation Steps

1. Download the plugin JAR file
2. Place `hyempires-1.0-SNAPSHOT.jar` into your server's `plugins` folder
3. Restart the server
4. The plugin will automatically create the data folder `plugins/HyEmpires/`

## ?? Quick Start

### 1. Establish Your First Village

1. Find a suitable location (preferably near villagers)
2. Place a **Bell** block
3. The village will be automatically created and display:
   - Village name
   - Current population count
   - Village location information

### 2. Establish Your First Campsite

1. Find an open area
2. Place a **Campfire** on grass or dirt
3. The campsite will be automatically created and generate:
   - Tent structure (wool and carpet)
   - Campfire
   - Chest (for storage)
   - Crafting table

## ?? Villager Management

The core feature of HyEmpires is **fine-grained management of each villager**. The plugin automatically tracks and manages all aspects of villagers.

### Automatic Villager Tracking

The plugin will:
- **Auto-naming**: Assign each villager a unique name (e.g., "Aldrich", "Beatrice", etc.)
- **Profession Tracking**: Record each villager's profession (Farmer, Smith, Librarian, etc.)
- **Workstation Tracking**: Track each villager's workstation location (X, Y, Z coordinates)
- **Bed Tracking**: Track each villager's bed location
- **Status Monitoring**: Monitor villager survival status

### Viewing Villager Data

All villager data is saved in `plugins/HyEmpires/villager_jobs.csv` file, containing the following information:

| Column | Description |
|--------|-------------|
| VillagerName | The villager's name |
| UUID | Unique identifier for the villager |
| JobsiteX/Y/Z | Coordinates of the workstation |
| Profession | Profession type |
| BedX/Y/Z | Coordinates of the bed |
| Status | Status (ALIVE/DEAD) |

### Managing Villager Work and Sleep

#### Setting Villager Workstations

1. **Place Workstation Blocks**:
   - Farmer ? Composter
   - Smith ? Smithing Table
   - Librarian ? Lectern
   - Fletcher ? Fletching Table
   - Cartographer ? Cartography Table
   - Cleric ? Brewing Stand
   - Armorer ? Blast Furnace
   - Butcher ? Smoker
   - Leatherworker ? Cauldron
   - Mason ? Stonecutter
   - Shepherd ? Loom
   - Toolsmith ? Smithing Table
   - Weaponsmith ? Grindstone
   - Fisherman ? Barrel
   - And more...

2. **Villagers Will Auto-Claim**:
   - Villagers will automatically find and claim the nearest workstation
   - The plugin will record the workstation location

3. **View Workstations**:
   - Open the `villager_jobs.csv` file to view each villager's workstation coordinates
   - Or use the plugin's API (if implemented)

#### Setting Villager Beds

1. **Place Beds**:
   - Place beds within the village area
   - Villagers will automatically claim beds

2. **Bed Management**:
   - Each villager can only claim one bed
   - The plugin tracks bed locations
   - Villagers will automatically return to their beds at night

### Villager Management Best Practices

1. **Plan Your Layout**:
   - Assign one workstation and one bed per villager
   - Keep a reasonable distance between workstations and beds (recommended 10-20 blocks)

2. **Profession Assignment**:
   - Assign villager professions based on village needs
   - Use workstation blocks to control villager professions

3. **Monitor Status**:
   - Regularly check the `villager_jobs.csv` file
   - Pay attention to villager survival status

4. **Villager Naming**:
   - The plugin automatically names villagers
   - Villager names are displayed above their heads (in yellow)

## ??? Village Management

### Creating a Village

1. **Place Village Administration Block**:
   - Place a **Bell** at the location where you want to establish a village
   - The village will be automatically created, covering a 48-block radius

2. **Village Information**:
   - Village name (auto-generated or custom)
   - Owner (player who placed the bell)
   - Population count (number of villagers within 48 blocks)
   - Creation date

### Village Operations

#### View Village Information

- **Right-click the Bell**: Display detailed village information
  - Village name
  - Owner
  - Population count
  - Location coordinates
  - Status (Active/Abandoned)

#### Administration Options (Owner Only)

- **Shift + Right-click the Bell**: Refresh population statistics
- **Left-click the Bell**: Abandon village administration

#### Village Protection

- The village center (16-block radius) is protected
- Only the village owner or OP can build/destroy within this area
- Outside the protection radius, building is free

### Village Range

- **Administration Range**: 48-block radius (from the bell location)
- **Protection Range**: 16-block radius (from the bell location)
- Villagers within this range will be counted in the village population

## ??? Campsite Management

### Creating a Campsite

1. **Place Campsite Block**:
   - Place a **Campfire** on grass, dirt, or other natural ground blocks
   - The campsite will be automatically created

2. **Auto-Generated Structures**:
   - **Tent**: A pyramid-shaped structure made of white wool and gray carpet
     - Floor: 5x5 gray carpet area
     - Walls and roof: Pyramid structure using white wool blocks
   - **Campfire**: Already placed (the campsite marker)
   - **Chest**: Storage container placed 3 blocks west of the campfire
   - **Crafting Table**: Workbench placed 3 blocks north of the campfire

### Campsite Operations

#### View Campsite Information

- **Right-click the Campfire**: Display campsite information
  - Campsite name
  - Owner
  - Location coordinates
  - Creation date

#### Abandon Campsite

- **Left-click the Campfire** (owner only): Abandon the campsite
  - This will mark the campsite as inactive
  - Structures will remain but the campsite will no longer be managed

### Campsite Structure Details

When you create a campsite, the following structures are automatically generated:

- **Tent Structure**:
  - Base: 5x5 gray carpet floor at Y+1
  - Walls: Pyramid structure starting at Y+2, decreasing in size each level
  - Material: White wool blocks forming the tent walls
  - Total height: 3 blocks above the floor

- **Campfire**: 
  - Located 3 blocks east of the campsite center
  - Provides light and can be used for cooking

- **Chest**: 
  - Located 3 blocks west of the campsite center
  - Provides storage space for supplies

- **Crafting Table**: 
  - Located 3 blocks north of the campsite center
  - Allows crafting of items

### Campsite Uses

- **Quick Base Establishment**: Rapidly set up a temporary base
- **Exploration Support**: Provide infrastructure for exploration
- **Village Outpost**: Serve as an outpost for your village
- **Resource Collection Point**: Use as a staging area for resource gathering

### Campsite Protection

- Only the campsite owner or OP can break the campfire block
- Other players cannot destroy your campsite marker
- Structures around the campsite are not protected (only the campfire itself)

### Campsite Data

All campsite data is stored in `plugins/HyEmpires/campsites.csv` with the following fields:

| Column | Description |
|--------|-------------|
| CampsiteName | Name of the campsite |
| World | World name |
| X, Y, Z | Coordinates |
| Owner | Owner UUID |
| CreatedDate | Creation timestamp |
| Active | Whether the campsite is active |

## ?? Data Storage

The plugin uses CSV file format for data storage, making it easy to view and edit:

### Data File Locations

All data files are saved in the `plugins/HyEmpires/` directory:

- `villager_jobs.csv` - Villager data
- `villages.csv` - Village data
- `campsites.csv` - Campsite data

### Data Format

All CSV files include headers and can be directly opened with Excel, Google Sheets, or other tools for viewing and editing.

### Data Backup

It is recommended to regularly backup the `plugins/HyEmpires/` folder to prevent data loss.

## ?? Technical Details

### Automatic Scanning Mechanism

The plugin periodically scans:
- **Villager Scan**: Scans all villagers every 60 seconds
- **Chunk Scan**: Scans loaded chunks when the server starts
- **Event Listening**: Real-time listening for villager profession changes, deaths, etc.

### Performance Optimization

- Scanning tasks are executed asynchronously, not affecting server performance
- Data updates use incremental save mechanism
- Files are only written when data changes

### API Access

The plugin provides the following managers for use by other plugins:

```java
HyEmpiresPlugin plugin = (HyEmpiresPlugin) Bukkit.getPluginManager().getPlugin("HyEmpires");

// Get managers
VillageManager villageManager = plugin.getVillageManager();
CampsiteManager campsiteManager = plugin.getCampsiteManager();
VillagerJobScanner villagerScanner = plugin.getVillagerJobScanner();
```

## ?? FAQ

### Q: What if villagers don't claim workstations?

A: Make sure:
1. The workstation block is within the villager's detection range (usually 48 blocks)
2. The villager has a clear path to reach the workstation
3. The workstation is not already claimed by another villager

### Q: How do I change a villager's profession?

A: Break the villager's current workstation, then place a new workstation block. The villager will automatically change professions.

### Q: Village population statistics are inaccurate?

A: Use Shift + Right-click on the bell to refresh population statistics.

### Q: Can I customize village names?

A: Currently, village names are auto-generated. You can modify names by editing the `villages.csv` file.

### Q: Will the plugin affect server performance?

A: The plugin is optimized, with scanning tasks executed asynchronously, having minimal impact on server performance.

### Q: How do I remove a campsite?

A: Left-click the campfire block (if you're the owner) to abandon the campsite. The structures will remain but the campsite will be marked as inactive.

### Q: Can multiple players share a campsite?

A: Currently, campsites are owned by a single player. Only the owner can abandon the campsite. However, other players can use the structures (chest, crafting table, etc.).

### Q: What happens if I break the campfire?

A: If you break the campfire block, the campsite will be marked as abandoned. The tent and other structures will remain but won't be managed by the plugin anymore.

## ?? Future Plans

- [ ] Add command system (/village, /campsite, /villager)
- [ ] Add GUI interface for villager management
- [ ] Support custom village names
- [ ] Add villager trading management
- [ ] Add village upgrade system
- [ ] Add villager task system
- [ ] Add campsite expansion features
- [ ] Add multi-player campsite sharing

## ?? License

This project uses the MIT License.

## ?? Contributing

Issues and Pull Requests are welcome!

---

**Enjoy building your Minecraft empire!** ??
