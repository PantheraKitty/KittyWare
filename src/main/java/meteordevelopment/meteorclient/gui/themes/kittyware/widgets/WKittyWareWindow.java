/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.kittyware.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.kittyware.KittyWareWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;

public class WKittyWareWindow extends WWindow implements KittyWareWidget
{
    public WKittyWareWindow(WWidget icon, String title)
    {
        super(icon, title);
    }

    @Override
    protected WHeader header(WWidget icon)
    {
        return new WKittyWarHeader(icon);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta)
    {
        if (expanded || animProgress > 0)
        {
            renderer.quad(x, y + header.height, width, height - header.height, theme().backgroundColor.get());
        }
    }

    private class WKittyWarHeader extends WHeader
    {
        public WKittyWarHeader(WWidget icon)
        {
            super(icon);
        }

        @Override
        public void init()
        {
            if (icon != null)
            {
                super.createList();
                add(icon).centerY();
            }

            if (beforeHeaderInit != null)
            {
                createList();
                beforeHeaderInit.accept(this);
            }

            add(theme.label(title, true)).expandCellX().centerY().pad(4);

            triangle = add(theme.triangle()).pad(4).right().centerY().widget();
            triangle.action = () -> setExpanded(!expanded);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta)
        {
            renderer.quad(this, theme().accentColor.get());
        }
    }
}
