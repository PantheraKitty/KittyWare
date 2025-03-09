/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.events.entity.player;

public class ClipAtLedgeEvent
{
    private static final ClipAtLedgeEvent INSTANCE = new ClipAtLedgeEvent();

    private boolean set, clip;

    public static ClipAtLedgeEvent get()
    {
        INSTANCE.reset();
        return INSTANCE;
    }

    public void reset()
    {
        set = false;
    }

    public boolean isSet()
    {
        return set;
    }

    public boolean isClip()
    {
        return clip;
    }

    public void setClip(boolean clip)
    {
        set = true;
        this.clip = clip;
    }
}
