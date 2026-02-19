package org.duoKeyboardKoalition.hyempires.systems;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Q-Learning agent for villager decision-making during rollcall and similar contexts.
 * Learns optimal actions through reward-based updates.
 * 
 * Q(s,a) := Q(s,a) + α * (r + γ * max_a' Q(s',a') - Q(s,a))
 */
public class VillagerQLearning {
    
    public enum QState {
        FAR_FROM_SPOT,      // Distance to assigned spot > 4
        APPROACHING_SPOT,   // 1.5 < distance <= 4
        AT_SPOT,           // At assigned spot, no player
        PLAYER_BLOCKING,    // At spot, player too close
        RETREATED,         // Moved away for player
        RETURNING          // Pathfinding home
    }
    
    public enum QAction {
        GO_TO_SPOT,        // Pathfind to assigned spot
        RETREAT,           // Move away from player
        STAY,              // Do nothing (already at spot)
        RETURN_HOME        // Pathfind to original location
    }
    
    private final UUID villagerUUID;
    private final Random random;
    
    // Q-table: Q[state][action] = expected cumulative reward
    private final Map<QState, Map<QAction, Double>> qTable;
    
    // Hyperparameters
    private static final double ALPHA = 0.2;   // Learning rate
    private static final double GAMMA = 0.9;   // Discount factor
    private static final double EPSILON_INIT = 0.3;  // Exploration rate (decays over time)
    private static final double EPSILON_MIN = 0.05;
    private static final double EPSILON_DECAY = 0.9995;
    
    private double epsilon = EPSILON_INIT;
    private QState lastState;
    private QAction lastAction;
    
    // Rewards
    private static final double R_REACHED_SPOT = 10.0;
    private static final double R_RETREATED = 5.0;
    private static final double R_PLAYER_COLLISION = -5.0;
    private static final double R_RETURNED_HOME = 3.0;
    private static final double R_STEP = -0.02;  // Small cost per step (encourage efficiency)
    
    public VillagerQLearning(UUID villagerUUID) {
        this.villagerUUID = villagerUUID;
        this.random = new Random(villagerUUID.hashCode());
        this.qTable = new ConcurrentHashMap<>();
        initQTable();
    }
    
    private void initQTable() {
        for (QState s : QState.values()) {
            Map<QAction, Double> row = new ConcurrentHashMap<>();
            for (QAction a : QAction.values()) {
                row.put(a, 0.0);
            }
            qTable.put(s, row);
        }
        
        // Bias towards sensible default behavior
        qTable.get(QState.FAR_FROM_SPOT).put(QAction.GO_TO_SPOT, 2.0);
        qTable.get(QState.APPROACHING_SPOT).put(QAction.GO_TO_SPOT, 2.0);
        qTable.get(QState.AT_SPOT).put(QAction.STAY, 3.0);
        qTable.get(QState.PLAYER_BLOCKING).put(QAction.RETREAT, 5.0);
        qTable.get(QState.RETREATED).put(QAction.GO_TO_SPOT, 1.0);
        qTable.get(QState.RETURNING).put(QAction.RETURN_HOME, 2.0);
    }
    
    /**
     * Observe current state from environment and select action (epsilon-greedy).
     */
    public QAction selectAction(QState state, Set<QAction> validActions) {
        if (validActions == null || validActions.isEmpty()) {
            return QAction.STAY;
        }
        
        QAction chosen;
        if (random.nextDouble() < epsilon) {
            // Explore: random valid action
            List<QAction> list = new ArrayList<>(validActions);
            chosen = list.get(random.nextInt(list.size()));
        } else {
            // Exploit: best valid action
            chosen = validActions.stream()
                .max(Comparator.comparingDouble(a -> getQ(state, a)))
                .orElse(QAction.STAY);
        }
        
        lastState = state;
        lastAction = chosen;
        
        return chosen;
    }
    
    /**
     * Receive reward and update Q-table. Call when a meaningful transition occurs.
     */
    public void update(double reward, QState newState) {
        if (lastState == null || lastAction == null) return;
        
        double oldQ = getQ(lastState, lastAction);
        double maxNextQ = Arrays.stream(QAction.values())
            .mapToDouble(a -> getQ(newState, a))
            .max().orElse(0);
        
        double newQ = oldQ + ALPHA * (reward + GAMMA * maxNextQ - oldQ);
        setQ(lastState, lastAction, newQ);
        
        epsilon = Math.max(EPSILON_MIN, epsilon * EPSILON_DECAY);
    }
    
    /**
     * Convenience: record a transition with standard reward.
     */
    public void recordTransition(QState fromState, QAction action, QState toState, String event) {
        double r = R_STEP;
        if ("reached_spot".equals(event)) r = R_REACHED_SPOT;
        else if ("retreated".equals(event)) r = R_RETREATED;
        else if ("player_collision".equals(event)) r = R_PLAYER_COLLISION;
        else if ("returned_home".equals(event)) r = R_RETURNED_HOME;
        
        lastState = fromState;
        lastAction = action;
        update(r, toState);
    }
    
    private double getQ(QState s, QAction a) {
        return qTable.getOrDefault(s, Collections.emptyMap()).getOrDefault(a, 0.0);
    }
    
    private void setQ(QState s, QAction a, double value) {
        qTable.computeIfAbsent(s, k -> new ConcurrentHashMap<>()).put(a, value);
    }
    
    /**
     * Get valid actions for a state (environment constraints).
     */
    public static Set<QAction> getValidActions(QState state) {
        switch (state) {
            case FAR_FROM_SPOT:
            case APPROACHING_SPOT:
                return EnumSet.of(QAction.GO_TO_SPOT, QAction.RETURN_HOME);
            case AT_SPOT:
                return EnumSet.of(QAction.STAY, QAction.RETREAT, QAction.RETURN_HOME);
            case PLAYER_BLOCKING:
                return EnumSet.of(QAction.RETREAT);
            case RETREATED:
                return EnumSet.of(QAction.GO_TO_SPOT, QAction.RETREAT);
            case RETURNING:
                return EnumSet.of(QAction.RETURN_HOME);
            default:
                return EnumSet.of(QAction.STAY);
        }
    }
    
    /**
     * Map environment observations to Q-learning state.
     */
    public static QState observeState(double distToSpot, boolean playerNearSpot, 
                                      boolean isRetreated, boolean isReturning) {
        if (isReturning) return QState.RETURNING;
        if (isRetreated) return QState.RETREATED;
        if (playerNearSpot && distToSpot <= 1.5) return QState.PLAYER_BLOCKING;
        if (distToSpot <= 1.5) return QState.AT_SPOT;
        if (distToSpot <= 4.0) return QState.APPROACHING_SPOT;
        return QState.FAR_FROM_SPOT;
    }
    
    public double getEpsilon() { return epsilon; }
    
    /**
     * Serialize Q-table for persistence (simple string format).
     */
    public Map<String, Object> toNBT() {
        Map<String, Object> out = new HashMap<>();
        Map<String, Object> table = new HashMap<>();
        for (Map.Entry<QState, Map<QAction, Double>> e : qTable.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            for (Map.Entry<QAction, Double> e2 : e.getValue().entrySet()) {
                row.put(e2.getKey().name(), e2.getValue());
            }
            table.put(e.getKey().name(), row);
        }
        out.put("qtable", table);
        out.put("epsilon", epsilon);
        return out;
    }
    
    /**
     * Load Q-table from persisted data.
     */
    @SuppressWarnings("unchecked")
    public void fromNBT(Map<String, Object> nbt) {
        if (nbt == null) return;
        Object tableObj = nbt.get("qtable");
        if (tableObj instanceof Map) {
            Map<String, Object> table = (Map<String, Object>) tableObj;
            for (String sk : table.keySet()) {
                try {
                    QState s = QState.valueOf(sk);
                    Object rowObj = table.get(sk);
                    if (rowObj instanceof Map) {
                        Map<String, Object> row = (Map<String, Object>) rowObj;
                        for (String ak : row.keySet()) {
                            try {
                                QAction a = QAction.valueOf(ak);
                                Object v = row.get(ak);
                                if (v instanceof Number) {
                                    setQ(s, a, ((Number) v).doubleValue());
                                }
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        Object epsObj = nbt.get("epsilon");
        if (epsObj instanceof Number) {
            epsilon = Math.max(EPSILON_MIN, ((Number) epsObj).doubleValue());
        }
    }
}
