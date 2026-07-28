package com.talhanation.recruits.finaltest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = "recruits", bus = EventBusSubscriber.Bus.GAME)
public final class RecruitsFinalRestartProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("RecruitsFinalRestartProbe");
    private static final Path PHASE = Path.of("recruits-final-restart.phase");
    private static final Path PASS = Path.of("recruits-final-restart.pass");
    private static final Path FAIL = Path.of("recruits-final-restart.fail");
    private static final BlockPos RECRUIT_POS = new BlockPos(8, 100, 8);
    private static final UUID RECRUIT_UUID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID OWNER_UUID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static int ticks;
    private static boolean prepared;
    private static boolean completed;

    private RecruitsFinalRestartProbe() {
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        ticks = 0;
        prepared = false;
        completed = false;
        forceChunk(event.getServer().overworld());
        LOGGER.info("RECRUITS_FINAL_RESTART_PROCESS_STARTED phaseExists={}", Files.exists(PHASE));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (completed || ++ticks < 80) {
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            if (Files.exists(PHASE)) {
                completed = true;
                verifyReload(server);
            } else if (!prepared) {
                prepareRecruit(server);
                prepared = true;
                ticks = 0;
            } else {
                completed = true;
                saveAndStop(server);
            }
        } catch (Throwable failure) {
            completed = true;
            LOGGER.error("RECRUITS_FINAL_RESTART_FAILED", failure);
            try {
                Files.writeString(FAIL, failure.toString(), StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception ignored) {
            }
            throw new IllegalStateException("Recruits final restart probe failed", failure);
        }
    }

    private static void prepareRecruit(MinecraftServer server) {
        ServerLevel level = server.overworld();
        forceChunk(level);
        level.setBlockAndUpdate(RECRUIT_POS.below(), Blocks.STONE.defaultBlockState());
        LivingEntity recruit = createCoreRecruit(level);
        recruit.setUUID(RECRUIT_UUID);
        recruit.setPos(RECRUIT_POS.getX() + 0.5D, RECRUIT_POS.getY(), RECRUIT_POS.getZ() + 0.5D);
        recruit.setCustomName(Component.literal("recruits-final-restart"));
        recruit.setCustomNameVisible(true);
        recruit.setNoGravity(true);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        recruit.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        recruit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        if (recruit instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }

        OwnerAccess owner = requireOwnerAccess(recruit);
        owner.set(recruit, OWNER_UUID);
        require(OWNER_UUID.equals(owner.get(recruit)), "Owner UUID setter/getter failed before add");

        Container inventory = requireInventory(recruit);
        require(inventory.getContainerSize() > 0, "Recruit inventory has no slots");
        inventory.setItem(0, new ItemStack(Items.EMERALD, 23));
        inventory.setChanged();

        require(level.addFreshEntity(recruit), "Recruit could not be added to the world");
        LOGGER.info("RECRUITS_FINAL_RESTART_PREPARED class={} uuid={} owner={} inventorySize={} emeralds={}",
                recruit.getClass().getName(), recruit.getUUID(), owner.get(recruit),
                inventory.getContainerSize(), inventory.getItem(0).getCount());
    }

    private static void saveAndStop(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        forceChunk(level);
        Entity entity = level.getEntity(RECRUIT_UUID);
        require(entity instanceof LivingEntity, "Prepared recruit is absent before save");
        LivingEntity recruit = (LivingEntity) entity;
        verifyRecruit(recruit);
        require(!recruit.isRemoved(), "Recruit was removed before save: " + recruit.getRemovalReason());
        require(server.saveEverything(true, true, true), "saveEverything returned false");
        Files.writeString(PHASE, "write-ok\n", StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        LOGGER.info("RECRUITS_FINAL_RESTART_WRITE_OK class={} uuid={} owner={} emeralds={}",
                recruit.getClass().getName(), recruit.getUUID(), requireOwnerAccess(recruit).get(recruit),
                requireInventory(recruit).getItem(0).getCount());
        server.halt(false);
    }

    private static void verifyReload(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        forceChunk(level);
        Entity entity = level.getEntity(RECRUIT_UUID);
        require(entity instanceof LivingEntity, "Persisted recruit was not found by UUID after restart");
        LivingEntity recruit = (LivingEntity) entity;
        verifyRecruit(recruit);
        require(!recruit.isRemoved(), "Reloaded recruit is removed: " + recruit.getRemovalReason());
        require(server.saveEverything(true, true, true), "Verification saveEverything returned false");
        Files.writeString(PASS,
                "RECRUITS_FINAL_RESTART_VERIFY_OK\n"
                        + "uuid=" + RECRUIT_UUID + "\n"
                        + "owner=" + OWNER_UUID + "\n"
                        + "emeralds=23\n"
                        + "mainhand=iron_sword\n"
                        + "offhand=shield\n"
                        + "helmet=iron_helmet\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        LOGGER.info("RECRUITS_FINAL_RESTART_VERIFY_OK class={} uuid={} owner={} emeralds={}",
                recruit.getClass().getName(), recruit.getUUID(), requireOwnerAccess(recruit).get(recruit),
                requireInventory(recruit).getItem(0).getCount());
        server.halt(false);
    }

    private static void verifyRecruit(LivingEntity recruit) {
        require(hasSuperclass(recruit.getClass(), "AbstractRecruitEntity"),
                "Persisted entity is not an AbstractRecruitEntity implementation");
        require(RECRUIT_UUID.equals(recruit.getUUID()), "Recruit UUID changed");
        require(recruit.hasCustomName() && "recruits-final-restart".equals(recruit.getCustomName().getString()),
                "Recruit custom name did not persist");
        require(recruit.getMainHandItem().is(Items.IRON_SWORD), "Main-hand iron sword did not persist");
        require(recruit.getOffhandItem().is(Items.SHIELD), "Off-hand shield did not persist");
        require(recruit.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "Iron helmet did not persist");
        require(recruit.isNoGravity(), "No-gravity marker did not persist");
        require(OWNER_UUID.equals(requireOwnerAccess(recruit).get(recruit)), "Owner UUID did not persist");
        Container inventory = requireInventory(recruit);
        require(inventory.getItem(0).is(Items.EMERALD) && inventory.getItem(0).getCount() == 23,
                "Recruit inventory did not preserve 23 emeralds");
    }

    private static LivingEntity createCoreRecruit(ServerLevel level) {
        return BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .filter(id -> "recruits".equals(id.getNamespace()))
                .sorted(Comparator.comparing(id -> !"recruit".equals(id.getPath())))
                .map(BuiltInRegistries.ENTITY_TYPE::get)
                .map(type -> type.create(level))
                .filter(entity -> entity instanceof LivingEntity
                        && hasSuperclass(entity.getClass(), "AbstractRecruitEntity"))
                .map(entity -> (LivingEntity) entity)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No core recruit entity type is registered"));
    }

    private static boolean hasSuperclass(Class<?> type, String simpleName) {
        Class<?> cursor = type;
        while (cursor != null) {
            if (simpleName.equals(cursor.getSimpleName())) {
                return true;
            }
            cursor = cursor.getSuperclass();
        }
        return false;
    }

    private static Container requireInventory(Object entity) {
        for (Method method : entity.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && "getInventory".equals(method.getName())
                    && Container.class.isAssignableFrom(method.getReturnType())) {
                try {
                    return (Container) method.invoke(entity);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Failed to access recruit inventory", failure);
                }
            }
        }
        throw new IllegalStateException("No public Container getInventory() method on " + entity.getClass().getName());
    }

    private static OwnerAccess requireOwnerAccess(Object entity) {
        OwnerAccess access = OwnerAccess.resolve(entity.getClass());
        if (access == null) {
            throw new IllegalStateException("No public owner UUID accessors on " + entity.getClass().getName());
        }
        return access;
    }

    private static void forceChunk(ServerLevel level) {
        level.setChunkForced(RECRUIT_POS.getX() >> 4, RECRUIT_POS.getZ() >> 4, true);
        level.getChunkAt(RECRUIT_POS);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record OwnerAccess(Method setter, Method getter, boolean optionalGetter) {
        static OwnerAccess resolve(Class<?> type) {
            Method setter = null;
            Method getter = null;
            boolean optional = false;
            for (Method method : type.getMethods()) {
                String lower = method.getName().toLowerCase();
                if (!lower.contains("owner")) {
                    continue;
                }
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == UUID.class
                        && method.getReturnType() == void.class) {
                    if (setter == null || "setOwnerUUID".equals(method.getName())) {
                        setter = method;
                    }
                } else if (method.getParameterCount() == 0 && method.getReturnType() == UUID.class) {
                    if (getter == null || "getOwnerUUID".equals(method.getName())) {
                        getter = method;
                        optional = false;
                    }
                } else if (method.getParameterCount() == 0
                        && Optional.class.isAssignableFrom(method.getReturnType())) {
                    if (getter == null) {
                        getter = method;
                        optional = true;
                    }
                }
            }
            return setter == null || getter == null ? null : new OwnerAccess(setter, getter, optional);
        }

        void set(Object target, UUID value) {
            try {
                setter.invoke(target, value);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Failed to set owner UUID", failure);
            }
        }

        UUID get(Object target) {
            try {
                Object value = getter.invoke(target);
                if (optionalGetter) {
                    return (UUID) ((Optional<?>) value).orElse(null);
                }
                return (UUID) value;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Failed to read owner UUID", failure);
            }
        }
    }
}
