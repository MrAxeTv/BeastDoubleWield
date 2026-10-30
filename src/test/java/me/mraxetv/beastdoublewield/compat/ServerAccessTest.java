package me.mraxetv.beastdoublewield.compat;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerAccessTest {
    @Test void modernActionsRemainNativeBukkitOperations() {
        Player player = mock(Player.class); Entity target = mock(Entity.class); Block block = mock(Block.class);
        when(player.breakBlock(block)).thenReturn(false); when(block.getBreakSpeed(player)).thenReturn(0.125F);
        ServerAccess.attack(player, target); verify(player).attack(target);
        assertFalse(ServerAccess.breakBlock(player, block));
        assertEquals(0.125F, ServerAccess.breakSpeed(player, block));
        verify(player, never()).damage(anyDouble());
    }
    @Test void earlyBukkitAttackApisCannotSelectTheMobOnlyPlayerPath() {
        assertTrue(ServerAccess.requiresNativePlayerAttack("org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer"));
        assertTrue(ServerAccess.requiresNativePlayerAttack("org.bukkit.craftbukkit.v1_16_R3.CraftServer"));
        assertTrue(ServerAccess.requiresNativePlayerAttack("org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer"));
        assertFalse(ServerAccess.requiresNativePlayerAttack("org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer"));
        assertFalse(ServerAccess.requiresNativePlayerAttack("org.bukkit.craftbukkit.entity.CraftPlayer"));
        assertFalse(ServerAccess.requiresNativePlayerAttack("org.bukkit.entity.Player$MockitoMock"));
    }
    @Test void rayIntersectsNearestFaceAndRejectsMissesAndOcclusion() {
        double[] box = {2, 0, -1, 3, 2, 1};
        assertEquals(2, ServerAccess.rayIntersection(new Vector(0, 1, 0), new Vector(1, 0, 0), box, 4, 0));
        assertEquals(-1, ServerAccess.rayIntersection(new Vector(0, 3, 0), new Vector(1, 0, 0), box, 4, 0));
        assertEquals(-1, ServerAccess.rayIntersection(new Vector(0, 1, 0), new Vector(1, 0, 0), box, 1, 0));
        assertEquals(0, ServerAccess.rayIntersection(new Vector(2.5, 1, 0), new Vector(1, 0, 0), box, 4, 0));
    }
    @Test void reachMeasuresHitboxInsteadOfEntityCenter() {
        assertEquals(9, ServerAccess.distanceSquared(new Vector(0, 1, 0), new double[]{3, 0, -1, 8, 2, 1}));
    }
    @Test void airDetectionDoesNotNeedModernMaterialMethods() {
        assertTrue(ServerAccess.air(Material.AIR)); assertTrue(ServerAccess.air(Material.CAVE_AIR));
        assertFalse(ServerAccess.air(Material.DIAMOND_AXE));
    }
    @Test void legacyInteractionAliasesUseTheirModernNames() {
        assertEquals("LEAD", ServerAccess.materialName(Material.valueOf("LEGACY_LEASH")));
        assertEquals("SPAWN_EGG", ServerAccess.materialName(Material.valueOf("LEGACY_MONSTER_EGG")));
        assertEquals("FIREWORK_ROCKET", ServerAccess.materialName(Material.valueOf("LEGACY_FIREWORK")));
        assertEquals("WHEAT_SEEDS", ServerAccess.materialName(Material.valueOf("LEGACY_SEEDS")));
        assertEquals("GOLDEN_SHOVEL", ServerAccess.materialName(Material.valueOf("LEGACY_GOLD_SPADE")));
    }
    @Test void legacyResolverUsesExplicitNameAndShape() {
        Fixture fixture = new Fixture();
        Method exact = LegacyNative.resolve(Fixture.class, "test attack", void.class, new String[]{"attack"}, "victim");
        LegacyNative.invoke(exact, fixture, "victim"); assertEquals("victim", fixture.value);
        assertThrows(IllegalStateException.class, () -> LegacyNative.resolve(Fixture.class, "wrong shape", void.class, new String[]{"attack"}, 1));
        assertThrows(IllegalStateException.class, () -> LegacyNative.resolve(Fixture.class, "unknown", void.class, new String[]{"unknown"}, "victim"));
    }
    @Test void nativeExceptionsAndFatalErrorsAreNotHidden() {
        Fixture fixture = new Fixture();
        Method failure = LegacyNative.resolve(Fixture.class, "test failure", void.class, new String[]{"failure"});
        assertThrows(IllegalArgumentException.class, () -> LegacyNative.invoke(failure, fixture));
        Method fatal = LegacyNative.resolve(Fixture.class, "test fatal", void.class, new String[]{"fatal"});
        assertThrows(AssertionError.class, () -> LegacyNative.invoke(fatal, fixture));
    }
    @Test void covariantCraftHandlesDoNotBecomeAmbiguous() {
        CowFixture fixture = new CowFixture();
        Method handle = LegacyNative.resolve(CowFixture.class, "native handle", null, new String[]{"getHandle"});
        assertFalse(handle.isBridge()); assertFalse(handle.isSynthetic());
        assertEquals("cow", LegacyNative.invoke(handle, fixture));
    }
    @Test void legacyAttributeKeysSupportStringsAndNativeRegistryObjects() {
        assertTrue(LegacyNative.isAttributeKey("generic.attackDamage", Attribute.GENERIC_ATTACK_DAMAGE));
        assertTrue(LegacyNative.isAttributeKey(new NativeAttributeKey("attribute.name.generic.attack_damage"), Attribute.GENERIC_ATTACK_DAMAGE));
        assertTrue(LegacyNative.isAttributeKey(new NativeAttributeKey("attribute.name.generic.attack_speed"), Attribute.GENERIC_ATTACK_SPEED));
        assertFalse(LegacyNative.isAttributeKey(new NativeAttributeKey("attribute.name.generic.attack_speed"), Attribute.GENERIC_ATTACK_DAMAGE));
        assertFalse(LegacyNative.isAttributeKey(new NativeAttributeKey("attribute.name.generic.armor"), Attribute.GENERIC_ATTACK_DAMAGE));
    }
    @Test void legacyAttributeOperationsSupportIntegerAndEnumFormats() {
        assertEquals(0, LegacyNative.operationId(0));
        assertEquals(0, LegacyNative.operationId(NativeOperation.ADDITION));
        assertEquals(1, LegacyNative.operationId(NativeOperation.MULTIPLY_BASE));
        assertEquals(2, LegacyNative.operationId(NativeOperation.MULTIPLY_TOTAL));
        assertThrows(IllegalStateException.class, () -> LegacyNative.operationId("unknown"));
    }
    @Test void soundGroupUsesMappedLegacyFieldWithoutGuessingObfuscatedAccessorLetters() {
        Object sound = new Object();
        assertSame(sound, LegacyNative.soundGroup(new StateWithBlock(new BlockWithLegacySound(sound))));
        assertThrows(IllegalStateException.class, () -> LegacyNative.soundGroup(new StateWithBlock(new Object())));
    }
    @Test void stateAndBlockSoundAccessorsRemainPreferredWhereAvailable() {
        Object sound = new Object();
        assertSame(sound, LegacyNative.soundGroup(new StateWithSound(sound)));
        assertSame(sound, LegacyNative.soundGroup(new StateWithBlock(new BlockWithSound(sound))));
    }
    @Test void stateDependentSoundAccessorReceivesTheActualBlockState() {
        BlockWithStateSound block = new BlockWithStateSound();
        StateWithBlock state = new StateWithBlock(block);
        assertSame(state, LegacyNative.soundGroup(state));
    }
    @Test void emptyHandsNeverRequireANativeToolStack() {
        Block block = mock(Block.class);
        assertFalse(ServerAccess.preferredTool(block, null));
        assertFalse(ServerAccess.preferredTool(block, new ItemStack(Material.AIR)));
        assertFalse(ServerAccess.preferredTool(block, new ItemStack(Material.DIAMOND_PICKAXE, 0)));
        verifyNoInteractions(block);
    }
    static class StateWithBlock {
        private final Object block;
        StateWithBlock(Object block) { this.block = block; }
        public Object getBlock() { return block; }
    }
    static class BlockWithLegacySound {
        protected final Object stepSound;
        BlockWithLegacySound(Object sound) { stepSound = sound; }
    }
    static class StateWithSound {
        private final Object sound;
        StateWithSound(Object sound) { this.sound = sound; }
        public Object getStepSound() { return sound; }
    }
    static class BlockWithSound extends BlockWithLegacySound {
        BlockWithSound(Object sound) { super(sound); }
        public Object getStepSound() { return stepSound; }
    }
    static class BlockWithStateSound extends BlockWithLegacySound {
        BlockWithStateSound() { super(new Object()); }
        public Object getStepSound(StateWithBlock state) { return state; }
    }
    enum NativeOperation { ADDITION, MULTIPLY_BASE, MULTIPLY_TOTAL }
    static class NativeAttributeKey {
        private final String name;
        NativeAttributeKey(String name) { this.name = name; }
        public String getName() { return name; }
    }
    static class EntityFixture { public Object getHandle() { return new Object(); } }
    static class CowFixture extends EntityFixture { @Override public String getHandle() { return "cow"; } }
    static class Fixture {
        String value;
        private void attack(String target) { value = target; }
        public void failure() { throw new IllegalArgumentException("native failure"); }
        public void fatal() { throw new AssertionError("fatal"); }
    }
}
