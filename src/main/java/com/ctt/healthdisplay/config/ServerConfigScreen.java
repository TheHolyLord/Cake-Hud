package com.ctt.healthdisplay.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** TEMP stub（仅用于本地编译验证）：见 {@link ConfigScreen}。**不要发布**。 */
public class ServerConfigScreen extends Screen {

    private final Screen parent;

    public ServerConfigScreen(Screen parent) {
        super(Component.literal("Cake Tower HUD Plus - Server"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}
