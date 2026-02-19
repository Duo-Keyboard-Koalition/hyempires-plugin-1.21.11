package org.duoKeyboardKoalition.hyempires.systems;

import org.bukkit.Location;
import org.bukkit.entity.Villager;

import java.util.Random;
import java.util.UUID;

/**
 * Finite State Machine for villager behavior during rollcall.
 * Each villager has unique behavior patterns based on artificial life principles.
 */
public class VillagerBehaviorState {
    public enum State {
        IDLE,           // Waiting
        APPROACHING,    // Moving towards bell
        ARRIVED,        // Reached bell area
        CIRCLE,         // Circling around bell
        FORMATION,      // Forming a pattern
        RETURNING       // Going back to original location
    }
    
    private final UUID villagerUUID;
    private State currentState;
    private Location targetLocation;
    private Location originalLocation;
    private long stateStartTime;
    private final Random random;
    
    // Personality traits (artificial life parameters)
    private final double speed;           // Movement speed multiplier (0.5 - 1.5)
    private final double patience;        // How long to wait before moving (0.0 - 1.0)
    private final double curiosity;       // Tendency to explore (0.0 - 1.0)
    private final double social;          // Tendency to group with others (0.0 - 1.0)
    private final double precision;      // How accurately to reach target (0.0 - 1.0)
    private final int formationPreference; // Preferred formation position
    
    public VillagerBehaviorState(UUID villagerUUID, Location originalLocation) {
        this.villagerUUID = villagerUUID;
        this.originalLocation = originalLocation.clone();
        this.currentState = State.IDLE;
        this.random = new Random(villagerUUID.hashCode()); // Deterministic randomness based on UUID
        
        // Generate unique personality traits based on UUID hash
        long hash = villagerUUID.getMostSignificantBits() ^ villagerUUID.getLeastSignificantBits();
        this.speed = 0.5 + (Math.abs(hash % 100) / 100.0); // 0.5 to 1.5
        this.patience = Math.abs((hash >> 8) % 100) / 100.0; // 0.0 to 1.0
        this.curiosity = Math.abs((hash >> 16) % 100) / 100.0;
        this.social = Math.abs((hash >> 24) % 100) / 100.0;
        this.precision = Math.abs((hash >> 32) % 100) / 100.0;
        this.formationPreference = (int) (Math.abs(hash >> 40) % 8); // 0-7 positions
    }
    
    /**
     * Update state machine based on current conditions.
     */
    public State update(Villager villager, Location bellLocation, int villagersInFormation) {
        long currentTime = System.currentTimeMillis();
        long timeInState = currentTime - stateStartTime;
        
        switch (currentState) {
            case IDLE:
                // Wait based on patience trait
                if (timeInState > (1000 + patience * 2000)) {
                    transitionTo(State.APPROACHING, bellLocation);
                }
                break;
                
            case APPROACHING:
                double distance = villager.getLocation().distance(targetLocation);
                if (distance < 3.0 + (precision * 2.0)) {
                    // Arrived, decide next action based on personality
                    if (social > 0.6 && villagersInFormation < 8) {
                        transitionTo(State.FORMATION, bellLocation);
                    } else if (curiosity > 0.5) {
                        transitionTo(State.CIRCLE, bellLocation);
                    } else {
                        transitionTo(State.ARRIVED, bellLocation);
                    }
                } else if (timeInState > 30000) {
                    // Took too long, give up
                    transitionTo(State.RETURNING, originalLocation);
                }
                break;
                
            case ARRIVED:
                // Stay at bell for a while, then return
                if (timeInState > (3000 + patience * 5000)) {
                    transitionTo(State.RETURNING, originalLocation);
                }
                break;
                
            case CIRCLE:
                // Circle around bell based on curiosity
                if (timeInState > (5000 + curiosity * 10000)) {
                    if (social > 0.4 && villagersInFormation < 8) {
                        transitionTo(State.FORMATION, bellLocation);
                    } else {
                        transitionTo(State.RETURNING, originalLocation);
                    }
                }
                break;
                
            case FORMATION:
                // Stay in formation
                if (timeInState > (10000 + social * 15000)) {
                    transitionTo(State.RETURNING, originalLocation);
                }
                break;
                
            case RETURNING:
                // Check if returned to original location
                double returnDistance = villager.getLocation().distance(originalLocation);
                if (returnDistance < 2.0) {
                    transitionTo(State.IDLE, null);
                } else if (timeInState > 30000) {
                    // Force stop if taking too long
                    transitionTo(State.IDLE, null);
                }
                break;
        }
        
        return currentState;
    }
    
    /**
     * Transition to a new state.
     */
    private void transitionTo(State newState, Location target) {
        this.currentState = newState;
        this.targetLocation = target != null ? target.clone() : null;
        this.stateStartTime = System.currentTimeMillis();
    }
    
    /**
     * Get movement speed multiplier for this villager.
     */
    public double getSpeedMultiplier() {
        return speed;
    }
    
    /**
     * Get target location for current state.
     */
    public Location getTargetLocation() {
        return targetLocation != null ? targetLocation.clone() : null;
    }
    
    /**
     * Get current state.
     */
    public State getCurrentState() {
        return currentState;
    }
    
    /**
     * Get formation position preference.
     */
    public int getFormationPreference() {
        return formationPreference;
    }
    
    /**
     * Get social trait (for grouping behavior).
     */
    public double getSocial() {
        return social;
    }
    
    /**
     * Get precision trait (for positioning accuracy).
     */
    public double getPrecision() {
        return precision;
    }
    
    /**
     * Get curiosity trait (for exploration behavior).
     */
    public double getCuriosity() {
        return curiosity;
    }
    
    /**
     * Get villager UUID.
     */
    public UUID getVillagerUUID() {
        return villagerUUID;
    }
    
    /**
     * Check if villager should return to original location.
     */
    public boolean shouldReturn() {
        return currentState == State.RETURNING;
    }
    
    /**
     * Get original location.
     */
    public Location getOriginalLocation() {
        return originalLocation != null ? originalLocation.clone() : null;
    }
}
