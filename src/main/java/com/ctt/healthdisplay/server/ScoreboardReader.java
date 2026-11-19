package com.ctt.healthdisplay.server;

import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;

/**
 * v8.3.0 · M7 · 服务端 scoreboard 读数 null 防御小工具。
 *
 * <p>CTT 地图里所有 mob / 玩家属性都走 scoreboard；但 objective 是否存在、
 * 该 holder 是否有 score 这两步每次都要手写 null 判断，堆积在各 broadcaster 里
 * 没有意义。抽出 {@link #readOrZero} 统一成一行。
 *
 * <p>未来 M8（玩家属性）/ M9（队友血量）也走这条路径。
 */
public final class ScoreboardReader {

    private ScoreboardReader() {}

    /**
     * 读某个 holder 在某个 objective 上的整数 score；objective 不存在 / 没 score 时返回 0。
     * <p>对应 vanilla {@link Scoreboard#getScore(ScoreAccess, Objective)}，
     * 但 null 检查展开到这里。
     */
    public static int readOrZero(Scoreboard sb, String objectiveName, ScoreHolder holder) {
        if (sb == null || objectiveName == null || holder == null) return 0;
        Objective obj = sb.getObjective(objectiveName);
        if (obj == null) return 0;
        ReadOnlyScoreInfo score = sb.getPlayerScoreInfo(holder, obj);
        if (score == null) return 0;
        return score.value();
    }

    /** 判断某 objective 是否存在于 scoreboard。 */
    public static boolean hasObjective(Scoreboard sb, String objectiveName) {
        return sb != null && objectiveName != null && sb.getObjective(objectiveName) != null;
    }
}
