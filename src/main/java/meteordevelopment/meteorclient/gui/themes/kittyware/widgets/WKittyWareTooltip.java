/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.kittyware.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.kittyware.KittyWareWidget;
import meteordevelopment.meteorclient.gui.widgets.WTooltip;

public class WKittyWareTooltip extends WTooltip implements KittyWareWidget
{
    public WKittyWareTooltip(String text)
    {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta)
    {
        renderer.quad(this, theme().backgroundColor.get());
    }
}
