package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.health.HealthData;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * v8.4.8 · 26.3 修复三件事：
 *
 * <p>1) 抖动：之前把 gizmos 提交进 {@code Minecraft.collectPerTickGizmos()}（按【游戏刻】
 * 排空的通道），但收集发生在每个渲染帧——一个 tick 内会堆积几十份用不同相机姿态烘出来的
 * 副本，移动视角时副本散开、血条像疯狂刷新位置。改用
 * {@code LevelRenderer.collectPerFrameRenderThreadGizmos()}：官方【每帧】渲染线程通道，
 * 本帧 finalizeGizmoCollection() 排空，绝不跨帧堆积。
 *
 * <p>2) 正面看颜色变暗：旧边框是盖住整条血条的 50% 透明黑矩形，与填充 quad 共面重叠，
 * 半透明 quad 固定晚于不透明 quad 渲染，等于永远给填充蒙一层黑。现在边框只用描边
 * （{@code GizmoStyle.stroke}，只画四条边线），空槽画成与填充相邻的独立不透明段，
 * 任何两个 quad 不再共面重叠，颜色与视角无关。
 *
 * <p>3) 布局：怪物头顶自上而下为 名字 → 护甲行 → 血量数字 → 血条，全部水平居中；
 * 血条宽度恒等于血量数字宽度；整组锚点抬高到生物头顶之上（不再插进头里）；
 * 坐标改用 {@code entity.getPosition(partialTick)} 插值位置，移动的生物也平滑跟手。
 *
 * <p>4) v8.5.0：护甲不再跟随名字轮换翻页——客户端的 MobArmorCollector 把
 * bossbar 后缀里轮换出现的护甲段按怪聚合，一行显示该怪身上全部护甲（0 除外）。
 * 纯客户端实现，不依赖服务端 mod。
 *
 * <p>5) v8.5.1：护甲行动态布局——全部为 0（或没收集到）时整行消失、不留空档，
 * 名字直接位于血量数字上方；超过 4 段自动换行（每行 4 段向上叠）；
 * 同时修复蓝盾 🛡️（带变体选择符）在收集器里匹配不上、永不显示的问题。
 *
 * <p>6) v8.5.2：① 血条全不透明——未锁定怪的"变暗"从 alpha 0xCC 改为压暗 RGB，
 * 空槽底色/边框本就 0xFF，任何情况下不再透视后方方块；② 怪物血条按
 * "红-魂-黑-蓝"分层上色（同玩家 HUD）：后缀收集到 💛/💙/🖤 心形段时按段分层，
 * 否则总血量超过红心上限（hp&gt;maxHP）的溢出部分整段按魂心黄上色；
 * ③ 护甲槽扩到 12 档 = 自动换行最多 3 行。
 *
 * <p>7) v8.5.3：对照 CakeTeamPack 数据包源码修正两条数据链路——
 * ① 护甲行：收集器正则此前用"字符类+低位代理"匹配 emoji，Java 正则按码点匹配，
 * 🛡/🔥/💛 等代理对图标全部匹配失败，导致两种 🛡（蓝 Defence / 白 TrueArmor）
 * 和火护甲永远不显示、只剩 BMP 符号（☃☠☀⚓⚡）；改为码点字面量交替后全部恢复。
 * ② 魂心推导：数据包从不把 💛 写进任何文本，而是用 bossbar【颜色=黄】+
 * 【进度=SoulHearts/MaxSoulHearts】表达 HeartValue=2；客户端据此推导魂心数
 * （推导在 HealthData#deriveHeartsFromBar；v8.5.5 起推导值用于护甲行的 💛 数量
 * 显示，不再参与血条分层上色——见 {@link MobBarScreenRenderer}）。
 * 血量数字维持数据包口径：左=AllHearts（全部心总量），右=MaxHP（红心上限）。
 *
 * <p>8) v8.5.5：怪物头顶血条不再走本类的 gizmo 通道，改由
 * {@link MobBarScreenRenderer} 在 HUD 屏幕空间绘制。本类此后只负责<b>队友</b>头顶条。
 *
 * <p>9) v8.5.7：删除本类遗留的怪物绘制代码，避免旧世界空间通道重新出现穿透问题。
 */
public class TeammateWorldRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("CTT-WorldHP");
    private static long lastDebugTime = 0;


    // 四色心层颜色（与 HealthBarRenderer.COLOR_*_HEART 同值）
    private static final int COLOR_RED_HEART_3D   = 0xFFE84040;
    private static final int COLOR_SOUL_HEART_3D  = 0xFFDDCC22;
    private static final int COLOR_BLACK_HEART_3D = 0xFF550088;
    private static final int COLOR_BLUE_HEART_3D  = 0xFF4488EE;

    // 旧版像素坐标 → 世界尺寸的换算系数（原 globalScale = 0.025）
    private static final double S = 0.025;

    // 空槽底色：与 2D 血条 EMPTY_FILL(0x50181818) 同色系，但完全不透明，
    // 避免半透明 quad 在 gizmo 渲染管线里被排到不透明填充之后叠色。
    private static final int EMPTY_BG_OPAQUE = 0xFF181818;

    /**
     * 每帧由 LevelRenderEvents.BEFORE_GIZMOS 调一次（相机已定、gizmos 即将 finalize）。
     */
    public static void render(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) return;
        ModConfig cfg = ModConfig.INSTANCE;
        boolean mates = cfg.showTeammateHeadHP;
        // v8.5.5：怪物头顶条移交 MobBarScreenRenderer（HUD 屏幕空间，最高优先级）。
        Camera cam = mc.gameRenderer.mainCamera();
        Vector3fc left = cam.leftVector();
        Vector3fc up = cam.upVector();
        Vector3fc fwd = cam.forwardVector();
        // right = -left
        final Vec3 right = new Vec3(-left.x(), -left.y(), -left.z());
        final Vec3 upV = new Vec3(up.x(), up.y(), up.z());
        // 朝相机方向（-forward），用于多层 quad 的确定性前后排序
        final Vec3 toCam = new Vec3(-fwd.x(), -fwd.y(), -fwd.z());
        // 与相机/世界渲染同一插值口径（v8.5.6）：HUD 系的 (false) partial 和世界帧不同步，
        // 移动时 gizmo 位置会滞后一帧产生"晃动"；相机自己的 getCameraEntityPartialTicks 才是
        // 世界渲染真正用的值。
        final float partialTick = cam.getCameraEntityPartialTicks(mc.getDeltaTracker());

        Vec3 camPos = cam.position();
        // 每帧渲染线程通道：finalizeGizmoCollection() 本帧排空，不跨帧堆积。
        try (Gizmos.TemporaryCollection ignored = ctx.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.isInvisible() || !entity.shouldRender(camPos.x, camPos.y, camPos.z)) continue;
                // 怪物头顶条已经完全交给 MobBarScreenRenderer 的 HUD 层。
                // 这里保留世界空间通道只绘制队友，避免旧的怪物 quad 继续穿透玻璃和实体模型。
                if (entity instanceof Player && entity != mc.player && mates) {
                    renderTeammate(entity, right, upV, toCam, cfg, partialTick);
                }
            }
        }
    }

    // ── 怪物 ──
    // 怪物血条不再走这里的 Gizmo 世界空间通道；统一由 MobBarScreenRenderer 绘制。

    // ── 队友 ──

    private static void renderTeammate(Entity entity, Vec3 right, Vec3 up, Vec3 toCam,
                                       ModConfig cfg, float partialTick) {
        String name = entity.getName().getString();
        Map<String, HealthData.TeammateData> map = HealthData.getTeammateMap();
        HealthData.TeammateData data = findTeammate(name, map);

        long now = System.currentTimeMillis();
        if (now - lastDebugTime > 5000) {
            lastDebugTime = now;
            LOGGER.info("[DEBUG] entity='{}', teammate={}, mapKeys={}", name, data != null, map.keySet());
        }
        if (data == null) return;

        Vec3 center = barCenter(entity, 0.55, partialTick);
        float halfW = cfg.teammateBarHalfWidth;
        float barTop = 10F, barH = 5F;

        // 血条：填充段与空槽段左右相邻、互不重叠；分层按“后画覆盖先画”语义拆段。
        // v8.5.6：maxHP<=0（还没解析出队友数据）时整条不画——旧版这时只剩一圈
        // 描边边框飘在墙上，正是用户截图里那个"不知道是什么鬼"的空框。
        if (data.maxHP > 0) {
            if (cfg.showTeammateLayeredHearts && data.hasLayeredHearts()) {
                drawLayeredBar(center, right, up, toCam, halfW, barTop, barH, data);
            } else {
                double fillW = Math.min(2.0 * halfW, 2.0 * halfW * data.getPercent() / 100.0);
                if (fillW > 0) {
                    quad(center, right, up, toCam, -halfW, -halfW + fillW, barTop, barTop + barH,
                            getHealthColor(data.getPercent()), 0.004);
                }
                if (fillW < 2.0 * halfW) {
                    quad(center, right, up, toCam, -halfW + fillW, halfW, barTop, barTop + barH,
                            EMPTY_BG_OPAQUE, 0.0);
                }
            }
        }

        String hpText = data.maxHP > 0 ? data.hp + "/" + data.maxHP : String.valueOf(data.hp);
        int textY = (int) (barTop - 12);
        Vec3 textPos = anchorOffset(center, right, up, -halfW, textY);
        text(textPos, hpText, colorForPct(data.getPercent()));
    }

    /**
     * 四色心分层：与 2D 版 drawLayeredBar 同语义（红→魂→黑→蓝，后者覆盖前者），
     * 但在 3D 里拆成互不重叠的左右分段，杜绝共面叠色。
     */
    private static void drawLayeredBar(Vec3 center, Vec3 right, Vec3 up, Vec3 toCam,
                                       float halfW, float barTop, float barH,
                                       HealthData.TeammateData data) {
        double full = 2.0 * halfW;
        double[] widths = new double[4];
        widths[0] = clampW(full, (double) data.hp / data.maxHP * full);
        widths[1] = clampW(full, (double) data.soulHearts / data.maxHP * full);
        widths[2] = clampW(full, (double) data.blackHearts / data.maxHP * full);
        widths[3] = clampW(full, (double) data.blueHearts / data.maxHP * full);
        int[] colors = {COLOR_RED_HEART_3D, COLOR_SOUL_HEART_3D, COLOR_BLACK_HEART_3D, COLOR_BLUE_HEART_3D};

        double[] bounds = {0, widths[0], widths[1], widths[2], widths[3]};
        java.util.Arrays.sort(bounds);
        for (int i = 0; i < bounds.length - 1; i++) {
            double b0 = bounds[i], b1 = bounds[i + 1];
            if (b1 - b0 < 1e-4) continue;
            // 该分段显示“绘制顺序里最后一个仍覆盖它的层”（即下标最大的覆盖层）
            int color = -1;
            for (int layer = 3; layer >= 0; layer--) {
                if (widths[layer] >= b1 - 1e-6 && widths[layer] > 0) {
                    color = colors[layer];
                    break;
                }
            }
            if (color < 0) continue;
            quad(center, right, up, toCam, -halfW + b0, -halfW + b1, barTop, barTop + barH, color, 0.004);
        }
        double maxW = Math.max(Math.max(widths[0], widths[1]), Math.max(widths[2], widths[3]));
        if (maxW < full - 1e-4) {
            quad(center, right, up, toCam, -halfW + maxW, halfW, barTop, barTop + barH, EMPTY_BG_OPAQUE, 0.0);
        }
    }

    private static double clampW(double full, double w) {
        return Math.max(0, Math.min(full, w));
    }

    // ── 几何工具 ──

    /**
     * 锚点 = 插值脚底 + 模型高度 + aboveHead 格。aboveHead 保证整组内容悬在头顶之上。
     */
    private static Vec3 barCenter(Entity entity, double aboveHead, float partialTick) {
        Vec3 pos = entity.getPosition(partialTick);
        return pos.add(0, entity.getBbHeight() + aboveHead, 0);
    }

    /**
     * 把"旧像素坐标系"（x 相对条中心 -halfW..halfW，y 向下为正——原版屏幕坐标写法）的一点
     * 换算到 billboard 世界坐标。gizmo billboard 的 y 轴向上为正，因此这里对 y 取负；
     * 不取负会让整组布局上下颠倒（血条跑到名字上方）。
     */
    private static Vec3 anchorOffset(Vec3 center, Vec3 right, Vec3 up, double x, double y) {
        return center.add(right.x * (x * S) + up.x * (-y * S),
                          right.y * (x * S) + up.y * (-y * S),
                          right.z * (x * S) + up.z * (-y * S));
    }

    /** 按不透明度改写 alpha 通道（0..1）。 */
    private static int withAlpha(int argb, double opacity) {
        int a = (int) Math.round(Math.max(0.0, Math.min(1.0, opacity)) * 255);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private static void quad(Vec3 center, Vec3 right, Vec3 up, Vec3 toCam, double x0, double x1,
                             double y0, double y1, int argb, double towardCam) {
        Vec3 a = anchorOffset(center, right, up, x0, y0);
        Vec3 b = anchorOffset(center, right, up, x1, y0);
        Vec3 c = anchorOffset(center, right, up, x1, y1);
        Vec3 d = anchorOffset(center, right, up, x0, y1);
        if (towardCam > 0) {
            double o = towardCam;
            a = a.add(toCam.x * o, toCam.y * o, toCam.z * o);
            b = b.add(toCam.x * o, toCam.y * o, toCam.z * o);
            c = c.add(toCam.x * o, toCam.y * o, toCam.z * o);
            d = d.add(toCam.x * o, toCam.y * o, toCam.z * o);
        }
        Gizmos.rect(a, b, c, d, GizmoStyle.fill(withAlpha(argb, ModConfig.INSTANCE.teamBarOpacity)));
    }

    // v8.5.6：删除描边 outline()——队友条不再画边框（用户反馈"只剩一圈空框很莫名"）。

    private static void text(Vec3 pos, String string, int argb) {
        Gizmos.billboardText(string, pos,
                TextGizmo.Style.forColor(withAlpha(argb, ModConfig.INSTANCE.teamTextOpacity)));
    }

    // ── 查找 / 配色（与旧版一致） ──

    private static HealthData.TeammateData findTeammate(String displayName,
                                                        Map<String, HealthData.TeammateData> map) {
        if (map.isEmpty() || displayName.isEmpty()) return null;
        HealthData.TeammateData data = map.get(displayName);
        if (data != null) return data;
        for (Map.Entry<String, HealthData.TeammateData> entry : map.entrySet()) {
            if (displayName.contains(entry.getKey()) || entry.getKey().contains(displayName)) {
                return entry.getValue();
            }
        }
        return null;
    }


    private static int colorForPct(int pct) {
        if (pct <= 25) return 0xFFFF6666;
        if (pct <= 50) return 0xFFFFDD44;
        return 0xFFFFFFFF;
    }

    private static int getHealthColor(int percent) {
        if (percent <= 25) return 0xFFAA1515;
        if (percent <= 50) return 0xFFCC6620;
        if (percent <= 75) return 0xFFD83030;
        return 0xFFE84040;
    }

}
