/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.kittyware.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.kittyware.KittyWareWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;

public class WKittyWareSection extends WSection
{
    public WKittyWareSection(String title, boolean expanded, WWidget headerWidget)
    {
        super(title, expanded, headerWidget);
    }

    @Override
    protected WHeader createHeader()
    {
        return new WMeteorHeader(title);
    }

    protected static class WHeaderTriangle extends WTriangle implements KittyWareWidget
    {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta)
        {
            renderer.rotatedQuad(x, y, width, height, rotation, GuiRenderer.TRIANGLE, theme().textColor.get());
        }
    }

    protected class WMeteorHeader extends WHeader
    {
        private WTriangle triangle;

        public WMeteorHeader(String title)
        {
            super(title);
        }

        @Override
        public void init()
        {
            add(theme.horizontalSeparator(title)).expandX();

            if (headerWidget != null) add(headerWidget);

            triangle = new WHeaderTriangle();
            triangle.theme = theme;
            triangle.action = this::onClick;

            add(triangle);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta)
        {
            triangle.rotation = (1 - animProgress) * -90;
        }
    }
}
