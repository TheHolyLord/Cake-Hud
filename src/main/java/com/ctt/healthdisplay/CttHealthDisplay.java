package com.ctt.healthdisplay;

import com.ctt.healthdisplay.client.ClientConfigReloadCommand;
import com.ctt.healthdisplay.client.CdpPersistence;
import com.ctt.healthdisplay.client.ClientDamageProbe;
import com.ctt.healthdisplay.client.BerserkDetector;
import com.ctt.healthdisplay.client.CurseOfUnknownDetector;
import com.ctt.healthdisplay.client.HudRenderGate;
import com.ctt.healthdisplay.client.LoreRegistry;
import com.ctt.healthdisplay.client.LorePopupManager;
import com.ctt.healthdisplay.client.LoreTextDecorator;
import com.ctt.healthdisplay.client.ClientMobHealthCache;
import com.ctt.healthdisplay.client.ClientStageProbe;
import com.ctt.healthdisplay.client.ClientStatsCache;
import com.ctt.healthdisplay.client.ClientStatsPushCache;
import com.ctt.healthdisplay.client.ClientTeamHeartsCache;
import com.ctt.healthdisplay.client.ServerChatNoiseFilter;
import com.ctt.healthdisplay.client.TriggerEchoFilter;
import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.health.HealthData;
import com.ctt.healthdisplay.health.MobHealthData;
import com.ctt.healthdisplay.health.StatsData;
import com.ctt.healthdisplay.hud.ClientStageLocation;
import com.ctt.healthdisplay.hud.DamagePanelRenderer;
import com.ctt.healthdisplay.hud.TeammateWorldRenderer;
import com.ctt.healthdisplay.hud.DamagePanelScreen;
import com.ctt.healthdisplay.hud.HealthBarRenderer;
import com.ctt.healthdisplay.hud.MobBarScreenRenderer;
import com.ctt.healthdisplay.hud.StageNameRegistry;
import com.ctt.healthdisplay.hud.StatsRenderer;
import com.ctt.healthdisplay.hud.StatsTableScreen;
import com.ctt.healthdisplay.network.MobHealthPayload;
import com.ctt.healthdisplay.network.PlayerStatsPayload;
import com.ctt.healthdisplay.network.StagePayload;
import com.ctt.healthdisplay.network.StatsSnapshotPayload;
import com.ctt.healthdisplay.network.TeamHeartsPayload;
import com.ctt.healthdisplay.server.DamageProbe;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CttHealthDisplay implements ClientModInitializer {

    public static final String MOD_ID = "ctt-health-display";
    private static final KeyMapping.Category CTT_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("ctt-health-display", "category"));
    public static boolean hudEnabled = true;
    public static boolean hideBossBars = false;

    private static final HealthData healthData = new HealthData();
    public static final StatsData statsData = new StatsData();
    private static KeyMapping openConfigKey;
    private static KeyMapping toggleHudKey;
    private static KeyMapping toggleStatsKey;
    private static KeyMapping toggleBossBarsKey;
    private static KeyMapping toggleDmgSessionKey;
    // v6.6.0 · M4 · 统计表格面板键。设计文档原拍板用 K，但 K 已绑 toggleBossBarsKey；
    // 这里临时用 N，玩家在控制设置里可重绑。
    private static KeyMapping toggleStatsTableKey;
    private static int autoStatsTimer = 0;
    private static long tickCounter = 0;
    private static long lastRefreshTick = 0;
    private static int prevAllHearts = -1;
    private static final long MIN_REFRESH_INTERVAL_TICKS = 20;
    // 瞬间治疗触发属性刷新的最小增量：只对"喝治疗药水"这种一次性回血 ≥ 25 的场景敏感，
    // 避免 buff 回血、自然再生这种慢速 tick 积分也刷属性栏。
    private static final int BIG_HEAL_THRESHOLD = 25;
    // 仅在 mob 离开视野/死亡后，再等多少 tick 才从血条记录里清理。
    // 给区块加载抖动一个缓冲，避免"同名怪物之一短暂不可见 → 记录丢失 → 回到视野时用错误 bar.hp 重新初始化"的错位 bug。
    private static final long MOB_ENTRY_STALE_TICKS = 100; // 5s
    private static long lastPartyBossbarToggleTick = -99999;
    private static final long PARTY_BOSSBAR_TOGGLE_COOLDOWN_TICKS = 60; // 3s：两次自动 toggle 最少间隔（v5.1.13）
    // 熔断：癫狂等特殊效果会让 team bar 持续不可解析，避免把 toggle 累积发到被服务端踢出。
    // 连续尝试 MAX_FAILED 次后依然没检测到 team bar → 进入 LONG 冷却，期间完全不发命令。
    // 一旦 hasTeamBossBar 再次为 true，计数立刻归零，熔断解除。
    private static int partyBossbarFailedToggles = 0;
    private static long partyBossbarFailsafeUntilTick = -1;
    private static final int MAX_FAILED_PARTY_BOSSBAR_TOGGLES = 5;
    private static final long PARTY_BOSSBAR_LONG_COOLDOWN_TICKS = 12000; // 10 分钟

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        ClientConfigReloadCommand.register();
        // v6.5.7 · 关卡 ID → 本地化名称硬编码表（来自 scripts/gen_stage_name_map.py 生成）。
        StageNameRegistry.load();
        healthData.setStatsData(statsData);

        // v6.5.6 · 接收服务端推送的关卡位置 payload，缓存到 ClientStageLocation 供 HUD 读。
        // payload type 必须双端都注册（server side 在 CttStatsServer.onInitialize），不然会握手失败。
        ClientPlayNetworking.registerGlobalReceiver(StagePayload.ID, (payload, ctx) -> {
            ClientStageLocation.onPayload(payload);
        });

        // v6.6.5 · M6 · 接收 stats 全量快照 payload。集成服务器场景下 ClientStatsCache
        // 的 isIntegrated() 直读路径会优先生效（payload 也会收到但被忽略），
        // 此分支主要服务于专用服务器 / LAN 远程客户端。
        // 客户端侧也注册一次 PayloadType（与 CttStatsServer 服务端注册对称）：
        // 集成服务器单 JVM 下 server entrypoint 已注册过，重复注册会抛异常 →
        // 只在客户端独立 JVM（dedicated client）需要这条；为安全 try-catch。
        try {
            PayloadTypeRegistry.clientboundPlay().register(StatsSnapshotPayload.ID, StatsSnapshotPayload.CODEC);
        } catch (IllegalArgumentException dup) {
            // 集成 server / LAN host 场景：CttStatsServer.onInitialize 已注册过，忽略
        }
        ClientPlayNetworking.registerGlobalReceiver(StatsSnapshotPayload.ID, (payload, ctx) -> {
            // v8.1.1 · 未来版本服务端 (ver > CURRENT_VERSION) → 当前客户端：codec 已经把不可解
            // 析的帧降级为空快照（ver 字段沿用原值），这里识别后直接丢弃，让 latest 保留上一帧
            // 真实数据，避免 UI 间歇性闪空。下次服务端降级 / 客户端升级后自动恢复。
            if (Byte.toUnsignedInt(payload.version()) > Byte.toUnsignedInt(StatsSnapshotPayload.CURRENT_VERSION)) {
                return;
            }
            ClientStatsCache.update(payload);
        });

        // v8.3.0 · M7 · 注册 mob 血量 S2C payload + receiver。集成服务器场景下 server
        // entrypoint 已经注册过 payload type（同 JVM 单例），重复注册会抛 IAE，try-catch 吞掉。
        // 纯 dedicated client / LAN 远端客户端独立 JVM 必须在这里注册才能完成握手。
        try {
            PayloadTypeRegistry.clientboundPlay().register(MobHealthPayload.ID, MobHealthPayload.CODEC);
        } catch (IllegalArgumentException dup) {
            // 集成服务器：CttStatsServer.onInitialize 已注册，忽略
        }
        ClientPlayNetworking.registerGlobalReceiver(MobHealthPayload.ID, (payload, ctx) -> {
            // 未来版本防御：codec 已把 ver > CURRENT_VERSION 的包降级成空 entries，
            // 这里识别后丢弃不刷 lastPushMs，让 isFresh 顺其自然过期回落本地路径。
            if (Byte.toUnsignedInt(payload.version()) > Byte.toUnsignedInt(MobHealthPayload.CURRENT_VERSION)) {
                return;
            }
            ClientMobHealthCache.onPayload(payload);
        });

        var npcType = com.ctt.healthdisplay.network.NpcHealthVisibilityPayload.ID;
        try {
            PayloadTypeRegistry.clientboundPlay().register(npcType, com.ctt.healthdisplay.network.NpcHealthVisibilityPayload.CODEC);
        } catch (IllegalArgumentException duplicate) {
            // 单人游戏中, 服务端入口可能已注册。
        }
        ClientPlayNetworking.registerGlobalReceiver(npcType, (payload, ctx) -> {
            ClientMobHealthCache.recordNpcs(payload.entities());
        });

        // v8.4.0 · 服务端属性 push payload。集成服务器 / dedicated client 双注册保险。
        // receiver 切到客户端线程灌 StatsData —— 该对象的字段同时被 HudRenderCallback /
        // ClientReceiveMessageEvents 读写，必须走 client.execute 保证单线程。
        try {
            PayloadTypeRegistry.clientboundPlay().register(PlayerStatsPayload.ID, PlayerStatsPayload.CODEC);
        } catch (IllegalArgumentException dup) {
            // 集成服务器：CttStatsServer.onInitialize 已注册，忽略
        }
        ClientPlayNetworking.registerGlobalReceiver(PlayerStatsPayload.ID, (payload, ctx) -> {
            if (Byte.toUnsignedInt(payload.version()) > Byte.toUnsignedInt(PlayerStatsPayload.CURRENT_VERSION)) {
                return;
            }
            ctx.client().execute(() -> {
                statsData.applyServerSnapshot(
                        payload.redHearts(), payload.soulHearts(),
                        payload.blackHearts(), payload.blueHearts(),
                        payload.crackedHearts(), payload.pinkHearts(),
                        payload.lines());
                ClientStatsPushCache.recordPush();
            });
        });

        // v8.4.0 · 全队四色心 push payload。snapshot 是 volatile + immutable map，
        // receiver 直接调 onPayload 即可（与 ClientMobHealthCache 同款无锁模式）。
        try {
            PayloadTypeRegistry.clientboundPlay().register(TeamHeartsPayload.ID, TeamHeartsPayload.CODEC);
        } catch (IllegalArgumentException dup) {
            // 集成服务器：CttStatsServer.onInitialize 已注册，忽略
        }
        ClientPlayNetworking.registerGlobalReceiver(TeamHeartsPayload.ID, (payload, ctx) -> {
            if (Byte.toUnsignedInt(payload.version()) > Byte.toUnsignedInt(TeamHeartsPayload.CURRENT_VERSION)) {
                return;
            }
            ClientTeamHeartsCache.onPayload(payload);
        });

        // 切服 / 断线时清缓存，避免悬挂上一局数据。
        // v8.1.0 起 ClientDamageProbe.resetForDisconnect 内部会先 freezeCurrentStage +
        // CdpPersistence.save，再清内存——这里调用方不需要额外操作。
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientStageLocation.reset();
            ClientStageProbe.reset();
            healthData.resetCoinData();
            ClientStatsCache.reset();
            ClientDamageProbe.INSTANCE.resetForDisconnect();
            com.ctt.healthdisplay.client.ClientStageDetector.onDisconnect();
            // v8.3.0 · M7 · 清 mob 血量缓存，切服后下一局起 isFresh=false 从头累积
            ClientMobHealthCache.reset();
            // v8.4.0 · 清服务端属性 push / 全队四色心 cache。切到无 mod 服务端时确保
            // ClientStatsPushCache.isFresh() 立刻 false → 客户端 auto refresh 立即接管
            // /trigger ViewStats（避免空白期）；ClientTeamHeartsCache 同步清空，让队友
            // 头顶条立刻退回 v8.3.x 单色多槽。
            ClientStatsPushCache.reset();
            ClientTeamHeartsCache.reset();
            com.ctt.healthdisplay.client.ActionBarCapture.reset();
            // 头顶护甲聚合缓存同理：换服后旧怪的护甲段不该出现在新局的怪身上
            com.ctt.healthdisplay.client.MobArmorCollector.reset();
            BerserkDetector.reset();
            CurseOfUnknownDetector.reset();
        });

        // v8.1.0 · CDP 持久化生命周期钩子：
        //   - CLIENT_STARTED：游戏启动后第一时间把磁盘 JSON 灌进 ClientDamageProbe
        //   - CLIENT_STOPPING：玩家正常退出游戏前 freeze + save，避免丢"当前关进行中"
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ClientDamageProbe.INSTANCE.applySnapshot(CdpPersistence.load());
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ClientDamageProbe.INSTANCE.freezeCurrentStage();
            CdpPersistence.save(ClientDamageProbe.INSTANCE.captureSnapshot());
        });

        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ctt-health-display.open_config", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, CTT_CATEGORY));

        toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ctt-health-display.toggle_hud",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_H,
                CTT_CATEGORY
        ));

        toggleStatsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ctt-health-display.toggle_stats",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_J,
                CTT_CATEGORY
        ));

        toggleBossBarsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ctt-health-display.toggle_bossbars",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_K,
                CTT_CATEGORY
        ));

        // 26.3 的 KeyMapping.setAll() 会把键值直接交给 InputConstants.isKeyDown()；
        // GLFW_KEY_UNKNOWN（-1）会被当作 DirectByteBuffer 下标并在进世界时崩溃。
        // 因此这里使用不常用但合法的 F24 作为安全默认值，玩家仍可在控制设置里重绑。
        toggleDmgSessionKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ctt-health-display.toggle_dmg_session",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F24,
                CTT_CATEGORY
        ));
        // 兼容旧版本已经写入 options.txt 的 GLFW_KEY_UNKNOWN（-1）。
        // 只要读回来的绑定仍是 unknown，就迁移到合法的 F24，避免旧配置继续触发同一崩溃。
        if (toggleDmgSessionKey.isUnbound()) {
            toggleDmgSessionKey.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F24));
        }

        toggleStatsTableKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ctt-health-display.toggle_stats_table",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_N,
                CTT_CATEGORY
        ));

        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !LoreTextDecorator.intercept(command, 0xFFFFFF));
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !LoreTextDecorator.intercept(message, 0xFFFFFF));

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            // v8.1.7 · 优先吞掉本模组自发 /trigger 命令的回显（自己/队友/OP admin 广播都覆盖），
            // 避免 ViewStats / TogglePartyBossbar 每几秒刷一条淹没聊天（反馈 2026-05-01）。
            if (TriggerEchoFilter.shouldHide(message, overlay)) return false;
            // 再吞掉服务器/地图脚本主动 broadcast 的固定字面噪声（如 "[@] L"、被合并 mod
            // 折叠成 "[@] L (x137)" 的形态）；规则维护在 ServerChatNoiseFilter.MUTED_LINES。
            if (ServerChatNoiseFilter.shouldHide(message, overlay)) return false;
            boolean wasReady = statsData.hasData();
            boolean hide = statsData.processMessage(message, overlay);
            return !hide;
        });

        cttRegisterLateEvents();
        LoreRegistry.registerReloadListener();
    }

    private static void cttExtractHud(net.minecraft.client.gui.GuiGraphicsExtractor context, net.minecraft.client.DeltaTracker tickCounter) {
            if (HudRenderGate.shouldSuppressModHud(statsData)) {
                MobBarScreenRenderer.render(context, tickCounter);
                return;
            }

            if (hudEnabled) {
                HealthBarRenderer.render(context, tickCounter, healthData, statsData);
            }
            var client = net.minecraft.client.Minecraft.getInstance();
            if (ModConfig.INSTANCE.showStatsOutsideInventory && !client.gui.hud.isHidden()
                    && !StatsRenderer.isPlayerInventory(client.gui.screen())) {
                StatsRenderer.render(context, statsData);
            }
            // v6.3.1 · 伤害分配面板：HUD 只读绘制。
            //   - 专属 DamagePanelScreen 自己会画，不要重复。
            //   - 其他 Screen（聊天 T / 背包 E / 暂停 ESC 等）打开时继续画，
            //     鼠标坐标从 mc.mouseHandler 取，绘出按钮 hover 效果。点击由 ScreenMouseEvents 接管。
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.gui.screen() instanceof DamagePanelScreen) {
                // 即使伤害面板单独接管绘制，也要让怪物血条留在 HUD 最顶层。
                MobBarScreenRenderer.render(context, tickCounter);
                return;
            }

            double mouseX = -1, mouseY = -1;
            if (mc.gui.screen() != null && mc.getWindow() != null) {
                // 26.3: MouseHandler 直接提供 GUI 缩放后的坐标
                mouseX = mc.mouseHandler.getScaledXPos(mc.getWindow());
                mouseY = mc.mouseHandler.getScaledYPos(mc.getWindow());
            }
            // v6.6.7 · 拖拽 tick：聊天栏 / 背包 / 暂停等 Screen 下持续追鼠标更新面板坐标。
            // DamagePanelScreen 本身在自己的 render 里调 tickDrag，HudRenderCallback 这里跳过它。
            if (DamagePanelRenderer.isDragging() && mc.gui.screen() != null
                    && !(mc.gui.screen() instanceof DamagePanelScreen)) {
                DamagePanelRenderer.tickDrag(mc, mouseX, mouseY);
            }
            DamagePanelRenderer.drawHud(context, mc, mouseX, mouseY);
            // 最后绘制怪物头顶条，确保它位于所有普通 HUD 内容之上。
            MobBarScreenRenderer.render(context, tickCounter);
    }

    private static void cttRegisterLateEvents() {
        // 头顶血条必须在 vanilla BossBar 之前提交：这样血条仍固定在敌人头顶，
        // 但 BossBar 的屏幕层级更高，不会被它覆盖，也不需要隐藏或移动血条。
        Identifier mainHudId = Identifier.fromNamespaceAndPath(MOD_ID, "main_hud");
        HudElementRegistry.attachElementBefore(VanillaHudElements.BOSS_BAR, mainHudId, new HudElement() {
            @Override
            public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context, net.minecraft.client.DeltaTracker tickCounter) {
                cttExtractHud(context, tickCounter);
            }
        });

        // v8.6.1 · 盾牌冷却条单独成层：main_hud 整体被 HudRenderGate（癫狂 / Curse of
        // the Unknown）压掉时它照样绘制——用户拍板"盾牌冷却条不会因这两个状态消失"。
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "shield_cooldown"), new HudElement() {
            @Override
            public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context, net.minecraft.client.DeltaTracker tickCounter) {
                com.ctt.healthdisplay.hud.ShieldCooldownRenderer.render(context, tickCounter);
            }
        });

        // 弓箭预览紧跟原版快捷栏绘制，确保独立物品格与快捷栏始终对齐。
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath(MOD_ID, "arrow_preview"), new HudElement() {
            @Override
            public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context, net.minecraft.client.DeltaTracker tickCounter) {
                com.ctt.healthdisplay.hud.ArrowPreviewRenderer.render(context, tickCounter);
            }
        });

        // v8.4.8 · 26.3：世界空间头顶血条走官方 gizmos 通道的【每帧渲染线程】收集器
        // （LevelRenderer.collectPerFrameRenderThreadGizmos，本帧 finalize 排空，不跨帧堆积）。
        // 之前误用 MC.collectPerTickGizmos()（按游戏刻排空），每帧提交导致 tick 内堆积
        // 几十份不同相机姿态的副本：移动视角时抖动、半透明边框叠色发暗。
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BEFORE_GIZMOS.register(
                TeammateWorldRenderer::render);

        // v6.3.1 · 任意 Screen（聊天 T / 背包 E / 暂停 ESC ...）打开时，
        //   注册鼠标点击 hook：点中 HUD 面板按钮即执行按钮动作并阻止 Screen 接收。
        //   DamagePanelScreen 本身由自己处理，跳过。
        // v6.6.7 · 同时支持拖拽：点中标题栏 (非按钮区) 开始拖；松开时结束。
        ScreenEvents.AFTER_INIT.register((mc, screen, scaledWidth, scaledHeight) -> {
            if (StatsRenderer.isPlayerInventory(screen)) {
                ScreenEvents.afterExtract(screen).register((sc, context, mouseX, mouseY, delta) -> {
                    if (!ModConfig.INSTANCE.showStatsInInventory || HudRenderGate.shouldSuppressModHud(statsData)) return;
                    context.nextStratum();
                    StatsRenderer.render(context, statsData);
                });
            }
            if (screen instanceof DamagePanelScreen) return;
            ScreenMouseEvents.allowMouseClick(screen).register((sc, ev) -> {
                if (ev.button() != 0) return true;
                if (!ModConfig.INSTANCE.damagePanelHudVisible) return true;
                double mx = ev.x(), my = ev.y();
                int idx = DamagePanelRenderer.hitTestButton(mx, my, mc);
                if (idx >= 0) {
                    DamagePanelRenderer.handleButton(idx, mc);
                    return false; // 拦截，不让聊天栏 / 背包等消费这次点击
                }
                if (DamagePanelRenderer.hitTestTitleBarDraggable(mx, my, mc)) {
                    DamagePanelRenderer.beginDrag(mc, mx, my);
                    return false; // 拦截，避免 vanilla Screen 误处理
                }
                return true;
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((sc, ev) -> {
                if (ev.button() == 0 && DamagePanelRenderer.isDragging()) {
                    DamagePanelRenderer.endDrag();
                    return false;
                }
                return true;
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            while (openConfigKey.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    client.gui.setScreen(new com.ctt.healthdisplay.config.ConfigScreen(null));
                }
            }
            LorePopupManager.clientTick(client);

            // Keep HealthData fresh even when HUD is off, so we can auto-toggle party bossbar.
            if (client.player != null && client.level != null) {
                healthData.update();
                BerserkDetector.tick(client, statsData);
                // v8.5.8 · "Curse of the Unknown" 诅咒房间：数据包把个人状态 bossbar 换成
                // 乱码翻译键文本，识别到就与癫狂一样隐藏本模组全部 UI。
                CurseOfUnknownDetector.tick(client);
                maybeAutoTogglePartyBossbar(client);
            }

            // 先应用原版计分板中最新的关卡变化，再按关卡归集这一刻的伤害。
            ClientStageProbe.tick(client);
            ClientDamageProbe.INSTANCE.onClientTick(client);

            // ALL / NEAREST 两档都要维持 mobHealthMap：NEAREST 档只是渲染时过滤非 targetted 的条，
            // 数据本身仍需更新，这样切换档位时立刻就能显示正确血量。
            if (ModConfig.INSTANCE.isMobHeadHPEnabled() && client.level != null) {
                updateMobTracking(client);
            }

            if (toggleHudKey.consumeClick()) {
                hudEnabled = !hudEnabled;
                if (client.player != null) {
                    client.gui.hud.setOverlayMessage(
                            Component.literal("CTT Health Display: " + (hudEnabled ? "ON" : "OFF")), false);
                }
            }
            if (toggleStatsKey.consumeClick()) {
                ModConfig cfg = ModConfig.INSTANCE;
                cfg.showStatsOutsideInventory = !cfg.showStatsOutsideInventory;
                cfg.save();
                if (client.player != null) {
                    var state = Component.translatable(cfg.showStatsOutsideInventory
                            ? "ctt-health-display.config.value.on" : "ctt-health-display.config.value.off");
                    client.gui.hud.setOverlayMessage(Component.translatable(
                            "ctt-health-display.config.option.stats_outside_inventory", state), false);
                }
            }
            if (toggleBossBarsKey.consumeClick()) {
                ModConfig cfg = ModConfig.INSTANCE;
                boolean allHidden = cfg.hidePersonalBar && cfg.hideTeamBar && cfg.hideMobBars;
                cfg.hidePersonalBar = !allHidden;
                cfg.hideTeamBar = !allHidden;
                cfg.hideMobBars = !allHidden;
                cfg.save();
                if (client.player != null) {
                    client.gui.hud.setOverlayMessage(
                            Component.literal("CTT Boss Bars: " + (!allHidden ? "\u9690\u85cf" : "\u663e\u793a")), false);
                }
            }
            if (toggleDmgSessionKey.consumeClick()) {
                handleDmgPanelToggle(client);
            }
            if (toggleStatsTableKey.consumeClick()) {
                handleStatsTableToggle(client);
            }
            // v6.3.0 · actionbar 只在 session 活跃且面板 Screen 未打开时显示（避免遮挡）
            if (DamageProbe.isSessionActive() && !(client.gui.screen() instanceof DamagePanelScreen)
                    && !HudRenderGate.shouldSuppressModHud(statsData)) {
                renderDmgSessionActionbar(client);
            }
            ModConfig config = ModConfig.INSTANCE;
            // v8.4.0 · 服务端属性 push 接管的快路径：fresh 期间完全跳过本节，
            // 不发 /trigger ViewStats，也不累 autoStatsTimer，保证切服 fresh → stale 时
            // 不会因为残留 autoStatsTimer 立刻发命令。prevAllHearts 仍要刷新避免下次
            // fresh 失效时把"自从 fresh 起的全部 HP 变化"误判为单 tick 大跳。
            if (ClientStatsPushCache.isFresh() || ClientStatsPushCache.isServerMode()) {
                // 服务端属性推送一旦被确认，整个连接期间都不再发自动 /trigger ViewStats；
                // 即使某一瞬间网络延迟让 fresh 窗口过期，也不能回退到客户端定时指令。
                prevAllHearts = healthData.allHearts;
                autoStatsTimer = 0;
            } else if (config.autoRefreshStats
                    && !statsData.isCapturing()
                    && client.player != null && client.getConnection() != null) {

                int currentAllHearts = healthData.allHearts;
                int heartsDelta = (prevAllHearts > 0) ? (currentAllHearts - prevAllHearts) : 0;
                boolean hpDropped = prevAllHearts > 0 && heartsDelta < 0;
                boolean bigHeal = prevAllHearts > 0 && heartsDelta >= BIG_HEAL_THRESHOLD;
                prevAllHearts = currentAllHearts;

                boolean canRefresh = (tickCounter - lastRefreshTick) >= MIN_REFRESH_INTERVAL_TICKS;

                if ((hpDropped || bigHeal) && canRefresh) {
                    triggerViewStats(client);
                } else {
                    autoStatsTimer++;
                    int intervalTicks = config.autoRefreshIntervalSeconds * 20;
                    if (autoStatsTimer >= intervalTicks) {
                        triggerViewStats(client);
                    }
                }
            } else {
                autoStatsTimer = 0;
            }
        });
    }

    // ========================================================================
    //  v6.6.0 hotfix · L 键 = 显示 / 隐藏 HUD 上的伤害分配面板。
    //
    //  历史：v6.3.0 L 键打开 DamagePanelScreen 交互菜单（▶/⊘/⏸/详情/切片 按钮）。
    //  v6.6.0 hotfix：服务器启动后默认 live=true，T1F1 自动 clear，玩家不需要手动
    //  start/stop/clear，菜单上的按钮失去日常用途；L 键改为更直觉的"显示/隐藏面板"。
    //
    //  保留：DamagePanelScreen 类未删除——若以后要拖拽面板位置仍可由别处入口（ConfigScreen
    //  或 Shift+L）打开。其他屏幕（聊天/背包）打开时点 HUD 上按钮的 hover/click 路径
    //  仍走 ScreenMouseEvents，按钮功能未失。
    // ========================================================================

    private static void handleDmgPanelToggle(net.minecraft.client.Minecraft client) {
        ModConfig cfg = ModConfig.INSTANCE;
        cfg.damagePanelHudVisible = !cfg.damagePanelHudVisible;
        cfg.save();
        if (client.player != null) {
            client.gui.hud.setOverlayMessage(
                    Component.literal("CTT \u4f24\u5bb3\u5206\u914d\u9762\u677f: "
                            + (cfg.damagePanelHudVisible ? "\u663e\u793a" : "\u9690\u85cf")), false);
        }
    }

    /** v6.6.0 · M4 · N 键 = 打开 / 关闭统计表格面板（设计 §6）。 */
    private static void handleStatsTableToggle(net.minecraft.client.Minecraft client) {
        if (StatsTableScreen.isOpen(client)) {
            client.gui.setScreen(null);
        } else {
            client.gui.setScreen(new StatsTableScreen());
        }
    }

    private static void renderDmgSessionActionbar(net.minecraft.client.Minecraft client) {
        if (client.player == null) return;
        long total = DamageProbe.getLiveTotal();
        int events = DamageProbe.getLiveEvents();
        double durSec = (System.currentTimeMillis() - DamageProbe.getSessionStartMs()) / 1000.0;
        String text = String.format("\u25c6 \u7edf\u8ba1\u4e2d: %d \u4f24\u5bb3 / %d \u547d\u4e2d / %.1fs",
                total, events, durSec);
        client.gui.hud.setOverlayMessage(
                Component.literal(text).withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), false);
    }

    private static void updateMobTracking(net.minecraft.client.Minecraft client) {
        // v8.3.0 · M7 · 服务端权威 mob 血量 cache 命中时整段客户端 bossbar → entity 近似
        // 搜索 + 本地 map 维护全部短路。反馈 2026-05-01 的 "Boss champion 场景头顶条闪烁"
        // 根因就是 vanilla bossbar 同一时刻只能锁一只（Boss 出现后永远锁 Boss），
        // 客户端拿不到其它同名 mob 的独立 HP；现在直接读服务端按距离排序的 32 条。
        if (ClientMobHealthCache.isFresh()) return;

        List<HealthData.MobBossBarEntry> bars = HealthData.getActiveMobBars();
        Map<UUID, MobHealthData> map = HealthData.getMobHealthMap();
        net.minecraft.world.phys.Vec3 playerPos = client.player.position();

        // 每个 bar 对应一只"当前服务端选中的同名 mob"。v8.5.8：数据包给正在被锁定的敌人
        // 挂了 Targetting 标签（客户端可读），优先按它精确认领这条 bar 的数据；没有标签
        // 信息时才退回"同名里最近的"近似——这样多只同名敌人时，被打的那只的数据不会
        // 写到别的敌人身上（修"攻击一只、其他同名怪血条跟着变"）。
        for (HealthData.MobBossBarEntry bar : bars) {
            String mobName = bar.name;

            Entity closest = null;
            double closestDist = Double.MAX_VALUE;
            int closestPriority = Integer.MIN_VALUE;
            for (Entity e : client.level.entitiesForRendering()) {
                if (e instanceof LivingEntity living
                        && !(e instanceof Player)
                        && !(e instanceof ArmorStand)
                        && living.isAlive()
                        && (!ModConfig.INSTANCE.suppressNpcHealth || !ClientMobHealthCache.isNpc(e))
                        && matchesName(e, mobName)) {
                    // Targetting = 数据包此刻锁定的敌人，最高优先；E 是敌人通用标签，
                    // Boss 是数据包给 boss 的额外优先级标签。客户端不一定能同步命令标签，
                    // 所以没有标签时仍保留名字 + 距离兜底。
                    int priority = e.entityTags().contains("Targetting") ? 4
                            : e.entityTags().contains("Boss") ? 2
                            : (e.entityTags().contains("E") ? 1 : 0);
                    double dist = e.position().distanceToSqr(playerPos);
                    if (priority > closestPriority
                            || (priority == closestPriority && dist < closestDist)) {
                        closestPriority = priority;
                        closestDist = dist;
                        closest = e;
                    }
                }
            }
            if (closest == null) continue;
            UUID closestUUID = closest.getUUID();

            // v8.3.6 · 切到 Component nameText：用 closest.getDisplayName() 拿到客户端已本地化的
            // Component（含 translate 键 + Style），渲染端用 OrderedText 直接画，颜色由 Style 携带，
            // 不再单独维护 nameColor int 字段。
            net.minecraft.network.chat.Component closestNameText = closest.getDisplayName();
            if (closestNameText == null) closestNameText = closest.getName();
            if (closestNameText == null) closestNameText = net.minecraft.network.chat.Component.literal(mobName);

            MobHealthData data = map.get(closestUUID);
            if (data == null) {
                data = new MobHealthData(closestNameText, bar.suffixText, bar.hp, bar.maxHP, tickCounter);
                map.put(closestUUID, data);
            } else {
                data.nameText = closestNameText;
                data.hp = bar.hp;
                data.maxHP = bar.maxHP;
                data.suffixText = bar.suffixText;
            }
            data.soulHearts = bar.soulHearts;
            data.blackHearts = bar.blackHearts;
            data.blueHearts = 0;
            data.targetted = true;
            data.lastUpdateTick = tickCounter;

            for (Map.Entry<UUID, MobHealthData> entry : map.entrySet()) {
                MobHealthData d = entry.getValue();
                if (!d.name().equals(mobName)) continue;
                if (!entry.getKey().equals(closestUUID)) {
                    d.targetted = false;
                }
            }
        }

        // 清理：仅当实体查不到且记录已陈旧（> MOB_ENTRY_STALE_TICKS 未被更新）才删，避免区块抖动丢数据。
        // 明确死亡 / 被移除的实体立即清理。
        if (tickCounter % 20 == 0 && client.level != null) {
            map.entrySet().removeIf(entry -> {
                Entity e = findEntityByUUID(client, entry.getKey());
                if (e != null && e.isAlive() && !e.isRemoved()) return false;
                if (e != null) return true; // 存在但死了 / 标记移除：立刻清
                return (tickCounter - entry.getValue().lastUpdateTick) > MOB_ENTRY_STALE_TICKS;
            });
        }
    }

    private static Entity findEntityByUUID(net.minecraft.client.Minecraft client, UUID uuid) {
        if (client.level == null) return null;
        for (Entity e : client.level.entitiesForRendering()) {
            if (e.getUUID().equals(uuid)) return e;
        }
        return null;
    }

    private static boolean matchesName(Entity entity, String bossBarName) {
        String entityName = entity.getName().getString().trim();
        String barName = bossBarName.trim();
        if (entityName.equalsIgnoreCase(barName)) return true;
        return entityName.contains(barName) || barName.contains(entityName);
    }

    private static void triggerViewStats(net.minecraft.client.Minecraft client) {
        if (client.getConnection() == null) return;
        autoStatsTimer = 0;
        lastRefreshTick = tickCounter;
        statsData.markAutoTriggered();
        client.getConnection().sendCommand("trigger ViewStats");
    }

    private static void maybeAutoTogglePartyBossbar(net.minecraft.client.Minecraft client) {
        if (client.getConnection() == null) return;

        // team bar 已经出现：目的达成，熔断计数归零。
        if (healthData.hasTeamBossBar) {
            partyBossbarFailedToggles = 0;
            partyBossbarFailsafeUntilTick = -1;
            return;
        }

        if (!healthData.hasPersonalBossBar) return;

        // 熔断长冷却期间：完全不发命令，避免癫狂等特殊效果导致命令被服务端 rate limit 踢出。
        if (tickCounter < partyBossbarFailsafeUntilTick) return;

        // 普通冷却：最少每 3 秒一次。
        if ((tickCounter - lastPartyBossbarToggleTick) < PARTY_BOSSBAR_TOGGLE_COOLDOWN_TICKS) return;

        lastPartyBossbarToggleTick = tickCounter;
        partyBossbarFailedToggles++;
        client.getConnection().sendCommand("trigger TogglePartyBossbar");

        // 连续尝试达到阈值仍未出现 team bar：进入长冷却（大概率玩家身上挂了癫狂之类状态，
        // 继续 toggle 也不会奏效，且会触发服务端 rate limit）。
        if (partyBossbarFailedToggles >= MAX_FAILED_PARTY_BOSSBAR_TOGGLES) {
            partyBossbarFailsafeUntilTick = tickCounter + PARTY_BOSSBAR_LONG_COOLDOWN_TICKS;
            partyBossbarFailedToggles = 0;
        }
    }
}
