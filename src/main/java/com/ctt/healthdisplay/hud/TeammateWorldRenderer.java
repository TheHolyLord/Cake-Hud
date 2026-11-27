package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.CttHealthDisplay;
import com.ctt.healthdisplay.client.HudRenderGate;
import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.health.HealthData;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/** 在每帧的世界渲染通道绘制队友血条，让它与玩家模型一起移动。 */
public final class TeammateWorldRenderer {
    private static final double S = 0.025;
    private static final double TEXT_WORLD_PER_PX = TextGizmo.Style.DEFAULT_SCALE / 16.0;
    // 最低一行也要留在原版名字上方。
    private static final double NAME_CLEARANCE = 1.05;
    private static final int BAR_TOP = 10;
    private static final int HEALTH_TEXT_Y = BAR_TOP - 11;
    private static final int HEART_COUNTS_Y = HEALTH_TEXT_Y - 11;
    private static final int[] TEXT_COLORS = {0xFFFF4444, 0xFFFFDD44, 0xFF7722AA, 0xFF5599FF};
    private static final int HEARTS_PER_ROW = 4;
    private static final double HEART_SCALE = 0.85;
    private static final double HEART_GAP = 2;
    private static final String HEART = "❤";

    private TeammateWorldRenderer() {}

    public static void render(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) return;
        ModConfig cfg = ModConfig.INSTANCE;
        if (!cfg.showTeammateHeadHP || mc.gui.hud.isHidden()
                || HudRenderGate.shouldSuppressModHud(CttHealthDisplay.statsData)) return;

        var teammates = HealthData.getTeammateMap();
        if (teammates.isEmpty()) return;
        Camera camera = mc.gameRenderer.mainCamera();
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z());
        Vec3 upVector = new Vec3(up.x(), up.y(), up.z());
        Vec3 cameraPos = camera.position();
        float partialTick = camera.getCameraEntityPartialTicks(mc.getDeltaTracker());
        double maxDistanceSq = (double) cfg.playerHeadDistance * cfg.playerHeadDistance;

        try (Gizmos.TemporaryCollection ignored = ctx.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof Player player) || player == mc.player || player.isSpectator()
                        || player.isInvisible() || !player.shouldRender(cameraPos.x, cameraPos.y, cameraPos.z)
                        || cameraPos.distanceToSqr(player.position()) > maxDistanceSq) continue;
                var data = teammates.get(player.getName().getString());
                if (data == null) continue;
                Vec3 center = player.getPosition(partialTick).add(0, player.getBbHeight() + NAME_CLEARANCE, 0);
                drawTeammate(mc.font, mc.level, cameraPos, center, right, upVector, data, cfg);
            }
        }
    }

    private static void drawTeammate(Font font, ClientLevel level, Vec3 cameraPos, Vec3 center, Vec3 right, Vec3 up,
                                     HealthData.TeammateData data, ModConfig cfg) {
        int max = data.maxHP;
        int red = data.redHeartsServer >= 0 ? data.redHeartsServer : Math.clamp(data.hp, 0, Math.max(0, max));
        int soul = data.redHeartsServer >= 0 ? Math.max(0, data.soulHearts) : max > 0 ? Math.max(0, data.hp - max) : 0;
        int black = Math.max(0, data.blackHearts);
        int blue = Math.max(0, data.blueHearts);
        // 地图队伍栏里的血量已经包含各类心，和侧栏使用同一个实时总数。
        int total = data.hp;

        if (max > 0 && cfg.showPlayerHeadBar) {
            int halfWidth = cfg.teammateBarHalfWidth;
            int width = halfWidth * 2;
            // 置顶阶段在玻璃之后绘制，但会跳过世界的深度遮挡。靠近实墙边缘时保留原来的遮挡绘制。
            double barMiddleY = BAR_TOP + 2.5;
            boolean drawAfterGlass = hasClearView(level, cameraPos, offset(center, right, up, 0, barMiddleY))
                    && hasClearView(level, cameraPos, offset(center, right, up, -halfWidth, barMiddleY))
                    && hasClearView(level, cameraPos, offset(center, right, up, halfWidth, barMiddleY));
            HeadHealthStyle.playerSpans(width, max, total, data.crackedHearts, soul, black, blue,
                    (start, end, color) -> HeadHealthStyle.rows(-halfWidth, BAR_TOP, width, (left, y, rightEdge, track) -> {
                        int x0 = Math.max(left, start - halfWidth);
                        int x1 = Math.min(rightEdge, end - halfWidth);
                        if (x1 <= x0) return;
                        int row = color == -1 ? track : HeadHealthStyle.tone(color, y - BAR_TOP);
                        int argb = HeadHealthStyle.alpha(row, cfg.playerBarOpacity);
                        var barRow = Gizmos.rect(offset(center, right, up, x0, y), offset(center, right, up, x1, y),
                                offset(center, right, up, x1, y + 1), offset(center, right, up, x0, y + 1),
                                GizmoStyle.fill(argb));
                        if (drawAfterGlass) barRow.setAlwaysOnTop();
                    }));
        }

        if (cfg.showPlayerHeadNumbers) {
            int effectiveMax = cfg.showPlayerHeadEffectiveMax ? data.effectiveMaxHp() : max;
            String hpText = HudNumbers.health(total, max, effectiveMax);
            Gizmos.billboardText(hpText, offset(center, right, up, 0, HEALTH_TEXT_Y),
                    TextGizmo.Style.forColorAndCentered(HeadHealthStyle.alpha(0xFFFFFFFF, cfg.playerTextOpacity)));
        }
        drawHeartCounts(font, center, right, up, new int[]{red, soul, black, blue}, max > 0, data.crackedHearts, cfg);
    }

    private static void drawHeartCounts(Font font, Vec3 center, Vec3 right, Vec3 up, int[] counts,
                                        boolean hasMax, int cracked, ModConfig cfg) {
        var labels = new ArrayList<HeartLabel>(5);
        if (hasMax && cfg.showPlayerHeadHeartCounts) {
            for (int i = 0; i < counts.length; i++) {
                if (i != 0 && counts[i] <= 0) continue;
                labels.add(new HeartLabel(HudNumbers.amount(counts[i]) + HEART,
                        TEXT_COLORS[i], false));
            }
        }
        if (cfg.showPlayerHeadCrackedCount && cracked > 0) {
            labels.add(new HeartLabel(HudNumbers.amount(cracked),
                    HeadHealthStyle.COLOR_CRACKED_HEART, true));
        }
        if (labels.isEmpty()) return;

        // 和敌人护甲一样，第一行贴近血量，其余行向上展开。
        double rowY = cfg.showPlayerHeadNumbers ? HEART_COUNTS_Y : HEALTH_TEXT_Y;
        for (int from = 0; from < labels.size(); from += HEARTS_PER_ROW) {
            List<HeartLabel> row = labels.subList(from, Math.min(from + HEARTS_PER_ROW, labels.size()));
            drawHeartRow(font, center, right, up, row, rowY, cfg);
            rowY -= 10;
        }
    }

    private static void drawHeartRow(Font font, Vec3 center, Vec3 right, Vec3 up, List<HeartLabel> row,
                                     double y, ModConfig cfg) {
        double rowWidth = row.stream().mapToInt(label -> label.width(font)).sum() + HEART_GAP * (row.size() - 1);
        // 心形行始终使用默认血条长度的排版空间，调整血条长度只改变血条本身。
        double maxWidth = ModConfig.DEFAULT_TEAMMATE_BAR_LENGTH * S / TEXT_WORLD_PER_PX;
        double scale = Math.min(HEART_SCALE, maxWidth / rowWidth);
        double pixel = scale * TEXT_WORLD_PER_PX / S;
        double x = -rowWidth / 2;
        for (HeartLabel label : row) {
            int textWidth = font.width(label.text());
            int color = HeadHealthStyle.alpha(label.color(), cfg.playerTextOpacity);
            Gizmos.billboardText(label.text(), offset(center, right, up, (x + textWidth / 2.0) * pixel, y),
                    TextGizmo.Style.forColorAndCentered(color)
                            .withScale((float) (TextGizmo.Style.DEFAULT_SCALE * scale)));
            if (label.cracked()) {
                double iconX = (x + textWidth + 1) * pixel;
                // 头顶文字的 y 是行顶，不是行中心。图案在同一行内垂直居中。
                double iconY = y + (font.lineHeight - CrackedHeartIcon.HEIGHT) * pixel / 2.0;
                CrackedHeartIcon.draw(color, (left, top, end, pixelColor) ->
                        Gizmos.rect(offset(center, right, up, iconX + left * pixel, iconY + top * pixel),
                                offset(center, right, up, iconX + end * pixel, iconY + top * pixel),
                                offset(center, right, up, iconX + end * pixel, iconY + (top + 1) * pixel),
                                offset(center, right, up, iconX + left * pixel, iconY + (top + 1) * pixel),
                                GizmoStyle.fill(pixelColor)));
            }
            x += label.width(font) + HEART_GAP;
        }
    }

    private record HeartLabel(String text, int color, boolean cracked) {
        int width(Font font) {
            return font.width(text) + (cracked ? 1 + CrackedHeartIcon.WIDTH : 0);
        }
    }

    private static boolean hasClearView(ClientLevel level, Vec3 cameraPos, Vec3 target) {
        var view = new ClipContext(cameraPos, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                CollisionContext.empty()) {
            @Override
            public VoxelShape getBlockShape(BlockState state, BlockGetter world, BlockPos pos) {
                // 普通玻璃和玻璃板已没有 VISUAL 形状，冰块等半透明方块在这里也按透明处理。
                return state.getBlock() instanceof HalfTransparentBlock
                        ? Shapes.empty() : super.getBlockShape(state, world, pos);
            }
        };
        return level.clip(view).getType() == HitResult.Type.MISS;
    }

    private static Vec3 offset(Vec3 center, Vec3 right, Vec3 up, double x, double y) {
        return center.add(right.x * x * S - up.x * y * S,
                right.y * x * S - up.y * y * S, right.z * x * S - up.z * y * S);
    }
}
