/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.themes.kittyware.widgets.pressable;

import meteordevelopment.meteorclient.gui.themes.kittyware.KittyWareWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WFavorite;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class WKittyWareFavorite extends WFavorite implements KittyWareWidget
{
    public WKittyWareFavorite(boolean checked)
    {
        super(checked);
    }

    @Override
    protected Color getColor()
    {
        return theme().favoriteColor.get();
    }
}
