/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.kittyware.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.kittyware.KittyWareWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;

public class WKittyWareView extends WView implements KittyWareWidget
{
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta)
    {
        if (canScroll && hasScrollBar)
        {
            renderer.quad(handleX(), handleY(), handleWidth(), handleHeight(), theme().scrollbarColor.get(handlePressed, handleMouseOver));
        }
    }
}
