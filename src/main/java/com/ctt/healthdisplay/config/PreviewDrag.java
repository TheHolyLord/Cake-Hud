package com.ctt.healthdisplay.config;

/** 由鼠标输入层捕获预览的按下、移动及松开。 */
public interface PreviewDrag {
    boolean beginPreviewDrag(double mouseX, double mouseY);
    void movePreviewDrag(double mouseX, double mouseY);
    void endPreviewDrag();
}
