package me.mraxetv.beastdoublewield.util;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PlayerPreferencesTest {
    @TempDir File directory;
    private Plugin owner() { Plugin plugin=mock(Plugin.class);when(plugin.getDataFolder()).thenReturn(directory);when(plugin.getName()).thenReturn("BeastDoubleWield");return plugin; }
    private Player player(UUID id) { Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(id);return player; }
    @Test void preferencesSurviveRestartByUuid() throws Exception {
        Plugin owner=owner(); UUID id=UUID.randomUUID(); PlayerPreferences preferences=new PlayerPreferences(owner);
        assertTrue(preferences.enabled(player(id)));preferences.setEnabled(player(id),false);
        assertFalse(new PlayerPreferences(owner).enabled(player(id)));
        assertTrue(new PlayerPreferences(owner).enabled(player(UUID.randomUUID())));
    }
    @Test void reenablePersistsAndDoesNotAdoptOldPreference() throws Exception {
        Plugin owner=owner(); Player player=player(UUID.randomUUID());PlayerPreferences preferences=new PlayerPreferences(owner);
        preferences.setEnabled(player,false);preferences.setEnabled(player,true);
        assertTrue(new PlayerPreferences(owner).enabled(player));
    }
}
