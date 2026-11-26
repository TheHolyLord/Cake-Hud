package com.ctt.healthdisplay.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** TEMP stub（仅用于本地编译验证）：作者补交 config 包前让工程能编译。**不要发布**。 */
public class ConfigScreen extends Screen {

    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Cake Tower HUD Plus"));
        this.parent = parent;
    }

    public boolean beginPreviewDrag(double mouseX, double mouseY) {
        return false;
    }

    public void movePreviewDrag(double mouseX, double mouseY) {
    }

    public void endPreviewDrag() {
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}
