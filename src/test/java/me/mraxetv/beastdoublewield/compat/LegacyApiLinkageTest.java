package me.mraxetv.beastdoublewield.compat;

import me.mraxetv.beastlib.api.BeastLibAPI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Optional real-artifact linkage checks; these do not boot a Minecraft server. */
class LegacyApiLinkageTest {
    private final Path repository = Paths.get(System.getProperty("maven.repo.local",
            Paths.get(System.getProperty("user.home"), ".m2", "repository").toString()));

    @Test void actual194NativeBindingsCoverMiningSoundsToolsRayBoundsAndPackets() throws Exception {
        Path nativeJar = Paths.get(System.getProperty("beastdoublewield.legacy.1_9_4.jar",
                jar("spigot", "1.9.4-R0.1-SNAPSHOT").toString()));
        Assumptions.assumeTrue(Files.isRegularFile(nativeJar), "Optional BuildTools Spigot 1.9.4 artifact is not cached");
        try (URLClassLoader loader = loader(nativeJar)) {
            String nms = "net.minecraft.server.v1_9_R2.";
            Class<?> player = load(loader, nms + "EntityPlayer"), entity = load(loader, nms + "Entity");
            Class<?> human = load(loader, nms + "EntityHuman"), world = load(loader, nms + "World");
            Class<?> position = load(loader, nms + "BlockPosition"), state = load(loader, nms + "IBlockData");
            Class<?> stack = load(loader, nms + "ItemStack"), vector = load(loader, nms + "Vec3D");
            assertEquals(void.class, player.getMethod("attack", entity).getReturnType());
            assertNotNull(player.getField("playerInteractManager"));
            assertNotNull(player.getField("playerConnection"));
            assertEquals(boolean.class, load(loader, nms + "PlayerInteractManager").getMethod("breakBlock", position).getReturnType());
            assertEquals(float.class, load(loader, nms + "IBlockProperties").getMethod("a", human, world, position).getReturnType());
            assertEquals(state, load(loader, "org.bukkit.craftbukkit.v1_9_R2.block.CraftBlock").getDeclaredMethod("getData0").getReturnType());
            assertEquals(float.class, stack.getMethod("a", state).getReturnType());
            assertEquals(boolean.class, stack.getMethod("b", state).getReturnType());
            assertEquals("com.google.common.collect.Multimap", stack.getMethod("a", load(loader, nms + "EnumItemSlot")).getReturnType().getName());
            assertNotNull(load(loader, nms + "EnumItemSlot").getField("MAINHAND"));
            assertNotNull(load(loader, nms + "EnumItemSlot").getField("OFFHAND"));
            Class<?> modifier = load(loader, nms + "AttributeModifier");
            assertEquals(UUID.class, modifier.getMethod("a").getReturnType());
            assertEquals(String.class, modifier.getMethod("b").getReturnType());
            assertEquals(int.class, modifier.getMethod("c").getReturnType());
            assertEquals(double.class, modifier.getMethod("d").getReturnType());
            assertEquals(load(loader, nms + "Block"), state.getMethod("getBlock").getReturnType());
            assertEquals(load(loader, nms + "SoundEffectType"), load(loader, nms + "Block").getDeclaredField("stepSound").getType());
            assertEquals(float.class, load(loader, nms + "SoundEffectType").getMethod("a").getReturnType());
            assertEquals(float.class, load(loader, nms + "SoundEffectType").getMethod("b").getReturnType());
            Class<?> hand = load(loader, nms + "EnumHand");
            assertNotNull(hand.getField("OFF_HAND"));
            assertNotNull(load(loader, nms + "EntityLiving").getMethod("a", hand));
            assertNotNull(load(loader, nms + "PacketPlayOutBlockBreakAnimation").getConstructor(int.class, position, int.class));
            assertNotNull(load(loader, nms + "PlayerConnection").getMethod("sendPacket", load(loader, nms + "Packet")));
            Class<?> bounds = load(loader, nms + "AxisAlignedBB");
            assertEquals(bounds, entity.getMethod("getBoundingBox").getReturnType());
            for (String field : Arrays.asList("a", "b", "c", "d", "e", "f"))
                assertEquals(double.class, bounds.getField(field).getType());
            assertNotNull(vector.getConstructor(double.class, double.class, double.class));
            Class<?> hit = load(loader, nms + "MovingObjectPosition");
            assertEquals(hit, world.getMethod("rayTrace", vector, vector, boolean.class, boolean.class, boolean.class).getReturnType());
            assertEquals(position, hit.getMethod("a").getReturnType());
            assertEquals(vector, hit.getField("pos").getType());
            for (String field : Arrays.asList("x", "y", "z")) assertEquals(double.class, vector.getField(field).getType());
            for (String method : Arrays.asList("getX", "getY", "getZ")) assertEquals(int.class, position.getMethod(method).getReturnType());
            Class<?> facade = Class.forName(ServerAccess.class.getName(), true, loader);
            assertEquals("attack=native legacy, break=native legacy, mining speed=native legacy, geometry=native legacy",
                    facade.getMethod("backendSummary").invoke(null));
            assertPublicSignaturesLoad(loader);
        }
    }

    @Test void actual112ApiLoadsEveryPublicPluginSignatureWithoutModernTypes() throws Exception {
        Path nativeJar = jar("spigot", "1.12.2-R0.1-SNAPSHOT");
        Assumptions.assumeTrue(Files.isRegularFile(nativeJar), "Optional Spigot 1.12.2 artifact is not cached");
        try (URLClassLoader loader = loader(nativeJar)) {
            Class<?> facade = Class.forName(ServerAccess.class.getName(), true, loader);
            assertEquals("attack=native legacy, break=native legacy, mining speed=native legacy, geometry=native legacy",
                    facade.getMethod("backendSummary").invoke(null));
            assertPublicSignaturesLoad(loader);
        }
    }

    @Test void actual112NativeSignaturesIncludeVanillaAttackBreakSpeedAndPackets() throws Exception {
        Path nativeJar = jar("spigot", "1.12.2-R0.1-SNAPSHOT");
        Assumptions.assumeTrue(Files.isRegularFile(nativeJar), "Optional Spigot 1.12.2 artifact is not cached");
        try (URLClassLoader loader = loader(nativeJar)) {
            String nms = "net.minecraft.server.v1_12_R1.";
            Class<?> player = load(loader, nms + "EntityPlayer"), entity = load(loader, nms + "Entity");
            Class<?> position = load(loader, nms + "BlockPosition");
            assertNotNull(player.getMethod("attack", entity));
            assertNotNull(player.getField("playerInteractManager"));
            assertNotNull(load(loader, nms + "PlayerInteractManager").getMethod("breakBlock", position));
            assertNotNull(load(loader, nms + "IBlockProperties").getMethod("a", load(loader, nms + "EntityHuman"), load(loader, nms + "World"), position));
            assertNotNull(load(loader, "org.bukkit.craftbukkit.v1_12_R1.block.CraftBlock").getDeclaredMethod("getData0"));
            assertNotNull(load(loader, nms + "EntityLiving").getMethod("a", load(loader, nms + "EnumHand")));
            assertNotNull(load(loader, nms + "PacketPlayOutBlockBreakAnimation").getConstructor(int.class, position, int.class));
            assertNotNull(load(loader, nms + "ItemStack").getMethod("a", load(loader, nms + "EnumItemSlot")));
            assertNotNull(entity.getMethod("getBoundingBox"));
        }
    }

    @Test void cachedMiddleAndModernApisChooseCapabilitiesIndependently() throws Exception {
        Path dependencies = jar("spigot", "1.12.2-R0.1-SNAPSHOT");
        Assumptions.assumeTrue(Files.isRegularFile(dependencies), "Optional dependency artifact is not cached");
        int checked = 0;
        for (String version : Arrays.asList("1.13.2-R0.1-SNAPSHOT", "1.16.4-R0.1-SNAPSHOT", "1.18.2-R0.1-SNAPSHOT", "1.20.2-R0.1-SNAPSHOT")) {
            Path api = jar("spigot-api", version);
            if (!Files.isRegularFile(api)) continue;
            try (URLClassLoader loader = loader(api, dependencies)) {
                Class<?> facade = Class.forName(ServerAccess.class.getName(), true, loader);
                String summary = (String) facade.getMethod("backendSummary").invoke(null);
                assertTrue(summary.endsWith("geometry=Bukkit"), version + ": " + summary);
                if (version.startsWith("1.13")) assertTrue(summary.startsWith("attack=native legacy, break=native legacy"), summary);
                if (version.startsWith("1.16")) assertTrue(summary.startsWith("attack=Bukkit, break=native legacy"), summary);
                if (version.startsWith("1.18") || version.startsWith("1.20")) assertTrue(summary.startsWith("attack=Bukkit, break=Bukkit, mining speed=Bukkit"), summary);
                checked++;
            }
        }
        Assumptions.assumeTrue(checked > 0, "No optional historical API artifacts cached");
    }

    private static Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException { return Class.forName(name, false, loader); }
    private static void assertPublicSignaturesLoad(ClassLoader loader) throws Exception {
        Path classes = Paths.get(ServerAccess.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classes.resolve("me/mraxetv/beastdoublewield"))) {
            paths.filter(p -> p.toString().endsWith(".class") && !p.getFileName().toString().contains("$Modern"))
                    .forEach(files::add);
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            String name = classes.relativize(file).toString().replace('\\', '.').replace('/', '.').replaceAll("\\.class$", "");
            Class<?> type = Class.forName(name, false, loader);
            assertDoesNotThrow(() -> { type.getDeclaredMethods(); type.getDeclaredFields(); }, name);
        }
    }
    private Path jar(String artifact, String version) {
        return repository.resolve("org/spigotmc").resolve(artifact).resolve(version).resolve(artifact + "-" + version + ".jar");
    }
    private URLClassLoader loader(Path first, Path... extra) throws Exception {
        List<URL> urls = new ArrayList<>(); urls.add(first.toUri().toURL());
        urls.add(ServerAccess.class.getProtectionDomain().getCodeSource().getLocation());
        urls.add(BeastLibAPI.class.getProtectionDomain().getCodeSource().getLocation());
        for (Path path : extra) urls.add(path.toUri().toURL());
        return new URLClassLoader(urls.toArray(new URL[0]), null);
    }
}
