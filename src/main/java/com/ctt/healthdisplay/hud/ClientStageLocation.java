package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.network.StagePayload;
import com.ctt.healthdisplay.server.StageKey;

/**
 * v6.5.6 / v7.0.10 · 客户端关卡位置缓存（单条全局，仅"我"自己的位置）。
 *
 * <h2>数据来源优先级</h2>
 * <ol>
 *   <li><b>fromServer</b>（首选）：服务端 mod 的 {@code StageProbeServer} 通过
 *       {@code StagePayload} 推过来的权威位置。装着服务端 mod 时这是数据源。<br>
 *       由 {@link com.ctt.healthdisplay.CttHealthDisplay} 在 {@code onInitializeClient} 里
 *       注册的 {@code ClientPlayNetworking.registerGlobalReceiver(StagePayload.ID, ...)}
 *       在每次收到服务端 push 时调用 {@link #onPayload(StagePayload)} 更新。</li>
 *   <li><b>fromScoreboard</b>：收到相关计分更新后计算；计分项被移除时清除。</li>
 *   <li><b>fromClient</b>（兜底）：标题、bossbar 和伤害等线索推断的位置。</li>
 * </ol>
 *
 * <p>{@link #current()} 按上述顺序选取可用位置。
 * HUD {@link StageLocation#probe()} 读这里。
 *
 * <p>线程安全：写入发生在 client network thread (server) / client tick thread (client probe)，
 * 读取发生在 render thread。{@code volatile} 字段足以保证可见性，快照本身是不可变 record。
 */
public final class ClientStageLocation {

    private ClientStageLocation() {}

    private static volatile StageLocation.Snapshot fromServer = StageLocation.Snapshot.unknown();
    private record ScoreboardLocation(StageLocation.Snapshot snapshot, StageKey stageKey) {}

    private static volatile ScoreboardLocation fromScoreboard;
    private static volatile StageLocation.Snapshot fromClient = StageLocation.Snapshot.unknown();
    private static volatile StageKey fromClientStageKey = null;

    /**
     * 优先返回服务端权威 snapshot；服务端无推送时回退到客户端探测的 snapshot。
     * 两侧都没数据 → {@link StageLocation.Snapshot#unknown()}。
     */
    public static StageLocation.Snapshot current() {
        StageLocation.Snapshot s = fromServer;
        if (s != null && s.kind() != StageLocation.Kind.UNKNOWN) return s;
        ScoreboardLocation scoreboard = fromScoreboard;
        if (scoreboard != null) return scoreboard.snapshot();
        return fromClient;
    }

    /**
     * v7.0.10 · 客户端探测的 stageKey（fromServer 不存在时供
     * {@link com.ctt.healthdisplay.client.ClientStatsCache#representativeStageKey()} 兜底用）。
     * 非战斗关 / 大厅 / GameOver 等返回 {@code null}（与 session 桶等价）。
     */
    public static StageKey clientFallbackStageKey() {
        ScoreboardLocation scoreboard = fromScoreboard;
        return scoreboard != null ? scoreboard.stageKey() : fromClientStageKey;
    }

    /**
     * v7.0.10 · 当前生效来源是否为客户端兜底（仅诊断 / UI 注释用）。
     */
    public static boolean isUsingClientFallback() {
        StageLocation.Snapshot s = fromServer;
        return s == null || s.kind() == StageLocation.Kind.UNKNOWN;
    }

    /** 收到服务端 payload 时调用（client network thread）。 */
    public static void onPayload(StagePayload payload) {
        fromServer = StageLocation.Snapshot.fromPayload(payload);
    }

    /**
     * 客户端根据标题、bossbar 等线索推断出的结果。
     * {@code stageKey} 为 {@code null} 表示非战斗关。
     */
    public static void setFromClientProbe(StageLocation.Snapshot snap, StageKey stageKey) {
        fromClient = snap == null ? StageLocation.Snapshot.unknown() : snap;
        fromClientStageKey = stageKey;
    }

    public static void setFromScoreboard(StageLocation.Snapshot snapshot, StageKey stageKey) {
        fromScoreboard = snapshot == null ? null : new ScoreboardLocation(snapshot, stageKey);
    }

    /** 玩家断线 / 切服时清空，避免悬挂上一局数据。 */
    public static void reset() {
        fromServer = StageLocation.Snapshot.unknown();
        fromScoreboard = null;
        fromClient = StageLocation.Snapshot.unknown();
        fromClientStageKey = null;
    }
}
