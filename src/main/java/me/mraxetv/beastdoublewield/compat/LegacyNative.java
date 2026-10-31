package me.mraxetv.beastdoublewield.compat;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Reflection adapter for CraftBukkit's native operations before their Bukkit APIs existed. */
final class LegacyNative {
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private LegacyNative() { }

    static void attack(Player player, Entity target) {
        Object human = handle(player), victim = handle(target);
        invoke(resolve(human.getClass(), "native attack", void.class, new String[]{"attack"}, victim), human, victim);
    }
    static boolean breakBlock(Player player, Block block) {
        Object human = handle(player);
        Object manager = fieldValue(human, "playerInteractManager", "gameMode");
        Object position = position(block);
        return (Boolean) invoke(resolve(manager.getClass(), "native break", boolean.class, new String[]{"breakBlock", "destroyBlock"}, position), manager, position);
    }
    static float breakSpeed(Player player, Block block) {
        Object state = state(block), human = handle(player), world = handle(block.getWorld()), position = position(block);
        // State#getDestroyProgress has this uniquely typed signature from 1.9 onward.
        Method method = resolve(state.getClass(), "native mining progress", float.class,
                new String[]{"getDestroyProgress", "getDamage", "a"}, human, world, position);
        return ((Number) invoke(method, state, human, world, position)).floatValue();
    }
    static boolean preferredTool(Block block, ItemStack item) {
        Object state = state(block), stack = nativeItem(item);
        Method speed = resolve(stack.getClass(), "native tool speed", float.class,
                new String[]{"getDestroySpeed", "a"}, state);
        Method harvest = resolve(stack.getClass(), "native tool harvest", boolean.class,
                new String[]{"isCorrectToolForDrops", "canDestroySpecialBlock", "b"}, state);
        // A specialised tool can either speed up the block or unlock its harvest.
        return ((Number) invoke(speed, stack, state)).floatValue() > 1.0F || (Boolean) invoke(harvest, stack, state);
    }
    static void swing(Player player) {
        Object human = handle(player);
        Class<?> handClass = legacyClass(human, "EnumHand");
        Object offhand = enumConstant(handClass, "OFF_HAND");
        invoke(resolve(human.getClass(), "native offhand swing", void.class, new String[]{"a", "swingHand"}, offhand), human, offhand);
    }
    static void crack(Player player, Block block, float progress) {
        Object human = handle(player), position = position(block);
        int stage = progress <= 0 ? -1 : Math.min(9, (int) (progress * 10));
        Object packet = construct(legacyClass(human, "PacketPlayOutBlockBreakAnimation"),
                new Class<?>[]{int.class, position.getClass(), int.class}, player.getEntityId(), position, stage);
        send(human, packet);
    }
    static void hitSound(Player player, Block block) {
        Object sounds = soundGroup(state(block));
        String hit = hitSoundKey(sounds);
        float volume = ((Number) invoke(resolve(sounds.getClass(), "sound volume", float.class, new String[]{"a", "getVolume"}), sounds)).floatValue();
        float pitch = ((Number) invoke(resolve(sounds.getClass(), "sound pitch", float.class, new String[]{"b", "getPitch"}), sounds)).floatValue();
        player.playSound(block.getLocation(), hit, volume * 0.25F, pitch);
    }
    static Runnable prepareBreakSound(Player player, Block block) {
        // Older Bukkit lacks SoundGroup (and 1.9 lacks SoundCategory). Its
        // actor-only STEP_SOUND effect lets the client select the native break
        // sound/category and particles from this material's default state.
        Material material = block.getType();
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        return () -> player.playEffect(location, Effect.STEP_SOUND, material);
    }
    static Object soundGroup(Object state) {
        if (hasMethod(state.getClass(), "getStepSound"))
            return invoke(resolve(state.getClass(), "native state sound group", null, new String[]{"getStepSound"}), state);
        Object block = invoke(resolve(state.getClass(), "native block", null, new String[]{"getBlock"}), state);
        if (hasMethod(block.getClass(), "getStepSound", state) || hasMethod(block.getClass(), "getSoundType", state))
            return invoke(resolve(block.getClass(), "native state-dependent sound group", null, new String[]{"getStepSound", "getSoundType"}, state), block, state);
        if (hasMethod(block.getClass(), "getStepSound") || hasMethod(block.getClass(), "getSoundType"))
            return invoke(resolve(block.getClass(), "native sound group", null, new String[]{"getStepSound", "getSoundType"}), block);
        // Spigot 1.9/1.10 map the protected Block field to stepSound, while its
        // vanilla accessor remains obfuscated as w(). Prefer the mapped field
        // over guessing an accessor letter that changes between releases.
        return fieldValue(block, "stepSound");
    }
    static Collection<AttributeModifier> modifiers(ItemStack item, EquipmentSlot slot, Attribute attribute) {
        Object stack = nativeItem(item);
        Class<?> nativeSlot = legacyClass(stack, "EnumItemSlot");
        Object hand = enumConstant(nativeSlot, slot == EquipmentSlot.OFF_HAND ? "OFFHAND" : "MAINHAND");
        Object rawMap = invoke(resolve(stack.getClass(), "native item attributes", null, new String[]{"a", "getAttributeModifiers"}, hand), stack, hand);
        if (!(rawMap instanceof com.google.common.collect.Multimap)) throw unsupported("Native item attributes did not return a Multimap", null);
        com.google.common.collect.Multimap<?, ?> map = (com.google.common.collect.Multimap<?, ?>) rawMap;
        List<AttributeModifier> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entries()) {
            if (!isAttributeKey(entry.getKey(), attribute)) continue;
            Object value = entry.getValue();
            UUID id = (UUID) invoke(resolve(value.getClass(), "attribute UUID", UUID.class, new String[]{"a", "getUniqueId"}), value);
            String name = (String) invoke(resolve(value.getClass(), "attribute name", String.class, new String[]{"b", "getName"}), value);
            Object rawOperation = invoke(resolve(value.getClass(), "attribute operation", null, new String[]{"getOperation", "c"}), value);
            int operation = operationId(rawOperation);
            double amount = ((Number) invoke(resolve(value.getClass(), "attribute amount", double.class, new String[]{"d", "getAmount"}), value)).doubleValue();
            if (operation < 0 || operation > 2 || !Double.isFinite(amount)) throw unsupported("Invalid native attribute", null);
            result.add(new AttributeModifier(id, name, amount, AttributeModifier.Operation.values()[operation]));
        }
        return result;
    }
    static boolean isAttributeKey(Object key, Attribute attribute) {
        if (key == null || (attribute != Attribute.GENERIC_ATTACK_DAMAGE && attribute != Attribute.GENERIC_ATTACK_SPEED)) return false;
        String name = key instanceof String ? (String) key
                : (String) invoke(resolve(key.getClass(), "attribute key name", String.class, new String[]{"getName"}), key);
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "");
        if (normalized.startsWith("attribute.name.")) normalized = normalized.substring("attribute.name.".length());
        return normalized.equals(attribute == Attribute.GENERIC_ATTACK_SPEED ? "generic.attackspeed" : "generic.attackdamage");
    }
    static int operationId(Object operation) {
        if (operation instanceof Number) return ((Number) operation).intValue();
        if (operation instanceof Enum) {
            switch (((Enum<?>) operation).name()) {
                case "ADDITION": case "ADD_NUMBER": return 0;
                case "MULTIPLY_BASE": case "ADD_SCALAR": return 1;
                case "MULTIPLY_TOTAL": case "MULTIPLY_SCALAR_1": return 2;
                default: break;
            }
        }
        throw unsupported("Unknown native attribute operation " + operation, null);
    }
    static double[] bounds(Entity entity) {
        Object nativeEntity = handle(entity);
        Object bounds = invoke(resolve(nativeEntity.getClass(), "native bounding box", null, new String[]{"getBoundingBox"}), nativeEntity);
        String[] fields = {"a", "b", "c", "d", "e", "f"};
        double[] result = new double[6];
        for (int i = 0; i < 6; i++) result[i] = ((Number) fieldValue(bounds, fields[i])).doubleValue();
        return result;
    }
    static Ray ray(Player player, double reach) {
        Object world = handle(player.getWorld());
        Class<?> vec = legacyClass(world, "Vec3D");
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().multiply(reach);
        Object start = construct(vec, new Class<?>[]{double.class, double.class, double.class}, eye.getX(), eye.getY(), eye.getZ());
        Object end = construct(vec, new Class<?>[]{double.class, double.class, double.class}, eye.getX() + direction.getX(), eye.getY() + direction.getY(), eye.getZ() + direction.getZ());
        Object hit = invoke(resolve(world.getClass(), "native block ray", null, new String[]{"rayTrace"}, start, end, false, true, false), world, start, end, false, true, false);
        if (hit == null) return new Ray(null, reach);
        Object pos = invoke(resolve(hit.getClass(), "ray block position", null, new String[]{"getBlockPosition", "a"}), hit);
        int x = coordinate(pos, "getX"), y = coordinate(pos, "getY"), z = coordinate(pos, "getZ");
        Object intersection = fieldValue(hit, "pos");
        Vector location = new Vector(((Number) fieldValue(intersection, "x")).doubleValue(),
                ((Number) fieldValue(intersection, "y")).doubleValue(), ((Number) fieldValue(intersection, "z")).doubleValue());
        return new Ray(player.getWorld().getBlockAt(x, y, z), eye.toVector().distance(location));
    }
    static final class Ray {
        final Block block; final double distance;
        Ray(Block block, double distance) { this.block = block; this.distance = distance; }
    }
    private static int coordinate(Object position, String name) {
        return ((Number) invoke(resolve(position.getClass(), name, int.class, new String[]{name}), position)).intValue();
    }
    private static boolean hasMethod(Class<?> owner, String name, Object... args) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass())
            for (Method method : type.getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic() || !method.getName().equals(name) || method.getParameterTypes().length != args.length) continue;
                boolean accepts = true;
                for (int i = 0; i < args.length; i++) if (!accepts(method.getParameterTypes()[i], args[i])) { accepts = false; break; }
                if (accepts) return true;
            }
        return false;
    }
    private static String hitSoundKey(Object group) {
        // Obfuscated field letters shift whenever new sound groups are added.
        // Select by the sound's actual registry identifier instead of a field index.
        for (Field soundField : group.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(soundField.getModifiers()) || !soundField.getType().getSimpleName().equals("SoundEffect")) continue;
            try {
                soundField.setAccessible(true); Object sound = soundField.get(group);
                if (sound == null) continue;
                for (Field keyField : sound.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(keyField.getModifiers()) || !keyField.getType().getSimpleName().equals("MinecraftKey")) continue;
                    keyField.setAccessible(true); String key = String.valueOf(keyField.get(sound));
                    if (key.endsWith(".hit")) return key;
                }
            } catch (IllegalAccessException inaccessible) { throw unsupported("Cannot inspect native sound group", inaccessible); }
        }
        throw unsupported("Native sound group has no hit sound identifier", null);
    }
    private static void send(Object human, Object packet) {
        Object connection = fieldValue(human, "playerConnection", "connection");
        invoke(resolve(connection.getClass(), "send native packet", void.class, new String[]{"sendPacket", "send"}, packet), connection, packet);
    }
    private static Object state(Block block) {
        return invoke(resolve(block.getClass(), "native block data", null, new String[]{"getNMS", "getData0"}), block);
    }
    private static Object position(Block block) {
        try { return invoke(block.getClass().getMethod("getPosition"), block); }
        catch (NoSuchMethodException legacy) {
            Object world = handle(block.getWorld());
            Class<?> type = legacyClass(world, "BlockPosition");
            return construct(type, new Class<?>[]{int.class, int.class, int.class}, block.getX(), block.getY(), block.getZ());
        }
    }
    private static Object nativeItem(ItemStack item) {
        String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
        Class<?> craftItem = load(craftPackage + ".inventory.CraftItemStack");
        return invoke(resolve(craftItem, "copy native item", null, new String[]{"asNMSCopy"}, item), null, item);
    }
    private static Object handle(Object value) {
        return invoke(resolve(value.getClass(), "native handle", null, new String[]{"getHandle"}), value);
    }
    private static Class<?> legacyClass(Object object, String simpleName) {
        String packageName = object.getClass().getPackage().getName();
        if (!packageName.startsWith("net.minecraft.server.v")) throw unsupported("Expected a versioned native package for " + simpleName, null);
        return load(packageName + "." + simpleName);
    }
    private static Class<?> load(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException absent) { throw unsupported("Missing native class " + name, absent); }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> type, String name) { return Enum.valueOf((Class<? extends Enum>) type, name); }
    private static Object construct(Class<?> type, Class<?>[] parameters, Object... args) {
        try { return type.getConstructor(parameters).newInstance(args); }
        catch (InvocationTargetException invoked) { throw propagate(invoked.getCause()); }
        catch (ReflectiveOperationException failed) { throw unsupported("Cannot construct " + type.getName(), failed); }
    }
    static Object invoke(Method method, Object target, Object... arguments) {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException invoked) { throw propagate(invoked.getCause()); }
        catch (ReflectiveOperationException failed) { throw unsupported("Cannot invoke " + method, failed); }
    }
    private static RuntimeException propagate(Throwable cause) {
        if (cause instanceof Error) throw (Error) cause;
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        return unsupported("Native operation failed", cause);
    }
    private static Object fieldValue(Object target, String... names) {
        for (String name : names) {
            String key = target.getClass().getName() + "#" + name;
            Field field = FIELDS.get(key);
            if (field == null) {
                for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                    try { field = type.getDeclaredField(name); field.setAccessible(true); FIELDS.put(key, field); break; }
                    catch (NoSuchFieldException ignored) { }
                }
            }
            if (field != null) try { return field.get(target); }
            catch (IllegalAccessException inaccessible) { throw unsupported("Cannot access " + field, inaccessible); }
        }
        throw unsupported("Missing field " + Arrays.toString(names) + " on " + target.getClass().getName(), null);
    }
    /** Explicit method names plus exact compatible argument shapes; never guess another operation. */
    static Method resolve(Class<?> owner, String operation, Class<?> result, String[] names, Object... args) {
        StringBuilder key = new StringBuilder(owner.getName()).append('#').append(operation);
        for (Object arg : args) key.append(':').append(arg == null ? "null" : arg.getClass().getName());
        Method cached = METHODS.get(key.toString());
        if (cached != null) return cached;
        for (String name : names) {
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                List<Method> matches = new ArrayList<>();
                for (Method method : type.getDeclaredMethods()) {
                    if (method.isBridge() || method.isSynthetic() || !method.getName().equals(name) || method.getParameterTypes().length != args.length
                            || (result != null && method.getReturnType() != result)) continue;
                    Class<?>[] parameters = method.getParameterTypes();
                    boolean accepts = true;
                    for (int i = 0; i < parameters.length; i++) if (!accepts(parameters[i], args[i])) { accepts = false; break; }
                    if (accepts) matches.add(method);
                }
                if (matches.size() > 1) throw unsupported("Ambiguous native " + operation + " on " + owner.getName(), null);
                if (matches.size() == 1) {
                    Method found = matches.get(0); found.setAccessible(true); METHODS.put(key.toString(), found); return found;
                }
            }
        }
        throw unsupported("Missing native " + operation + " on " + owner.getName(), null);
    }
    private static boolean accepts(Class<?> type, Object value) {
        if (value == null) return !type.isPrimitive();
        if (!type.isPrimitive()) return type.isInstance(value);
        return (type == boolean.class && value instanceof Boolean) || (type == int.class && value instanceof Integer)
                || (type == long.class && value instanceof Long) || (type == float.class && value instanceof Float)
                || (type == double.class && value instanceof Double);
    }
    private static IllegalStateException unsupported(String detail, Throwable cause) {
        return new IllegalStateException("This server's native compatibility binding is unavailable: " + detail, cause);
    }
}
