/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.entity.PlayerDeathEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.PlayerJoinLeaveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.ArrayListDeque;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import static meteordevelopment.meteorclient.utils.player.ChatUtils.formatCoords;

public class Notifier extends Module {
    private final SettingGroup sgTotemPops = settings.createGroup("Totem Pops");
    private final SettingGroup sgVisualRange = settings.createGroup("Visual Range");
    private final SettingGroup sgPearl = settings.createGroup("Pearl");
    private final SettingGroup sgJoinsLeaves = settings.createGroup("Joins/Leaves");

    // Totem Pops

    private final Setting<Boolean> totemPops = sgTotemPops.add(new BoolSetting.Builder()
            .name("totem-pops").description("Notifies you when a player pops a totem.")
            .defaultValue(true).build());

    private final Setting<Boolean> totemsDistanceCheck =
            sgTotemPops.add(new BoolSetting.Builder().name("distance-check")
                    .description("Limits the distance in which the pops are recognized.")
                    .defaultValue(false).visible(totemPops::get).build());

    private final Setting<Integer> totemsDistance = sgTotemPops.add(new IntSetting.Builder()
            .name("player-radius").description("The radius in which to log totem pops.")
            .defaultValue(30).sliderRange(1, 50).range(1, 100)
            .visible(() -> totemPops.get() && totemsDistanceCheck.get()).build());

    private final Setting<Boolean> totemsIgnoreOwn =
            sgTotemPops.add(new BoolSetting.Builder().name("ignore-own")
                    .description("Ignores your own totem pops.").defaultValue(false).build());

    private final Setting<Boolean> totemsIgnoreFriends =
            sgTotemPops.add(new BoolSetting.Builder().name("ignore-friends")
                    .description("Ignores friends totem pops.").defaultValue(false).build());

    private final Setting<Boolean> totemsIgnoreOthers =
            sgTotemPops.add(new BoolSetting.Builder().name("ignore-others")
                    .description("Ignores other players totem pops.").defaultValue(false).build());

    // Visual Range

    private final Setting<Boolean> visualRange =
            sgVisualRange.add(new BoolSetting.Builder().name("visual-range")
                    .description("Notifies you when an entity enters your render distance.")
                    .defaultValue(false).build());

    private final Setting<Event> event =
            sgVisualRange.add(new EnumSetting.Builder<Event>().name("event")
                    .description("When to log the entities.").defaultValue(Event.Both).build());

    private final Setting<Set<EntityType<?>>> entities =
            sgVisualRange.add(new EntityTypeListSetting.Builder().name("entities")
                    .description("Which entities to notify about.").defaultValue(EntityType.PLAYER)
                    .build());

    private final Setting<Boolean> visualRangeIgnoreNakeds =
            sgVisualRange.add(new BoolSetting.Builder().name("ignore-nakeds")
                    .description("Ignore players with no items.").defaultValue(true).build());

    private final Setting<Boolean> visualRangeIgnoreFriends =
            sgVisualRange.add(new BoolSetting.Builder().name("ignore-friends")
                    .description("Ignores friends.").defaultValue(true).build());

    private final Setting<Boolean> visualRangeIgnoreFakes =
            sgVisualRange.add(new BoolSetting.Builder().name("ignore-fake-players")
                    .description("Ignores fake players.").defaultValue(true).build());

    private final Setting<Boolean> visualMakeSound = sgVisualRange.add(new BoolSetting.Builder()
            .name("sound").description("Emits a sound effect on enter / leave").defaultValue(true)
            .build());

    // Pearl

    private final Setting<Boolean> pearl = sgPearl.add(new BoolSetting.Builder().name("pearl")
            .description("Notifies you when a player is teleported using an ender pearl.")
            .defaultValue(true).build());

    private final Setting<Boolean> pearlIgnoreOwn =
            sgPearl.add(new BoolSetting.Builder().name("ignore-own")
                    .description("Ignores your own pearls.").defaultValue(false).build());

    private final Setting<Boolean> pearlIgnoreFriends =
            sgPearl.add(new BoolSetting.Builder().name("ignore-friends")
                    .description("Ignores friends pearls.").defaultValue(false).build());

    private final Setting<Boolean> showPosition =
            sgPearl.add(new BoolSetting.Builder().name("show-position")
                    .description("Whether or not to show the position of the pearl when it lands")
                    .defaultValue(false).build());

    // Joins/Leaves

    private final Setting<JoinLeaveModes> joinsLeavesMode =
            sgJoinsLeaves.add(new EnumSetting.Builder<JoinLeaveModes>().name("player-joins-leaves")
                    .description("How to handle player join/leave notifications.")
                    .defaultValue(JoinLeaveModes.None).build());

    private final Setting<Integer> notificationDelay = sgJoinsLeaves.add(new IntSetting.Builder()
            .name("notification-delay")
            .description(
                    "How long to wait in ticks before posting the next join/leave notification in your chat.")
            .range(0, 1000).sliderRange(0, 100).defaultValue(0).build());

    private final Setting<Boolean> simpleNotifications = sgJoinsLeaves.add(new BoolSetting.Builder()
            .name("simple-notifications")
            .description(
                    "Display join/leave notifications without a prefix, to reduce chat clutter.")
            .defaultValue(true).build());

    private int timer;
    private final Object2IntMap<UUID> chatIdMap = new Object2IntOpenHashMap<>();
    private final Map<Integer, Vec3d> pearlStartPosMap = new HashMap<>();
    private final ArrayListDeque<Text> messageQueue = new ArrayListDeque<>();
    private final Queue<PlayerEntity> visualRangeEnterQueue = new ConcurrentLinkedQueue<>();

    private final Random random = new Random();

    public Notifier() {
        super(Categories.Misc, "notifier", "Notifies you of different events.");
    }

    // Visual Range

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!event.entity.getUuid().equals(mc.player.getUuid())
                && entities.get().contains(event.entity.getType()) && visualRange.get()
                && this.event.get() != Event.Despawn) {
            if (event.entity instanceof PlayerEntity player) {
                if ((!visualRangeIgnoreFriends.get()
                        || !Friends.get().isFriend(((PlayerEntity) event.entity)))
                        && (!visualRangeIgnoreFakes.get()
                                || !(event.entity instanceof FakePlayerEntity))) {

                    visualRangeEnterQueue.add(player);

                }
            } else {
                MutableText text = Text.literal(event.entity.getType().getName().getString())
                        .formatted(Formatting.WHITE);
                text.append(Text.literal(" has spawned at ").formatted(Formatting.GRAY));
                text.append(formatCoords(event.entity.getPos()));
                text.append(Text.literal(".").formatted(Formatting.GRAY));
                info(text);
            }
        }

        if (pearl.get() && event.entity instanceof EnderPearlEntity pearlEntity) {
            pearlStartPosMap.put(pearlEntity.getId(),
                    new Vec3d(pearlEntity.getX(), pearlEntity.getY(), pearlEntity.getZ()));
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (!event.entity.getUuid().equals(mc.player.getUuid())
                && entities.get().contains(event.entity.getType()) && visualRange.get()
                && this.event.get() != Event.Spawn) {
            if (event.entity instanceof PlayerEntity player) {
                if ((!visualRangeIgnoreFriends.get()
                        || !Friends.get().isFriend(((PlayerEntity) event.entity)))
                        && (!visualRangeIgnoreFakes.get()
                                || !(event.entity instanceof FakePlayerEntity))) {

                    if (visualRangeIgnoreNakeds.get()) {
                        if (player.getInventory().armor.get(0).isEmpty()
                                && player.getInventory().armor.get(1).isEmpty()
                                && player.getInventory().armor.get(2).isEmpty()
                                && player.getInventory().armor.get(3).isEmpty())
                            return;
                    }

                    ChatUtils.sendMsg(event.entity.getId() + 100, Formatting.GRAY,
                            "(highlight)%s(default) has left your visual range!",
                            event.entity.getName().getString());

                    if (visualMakeSound.get())
                        mc.world.playSoundFromEntity(mc.player, mc.player,
                                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.AMBIENT,
                                3.0F, 1.0F);
                }
            } else {
                MutableText text = Text.literal(event.entity.getType().getName().getString())
                        .formatted(Formatting.WHITE);
                text.append(Text.literal(" has despawned at ").formatted(Formatting.GRAY));
                text.append(formatCoords(event.entity.getPos()));
                text.append(Text.literal(".").formatted(Formatting.GRAY));
                info(text);
            }
        }

        if (pearl.get()) {
            Entity e = event.entity;
            int i = e.getId();
            if (pearlStartPosMap.containsKey(i)) {
                EnderPearlEntity pearl = (EnderPearlEntity) e;
                if (pearl.getOwner() != null && pearl.getOwner() instanceof PlayerEntity p) {
                    double d = pearlStartPosMap.get(i).distanceTo(e.getPos());
                    if ((!Friends.get().isFriend(p) || !pearlIgnoreFriends.get())
                            && (!p.equals(mc.player) || !pearlIgnoreOwn.get())) {
                        if (showPosition.get()) {
                            info("(highlight)%s's(default) pearl landed at %d, %d, %d (highlight)(%.1fm away, travelled %.1fm)(default).",
                                    pearl.getOwner().getName().getString(),
                                    pearl.getBlockPos().getX(), pearl.getBlockPos().getY(),
                                    pearl.getBlockPos().getZ(), pearl.distanceTo(mc.player), d);
                        } else {
                            info("(highlight)%s's(default) pearl landed at (highlight)(%.1fm away, travelled %.1fm)(default).",
                                    pearl.getOwner().getName().getString(),
                                    pearl.distanceTo(mc.player), d);
                        }
                    }
                }
                pearlStartPosMap.remove(i);
            }
        }
    }

    // Totem Pops && Joins/Leaves

    @Override
    public void onActivate() {
        chatIdMap.clear();
        pearlStartPosMap.clear();
    }

    @Override
    public void onDeactivate() {
        timer = 0;
        messageQueue.clear();
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        timer = 0;
        chatIdMap.clear();
        messageQueue.clear();
        pearlStartPosMap.clear();
    }

    @EventHandler
    private void onTotemPop(PlayerDeathEvent.TotemPop event) {
        if (!totemPops.get())
            return;
        if (totemsIgnoreOwn.get() && event.getPlayer() == mc.player)
            return;
        if (totemsIgnoreFriends.get() && Friends.get().isFriend(event.getPlayer()))
            return;
        if (totemsIgnoreOthers.get() && event.getPlayer() != mc.player)
            return;

        double distance = PlayerUtils.distanceTo(event.getPlayer());
        if (totemsDistanceCheck.get() && distance > totemsDistance.get())
            return;

        ChatUtils.sendMsg(getChatId(event.getPlayer()), Formatting.GRAY,
                "(highlight)%s (default)popped (highlight)%d (default)%s.",
                event.getPlayer().getName().getString(), event.getPops(),
                event.getPops() == 1 ? "totem" : "totems");
    }

    @EventHandler
    private void onDeath(PlayerDeathEvent.Death event) {
        if (!totemPops.get())
            return;
        if (totemsIgnoreOwn.get() && event.getPlayer() == mc.player)
            return;
        if (totemsIgnoreFriends.get() && Friends.get().isFriend(event.getPlayer()))
            return;
        if (totemsIgnoreOthers.get() && event.getPlayer() != mc.player)
            return;

        ChatUtils.sendMsg(getChatId(event.getPlayer()), Formatting.GRAY,
                "(highlight)%s (default)died after popping (highlight)%d (default)%s.",
                event.getPlayer().getName().getString(), event.getPops(),
                event.getPops() == 1 ? "totem" : "totems");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (joinsLeavesMode.get() != JoinLeaveModes.None) {
            timer++;
            while (timer >= notificationDelay.get() && !messageQueue.isEmpty()) {
                timer = 0;
                if (simpleNotifications.get()) {
                    mc.player.sendMessage(messageQueue.removeFirst());
                } else {
                    ChatUtils.sendMsg(messageQueue.removeFirst());
                }
            }
        }

        while (!visualRangeEnterQueue.isEmpty()) {
            PlayerEntity player = visualRangeEnterQueue.peek();

            if (player.age > 0) {
                visualRangeEnterQueue.remove();

                if (visualRangeIgnoreNakeds.get()) {
                    if (player.getInventory().armor.get(0).isEmpty()
                            && player.getInventory().armor.get(1).isEmpty()
                            && player.getInventory().armor.get(2).isEmpty()
                            && player.getInventory().armor.get(3).isEmpty())
                        return;
                }


                ChatUtils.sendMsg(player.getId() + 100, Formatting.GRAY,
                        "(highlight)%s(default) has entered your visual range!",
                        player.getName().getString());

                if (visualMakeSound.get()) {
                    mc.world.playSoundFromEntity(mc.player, mc.player,
                            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.AMBIENT, 3.0F,
                            1.0F);
                }
            } else {
                break;
            }
        }
    }

    private int getChatId(Entity entity) {
        return chatIdMap.computeIfAbsent(entity.getUuid(), value -> random.nextInt());
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinLeaveEvent.Join event) {
        if (joinsLeavesMode.get().equals(JoinLeaveModes.None)
                || joinsLeavesMode.get().equals(JoinLeaveModes.Leaves))
            return;
        if (event.getEntry().profile() == null)
            return;

        if (simpleNotifications.get()) {
            messageQueue.addLast(Text.literal(Formatting.GRAY + "[" + Formatting.GREEN + "+"
                    + Formatting.GRAY + "] " + event.getEntry().profile().getName()));
        } else {
            messageQueue.addLast(Text.literal(Formatting.WHITE
                    + event.getEntry().profile().getName() + Formatting.GRAY + " joined."));
        }
    }

    @EventHandler
    private void onPlayerLeave(PlayerJoinLeaveEvent.Leave event) {
        if (joinsLeavesMode.get().equals(JoinLeaveModes.None)
                || joinsLeavesMode.get().equals(JoinLeaveModes.Joins))
            return;

        if (simpleNotifications.get()) {
            messageQueue.addLast(Text.literal(Formatting.GRAY + "[" + Formatting.RED + "-"
                    + Formatting.GRAY + "] " + event.getEntry().getProfile().getName()));
        } else {
            messageQueue.addLast(Text.literal(Formatting.WHITE
                    + event.getEntry().getProfile().getName() + Formatting.GRAY + " left."));
        }
    }

    // Useless functions to not break Stardust
    @SuppressWarnings("unused")
    private void createJoinNotifications(PlayerListS2CPacket packet) {

    }

    @SuppressWarnings("unused")
    private void createLeaveNotification(PlayerRemoveS2CPacket packet) {

    }

    public enum Event {
        Spawn, Despawn, Both
    }

    public enum JoinLeaveModes {
        None, Joins, Leaves, Both
    }
}
