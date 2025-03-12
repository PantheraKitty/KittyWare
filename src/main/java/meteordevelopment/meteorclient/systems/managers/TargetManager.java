package meteordevelopment.meteorclient.systems.managers;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class TargetManager {
    private final Settings settings = new Settings();
    private final SettingGroup sgTargets = settings.createGroup("Targets");

    private final Setting<Double> range = sgTargets.add(new DoubleSetting.Builder().name("range")
            .description("Max range to target.").defaultValue(6.5).min(0).sliderMax(7.0).build());

    private final Setting<TargetMode> targetMode = sgTargets
            .add(new EnumSetting.Builder<TargetMode>().name("target-mode")
                    .description("How many targets to choose.").defaultValue(TargetMode.Single)
                    .build());

    private final Setting<TargetSortMode> targetSortMode = sgTargets
            .add(new EnumSetting.Builder<TargetSortMode>().name("target-sort-mode")
                    .description("How to sort the targets.").defaultValue(TargetSortMode.ClosestAngle)
                    .build());

    private final Setting<Integer> numTargets = sgTargets.add(new IntSetting.Builder().name("num-targets")
            .description("Max range to target.").defaultValue(2).min(1).sliderMax(5)
            .visible(() -> targetMode.get() == TargetMode.Multi).build());

    private final Setting<Boolean> ignoreNakeds = sgTargets.add(new BoolSetting.Builder().name("ignore-nakeds")
            .description("Ignore players with no items.").defaultValue(true).build());

    private final Setting<Boolean> ignorePassive = sgTargets.add(new BoolSetting.Builder().name("ignore-passive")
            .description("Does not attack passive mobs.").defaultValue(false).build());

    private Setting<Set<EntityType<?>>> validEntities = null;

    public TargetManager(Module module, boolean entityListFilter) {
        module.settings.groups.addAll(settings.groups);

        validEntities = sgTargets.add(
                new EntityTypeListSetting.Builder().name("entities").description("Entities to target.")
                        .onlyAttackable().defaultValue(EntityType.PLAYER).build());
    }

    // Always returns true to use the normal filters
    public List<PlayerEntity> getPlayerTargets() {
        return getPlayerTargets(entity -> true);
    }

    public List<PlayerEntity> getPlayerTargets(Predicate<PlayerEntity> isGood) {
        List<PlayerEntity> entities = new ArrayList<>();

        Vec3d pos = mc.player.getPos();
        Box box = new Box(
                pos.x - range.get(), pos.y - range.get(), pos.z - range.get(),
                pos.x + range.get(), pos.y + range.get(), pos.z + range.get());

        double rangeSqr = range.get() * range.get();

        // Entities by class to use the box for more optimized intersection AND class
        // type checking
        for (PlayerEntity entity : mc.world.getEntitiesByClass(PlayerEntity.class, box, e -> !e.isRemoved())) {
            if (entity != null && entity.getBoundingBox().squaredMagnitude(pos) < rangeSqr && isGood.test(entity)) {
                if (ignoreNakeds.get()) {
                    if (entity.getInventory().armor.get(0).isEmpty()
                            && entity.getInventory().armor.get(1).isEmpty()
                            && entity.getInventory().armor.get(2).isEmpty()
                            && entity.getInventory().armor.get(3).isEmpty())
                        continue;
                }

                if (entity.isCreative())
                    continue;
                if (!Friends.get().shouldAttack(entity))
                    continue;
                if (entity.equals(mc.player) || entity.equals(mc.cameraEntity))
                    continue;
                if (entity.isDead())
                    continue;

                entities.add(entity);
            }
        }

        entities.sort(targetSortMode.get());

        switch (targetMode.get()) {
            case Single -> {
                // Returns a list of just the first entity
                if (entities.size() >= 1) {
                    entities = List.of(entities.get(0));
                }
            }
            case Multi -> {
                // Returns a list of the first N entities
                if (entities.size() > numTargets.get()) {
                    entities.subList(numTargets.get(), entities.size()).clear();
                }
            }
            case All -> {
                // Returns all entities
            }
        }

        return entities;
    }

    // Always returns true to use the normal filters
    public List<Entity> getEntityTargets() {
        return getEntityTargets(entity -> true);
    }

    public List<Entity> getEntityTargets(Predicate<Entity> isGood) {
        List<Entity> entities = new ArrayList<>();

        Vec3d pos = mc.player.getPos();
        Box box = new Box(
                pos.x - range.get(), pos.y - range.get(), pos.z - range.get(),
                pos.x + range.get(), pos.y + range.get(), pos.z + range.get());

        double rangeSqr = range.get() * range.get();

        // Entities by class to use the box for more optimized intersection
        for (Entity entity : mc.world.getEntitiesByClass(Entity.class, box, e -> !e.isRemoved())) {
            if (entity != null && entity.getBoundingBox().squaredMagnitude(pos) < rangeSqr && isGood.test(entity)) {
                if (entity.equals(mc.player) || entity.equals(mc.cameraEntity))
                    continue;

                if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead())
                        || !entity.isAlive())
                    continue;

                if (validEntities != null && !validEntities.get().contains(entity.getType()))
                    continue;

                if (ignorePassive.get()) {
                    if (entity instanceof EndermanEntity enderman && !enderman.isAngry())
                        continue;
                    if (entity instanceof ZombifiedPiglinEntity piglin && !piglin.isAngryAt(mc.player))
                        continue;
                    if (entity instanceof WolfEntity wolf && !wolf.isAttacking())
                        continue;
                }

                if (entity instanceof PlayerEntity player) {
                    if (player.isCreative())
                        continue;
                    if (!Friends.get().shouldAttack(player))
                        continue;
                }

                entities.add(entity);
            }
        }

        entities.sort(targetSortMode.get());

        switch (targetMode.get()) {
            case Single -> {
                // Returns a list of just the first entity
                if (entities.size() >= 1) {
                    entities = List.of(entities.get(0));
                }
            }
            case Multi -> {
                // Returns a list of the first N entities
                if (entities.size() > numTargets.get()) {
                    entities.subList(numTargets.get(), entities.size()).clear();
                }
            }
            case All -> {
                // Returns all entities
            }
        }

        return entities;
    }

    public enum TargetMode {
        Single, Multi, All
    }

    // Java is so weird
    public enum TargetSortMode implements Comparator<Entity> {
        LowestDistance(
                Comparator.comparingDouble(entity -> entity.getEyePos().squaredDistanceTo(mc.player.getEyePos()))),

        HighestDistance((e1, e2) -> Double.compare(e2.getEyePos().squaredDistanceTo(mc.player.getEyePos()),
                e1.getEyePos().squaredDistanceTo(mc.player.getEyePos()))),

        ClosestAngle(TargetSortMode::sortAngle);

        private final Comparator<Entity> comparator;

        TargetSortMode(Comparator<Entity> comparator) {
            this.comparator = comparator;
        }

        @Override
        public int compare(Entity o1, Entity o2) {
            return comparator.compare(o1, o2);
        }

        private static int sortAngle(Entity e1, Entity e2) {
            float[] angle1 = MeteorClient.ROTATION.getRotation(e1.getEyePos());
            float[] angle2 = MeteorClient.ROTATION.getRotation(e1.getEyePos());

            double e1yaw = Math.abs(angle1[0] - mc.player.getYaw());
            double e2yaw = Math.abs(angle2[0] - mc.player.getYaw());

            return Double.compare(e1yaw * e1yaw, e2yaw * e2yaw);
        }
    }
}
