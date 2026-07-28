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
        List<ResourceLocation> ids = recruitEntityIds();
        helper.assertTrue(ids.size() >= 8, "Expected at least 8 Recruits entity types, found " + ids.size());
        List<String> failures = new ArrayList<>();
        int living = 0;
        for (ResourceLocation id : ids) {
            try {
                Entity entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(helper.getLevel());
                if (entity == null) failures.add(id + " returned null");
                else if (entity instanceof LivingEntity) living++;
            } catch (Throwable failure) {
                failures.add(id + " -> " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
        helper.assertTrue(failures.isEmpty(), "Entity instantiation failures: " + failures);
        helper.assertTrue(living >= 5, "Expected at least 5 living entities, found " + living);
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

        OwnerAccess owner = requireOwnerAccess(recruit);
        owner.set(recruit, OWNER_UUID);
        setOwned(recruit, true);
        helper.assertTrue(OWNER_UUID.equals(owner.get(recruit)), "Owner UUID was not set");
        helper.assertTrue(isOwned(recruit), "Owned flag was not set");

        Container inventory = requireInventory(recruit);
        inventory.setItem(0, new ItemStack(Items.EMERALD, 23));
        inventory.setChanged();

        CompoundTag tag = new CompoundTag();
        helper.assertTrue(recruit.save(tag), "Core recruit refused NBT save");
        Entity loaded = EntityType.loadEntityRecursive(tag, level, entity -> entity);
        helper.assertTrue(loaded instanceof LivingEntity, "Core recruit failed NBT reload");
        LivingEntity restored = (LivingEntity) loaded;

        helper.assertTrue(ENTITY_UUID.equals(restored.getUUID()), "Entity UUID did not persist");
        helper.assertTrue(restored.hasCustomName()
                        && "recruits-final-gametest".equals(restored.getCustomName().getString()),
                "Custom name did not persist");
        helper.assertTrue(restored.getMainHandItem().is(Items.IRON_SWORD), "Main hand did not persist");
        helper.assertTrue(restored.getOffhandItem().is(Items.SHIELD), "Off hand did not persist");
        helper.assertTrue(restored.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "Helmet did not persist");
        helper.assertTrue(OWNER_UUID.equals(requireOwnerAccess(restored).get(restored)),
                "Owner UUID did not persist");
        helper.assertTrue(isOwned(restored), "Owned flag did not persist");
        Container restoredInventory = requireInventory(restored);
        helper.assertTrue(restoredInventory.getItem(0).is(Items.EMERALD)
                        && restoredInventory.getItem(0).getCount() == 23,
                "Inventory did not preserve 23 emeralds");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 160)
    public static void allLivingRecruitEntitiesSave(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (ResourceLocation id : recruitEntityIds()) {
            try {
                Entity entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(helper.getLevel());
                if (!(entity instanceof LivingEntity living)) continue;
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
                Entity loaded = EntityType.loadEntityRecursive(tag, helper.getLevel(), value -> value);
                if (loaded == null || loaded.getType() != living.getType()) failures.add(id + " failed reload");
            } catch (Throwable failure) {
                failures.add(id + " -> " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
        helper.assertTrue(checked >= 5, "Expected at least 5 living entities, found " + checked);
        helper.assertTrue(failures.isEmpty(), "Living entity save/reload failures: " + failures);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void commonEntityHierarchyContainsNoClientTypes(GameTestHelper helper) {
        List<String> leaks = new ArrayList<>();
        for (ResourceLocation id : recruitEntityIds()) {
            Entity entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(helper.getLevel());
            if (entity == null) continue;
            Class<?> type = entity.getClass();
            while (type != null && type != Object.class) {
                for (Method method : type.getDeclaredMethods()) {
                    checkType(method.getReturnType(), type, method.getName(), leaks);
                    for (Class<?> parameter : method.getParameterTypes())
                        checkType(parameter, type, method.getName(), leaks);
                }
                type = type.getSuperclass();
            }
        }
        helper.assertTrue(leaks.isEmpty(), "Client-only common API leakage: " + leaks);
        helper.succeed();
    }

    private static List<ResourceLocation> recruitEntityIds() {
        return BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .filter(id -> "recruits".equals(id.getNamespace()))
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }

    private static LivingEntity createCoreRecruit(ServerLevel level) {
        return recruitEntityIds().stream()
                .sorted(Comparator.comparing(id -> !"recruit".equals(id.getPath())))
                .map(BuiltInRegistries.ENTITY_TYPE::get)
                .map(type -> type.create(level))
                .filter(entity -> entity instanceof LivingEntity
                        && hasSuperclass(entity.getClass(), "AbstractRecruitEntity"))
                .map(entity -> (LivingEntity) entity)
                .findFirst().orElseThrow(() -> new IllegalStateException("No AbstractRecruitEntity is registered"));
    }

    private static void checkType(Class<?> referenced, Class<?> owner, String method, List<String> leaks) {
        String name = referenced.getName();
        if (name.startsWith("net.minecraft.client.") || name.startsWith("com.mojang.blaze3d."))
            leaks.add(owner.getName() + "#" + method + " -> " + name);
    }

    private static boolean hasSuperclass(Class<?> type, String simpleName) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass())
            if (simpleName.equals(cursor.getSimpleName())) return true;
        return false;
    }

    private static Container requireInventory(Object entity) {
        for (Method method : entity.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && "getInventory".equals(method.getName())
                    && Container.class.isAssignableFrom(method.getReturnType())) {
                try {
                    Container inventory = (Container) method.invoke(entity);
                    if (inventory != null && inventory.getContainerSize() > 0) return inventory;
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Failed to access recruit inventory", failure);
                }
            }
        }
        throw new IllegalStateException("No non-empty public Container inventory on " + entity.getClass().getName());
    }

    private static void setOwned(Object entity, boolean value) {
        invokeBoolean(entity, "setIsOwned", value);
    }

    private static boolean isOwned(Object entity) {
        Object value = invokeNoArgs(entity, "getIsOwned");
        return value instanceof Boolean bool && bool;
    }

    private static Object invokeNoArgs(Object target, String name) {
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to call " + name, failure);
        }
    }

    private static void invokeBoolean(Object target, String name, boolean value) {
        try {
            target.getClass().getMethod(name, boolean.class).invoke(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to call " + name, failure);
        }
    }

    private static OwnerAccess requireOwnerAccess(Object entity) {
        OwnerAccess access = OwnerAccess.resolve(entity.getClass());
        if (access == null) throw new IllegalStateException("Owner UUID accessors unavailable on " + entity.getClass().getName());
        return access;
    }

    private record OwnerAccess(Method setter, Method getter, boolean optionalSetter, boolean optionalGetter) {
        static OwnerAccess resolve(Class<?> type) {
            Method setter = null;
            Method getter = null;
            boolean setterOptional = false;
            boolean getterOptional = false;
            for (Method method : type.getMethods()) {
                String lower = method.getName().toLowerCase();
                if (!lower.contains("owner")) continue;
                if (method.getParameterCount() == 1 && method.getReturnType() == void.class) {
                    Class<?> parameter = method.getParameterTypes()[0];
                    if (parameter == UUID.class || Optional.class.isAssignableFrom(parameter)) {
                        if (setter == null || "setOwnerUUID".equals(method.getName())) {
                            setter = method;
                            setterOptional = Optional.class.isAssignableFrom(parameter);
                        }
                    }
                } else if (method.getParameterCount() == 0) {
                    if (method.getReturnType() == UUID.class
                            || Optional.class.isAssignableFrom(method.getReturnType())) {
                        if (getter == null || "getOwnerUUID".equals(method.getName())) {
                            getter = method;
                            getterOptional = Optional.class.isAssignableFrom(method.getReturnType());
                        }
                    }
                }
            }
            return setter == null || getter == null ? null
                    : new OwnerAccess(setter, getter, setterOptional, getterOptional);
        }

        void set(Object target, UUID value) {
            try {
                setter.invoke(target, optionalSetter ? Optional.ofNullable(value) : value);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Failed to set owner UUID", failure);
            }
        }

        UUID get(Object target) {
            try {
                Object value = getter.invoke(target);
                return optionalGetter ? (UUID) ((Optional<?>) value).orElse(null) : (UUID) value;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Failed to read owner UUID", failure);
            }
        }
    }
}
