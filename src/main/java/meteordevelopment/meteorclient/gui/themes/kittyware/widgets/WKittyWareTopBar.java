/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.kittyware.widgets;

import meteordevelopment.meteorclient.gui.themes.kittyware.KittyWareWidget;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WKittyWareTopBar extends WTopBar implements KittyWareWidget
{
    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered)
    {
        return theme().backgroundColor.get(pressed, hovered);
    }

    @Override
    protected Color getNameColor()
    {
        return theme().textColor.get();
    }
}
