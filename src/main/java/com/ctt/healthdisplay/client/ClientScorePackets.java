package com.ctt.healthdisplay.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Cache scoped to one connection and one play listener, including reconfiguration on the same connection. */
public final class ClientScorePackets {
    public interface Access {
        ClientScorePackets ctt$getScorePackets();
    }

    private static final Set<String> STAGE_HOLDERS = Set.of("#Tier", "#Floor", "#Boss", "#MBoss",
            "#Dungeon", "#Shop", "#Ally", "#Misc", "#BreakRoomID", "#CheckPoint", "#GameOver", "#LobbyMiniGame");
    private static final int MAX_PLAYER_SCORES = 4096;

    private final Map<String, Integer> stage = new HashMap<>();
    private final Map<String, Integer> lives = new LinkedHashMap<>();
    private final Map<String, Integer> coins = new LinkedHashMap<>();
    private final Map<String, Integer> health = new LinkedHashMap<>();
    private ClientPacketListener listener;
    private Integer gameId;
    private long stageRevision;

    private static ClientScorePackets current(ClientPacketListener listener) {
        return listener != null && listener.getConnection() instanceof Access access ? access.ctt$getScorePackets() : null;
    }

    public static Integer playerScore(Minecraft client, String objective, String owner) {
        var listener = client.getConnection();
        var cache = current(listener);
        return cache == null ? null : cache.playerScore(listener, objective, owner);
    }

    public static StageSnapshot stage(Minecraft client) {
        var listener = client.getConnection();
        var cache = current(listener);
        return cache == null ? StageSnapshot.EMPTY : cache.stage(listener);
    }

    public static boolean isScorePacket(Packet<?> packet) {
        return packet instanceof ClientboundSetScorePacket || packet instanceof ClientboundResetScorePacket
                || packet instanceof ClientboundSetObjectivePacket || packet instanceof ClientboundBundlePacket;
    }

    public synchronized void accept(ClientPacketListener listener, Packet<?> packet) {
        if (this.listener != listener) {
            this.listener = listener;
            stage.clear();
            lives.clear();
            coins.clear();
            health.clear();
            gameId = null;
            updateStageSnapshot();
        }
        long oldRevision = stageRevision;
        apply(packet);
        if (stageRevision != oldRevision) updateStageSnapshot();
    }

    private void apply(Packet<?> packet) {
        switch (packet) {
            case ClientboundSetScorePacket score -> setScore(score.objectiveName(), score.owner(), score.score());
            case ClientboundResetScorePacket reset -> {
                if (reset.objectiveName() == null) {
                    setScore("CTT", reset.owner(), null);
                    setScore("GameID", reset.owner(), null);
                    lives.remove(reset.owner());
                    coins.remove(reset.owner());
                    health.remove(reset.owner());
                } else {
                    setScore(reset.objectiveName(), reset.owner(), null);
                }
            }
            case ClientboundSetObjectivePacket objective -> {
                if (objective.getMethod() == ClientboundSetObjectivePacket.METHOD_REMOVE) {
                    switch (objective.getObjectiveName()) {
                        case "CTT" -> {
                            if (!stage.isEmpty()) {
                                stage.clear();
                                stageRevision++;
                            }
                        }
                        case "GameID" -> setScore("GameID", "#CTT", null);
                        case "Lives" -> lives.clear();
                        case "Coins" -> coins.clear();
                        case "HealthPercent" -> health.clear();
                    }
                }
            }
            case ClientboundBundlePacket bundle -> {
                for (var child : bundle.subPackets()) apply(child);
            }
            default -> { }
        }
    }

    private void setScore(String objective, String owner, Integer value) {
        if ("CTT".equals(objective)) {
            if (STAGE_HOLDERS.contains(owner)) {
                Integer old = value == null ? stage.remove(owner) : stage.put(owner, value);
                if (!Objects.equals(old, value)) stageRevision++;
            }
        } else if ("GameID".equals(objective)) {
            if ("#CTT".equals(owner) && !Objects.equals(gameId, value)) {
                gameId = value;
                stageRevision++;
            }
        } else {
            Map<String, Integer> scores = playerScores(objective);
            if (scores == null) return;
            if (value == null) {
                scores.remove(owner);
            } else {
                scores.put(owner, value);
                if (scores.size() > MAX_PLAYER_SCORES) scores.remove(scores.keySet().iterator().next());
            }
        }
    }

    private Map<String, Integer> playerScores(String objective) {
        return switch (objective) {
            case "Lives" -> lives;
            case "Coins" -> coins;
            case "HealthPercent" -> health;
            default -> null;
        };
    }

    private synchronized Integer playerScore(ClientPacketListener listener, String objective, String owner) {
        var scores = playerScores(objective);
        return this.listener == listener && scores != null ? scores.get(owner) : null;
    }

    public record StageSnapshot(Map<String, Integer> scores, Integer gameId) {
        private static final StageSnapshot EMPTY = new StageSnapshot(Map.of(), null);
    }

    private StageSnapshot stageSnapshot = StageSnapshot.EMPTY;

    private void updateStageSnapshot() {
        stageSnapshot = new StageSnapshot(Map.copyOf(stage), gameId);
    }

    private synchronized StageSnapshot stage(ClientPacketListener listener) {
        return this.listener == listener ? stageSnapshot : StageSnapshot.EMPTY;
    }
}
