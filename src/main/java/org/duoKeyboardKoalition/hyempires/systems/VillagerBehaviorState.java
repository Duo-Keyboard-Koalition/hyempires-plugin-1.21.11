package org.duoKeyboardKoalition.hyempires.systems;

import org.bukkit.Location;
import org.bukkit.entity.Villager;

import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Finite State Machine for villager behavior.
 * Uses pathfinding to reach assigned spots; villagers make way when players step through.
 * Driven by Q-learning for action selection.
 * (Previously used for rollcall feature, currently unused but kept for future use)
 */
public class VillagerBehaviorState {
    public enum State {
        IDLE,           // Waiting to start
        APPROACHING,    // Pathfinding to assigned spot
        AT_POSITION,    // Standing at assigned spot
        STEPPED_BACK,   // Temporarily retreated to make way for player
        RETURNING       // Pathfinding back to original location
    }
    
    private final UUID villagerUUID;
    private State currentState;
    private Location assignedSpot;
    private Location retreatSpot;
    private Location originalLocation;
    private long stateStartTime;
    
    private final VillagerQLearning qLearning;
    private State prevState;
    
    // Personality traits
    private final double speed;
    private final double patience;
    private final double social;
    
    public VillagerBehaviorState(UUID villagerUUID, Location originalLocation) {
        this.villagerUUID = villagerUUID;
        this.originalLocation = originalLocation.clone();
        this.currentState = State.IDLE;
        this.prevState = State.IDLE;
        this.qLearning = new VillagerQLearning(villagerUUID);
        Random random = new Random(villagerUUID.hashCode());
        
        long hash = villagerUUID.getMostSignificantBits() ^ villagerUUID.getLeastSignificantBits();
        this.speed = 0.6 + (Math.abs(hash % 100) / 250.0);
        this.patience = Math.abs((hash >> 8) % 100) / 100.0;
        this.social = Math.abs((hash >> 16) % 100) / 100.0;
    }
    
    public void setAssignedSpot(Location spot) {
        this.assignedSpot = spot != null ? spot.clone() : null;
    }
    
    public void setRetreatSpot(Location spot) {
        this.retreatSpot = spot != null ? spot.clone() : null;
    }
    
    public Location getAssignedSpot() {
        return assignedSpot != null ? assignedSpot.clone() : null;
    }
    
    public Location getRetreatSpot() {
        return retreatSpot != null ? retreatSpot.clone() : null;
    }
    
    /**
     * Update state machine. Q-learning selects actions; FSM executes them.
     */
    public State update(Villager villager, Location bellLocation, boolean playerNearMySpot, long rollcallDurationMs) {
        long timeInState = System.currentTimeMillis() - stateStartTime;
        double distToSpot = assignedSpot != null ? villager.getLocation().distance(assignedSpot) : 99;
        
        // Observe Q-learning state
        VillagerQLearning.QState qState = VillagerQLearning.observeState(
            distToSpot, playerNearMySpot,
            currentState == State.STEPPED_BACK,
            currentState == State.RETURNING
        );
        
        Set<VillagerQLearning.QAction> valid = VillagerQLearning.getValidActions(qState);
        VillagerQLearning.QAction action = qLearning.selectAction(qState, valid);
        
        // Apply Q-learned action and detect transitions for reward
        prevState = currentState;
        
        String transitionEvent = null;
        VillagerQLearning.QState nextQState = qState;
        
        switch (currentState) {
            case IDLE:
                break;
                
            case APPROACHING:
                if (assignedSpot != null) {
                    if (distToSpot < 1.2) {
                        transitionEvent = "reached_spot";
                        nextQState = VillagerQLearning.QState.AT_SPOT;
                        transitionTo(State.AT_POSITION);
                    } else if (action == VillagerQLearning.QAction.RETURN_HOME || timeInState > 60000) {
                        nextQState = VillagerQLearning.QState.RETURNING;
                        transitionTo(State.RETURNING);
                    }
                }
                break;
                
            case AT_POSITION:
                if (playerNearMySpot) {
                    if (action == VillagerQLearning.QAction.RETREAT) {
                        transitionEvent = "retreated";
                        nextQState = VillagerQLearning.QState.RETREATED;
                        transitionTo(State.STEPPED_BACK);
                    } else {
                        transitionEvent = "player_collision";  // Chose wrong action, reward penalty
                        nextQState = VillagerQLearning.QState.RETREATED;
                        transitionTo(State.STEPPED_BACK);
                    }
                } else if (action == VillagerQLearning.QAction.RETURN_HOME 
                    || (rollcallDurationMs > 0 && timeInState > Math.min(rollcallDurationMs, 45000))) {
                    nextQState = VillagerQLearning.QState.RETURNING;
                    transitionTo(State.RETURNING);
                }
                break;
                
            case STEPPED_BACK:
                if (!playerNearMySpot && action == VillagerQLearning.QAction.GO_TO_SPOT) {
                    nextQState = VillagerQLearning.QState.AT_SPOT;
                    transitionTo(State.AT_POSITION);
                }
                break;
                
            case RETURNING:
                if (originalLocation != null) {
                    double distHome = villager.getLocation().distance(originalLocation);
                    if (distHome < 2.0 || timeInState > 90000) {
                        transitionEvent = "returned_home";
                        nextQState = VillagerQLearning.QState.FAR_FROM_SPOT;
                        transitionTo(State.IDLE);
                    }
                }
                break;
        }
        
        // Q-learning update: use transition reward or step penalty
        if (transitionEvent != null) {
            qLearning.recordTransition(qState, action, nextQState, transitionEvent);
        } else {
            VillagerQLearning.QState finalQState = VillagerQLearning.observeState(
                assignedSpot != null ? villager.getLocation().distance(assignedSpot) : 99,
                playerNearMySpot, currentState == State.STEPPED_BACK, currentState == State.RETURNING);
            qLearning.update(-0.02, finalQState);
        }
        
        return currentState;
    }
    
    public void transitionTo(State newState) {
        this.currentState = newState;
        this.stateStartTime = System.currentTimeMillis();
    }
    
    public double getSpeedMultiplier() {
        return speed;
    }
    
    public State getCurrentState() {
        return currentState;
    }
    
    public Location getOriginalLocation() {
        return originalLocation != null ? originalLocation.clone() : null;
    }
    
    public UUID getVillagerUUID() {
        return villagerUUID;
    }
    
    public double getSocial() { return social; }
    public double getPrecision() { return 0.8; }
    public double getCuriosity() { return 0.5; }
    public int getFormationPreference() { return 0; }
    
    public VillagerQLearning getQLearning() { return qLearning; }
}
