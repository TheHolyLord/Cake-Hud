package com.ctt.healthdisplay.config;

import com.ctt.healthdisplay.CttHealthDisplay;
import com.ctt.healthdisplay.health.StatsData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本 fork 移植：属性面板「行过滤」界面。
 *
 * <p>把当前属性面板出现过的行标签列出来，逐个开关；隐藏列表存在
 * {@link ModConfig#hiddenStats}（按行标签匹配，跨关卡保留）。</p>
 */
public class StatsFilterScreen extends Screen {

    private final Screen parent;
    private int scroll;

    public StatsFilterScreen(Screen parent) {
        super(Component.translatable("ctt-health-display.config.title.stats_filter"));
        this.parent = parent;
    }

    /** 从当前属性面板的行里提取标签（"标签: 值" 取冒号前；没有冒号就整行）。 */
    private static List<String> labels() {
        Set<String> out = new LinkedHashSet<>();
        StatsData data = CttHealthDisplay.statsData;
        if (data != null && data.hasData()) {
            for (Component line : data.getLines()) {
                String plain = line.getString().trim();
                if (plain.isEmpty()) continue;
                int idx = plain.indexOf(':');
                int idx2 = plain.indexOf('：');
                int cut = idx < 0 ? idx2 : (idx2 < 0 ? idx : Math.min(idx, idx2));
                if (cut > 0) plain = plain.substring(0, cut).trim();
                out.add(plain);
            }
        }
        return new ArrayList<>(out);
    }

    @Override
    protected void init() {
        List<String> labels = labels();
        if (labels.isEmpty()) return;
        int x = this.width / 2 - 120;
        int y = 44 - this.scroll;
        for (String label : labels) {
            Button button = Button.builder(this.text(label), btn -> {
                List<String> hidden = ModConfig.INSTANCE.hiddenStats;
                if (hidden == null) {
                    hidden = new ArrayList<>();
                    ModConfig.INSTANCE.hiddenStats = hidden;
                }
                if (hidden.contains(label)) hidden.remove(label); else hidden.add(label);
                ModConfig.INSTANCE.save();
                btn.setMessage(this.text(label));
            }).bounds(x, y, 240, 20).build();
            this.addRenderableWidget(button);
            y += 24;
        }
        this.addRenderableWidget(Button.builder(Component.translatable("ctt-health-display.config.btn.done"),
                btn -> this.minecraft.gui.setScreen(this.parent))
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private Component text(String label) {
        boolean hidden = ModConfig.INSTANCE.hiddenStats != null
                && ModConfig.INSTANCE.hiddenStats.contains(label);
        return Component.literal((hidden ? "\u2716 " : "\u2714 ") + label);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
        List<String> labels = labels();
        if (labels.isEmpty()) {
            context.centeredText(this.font,
                    Component.translatable("ctt-health-display.config.hint.stats_filter_empty"),
                    this.width / 2, this.height / 2 - 10, 0xFF888888);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
