package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.CttHealthDisplay;
import com.ctt.healthdisplay.mixin.BossBarHudAccessor;
import com.ctt.healthdisplay.client.ClientMobHealthCache;
import com.ctt.healthdisplay.client.HudRenderGate;
import com.ctt.healthdisplay.client.MobArmorCollector;
import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.health.HealthData;
import com.ctt.healthdisplay.health.MobHealthData;
import com.ctt.healthdisplay.mixin.GameRendererAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * v8.5.5 · 怪物头顶血条改在 <b>HUD 屏幕空间</b>绘制（用户拍板："血条应该优先级最高"）。
 *
 * <p>旧版（v8.5.4 及以前）用 Gizmos 把血条 quad / billboard 文字提交进世界渲染管线：
 * quad 走深度测试，玻璃等半透明方块在 gizmo 之后合成 → 隔着玻璃墙时血条被玻璃叠色、
 * 看起来又"透"了；血条 quad 与怪物模型相交时也会被模型切掉一块（"透视怪物模型"）。
 * 本类把怪物整组头顶 UI（名字 / 护甲+心形数量行 / 血量数字 / 血条）投影成屏幕坐标、
 * 在 {@code HudElementRegistry.addLast} 层用完全不透明的 2D fill + 缩放文字绘制：
 * <ul>
 *   <li>在任何方块、任何实体模型之上，玻璃 / 怪物模型 / 粒子都无法遮挡或混色；</li>
 *   <li>保持旧版的世界缩放手感：尺寸 = 世界尺寸 × 该距离的"像素每格"，随距离等比缩放；</li>
 *   <li>布局常量与旧 Gizmo 版一一对应（barTop=6 / barH=5 / numY=-4 / rowStep=12 / S=0.025）。</li>
 * </ul>
 *
 * <p>同版本行为变化（用户拍板）：
 * <ul>
 *   <li>血条<b>永远是红心色渐变</b>——删除"有魂心就把血条染成黄/紫/蓝分层"与溢出彩虹机制；
 *     魂 / 黑 / 蓝心改在护甲行用 💛🖤💙 图标显示数量（{@link MobArmorCollector#currentWithHearts}）。</li>
 *   <li>血量数字维持：左=AllHearts（全部心总量），右=MaxHP（红心上限）。</li>
 * </ul>
 *
 * <p>v8.5.6 · 修掉 v8.5.5 的四个屏幕空间绘制问题（用户截图反馈）：
 * <ul>
 *   <li><b>满屏黑线/边框</b>：{@code GuiGraphicsExtractor.outline(x, y, w, h, color)} 的第 3、4
 *       参是<b>宽高</b>，旧代码传的是右下角<b>绝对坐标</b> → 边框被拉成横跨全屏的黑线。
 *       现在整条边框删除（用户也不要边框）。</li>
 *   <li><b>抬头/低头血条变大变小</b>：旧缩放用"头顶再上 1 格的两点屏幕 y 差"测量，
 *       俯仰角一变 Y 轴投影就被压扁/拉长 → 尺寸跟着视角抖。现改沿<b>相机右向量</b>
 *       （恒水平、恒垂直视线）投影测"每格像素数"，只随距离变、与俯仰完全无关。</li>
 *   <li><b>穿墙可见</b>：HUD 层没有深度测试，隔墙也照画。现对"相机→怪物头顶锚点"做
 *       方块轮廓与实体碰撞箱射线检测：任何方块（包括玻璃）或其他生物模型挡住视线 →
 *       整组 UI 隐藏。</li>
 *   <li><b>移动时血条晃动</b>：血条矩形坐标四舍五入到整数 GUI 像素，1 格 = 3 屏幕像素的
 *       跳变在移动时肉眼可见。现整条血条放进 {@code pose.translate+scale}（浮点）的
 *       "旧像素"子坐标系里绘制，边缘与位置都是亚像素精度，跟手不抖。</li>
 * </ul>
 *
 * <p>v8.5.7 · 用户拍板的四条显示规则：
 * <ul>
 *   <li><b>整箱可见才显示</b>：遮挡判定从"射线打到头顶锚点"升级为"怪物碰撞箱的
 *       8 个角全部投在屏幕内、且相机到每个角都不被方块阻挡"。任何一角被墙挡 /
 *       出屏 → 整组 UI 隐藏。（判定框内缩 2cm，站在地面上的怪不算"脚被方块挡"。）</li>
 *   <li><b>实体模型不再算遮挡</b>：别的怪物挡在视线里不影响血条——血条只有
 *       "方块挡住"和"没看全"两种隐藏原因。显示出来的血条永远画在 HUD 最顶层，
 *       方块 / 模型 / 粒子都压不住它。</li>
 *   <li><b>受伤强显 4 秒</b>：检测到该怪血量下降（无论伤害来源）→ 无视遮挡和
 *       整箱限制，血条强制显示 4 秒，期间再受伤会刷新时长。</li>
 *   <li><b>血条钉死在头顶，不随屏幕晃</b>：26.3 的视角晃动（行走 bob + 受伤倾斜）
 *       是 {@code GameRenderer.renderLevel} 渲染世界时才加进矩阵的，HUD 之前的投影
 *       矩阵没有它 → 模型晃、血条不晃，看起来就是"血条跟着屏幕漂移"。现在通过
 *       {@link GameRendererAccessor} 调用 vanilla 同一对 {@code bobHurt/bobView}，
 *       血条用与实体渲染逐位一致的"投影×晃动×视图旋转"矩阵，永远贴在头顶。</li>
 * </ul>
 * <p>头顶锚点回到 0.45 格（Psy 系敌人单独 0.8 格，模型/名牌更高）。
 *
 * <p>v8.5.8 · 显示规则再修订（用户拍板，四条）：
 * <ul>
 *   <li><b>受伤强制显示不再走捷径</b>：受伤窗口内照样要"无方块遮挡 + 看全碰撞箱"，
 *       隔墙、没看全、出屏一样不画——受伤只保证"满足条件时持续显示"，不豁免条件。</li>
 *   <li><b>近距离豁免整箱限制</b>：与敌人距离 ≤ 10 格时不再要求
 *       看全碰撞箱（贴脸时怪的身体本来就会出屏），但仍要求无方块遮挡且屏幕上
 *       确实有这个敌人。</li>
 *   <li><b>必须看屏才显示</b>：碰撞箱至少有一个采样点（中心 + 8 角）落在相机前方、
 *       屏幕矩形内才画——修"敌人离得很近还没进屏幕，血条先冒出来"。</li>
 *   <li><b>同名敌人血条互相独立</b>：血量数据按实体 UUID 一对一认领；某个条目
 *       对应的实体在场时，其他同名敌人不得借用它的数据——修"打一只、全场同名
 *       怪的血条一起变"。</li>
 * </ul>
 *
 * <p>队友头顶条仍走 {@link TeammateWorldRenderer} 的 gizmo 通道，本类只管怪物。
 *
 * <p>v8.5.9 · 同名怪血条联动彻底根治（用户反馈"新敌人进入视野时血条刷新、同名怪
 * 又统一了"）：删除 {@code findMobData} 的按名字借数据兜底。每只怪只按自己的
 * UUID 认领血量条目，认领不到就不画条——共享通道从机制上不存在了。
 */
public final class MobBarScreenRenderer {

    /** 旧版像素坐标系 → 世界单位 换算系数（与 TeammateWorldRenderer.S 一致）。 */
    private static final double S = 0.025;
    /** billboard 文字：每"旧像素"的世界尺寸（与旧 Gizmo 版同一公式，保证手感一致）。 */
    private static final double TEXT_WORLD_PER_PX = TextGizmo.Style.DEFAULT_SCALE / 16.0;

    private static final double BAR_TOP = 6.0;    // 血条顶边（旧像素系，向下为正）
    private static final double BAR_H = 5.0;      // 血条高度
    /** 整组 UI 与实体头顶之间的世界距离：普通怪 0.45 格（用户拍板"改回 0.45"）。 */
    private static final double HEAD_GAP = 0.45;
    /** Psy 系敌人（Boss/PsyClone）模型更高、名牌更长，单独用 0.8 格。 */
    private static final double HEAD_GAP_PSY = 0.80;
    /** 普通怪整组血条的绘制上移量（旧像素系），给下方的图标和数字留空。 */
    private static final double NORMAL_BAR_LIFT = 8.0;
    private static final double NUM_Y = BAR_TOP - 10.0; // 血量数字行（血条上方）
    private static final double ROW_STEP = 9.0;   // 护甲/名字行距，避免无数据时出现空白行
    private static final int HALF_W_MIN_I = 6;    // 血条最小半宽（旧像素系，v8.5.6 整数版）

    /** 整箱可见性判定的框内缩量：怪站在地面/贴墙时，边角恰好压在方块面上不算遮挡。 */
    private static final double BOX_INSET = 0.02;
    /** 近距离豁免距离（格）：≤10 格不要求"看全碰撞箱"（用户拍板新规则）。 */
    private static final double CLOSE_RANGE = 10.0;
    /** 角点"在屏幕内"判定的像素容差。 */
    private static final double SCREEN_EPS = 0.5;

    private static final int EMPTY_FILL = 0xFF181818;
    private static final int TEXT_WHITE = 0xFFFFFFFF;

    private MobBarScreenRenderer() {}

    // v8.5.9 · 彻底删除"按名字借数据"的兜底通道。
    // 旧版允许"名字匹配且键未被在场实体认领"的条目被无主实体借用——正是
    // "新敌人进入视野 → 血条刷新 → 同名怪血条统一"的根源：视野切换 / 缓存
    // 失鲜的空窗里，多只同名怪会先后借到同一批数值雷同的旧条目，看起来
    // 就是"打一只、全体一起变"。现在每只怪只认自己的 UUID（服务端按实体
    // 逐只推数据），借不到就干脆不显示，绝不共享。

    /** 单个怪物的投影结果，收集后按"远的先画"排序。 */
    private static final class Placement {
        Entity entity;
        MobHealthData data;
        UUID dataUuid;   // 显示数据来源实体；PsyClone 时指向带 Boss 标签的 Psy
        double gx, gy;   // 锚点屏幕坐标（GUI 像素）
        double ppu;      // 每格多少 GUI 像素（随距离增大而减小）
    }

    private static final List<Placement> BUF = new ArrayList<>();

    private static final class ResolvedData {
        final MobHealthData data;
        final UUID dataUuid;

        ResolvedData(MobHealthData data, UUID dataUuid) {
            this.data = data;
            this.dataUuid = dataUuid;
        }
    }

    /**
     * 每帧由 HUD 层调用（{@code HudElementRegistry.addLast}，在 vanilla 世界 / 3D HUD /
     * 其它 mod HUD 之后 → 屏幕最顶层，什么都不可能盖住它）。
     */
    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer == null) return;
        ModConfig cfg = ModConfig.INSTANCE;
        if (!cfg.isMobHeadHPEnabled()) return;
        if (mc.gui.hud.isHidden()) return;
        // 与名牌抑制同门：mod HUD 被整体压掉的场景（过场 / 特殊 boss 时刻）这里也不画，
        // 避免与 vanilla 名牌形成双份信息。
        if (HudRenderGate.shouldSuppressModHud(CttHealthDisplay.statsData)) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        Camera camera = mc.gameRenderer.mainCamera();
        // v8.5.7：用与实体渲染逐位一致的"投影 × bobHurt × bobView × 视图旋转"矩阵——
        // 屏幕晃动（行走 bob / 受伤倾斜）发生时血条与怪物模型同摆同停，钉死在头顶。
        Matrix4f viewProj = bobbedViewProjection(mc, new Matrix4f());
        // 相机右向量：恒水平、恒垂直视线。沿它偏移 1 格测"每格像素数"，
        // 结果只与距离有关，抬头低头（俯仰）完全不影响缩放（v8.5.6 修"变大变小"）。
        Vector3fc left = camera.leftVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z());
        // 必须用相机同款插值 partial tick（= getGameTimeDeltaPartialTick(true)，含"实体被冻结"
        // 特判）；旧版取 HUD 的 (false) 与世界的 (true) 是两套进度，移动时血条与怪物模型
        // 对不上帧 → "血条跟着屏幕晃动"（v8.5.6 修）。
        float partialTick = camera.getCameraEntityPartialTicks(mc.getDeltaTracker());
        double guiW = context.guiWidth();
        double guiH = context.guiHeight();
        double margin = 150.0;

        BUF.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.player.Player) continue;
            // v8：服务端标记抑制的实体（NPC 布景）不画。
            if (ClientMobHealthCache.isSuppressed(entity.getUUID())) continue;
            if (entity.isInvisible() || !entity.shouldRender(camPos.x, camPos.y, camPos.z)) continue;
            ResolvedData resolved = findMobData(mc, entity);
            if (resolved == null) continue;
            MobHealthData data = resolved.data;
            if (cfg.isMobHeadHPNearestOnly() && !data.targetted) continue;

            // 锚点：脚底插值位置 + 模型高度 + 头顶间距（Psy 0.8，其他 0.45）。
            boolean psy = isPsyEnemy(entity, data);
            double headGap = psy ? HEAD_GAP_PSY : HEAD_GAP;
            Vec3 anchor = entity.getPosition(partialTick).add(0, entity.getBbHeight() + headGap, 0);

            // v8.5.8 显示门槛（正常 / 受伤期同一套规则，用户拍板）：屏幕上必须看到这个
            // 敌人且视线无方块遮挡；>10 格还要看全碰撞箱。实体模型挡在中间不算遮挡
            // （"血条不能被实体模型挡住"）；显示出来的血条画在 HUD 最顶层压不住。
            if (!isEnemyVisible(mc, camPos, viewProj, entity, partialTick, guiW, guiH, anchor)) {
                continue;
            }

            // 护甲数据跟随实际来源实体；PsyClone 时来源是带 Boss 标签的 Psy。
            MobArmorCollector.collect(resolved.dataUuid, data.suffixText);

            double[] pt = new double[2];
            if (!project(viewProj, camPos, anchor, guiW, guiH, pt)) continue;
            // 每格像素数：沿相机右向量再投影 1 格，两点屏幕 x 差即缩放（俯仰无关）
            double[] ptR = new double[2];
            if (!project(viewProj, camPos, anchor.add(right), guiW, guiH, ptR)) continue;
            double ppu = ptR[0] - pt[0];
            if (!(ppu > 0.0)) continue;
            if (pt[0] < -margin || pt[0] > guiW + margin || pt[1] < -margin || pt[1] > guiH + margin) continue;

            Placement p = new Placement();
            p.entity = entity;
            p.data = data;
            p.dataUuid = resolved.dataUuid;
            p.gx = pt[0];
            // 仅平移绘制位置，原锚点仍用于遮挡检测和缩放。
            boolean keepHeight = psy || entity.entityTags().contains("Psy")
                    || "鸟叔".equals(data.name()) || "鸟叔".equals(entity.getName().getString());
            p.gy = keepHeight ? pt[1] : pt[1] - NORMAL_BAR_LIFT * S * ppu;
            p.ppu = ppu;
            BUF.add(p);
        }
        if (BUF.isEmpty()) return;

        // 远的先画（ppu 小 = 远），近怪的血条覆盖远怪的，屏幕空间没有深度也要有合理叠序
        BUF.sort((a, b) -> Double.compare(a.ppu, b.ppu));
        Font font = Minecraft.getInstance().font;
        for (Placement p : BUF) {
            drawOne(context, font, p, cfg);
        }
    }

    // ── 单只怪整组绘制 ──

    private static void drawOne(GuiGraphicsExtractor context, Font font, Placement p, ModConfig cfg) {
        MobHealthData data = p.data;
        double ppu = p.ppu;
        double unit = S * ppu;                       // "旧像素"→GUI 像素（布局系）
        double ts = TEXT_WORLD_PER_PX * ppu;         // 文字缩放（GUI 像素 / 字体像素）
        ts = Math.max(0.5, Math.min(6.0, ts));       // 远到糊/近到爆时钳制可读性
        if (BAR_H * unit < 0.75) return;             // 小到画不出：整组不画（旧版此时也≈不可见）

        double gx = p.gx;
        double gy = p.gy;

        // 护甲 + 心形数量行（12 档上限 = 3 行 × 4 段）
        List<MobArmorCollector.Segment> segs =
                MobArmorCollector.currentWithHearts(p.dataUuid, data.soulHearts, data.blackHearts, data.blueHearts);
        if (segs.size() > 12) segs = segs.subList(0, 12);
        int armorRows = (segs.size() + 3) / 4;       // 0..3

        // 本 fork 风格：hp 带 ❤ 前缀，并额外画一行「魂/黑/蓝心数量」。
        boolean forkStyle = ModConfig.INSTANCE.hudStyle == 1;
        java.util.List<String> forkRows = new java.util.ArrayList<>();
        if (forkStyle) {
            if (data.soulHearts > 0) forkRows.add("\uD83D\uDC9B " + data.soulHearts);
            if (data.blackHearts > 0) forkRows.add("\uD83D\uDDA4 " + data.blackHearts);
            if (data.blueHearts > 0) forkRows.add("\uD83D\uDC99 " + data.blueHearts);
            for (MobArmorCollector.Segment seg : segs) {
                if (seg.text != null && seg.text.startsWith("\uD83D\uDEE1")) {
                    forkRows.add(seg.text);
                    break;
                }
            }
            armorRows = 0;
        }
        int extraRows = forkRows.size();
        int totalRows = armorRows + extraRows;

        // v8.6.0 · 纯客户端兜底条目只有百分比（"73%"）；有绝对值的条目维持 "hp/maxHP"。
        String hpText = (forkStyle ? "\u2764 " : "")
                + (data.percentOnly ? data.hp + "%" : data.hp + "/" + data.maxHP);
        int pct = data.getPercent();
        // 不透明填充；非"当前锁定目标"靠压暗 RGB（而非降 alpha）区分
        int dim = data.targetted ? 0xFF : 0xB3;

        // 血条宽度 = 血量数字文本宽度（与旧版同一公式；v8.5.6 起在"旧像素"系里算，
        // 见下面血条绘制段）

        // 1) 名字（最上方，居中）
        double nameY = NUM_Y - ROW_STEP * totalRows - 11.0;
        drawScaledText(context, font, data.name(), gx, gy + nameY * unit, ts, true, TEXT_WHITE);

        // 2) 护甲/心形行：每行 ≤4 段、逐行居中；第一行离名字最近，最后一行贴着数字上方
        double gap = Math.max(1.0, font.width(" ") * ts * 0.75);
        for (int r = 0; r < armorRows; r++) {
            int from = r * 4;
            int to = Math.min(from + 4, segs.size());
            double totalW = 0.0;
            for (int i = from; i < to; i++) {
                totalW += font.width(segs.get(i).text) * ts;
            }
            totalW += gap * (to - from - 1);
            double cursor = gx - totalW / 2.0;
            double rowY = NUM_Y - ROW_STEP * (totalRows - 1 - r);
            double ty = gy + rowY * unit;
            for (int i = from; i < to; i++) {
                MobArmorCollector.Segment seg = segs.get(i);
                drawScaledText(context, font, seg.text, cursor, ty, ts, false, seg.argb);
                cursor += font.width(seg.text) * ts + gap;
            }
        }

        // 3) 血条：永远红心色单色（v8.5.5 起不再按魂/黑/蓝分层、不再彩虹溢出）。
        //    v8.5.6：放进浮点 pose 子坐标系画——translate 用浮点屏幕坐标、scale 用浮点缩放，
        //    fill 的整数坐标是"旧像素"系，经矩阵变换后边缘是亚像素精度。旧版把矩形四角
        //    四舍五入成整数 GUI 像素（1 GUI 像素 = 2~3 屏幕像素），移动时血条一跳一跳，
        //    看起来就是"跟着屏幕晃"。另外彻底删掉 outline 边框：该 API 的第 3、4 参是
        //    宽高而非右下角坐标，旧调用传了绝对坐标，边框被拉成横跨全屏的黑线（用户截图）。
        int halfOld = Math.max(HALF_W_MIN_I,
                (int) Math.ceil(font.width(hpText) * (TEXT_WORLD_PER_PX / (2.0 * S))));
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate((float) gx, (float) gy);
        pose.scale((float) unit, (float) unit);
        context.fill(-halfOld, (int) BAR_TOP, halfOld, (int) (BAR_TOP + BAR_H),
                withAlpha(EMPTY_FILL, ModConfig.INSTANCE.mobBarOpacity));
        int fillOld = (int) Math.round(2.0 * halfOld * pct / 100.0);
        if (fillOld > 0) {
            context.fill(-halfOld, (int) BAR_TOP, -halfOld + fillOld, (int) (BAR_TOP + BAR_H),
                    withAlpha(dimAlpha(getMobBarColor(pct), dim), ModConfig.INSTANCE.mobBarOpacity));
        }
        pose.popMatrix();

        // 4) 血量数字：血条上方居中（左=所有心总量，右=红心上限）
        drawScaledText(context, font, hpText, gx, gy + NUM_Y * unit, ts, true, TEXT_WHITE);
    }

    // ── 工具 ──

    /**
     * 世界点 → GUI 像素。用相机"视图旋转×投影"矩阵变换相对向量（语义与
     * {@code GameRenderer.projectPointToScreen} 一致），w&lt;0.3 视为在相机身后。
     */
    private static boolean project(Matrix4f viewProj, Vec3 camPos, Vec3 world,
                                   double guiW, double guiH, double[] out) {
        Vector4f v = new Vector4f(
                (float) (world.x - camPos.x),
                (float) (world.y - camPos.y),
                (float) (world.z - camPos.z),
                1.0f);
        viewProj.transform(v);
        if (v.w() < 0.3f) return false;
        double ndcX = v.x() / v.w();
        double ndcY = v.y() / v.w();
        if (!Double.isFinite(ndcX) || !Double.isFinite(ndcY)) return false;
        out[0] = (ndcX * 0.5 + 0.5) * guiW;
        out[1] = (0.5 - ndcY * 0.5) * guiH;
        return true;
    }

    /**
     * 与 {@code GameRenderer.renderLevel} 逐位一致的世界矩阵：
     * {@code projection × bobPose(bobHurt+bobView) × viewRotation}。
     *
     * <p>26.3 的屏幕晃动不在 {@code Camera} 上，而是世界渲染时经两个私有方法加进矩阵；
     * 这里通过 {@link GameRendererAccessor} 调用同一对方法读同一帧的
     * {@code CameraRenderState}，保证 HUD 投影和实体模型用的是<b>同一套</b>晃动。
     * 任何异常（理论不会发生）回落到无 bob 的旧矩阵，血条只是不跟晃，不会消失。
     */
    private static Matrix4f bobbedViewProjection(Minecraft mc, Matrix4f out) {
        try {
            GameRendererAccessor gr = (GameRendererAccessor) mc.gameRenderer;
            GameRenderState state = gr.ctt$getGameRenderState();
            CameraRenderState cam = state.levelRenderState.cameraRenderState;
            PoseStack bob = new PoseStack();
            gr.ctt$bobHurt(cam, bob);
            if (state.optionsRenderState.bobView) {
                gr.ctt$bobView(cam, bob);
            }
            out.set(cam.projectionMatrix);
            out.mul(bob.last().pose());
            out.mul(cam.viewRotationMatrix);
            return out;
        } catch (Throwable t) {
            return mc.gameRenderer.mainCamera().getViewRotationProjectionMatrix(out);
        }
    }

    /**
     * v8.5.8 显示门槛（正常显示与"受伤后的几秒"完全同一套规则，用户拍板："受伤的
     * 强制显示也必须遵循无方块遮挡、看全碰撞箱"）：
     * <ol>
     *   <li><b>必须看屏</b>：碰撞箱至少一个采样点（中心 + 8 角）在相机前方且落在
     *       屏幕矩形内——修"敌人还没进屏幕、尤其贴得很近时，血条先显示出来"；</li>
     *   <li><b>无方块遮挡</b>：&gt;10 格要求到 8 个角的射线都不被方块（含玻璃）阻挡；
     *       ≤10 格（近距离豁免"看全碰撞箱"；v8.6.1 起水平 10 格内或垂直上下
     *       10 格内都算近距离）仍要求到中心和头顶锚点的射线畅通；</li>
     *   <li><b>看全碰撞箱</b>：&gt;10 格时 8 个角必须全部在屏幕内；≤10 格豁免。</li>
     * </ol>
     * 其他实体挡在视线中间<b>不算</b>遮挡——怪物模型不影响血条显示。
     * 区块未加载等异常按"可见"放行，宁可多画一帧。
     */
    private static boolean isEnemyVisible(Minecraft mc, Vec3 camPos, Matrix4f viewProj,
                                          Entity entity, float partialTick,
                                          double guiW, double guiH, Vec3 anchor) {
        AABB box = entity.getBoundingBox();
        Vec3 cur = entity.position();
        Vec3 interp = entity.getPosition(partialTick);
        double dx = interp.x - cur.x, dy = interp.y - cur.y, dz = interp.z - cur.z;
        if (dx * dx + dy * dy + dz * dz > 1.0e-12) box = box.move(dx, dy, dz);
        box = box.inflate(-BOX_INSET);
        double x0 = Math.min(box.minX, box.maxX), x1 = Math.max(box.minX, box.maxX);
        double y0 = Math.min(box.minY, box.maxY), y1 = Math.max(box.minY, box.maxY);
        double z0 = Math.min(box.minZ, box.maxZ), z1 = Math.max(box.minZ, box.maxZ);
        Vec3 center = new Vec3((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0);
        // "离敌人 10 格内"按相机到碰撞箱最近点的距离算：站在巨型怪身下也算贴脸。
        // v8.6.1 · 豁免范围同时包含 Y 轴上下 10 格（用户拍板）：水平 10 格内 **或**
        // 垂直 10 格内（怪在楼上 / 楼下同一竖井里）都算近距离，不再要求看全碰撞箱。
        double nearX = Math.max(x0, Math.min(x1, camPos.x));
        double nearY = Math.max(y0, Math.min(y1, camPos.y));
        double nearZ = Math.max(z0, Math.min(z1, camPos.z));
        double horizSqr = (camPos.x - nearX) * (camPos.x - nearX) + (camPos.z - nearZ) * (camPos.z - nearZ);
        double vertSqr = (camPos.y - nearY) * (camPos.y - nearY);
        boolean close = horizSqr <= CLOSE_RANGE * CLOSE_RANGE || vertSqr <= CLOSE_RANGE * CLOSE_RANGE;

        double[] pt = new double[2];
        boolean anyOnScreen = false;
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3(
                    (i & 1) == 0 ? x0 : x1,
                    (i & 2) == 0 ? y0 : y1,
                    (i & 4) == 0 ? z0 : z1);
            boolean frontAndOnScreen = project(viewProj, camPos, corner, guiW, guiH, pt)
                    && pt[0] >= -SCREEN_EPS && pt[0] <= guiW + SCREEN_EPS
                    && pt[1] >= -SCREEN_EPS && pt[1] <= guiH + SCREEN_EPS;
            if (frontAndOnScreen) anyOnScreen = true;
            if (!close) {
                // 远怪：看全碰撞箱——任何一角在相机身后 / 出屏 / 被方块挡 → 隐藏。
                if (!frontAndOnScreen) return false;
                if (!rayClearTo(mc, camPos, corner)) return false;
            }
        }
        // 近处巨型怪（如贴脸的 Boss）角点可能全部出屏，但身体明显可见：中心点补一路采样。
        if (!anyOnScreen && project(viewProj, camPos, center, guiW, guiH, pt)) {
            anyOnScreen = pt[0] >= -SCREEN_EPS && pt[0] <= guiW + SCREEN_EPS
                    && pt[1] >= -SCREEN_EPS && pt[1] <= guiH + SCREEN_EPS;
        }
        if (!anyOnScreen) return false;

        if (close) {
            // 近距离豁免"看全碰撞箱"，但"无方块遮挡"照旧：中心 + 头顶锚点视线都必须畅通。
            if (!rayClearTo(mc, camPos, center)) return false;
            if (!rayClearTo(mc, camPos, anchor)) return false;
        }
        return true;
    }

    /** 相机 → 目标点射线是否不被任何方块（含玻璃）阻挡。区块未加载等异常视为畅通。 */
    private static boolean rayClearTo(Minecraft mc, Vec3 camPos, Vec3 target) {
        try {
            HitResult hit = mc.level.clip(new ClipContext(
                    camPos, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            return hit.getType() != HitResult.Type.BLOCK;
        } catch (Exception ignored) {
            return true;
        }
    }

    /** 客户端寻找 Psy 本体时的最大关联距离（格）。 */
    private static final double PSY_SEARCH_RADIUS = 200.0;

    /** Psy 系敌人：名字含 "psy"（Boss Psy / 分身显示名同源）或带 PsyClone 标签。 */
    private static boolean isPsyEnemy(Entity entity, MobHealthData data) {
        if (entity.entityTags().contains("PsyClone")) return true;
        String n = entity.getName().getString();
        if (n != null && n.toLowerCase(Locale.ROOT).contains("psy")) return true;
        String dn = data.name();
        return dn != null && dn.toLowerCase(Locale.ROOT).contains("psy");
    }

    /** 在屏幕坐标 (sx,sy) 处按文字缩放 ts 画一行字（带阴影；center=true 水平居中）。 */
    private static void drawScaledText(GuiGraphicsExtractor context, Font font, String s,
                                       double sx, double sy, double ts, boolean center, int color) {
        if (s == null || s.isEmpty()) return;
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate((float) sx, (float) sy);
        pose.scale((float) ts, (float) ts);
        int x = center ? -font.width(s) / 2 : 0;
        // 字体 8~9px 高：-4 让文字垂直居中在锚点上（旧 billboard 文字语义）
        context.text(font, s, x, -4, withAlpha(color, ModConfig.INSTANCE.mobTextOpacity),
                ModConfig.INSTANCE.textShadow);
        pose.popMatrix();
    }

    /** 完全不透明的"调暗"：RGB 各乘 dim/255，alpha 恒 FF。 */
    /** 按不透明度改写 alpha 通道（0..1）。 */
    private static int withAlpha(int argb, double opacity) {
        int a = (int) Math.round(Math.max(0.0, Math.min(1.0, opacity)) * 255);
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private static int dimAlpha(int rgb, int dim) {
        int r = ((rgb >> 16) & 0xFF) * dim / 0xFF;
        int g = ((rgb >> 8) & 0xFF) * dim / 0xFF;
        int b = (rgb & 0xFF) * dim / 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 血条红心色渐变（与旧版一致；v8.5.5 起这是怪物血条唯一的配色路径）。 */
    private static int getMobBarColor(int pct) {
        if (pct <= 25) return 0xFFAA1515;
        if (pct <= 50) return 0xFFCC6620;
        if (pct <= 75) return 0xFFD83030;
        return 0xFFE84040;
    }

    // ── v8.1：Psy 分身组 ──
    /** 组快照缓存时长：50 ms（够一帧，且不会在 4 Hz 包之间抖动）。 */
    private static final long PSY_GROUP_TTL_MS = 50L;
    private static long psyGroupAt = 0L;
    private static ResolvedData psyGroupCache;

    /** 地图给 Psy 的 custom name 键（去掉末尾不可见字符 U+E007 后）。 */
    private static final java.util.Set<String> PSY_NAME_KEYS =
            java.util.Set.of("Psy", "Psy_2XXX", "Rockstar_Psy", "Rockstar Psy");

    private static String stripPsySuffix(String key) {
        if (key == null) return null;
        int end = key.length();
        while (end > 0 && key.charAt(end - 1) == '\uE007') end--;
        return key.substring(0, end);
    }

    /** 名字组件的 translate 键是否是 Psy 家族（与显示语言无关）。 */
    private static boolean isPsyName(Component name) {
        if (name == null) return false;
        if (name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && tc.getKey() != null) {
            return PSY_NAME_KEYS.contains(stripPsySuffix(tc.getKey()));
        }
        return false;
    }

    private static boolean isPsyMember(Entity entity, Map<UUID, MobHealthData> map) {
        if (entity.entityTags().contains("Psy") || entity.entityTags().contains("PsyClone")) return true;
        if (isPsyName(entity.getCustomName())) return true;
        MobHealthData d = map.get(entity.getUUID());
        return d != null && isPsyName(d.nameText);
    }

    /** 纯客户端用的百分比条目（不要求客户端可见的 E/Boss/Targetting tag）。 */
    private static MobHealthData percentOf(Minecraft mc, Entity entity) {
        int pct = healthPercentOf(mc, entity);
        if (pct < 0) return null;
        Component name = entity.getDisplayName();
        if (name == null) name = entity.getName();
        MobHealthData d = new MobHealthData(name == null ? Component.empty() : name,
                Component.empty(), pct, 100, 0L);
        d.percentOnly = true;
        return d;
    }

    /**
     * 整组共同读数：<ol>
     *   <li>组内任意实体在 payload map 里已有条目（双端；服务端已把分身映射到 Boss）→ 用它；</li>
     *   <li>否则取组内<b>最低</b> HealthPercent（纯客户端；分身恒 100%，真身掉血后更低）。</li>
     * </ol>
     */
    private static ResolvedData psyGroupData(Minecraft mc, Map<UUID, MobHealthData> map) {
        long now = System.currentTimeMillis();
        if (now - psyGroupAt < PSY_GROUP_TTL_MS) return psyGroupCache;
        psyGroupAt = now;
        if (mc.level == null) { psyGroupCache = null; return null; }
        ResolvedData best = null;
        MobHealthData lowest = null;
        UUID lowestUuid = null;
        for (Entity other : mc.level.entitiesForRendering()) {
            if (other == null || other.isRemoved() || !other.isAlive()) continue;
            if (!isPsyMember(other, map)) continue;
            MobHealthData d = map.get(other.getUUID());
            if (d != null) { best = new ResolvedData(d, other.getUUID()); break; }
            MobHealthData pct = percentOf(mc, other);
            if (pct != null && (lowest == null || pct.hp < lowest.hp)) {
                lowest = pct;
                lowestUuid = other.getUUID();
            }
        }
        if (best == null && lowest != null) best = new ResolvedData(lowest, lowestUuid);
        psyGroupCache = best;
        return best;
    }

    private static ResolvedData findMobData(Minecraft mc, Entity entity) {
        Map<UUID, MobHealthData> map = HealthData.getMobHealthMap();
        // v8.6.0 · 空表不再一票否决：纯客户端模式下百分比兜底不依赖这张表。
        if (map.isEmpty() && ClientMobHealthCache.isFresh()) return null;

        // v8.1：Psy 家族（真身 + 所有分身）整组共用同一份数据 —— 纯客户端也必须一致。
        // 旧逻辑依赖 entityTags()/名字，客户端 tag 恒空、名字又是翻译后的「鸟叔」，
        // 纯客户端下分身只能读自己的 HealthPercent，数值不同 → 暴露真身。
        if (isPsyMember(entity, map)) {
            ResolvedData group = psyGroupData(mc, map);
            if (group != null) return group;
        }

        // PsyClone 只是场地上的分身，用户要求它的血量、护甲和特殊心始终跟随
        // 同场地、带 Boss 标签的 Psy，而不是读取分身自己的 RedHearts。
        // v8.6.1 · 修复"本体不在我屏幕上 / 分身离本体太远 → 分身血条变成自己的"：
        //   1) 找本体不再要求名字含 "psy"（中文客户端本体的显示名是 "鸟叔"，旧判定
        //      直接失效）——优先认数据包 "Psy" 标签，其次 Boss 标签 + 同名；
        //   2) 本体实体在客户端未加载（超出实体同步距离）时，用记住的
        //      分身→本体 UUID 映射继续读本体数据：血条表里本体的条目，或
        //      客户端 scoreboard 上本体的 HealthPercent（below_name 展示槽，全量广播）；
        //   3) 彻底删掉旧"按名字借条目"的兜底——分身和本体同名，那条通道借到的
        //      往往正是分身自己的数据，就是用户看到的"变成自己的"。
        if (entity.entityTags().contains("PsyClone")) {
            // 服务端权威包已经把 Psy 本体的数值写到分身 UUID 上；客户端不能
            // 再强行改查本体 UUID，否则本体不在客户端加载范围时会错误回退到分身。
            if (ClientMobHealthCache.isFresh()) {
                MobHealthData authoritative = map.get(entity.getUUID());
                if (authoritative != null) return new ResolvedData(authoritative, entity.getUUID());
            }
            ResolvedData mirrored = resolvePsyCloneData(mc, entity, map);
            if (mirrored != null) return mirrored;
            // 纯客户端没有本体 UUID 时不能把分身自己的 HealthPercent 当成本体，
            // 否则就会出现“客户端看不到本体时，分身显示自己的血条”。宁可暂不显示，
            // 等本体进入 200 格关联范围或记忆映射恢复后再显示。
            if (!ClientMobHealthCache.isFresh()) return null;
        }

        // v8.5.9 · 每只怪的数据严格独立：只按自己的 UUID 取数（服务端 cache 与本地
        // map 都以实体 UUID 为键）。取不到 = 本帧没有这只怪的血量数据 = 不画条，
        // 绝不按名字借用别的怪的条目——同名怪联动从机制上不再可能。
        boolean serverAuthoritative = ClientMobHealthCache.isFresh();
        MobHealthData byId = map.get(entity.getUUID());
        if (byId != null && serverAuthoritative) {
            return new ResolvedData(byId, entity.getUUID());
        }

        // v8.6.0 · 纯客户端（服务器没装本 mod）取数顺序：
        //   1) 数据包正在锁定它（Targetting 标签）→ 本地 map 里那条有绝对数值的
        //      bossbar 数据（"146/200"）；
        //   2) 否则读客户端 scoreboard 上这只怪自己的 HealthPercent（数据包把它放在
        //      below_name 展示槽，服务端会广播全部 holder 的分数）→ 逐怪独立画条，
        //      数字列退化为 "73%"。同名怪挤在一起时锁定 bar 在怪之间来回跳也只会
        //      让"数字条"换宿主，百分比条永远各画各的——不再有"血条统一"。
        //   3) 两者都没有 → 不画。
        if (!serverAuthoritative) {
            if (byId != null && byId.targetted && entity.entityTags().contains("Targetting")) {
                return new ResolvedData(byId, entity.getUUID());
            }
            MobHealthData pct = percentFallback(mc, entity);
            if (pct != null) return new ResolvedData(pct, entity.getUUID());
            if (byId != null && byId.targetted) return new ResolvedData(byId, entity.getUUID());
        }
        return null;
    }

    /**
     * v8.6.1 · PsyClone 血条镜像本体（用户拍板："本体不在屏幕上或分身离本体太远时，
     * 分身血条也必须跟着本体，不能变成分身自己的"）。
     *
     * <p>解析顺序：
     * <ol>
     *   <li>客户端在场实体里找最近的正式 Psy 本体（"Psy" 标签优先；其次 Boss 标签
     *       且与分身同名；最后名字含 "psy" 的 Boss），找到就记住 分身UUID→本体UUID；</li>
     *   <li>有本体 UUID → 先取血条表里的本体条目（服务端 payload / 本地 bossbar 通道，
     *       绝对数值 "75/75"）；取不到就用本体实体的 HealthPercent 分数（"73%"）；</li>
     *   <li>本体实体没加载 → 用记住的 UUID 查血条表，再查客户端 scoreboard
     *       （holder 名 = UUID 字符串，分数不随实体卸载消失）；</li>
     *   <li>全部落空返回 null；纯客户端不再回落到分身自己的条目，避免错显示。</li>
     * </ol>
     */
    private static ResolvedData resolvePsyCloneData(Minecraft mc, Entity clone,
                                                    Map<UUID, MobHealthData> map) {
        Entity boss = findPsyBossEntity(mc, clone);
        if (boss != null) {
            if (PSY_BOSS_MEMORY.size() > 64) PSY_BOSS_MEMORY.clear();
            PSY_BOSS_MEMORY.put(clone.getUUID(), boss.getUUID());
        } else {
            UUID remembered = PSY_BOSS_MEMORY.get(clone.getUUID());
            if (remembered != null) boss = mc.level.getEntity(remembered);
        }
        UUID bossUuid = boss != null ? boss.getUUID() : PSY_BOSS_MEMORY.get(clone.getUUID());
        if (bossUuid == null) return null;

        MobHealthData bossData = map.get(bossUuid);
        if (bossData != null) return new ResolvedData(bossData, bossUuid);

        if (boss != null) {
            MobHealthData pct = percentFallback(mc, boss);
            if (pct != null) return new ResolvedData(pct, bossUuid);
        }
        // 本体实体没加载：scoreboard 分数按 holder 名（UUID 字符串）仍可读。
        MobHealthData pct = percentFallbackByName(mc, bossUuid, clone.getDisplayName());
        if (pct != null) return new ResolvedData(pct, bossUuid);
        return null;
    }

    /** 分身→本体 UUID 记忆：本体离开客户端加载范围后仍能持续镜像本体血量。 */
    private static final Map<UUID, UUID> PSY_BOSS_MEMORY = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 找最近的正式 Psy 本体。v8.6.1 判定放宽：
     *   1) 带数据包 "Psy" 标签且不带 PsyClone（最可靠，标签由数据包写在 Boss 上）；
     *   2) 带 Boss 标签、名字与分身显示名相同（本体的 scoreboard/CustomName 同源）；
     *   3) 带 Boss 标签且名字含 "psy"（英文客户端旧行为）。
     */
    private static Entity findPsyBossEntity(Minecraft mc, Entity clone) {
        String cloneName = clone.getName().getString();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB search = clone.getBoundingBox().inflate(PSY_SEARCH_RADIUS);
        for (Entity candidate : mc.level.getEntities((Entity) null, search, other -> true)) {
            if (candidate == clone || candidate.isRemoved() || !candidate.isAlive()) continue;
            if (!(candidate instanceof LivingEntity)) continue; // marker / NPC 假实体不算本体
            if (candidate.entityTags().contains("PsyClone")) continue;
            boolean isPsy = candidate.entityTags().contains("Psy");
            if (!isPsy && !candidate.entityTags().contains("Boss")) continue;
            if (!isPsy) {
                String candidateName = candidate.getName().getString();
                boolean nameOk = (cloneName != null && candidateName != null
                        && cloneName.equalsIgnoreCase(candidateName.trim()))
                        || (candidateName != null
                        && candidateName.toLowerCase(Locale.ROOT).contains("psy"));
                if (!nameOk) continue;
            }
            double distance = candidate.distanceToSqr(clone);
            if (distance > PSY_SEARCH_RADIUS * PSY_SEARCH_RADIUS) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * v8.6.0 · 客户端 scoreboard {@code HealthPercent}（0..100）合成逐怪独立条目。
     * 只对数据包管控的敌对实体生效（E / Boss / Targetting 标签，避开友军 NPC）；
     * objective 不存在（非 CTT 地图 / 没上展示槽）、实体没有分数时返回 null 不画条。
     */
    private static MobHealthData percentFallback(Minecraft mc, Entity entity) {
        if (entity instanceof Player || entity instanceof ArmorStand) return null;
        if (!(entity instanceof LivingEntity)) return null;
        if (!entity.isAlive() || entity.isRemoved()) return null;
        if (!entity.entityTags().contains("E") && !entity.entityTags().contains("Boss")
                && !entity.entityTags().contains("Targetting")) return null;
        int pct = healthPercentOf(mc, entity);
        if (pct < 0) return null;
        Component name = entity.getDisplayName();
        if (name == null) name = entity.getName();
        if (name == null) name = Component.empty();
        MobHealthData d = new MobHealthData(name, Component.empty(), pct, 100, 0L);
        d.percentOnly = true;
        return d;
    }

    /**
     * v8.6.1 · 本体实体已不在客户端加载范围时的镜像通道：scoreboard 的 holder 名
     * 就是实体 UUID 字符串，分数不随实体卸载清理，按名字直接取 HealthPercent。
     */
    private static MobHealthData percentFallbackByName(Minecraft mc, java.util.UUID holderUuid,
                                                       Component displayName) {
        int pct = healthPercentOf(mc, net.minecraft.world.scores.ScoreHolder.forNameOnly(holderUuid.toString()));
        if (pct < 0) return null;
        MobHealthData d = new MobHealthData(displayName == null ? Component.empty() : displayName,
                Component.empty(), pct, 100, 0L);
        d.percentOnly = true;
        return d;
    }

    /** HealthPercent objective 读数；objective 缺失 / 无分数返回 -1。 */
    private static int healthPercentOf(Minecraft mc, net.minecraft.world.scores.ScoreHolder holder) {
        Scoreboard sb = mc.level.getScoreboard();
        if (sb == null) return -1;
        Objective obj = sb.getObjective("HealthPercent");
        if (obj == null) return -1;
        ReadOnlyScoreInfo info = sb.getPlayerScoreInfo(holder, obj);
        if (info == null) return -1;
        return Math.max(0, Math.min(100, info.value()));
    }

    /** 世界切换 / 断线时与 gizmo 通道保持一致的清理点。 */
    public static void reset() {
        BUF.clear();
        PSY_BOSS_MEMORY.clear();
    }
}
