package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.server.PlayerDamageStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * v6.3.1 · 伤害分配面板交互屏幕（纯 overlay，不画背景）。
 *
 * <p>L 键打开；ESC / 再按 L / 点击面板外部关闭。标题栏可拖拽移动面板位置。
 * 相比 v6.3.0：删除半透明屏幕背景，点击操作全部委托给
 * {@link DamagePanelRenderer#hitTestButton / handleButton}，和"聊天栏点击按钮"走同一条路径。
 */
public final class DamagePanelScreen extends Screen {

    private boolean dragging;
    private int dragDX;
    private int dragDY;

    public DamagePanelScreen() {
        super(Component.literal("CTT 伤害分配面板"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (minecraft == null) return;
        // 不画任何屏幕遮罩——保持游戏画面清晰
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // v6.6.0 · M1 · 切片：根据 ModConfig.damagePanelStageScope 取 session 总或当前关
        PlayerDamageStats.Snapshot snap = DamagePanelRenderer.currentScopedSnapshot();

        // 拖拽中：实时更新 ModConfig 坐标百分比（松手时 mouseReleased 保存）
        if (dragging) {
            ModConfig cfg = ModConfig.INSTANCE;
            int newX = Math.max(0, Math.min(this.width - DamagePanelRenderer.PANEL_WIDTH, mouseX - dragDX));
            int newY = Math.max(0, Math.min(this.height - 20, mouseY - dragDY));
            cfg.damagePanelX = (float) newX / Math.max(1, this.width);
            cfg.damagePanelY = (float) newY / Math.max(1, this.height);
        }

        int hovered = DamagePanelRenderer.hitTestButton(mouseX, mouseY, minecraft);
        int panelX = DamagePanelRenderer.currentPanelX(minecraft);
        int panelY = DamagePanelRenderer.currentPanelY(minecraft);
        DamagePanelRenderer.drawCore(ctx, this.font, snap,
                panelX, panelY, ModConfig.INSTANCE.damagePanelDetailed,
                hovered, true);

        if (hovered >= 0) {
            String[] tips = DamagePanelRenderer.buttonTooltips(snap);
            String tip = tips[hovered];
            int tipX = Math.min(this.width - this.font.width(tip) - 4, mouseX + 8);
            int tipY = mouseY + 12;
            ctx.text(this.font, Component.literal(tip), tipX, tipY, 0xFFFFE070, true);
        }

        ctx.text(this.font,
                Component.literal("拖 [+] 移动 · 点按钮控制 · L / ESC 关闭"),
                8, this.height - 12, 0xFFC0C0C0, true);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mx = event.x(); double my = event.y(); int button = event.button();
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubled);
        if (minecraft == null) return super.mouseClicked(event, doubled);

        int idx = DamagePanelRenderer.hitTestButton(mx, my, minecraft);
        if (idx >= 0) {
            DamagePanelRenderer.handleButton(idx, minecraft);
            return true;
        }

        int panelX = DamagePanelRenderer.currentPanelX(minecraft);
        int panelY = DamagePanelRenderer.currentPanelY(minecraft);
        if (onTitleBar((int) mx, (int) my, panelX, panelY) && !onButtonRow((int) mx, (int) my, panelX, panelY)) {
            dragging = true;
            dragDX = (int) mx - panelX;
            dragDY = (int) my - panelY;
            return true;
        }

        if (!onPanel((int) mx, (int) my, panelX, panelY)) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mx = event.x(); double my = event.y(); int button = event.button();
        if (dragging && button == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false;
            ModConfig.INSTANCE.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.keycode(); int scanCode = event.key(); int modifiers = event.modifiers();
        if (keyCode == InputConstants.KEY_L || keyCode == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // =========================================================================
    //  命中检测（本类内专用：拖拽区 / 面板区）
    // =========================================================================
    private boolean onPanel(int mx, int my, int panelX, int panelY) {
        // v6.6.0 · M1 · 用切片快照测高（CURRENT_STAGE 时空表更矮，命中精度对齐显示）
        int h = DamagePanelRenderer.measureHeight(
                DamagePanelRenderer.currentScopedSnapshot(), ModConfig.INSTANCE.damagePanelDetailed);
        return mx >= panelX && mx <= panelX + DamagePanelRenderer.PANEL_WIDTH
                && my >= panelY && my <= panelY + h;
    }

    private boolean onTitleBar(int mx, int my, int panelX, int panelY) {
        return mx >= panelX && mx <= panelX + DamagePanelRenderer.PANEL_WIDTH
                && my >= DamagePanelRenderer.titleBarTop(panelY)
                && my <  DamagePanelRenderer.titleBarBottom(panelY);
    }

    private boolean onButtonRow(int mx, int my, int panelX, int panelY) {
        int by = DamagePanelRenderer.buttonY(panelY);
        if (my < by || my >= by + DamagePanelRenderer.BTN_HEIGHT) return false;
        int firstX = DamagePanelRenderer.buttonX(panelX, 0);
        int lastX  = DamagePanelRenderer.buttonX(panelX, DamagePanelRenderer.BTN_COUNT - 1)
                    + DamagePanelRenderer.BTN_WIDTH;
        return mx >= firstX && mx < lastX;
    }

    /** 供主类判断 L 键时是否当前已经打开本 Screen。 */
    public static boolean isOpen(Minecraft mc) {
        return mc != null && mc.gui.screen() instanceof DamagePanelScreen;
    }
}
