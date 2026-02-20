package org.duoKeyboardKoalition.hyempires;

/**
 * The Feudal Villager Hierarchy: classification by Bed and Workstation.
 * Only VASSAL counts as village population (bed + workstation in village).
 */
public enum FeudalVillagerType {
    /** [NO Bed | NO Workstation] – Dark Age unit. Wanderer, no housing, no resources. */
    SCOUT("Scout", "§7Wanderer – no bed, no workstation"),
    /** [NO Bed | YES Workstation] – Migrant worker/serf. Has job, no permanent home. */
    LABORER("Laborer", "§eMigrant worker – workstation, no bed"),
    /** [YES Bed | NO Workstation] – Settled but idle. Takes population capacity, no job yet. */
    PEASANT("Peasant", "§6Settled idle – bed, no workstation"),
    /** [YES Bed | YES Workstation] – Full member of the fiefdom. Counts as village population. */
    VASSAL("Vassal", "§aVassal – bed + workstation in village");

    private final String displayName;
    private final String description;

    FeudalVillagerType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Classify from presence of bed and workstation (no village check).
     */
    public static FeudalVillagerType from(boolean hasBed, boolean hasWorkstation) {
        if (hasBed && hasWorkstation) return VASSAL;
        if (hasBed) return PEASANT;
        if (hasWorkstation) return LABORER;
        return SCOUT;
    }
}
