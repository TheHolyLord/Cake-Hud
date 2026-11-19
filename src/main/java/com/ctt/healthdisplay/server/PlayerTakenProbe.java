package com.ctt.healthdisplay.server;

import com.ctt.healthdisplay.config.ServerConfig;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v6.5.0 · 玩家承伤采集器。
 *
 * <h2>数据来源</h2>
 * <p>地图 {@code cake_team_tower:misc/damage.mcfunction} line 983 每 tick 对每个
 * 受击实体写：
 * <pre>execute as @e[scores={Damage=1..}] run scoreboard players operation @s DamageTook = @s Damage</pre>
 * <p>{@code DamageTook} 的值就是 Damage 经过 {@code DamagePercent} 护甲减免后、
 * 但尚未被蓝/黑/灵/红四层血吸收的本 tick 原始应扣血量。对每个实体独立写、无粒子污染、
 * 无 {@code limit=10} 取样损失 —— 是最干净的玩家承伤口径。
 *
 * <h2>采集时机</h2>
 * <p>{@code damage_universal.mcfunction} line 6 每 tick 头 {@code reset @e DamageTook}，
 * 接着才执行 damage 管线。本 probe 挂在 {@link net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents#END_SERVER_TICK}
 * —— 此时地图管线已跑完、下 tick 的 reset 还没发生 —— 读到的值恰是本 tick 的承伤量。
 *
 * <h2>广播策略</h2>
 * <p>可选地把每次 &gt; {@code broadcastTakenThreshold} 的承伤事件广播到聊天栏，
 * 格式 {@code [承伤] PlayerName -40}。测试期默认开、阈值 1。
 *
 * @see PlayerTakenStats
 */
public final class PlayerTakenProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("ctt-taken");

    private static final String OBJECTIVE = "DamageTook";

    /** 诊断日志间隔（tick）。30 s 输出一次总事件数。 */
    private static final long DIAG_INTERVAL_TICKS = 600;
    private static long lastDiagTick = 0;
    private static long totalEventsSeen = 0;

    private PlayerTakenProbe() {}

    public static void tickEnd(MinecraftServer server) {
        if (server == null) return;

        // 未启用会话时直接返回（但诊断还是尝试输出一次）
        if (!PlayerTakenStats.isLive() || PlayerTakenStats.isFrozen()) {
            return;
        }

        Scoreboard sb = server.getScoreboard();
        Objective obj = sb.getObjective(OBJECTIVE);
        if (obj == null) {
            // 地图 datapack 没加载 / 不支持。测试性质，打一次日志便于排错。
            long tick = DamageProbe.currentTick();
            if (tick - lastDiagTick >= DIAG_INTERVAL_TICKS) {
                LOGGER.warn("[CTT Taken] scoreboard objective '{}' not found (datapack not loaded?)", OBJECTIVE);
                lastDiagTick = tick;
            }
            return;
        }

        long tick = DamageProbe.currentTick();
        ServerConfig cfg = ServerConfig.INSTANCE;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.entityTags().contains("CTT")) continue;
            ReadOnlyScoreInfo score = sb.getPlayerScoreInfo(p, obj);
            if (score == null) continue;
            int v = score.value();
            if (v <= 0) continue;

            String name = p.getName().getString();
            PlayerTakenStats.addTaken(p.getUUID(), name, v, tick);
            totalEventsSeen++;

            // v8.x · 双路由：global=true → 全服广播；否则仅发 per-player 订阅者。
            // 阈值 broadcastTakenThreshold 仍是全局配置（per-player 也共享同一阈值）。
            if (v >= cfg.broadcastTakenThreshold) {
                boolean takenGlobal = cfg.broadcastTakenInChat;
                boolean takenAnySub = com.ctt.healthdisplay.server.command.BroadcastSubscribers
                        .hasAnySubscriber(com.ctt.healthdisplay.server.command.BroadcastSubscribers.Channel.TAKEN);
                if (takenGlobal || takenAnySub) {
                    Component msg = buildChatLine(name, v);
                    if (takenGlobal) {
                        server.getPlayerList().broadcastSystemMessage(msg, false);
                    } else {
                        com.ctt.healthdisplay.server.command.BroadcastSubscribers.sendTo(
                                server, com.ctt.healthdisplay.server.command.BroadcastSubscribers.Channel.TAKEN, msg);
                    }
                }
            }
        }

        if (tick - lastDiagTick >= DIAG_INTERVAL_TICKS) {
            if (totalEventsSeen > 0) {
                LOGGER.info("[CTT Taken/diag] tick={} total_events_seen={}", tick, totalEventsSeen);
            }
            lastDiagTick = tick;
        }
    }

    private static Component buildChatLine(String playerName, int dmg) {
        MutableComponent msg = Component.literal("[\u627f\u4f24] ").withStyle(ChatFormatting.RED); // 承伤
        msg.append(Component.literal(playerName).withStyle(ChatFormatting.WHITE));
        msg.append(Component.literal(" -" + dmg).withStyle(ChatFormatting.DARK_RED));
        return msg;
    }
}
