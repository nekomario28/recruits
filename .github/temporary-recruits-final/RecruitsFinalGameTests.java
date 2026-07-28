package com.talhanation.recruits.gametest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder("recruits")
@PrefixGameTestTemplate(false)
public final class RecruitsFinalGameTests {
    private static final UUID ENTITY_UUID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_UUID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private RecruitsFinalGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 40)
    public static void requiredModLoads(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("recruits"), "Recruits must be loaded");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 100)
    public static void registeredEntityTypesInstantiate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ResourceLocation> ids = recruitEntityIds();
        helper.assertTrue(ids.size() >= 8, "Expected at least 8 Recruits entity types, found " + ids.size());
        List<String> failures = new ArrayList<>();
        int living = 0;
        for (ResourceLocation id : ids) {
            try {
                Entity entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(level);
                if (entity == null) {
                    failures.add(id + " returned null");
                } else if (entity instanceof LivingEntity) {
                    living++;
                }
            } catch (Throwable failure) {
                failures.add(id + " -> " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
        helper.assertTrue(failures.isEmpty(), "Entity instantiation failures: " + failures);
        helper.assertTrue(living >= 5, "Expected at least 5 living Recruits entities, found " + living);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 120)
    public static void coreRecruitNbtRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingEntity recruit = createCoreRecruit(level);
        recruit.setUUID(ENTITY_UUID);
        recruit.setCustomName(Component.literal("recruits-final-gametest"));
        recruit.setCustomNameVisible(true);
        recruit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        recruit.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        recruit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        if (recruit instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }

        OwnerAccess owner = OwnerAccess.resolve(recruit.getClass());
        helper.assertTrue(owner != null, "Could not resolve public owner UUID accessors on " + recruit.getClass().getName());
        owner.set(recruit, OWNER_UUID);
        helper.assertTrue(OWNER_UUID.equals(owner.get(recruit)), "Owner UUID setter/getter did not agree before save");

        Container inventory = resolveInventory(recruit);
        helper.assertTrue(inventory != null && inventory.getContainerSize() > 0,
                "Core recruit must expose a non-empty Container inventory");
        inventory.setItem(0, new ItemStack(Items.EMERALD, 23));
        inventory.setChanged();

        CompoundTag tag = new CompoundTag();
        helper.assertTrue(recruit.save(tag), "Core recruit refused to save to NBT");
        Entity loaded = EntityType.loadEntityRecursive(tag, level, entity -> entity).orElse(null);
        helper.assertTrue(loaded instanceof LivingEntity, "Core recruit could not be loaded from saved NBT");
        LivingEntity restored = (LivingEntity) loaded;

        helper.assertTrue(restored.getUUID().equals(ENTITY_UUID), "Entity UUID did not survive NBT round trip");
        helper.assertTrue(restored.hasCustomName()
                        && "recruits-final-gametest".equals(restored.getCustomName().getString()),
                "Custom name did not survive NBT round trip");
        helper.assertTrue(restored.getMainHandItem().is(Items.IRON_SWORD), "Main-hand item did not persist");
        helper.assertTrue(restored.getOffhandItem().is(Items.SHIELD), "Off-hand item did not persist");
        helper.assertTrue(restored.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "Helmet did not persist");

        OwnerAccess restoredOwner = OwnerAccess.resolve(restored.getClass());
        helper.assertTrue(restoredOwner != null && OWNER_UUID.equals(restoredOwner.get(restored)),
                "Owner UUID did not survive NBT round trip");
        Container restoredInventory = resolveInventory(restored);
        helper.assertTrue(restoredInventory != null && restoredInventory.getContainerSize() > 0,
                "Restored recruit inventory is unavailable");
        helper.assertTrue(restoredInventory.getItem(0).is(Items.EMERALD)
                        && restoredInventory.getItem(0).getCount() == 23,
                "Recruit inventory slot 0 did not preserve 23 emeralds");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 160)
    public static void allLivingRecruitEntitiesSave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (ResourceLocation id : recruitEntityIds()) {
            try {
                Entity entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(level);
                if (!(entity instanceof LivingEntity living)) {
                    continue;
                }
                checked++;
                living.setCustomName(Component.literal("save-check-" + id.getPath()));
                if (living instanceof Mob mob) {
                    mob.setNoAi(true);
                    mob.setPersistenceRequired();
                }
                CompoundTag tag = new CompoundTag();
                if (!living.save(tag)) {
                    failures.add(id + " refused save");
                    continue;
                }
                Entity loaded = EntityType.loadEntityRecursive(tag, level, value -> value).orElse(null);
                if (loaded == null || loaded.getType() != living.getType()) {
                    failures.add(id + " failed reload");
                }
            } catch (Throwable failure) {
                failures.add(id + " -> " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
        helper.assertTrue(checked >= 5, "Expected at least 5 living entities to check, found " + checked);
        helper.assertTrue(failures.isEmpty(), "Living entity save/reload failures: " + failures);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void commonEntityHierarchyContainsNoClientTypes(GameTestHelper helper) {
        List<String> leaks = new ArrayList<>();
        for (ResourceLocation id : recruitEntityIds()) {
            Entity entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(helper.getLevel());
            if (entity == null) {
                continue;
            }
            Class<?> type = entity.getClass();
            while (type != null && type != Object.class) {
                for (Method method : type.getDeclaredMethods()) {
                    checkType(method.getReturnType(), type, method.getName(), leaks);
                    for (Class<?> parameter : method.getParameterTypes()) {
                        checkType(parameter, type, method.getName(), leaks);
                    }
                }
                type = type.getSuperclass();
            }
        }
        helper.assertTrue(leaks.isEmpty(), "Client-only types leaked into common entity APIs: " + leaks);
        helper.succeed();
    }

    private static void checkType(Class<?> referenced, Class<?> owner, String method, List<String> leaks) {
        String name = referenced.getName();
        if (name.startsWith("net.minecraft.client.") || name.startsWith("com.mojang.blaze3d.")) {
            leaks.add(owner.getName() + "#" + method + " -> " + name);
        }
    }

    private static List<ResourceLocation> recruitEntityIds() {
        return BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .filter(id -> "recruits".equals(id.getNamespace()))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    private static LivingEntity createCoreRecruit(ServerLevel level) {
        List<ResourceLocation> ids = recruitEntityIds();
        ids = ids.stream().sorted(Comparator.comparing(id -> !"recruit".equals(id.getPath()))).toList();
        for (ResourceLocation id : ids) {
            Entity entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(level);
            if (entity instanceof LivingEntity living && hasSuperclass(living.getClass(), "AbstractRecruitEntity")) {
                return living;
            }
        }
        throw new IllegalStateException("No living AbstractRecruitEntity implementation is registered");
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

    private static Container resolveInventory(Object entity) {
        for (Method method : entity.getClass().getMethods()) {
            if (method.getParameterCount() == 0
                    && "getInventory".equals(method.getName())
                    && Container.class.isAssignableFrom(method.getReturnType())) {
                try {
                    return (Container) method.invoke(entity);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Failed to access recruit inventory", failure);
                }
            }
        }
        return null;
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
