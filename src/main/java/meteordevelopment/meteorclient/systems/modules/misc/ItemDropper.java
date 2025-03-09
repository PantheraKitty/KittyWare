package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

public final class ItemDropper extends Module
{
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> delaySetting = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .description("description")
        .defaultValue(0)
        .min(0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<List<Item>> itemsSetting = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("description")
        .defaultValue()
        .build()
    );

    private final Timer delayTimer = new Timer();

    public ItemDropper()
    {
        super(Categories.Misc, "item-dropper", "description");
    }

    @Override
    public void onActivate()
    {
        delayTimer.reset();
    }

    @EventHandler
    public void onUpdate(final TickEvent.Post event)
    {
        if (!delayTimer.passedS(delaySetting.get()) || !(mc.player.currentScreenHandler instanceof HopperScreenHandler hopperScreenHandler))
        {
            return;
        }

        for (int slot = 0; slot < 5; slot++)
        {
            final ItemStack stack = hopperScreenHandler.getSlot(slot).getStack();

            if (stack.isEmpty())
            {
                continue;
            }

            for (final Item item : itemsSetting.get())
            {
                if (!stack.isOf(item))
                {
                    continue;
                }

                dropOneItem(slot);
                System.out.println(slot);
                delayTimer.reset();
                break;
            }
            break;
        }
    }

    private void dropOneItem(final int slot)
    {
        if (mc.interactionManager == null || mc.player == null)
        {
            return;
        }

        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId, slot, 0, SlotActionType.THROW, mc.player
        );
    }
}
