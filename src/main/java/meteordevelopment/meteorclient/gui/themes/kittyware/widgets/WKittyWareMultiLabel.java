/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.kittyware.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.kittyware.KittyWareWidget;
import meteordevelopment.meteorclient.gui.widgets.WMultiLabel;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WKittyWareMultiLabel extends WMultiLabel implements KittyWareWidget
{
    public WKittyWareMultiLabel(String text, boolean title, double maxWidth)
    {
        super(text, title, maxWidth);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta)
    {
        double h = theme.textHeight(title);
        Color defaultColor = theme().textColor.get();

        for (int i = 0; i < lines.size(); i++)
        {
            renderer.text(lines.get(i), x, y + h * i, color != null ? color : defaultColor, false);
        }
    }
}
