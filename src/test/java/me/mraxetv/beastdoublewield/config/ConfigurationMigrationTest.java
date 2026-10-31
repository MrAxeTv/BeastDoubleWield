package me.mraxetv.beastdoublewield.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationMigrationTest {
    @TempDir Path directory;

    private ConfigurationMigration.Prepared prepare() throws Exception {
        return ConfigurationMigration.prepare(directory.toFile(), name ->
                getClass().getClassLoader().getResourceAsStream(name));
    }

    private void write(String name, String text) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private void accept(ConfigurationMigration.Prepared prepared) throws IOException {
        try (ConfigurationMigration.Commit commit = prepared.commit()) { commit.accept(); }
    }

    @Test void explicitAliasesPreserveFalseEmptyListsNumbersStringsAndUnknownOptions() throws Exception {
        write("config.yml", "disabled-worlds: []\nupdates:\n  manifest-url: ''\n  notify-admins: false\n"
                + "  interval-minutes: 77\nmetrics:\n  interval-minutes: 19\ninteraction:\n"
                + "  preserve-entity-use: false\n  preserve-block-use: false\n  preserve-main-hand-use: false\n"
                + "placeholder:\n  enabled: true\n  first-person-arm: true\nattack:\n  allow-empty-hand: true\n  game-modes: []\n"
                + "  correct-tool-only: true\n  cancel-interaction: false\n  protect-npcs: false\n"
                + "mine:\n  input-progress-ticks: 3\n  input-timeout-ticks: 9\n  allow-empty-hand: true\n"
                + "  game-modes: [SURVIVAL, ADVENTURE]\n  correct-tool-only: true\n  cancel-interaction: false\n"
                + "# Keep this owner comment.\nowner-option:\n  zero: 0\n  label: custom\n");
        ConfigurationMigration.Prepared prepared = prepare();
        assertEquals(Collections.emptyList(), prepared.settings().getStringList("DisabledWorlds"));
        assertEquals("", prepared.settings().getString("Updates.ManifestUrl"));
        assertFalse(prepared.settings().getBoolean("Updates.NotifyAdmins"));
        assertEquals(77, prepared.settings().getInt("Updates.IntervalMinutes"));
        assertEquals(19, prepared.settings().getInt("Metrics.IntervalMinutes"));
        assertFalse(prepared.settings().getBoolean("Interaction.PreserveMainHandUse"));
        assertTrue(prepared.settings().getBoolean("OffhandArm.Enabled"));
        assertEquals(Collections.emptyList(), prepared.settings().getStringList("Attack.GameModes"));
        assertEquals(Arrays.asList("SURVIVAL", "ADVENTURE"), prepared.settings().getStringList("Mine.GameModes"));
        assertEquals(3, prepared.settings().getInt("Mine.InputProgressTicks"));
        assertEquals(9, prepared.settings().getInt("Mine.InputTimeoutTicks"));
        assertEquals(0, prepared.settings().getInt("owner-option.zero"));
        assertFalse(prepared.settings().contains("mine.input-timeout-ticks"));
        accept(prepared);
        assertTrue(new String(Files.readAllBytes(directory.resolve("config.yml")), StandardCharsets.UTF_8)
                .contains("Keep this owner comment."));
    }

    @Test void canonicalExplicitValuesWinBeforeDefaultsAreMerged() throws Exception {
        write("config.yml", "disabled-worlds: [legacy]\ndisabledWorlds: [camel]\nDisabledWorlds: []\nattack:\n"
                + "  allow-empty-hand: true\n  allowEmptyHand: true\nAttack:\n  AllowEmptyHand: false\nUpdates:\n"
                + "  manifest-url: 'https://old.example/feed.yml'\n  manifestUrl: 'https://camel.example/feed.yml'\n  ManifestUrl: ''\n");
        write("Lang/English.yml", "prefix: 'Old prefix'\nPrefix: 'Canonical prefix'\n"
                + "help: 'Old help'\nHelp: ''\nOwnerMessage: 'untouched'\n");
        ConfigurationMigration.Prepared prepared = prepare();
        assertTrue(prepared.settings().getStringList("DisabledWorlds").isEmpty());
        assertFalse(prepared.settings().getBoolean("Attack.AllowEmptyHand"));
        assertEquals("", prepared.settings().getString("Updates.ManifestUrl"));
        assertEquals("Canonical prefix", prepared.language().getString("Prefix"));
        assertEquals("", prepared.language().getString("Help"));
        assertEquals("untouched", prepared.language().getString("OwnerMessage"));
        assertNull(prepared.language().getBlock("prefix"));
    }

    @Test void originalPascalAndPreviousCamelMessagesKeepTheirCustomText() throws Exception {
        String[] keys = {"Prefix", "NoPermission", "PlayerOnly", "PreferenceFailed", "Enabled", "Disabled",
                "Reloaded", "ReloadFailed", "Status", "PersonalStatus", "Help", "AdminHelp", "ServicesStatus",
                "UpdateRequest", "UpdateAvailable", "UpdateCurrent", "UpdateFailed", "IntegrationStatus"};
        for (boolean camel : new boolean[]{false, true}) {
            StringBuilder old = new StringBuilder();
            for (String key : keys) {
                String written = camel ? Character.toLowerCase(key.charAt(0)) + key.substring(1) : key;
                old.append(written).append(": 'owner ").append(key).append("'\n");
            }
            write("Lang/English.yml", old.toString());
            ConfigurationMigration.Prepared prepared = prepare();
            for (String key : keys) {
                assertEquals("owner " + key, prepared.language().getString(key));
                assertNull(prepared.language().getBlock(Character.toLowerCase(key.charAt(0)) + key.substring(1)));
            }
        }
    }

    @Test void partialPascalAndLegacySectionsMergeWithoutDroppingOwnerValues() throws Exception {
        write("config.yml", "Attack:\n  AllowEmptyHand: false\n  Materials:\n    Mode: WHITELIST\n"
                + "    owner:\n      preferred: canonical\nattack:\n  allowEmptyHand: true\n"
                + "  correct-tool-only: true\n  # Keep this nested comment.\n  ownerOption: kept\n"
                + "  materials:\n    mode: BLACKLIST\n    patterns: []\n    owner:\n      extra: retained\n"
                + "mine:\n  allow-empty-hand: false\n  allowEmptyHand: true\n"
                + "  input-progress-ticks: 2\n  inputProgressTicks: 3\n");
        ConfigurationMigration.Prepared prepared = prepare();
        assertFalse(prepared.settings().getBoolean("Attack.AllowEmptyHand"));
        assertTrue(prepared.settings().getBoolean("Attack.CorrectToolOnly"));
        assertEquals("kept", prepared.settings().getString("Attack.ownerOption"));
        assertEquals("WHITELIST", prepared.settings().getString("Attack.Materials.Mode"));
        assertTrue(prepared.settings().getStringList("Attack.Materials.Patterns").isEmpty());
        assertEquals("canonical", prepared.settings().getString("Attack.Materials.owner.preferred"));
        assertEquals("retained", prepared.settings().getString("Attack.Materials.owner.extra"));
        assertTrue(prepared.settings().getBoolean("Mine.AllowEmptyHand"));
        assertEquals(3, prepared.settings().getInt("Mine.InputProgressTicks"));
        assertFalse(prepared.settings().contains("attack"));
        assertFalse(prepared.settings().contains("Mine.allowEmptyHand"));
        accept(prepared);
        assertTrue(new String(Files.readAllBytes(directory.resolve("config.yml")), StandardCharsets.UTF_8)
                .contains("Keep this nested comment."));
    }

    @Test void successfulMigrationBacksUpExactBytesAndRepeatedLoadDoesNotRewrite() throws Exception {
        write("config.yml", "disabled-worlds: [lobby]\n");
        write("Lang/English.yml", "Prefix: 'owner'\n");
        byte[] original = Files.readAllBytes(directory.resolve("config.yml"));
        accept(prepare());
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml.pre-PascalCase.bak")));
        Path config = directory.resolve("config.yml"), language = directory.resolve("Lang/en.yml");
        byte[] configBytes = Files.readAllBytes(config), languageBytes = Files.readAllBytes(language);
        FileTime stamp = FileTime.fromMillis(1234567890000L);
        Files.setLastModifiedTime(config, stamp);
        Files.setLastModifiedTime(language, stamp);
        for (String code : LanguageCatalog.codes())
            Files.setLastModifiedTime(directory.resolve(LanguageCatalog.resource(code)), stamp);
        accept(prepare());
        assertArrayEquals(configBytes, Files.readAllBytes(config));
        assertArrayEquals(languageBytes, Files.readAllBytes(language));
        assertEquals(stamp, Files.getLastModifiedTime(config));
        assertEquals(stamp, Files.getLastModifiedTime(language));
        for (String code : LanguageCatalog.codes())
            assertEquals(stamp, Files.getLastModifiedTime(directory.resolve(LanguageCatalog.resource(code))), code);
        assertFalse(Files.exists(directory.resolve("config.yml.pre-PascalCase.1.bak")));
    }

    @Test void preferredSectionWinsMixedLeafSpellingsAndUnknownSubtreesStayUntouched() throws Exception {
        write("config.yml", "Attack:\n  allow-empty-hand: false\nattack:\n  AllowEmptyHand: true\n"
                + "  owner:\n    enabled: false\n    allowEmptyHand: custom\n");
        ConfigurationMigration.Prepared prepared = prepare();
        assertFalse(prepared.settings().getBoolean("Attack.AllowEmptyHand"));
        assertEquals(Boolean.FALSE, prepared.settings().get("Attack.owner.enabled"));
        assertEquals("custom", prepared.settings().getString("Attack.owner.allowEmptyHand"));
        assertFalse(prepared.settings().contains("Attack.owner.Enabled"));
        assertFalse(prepared.settings().contains("Attack.owner.AllowEmptyHand"));
        write("config.yml", "Attack: false\nattack:\n  enabled: true\n");
        assertThrows(IllegalArgumentException.class, this::prepare);
    }

    @Test void abandonedCommitRestoresBothFilesAndDoesNotOverwritePreviousBackup() throws Exception {
        write("config.yml", "enabled: false\n");
        write("Lang/English.yml", "Prefix: 'owner'\n");
        write("config.yml.pre-PascalCase.bak", "previous backup");
        write("config.yml.pre-camelCase.bak", "earlier camel backup");
        byte[] config = Files.readAllBytes(directory.resolve("config.yml"));
        byte[] language = Files.readAllBytes(directory.resolve("Lang/English.yml"));
        try (ConfigurationMigration.Commit ignored = prepare().commit()) {
            assertFalse(Arrays.equals(config, Files.readAllBytes(directory.resolve("config.yml"))));
        }
        assertArrayEquals(config, Files.readAllBytes(directory.resolve("config.yml")));
        assertArrayEquals(language, Files.readAllBytes(directory.resolve("Lang/English.yml")));
        for (String code : LanguageCatalog.codes()) assertFalse(Files.exists(directory.resolve(LanguageCatalog.resource(code))));
        assertEquals("previous backup", new String(Files.readAllBytes(directory.resolve("config.yml.pre-PascalCase.bak")), StandardCharsets.UTF_8));
        assertArrayEquals(config, Files.readAllBytes(directory.resolve("config.yml.pre-PascalCase.1.bak")));
        assertEquals("earlier camel backup", new String(Files.readAllBytes(directory.resolve("config.yml.pre-camelCase.bak")), StandardCharsets.UTF_8));
    }

    @Test void interveningOwnerEditPreventsCommitBeforeAnyOtherFileChanges() throws Exception {
        write("config.yml", "disabled-worlds: []\n");
        write("Lang/English.yml", "Prefix: 'owner'\n");
        ConfigurationMigration.Prepared prepared = prepare();
        byte[] original = Files.readAllBytes(directory.resolve("config.yml"));
        write("Lang/English.yml", "Prefix: 'edited while preparing'\n");
        assertThrows(IOException.class, prepared::commit);
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml")));
        assertFalse(Files.exists(directory.resolve("config.yml.pre-PascalCase.bak")));
    }

    @Test void duplicateKeysAndNonStringMessagesAreRejectedWithoutWrites() throws Exception {
        write("config.yml", "enabled: true\nenabled: false\n");
        assertThrows(InvalidConfigurationException.class, this::prepare);
        write("config.yml", "enabled: true\n");
        write("Lang/English.yml", "Prefix: [not, a, string]\n");
        assertThrows(IllegalArgumentException.class, this::prepare);
        assertFalse(Files.exists(directory.resolve("config.yml.pre-PascalCase.bak")));
    }

    @Test void scalarCannotReplaceAKnownSection() throws Exception {
        for (String section : Arrays.asList("placeholder", "interaction", "updates", "swing")) {
            write("config.yml", section + ": false\n");
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, this::prepare);
            assertTrue(error.getMessage().contains(Character.toUpperCase(section.charAt(0)) + section.substring(1)));
            assertEquals(section + ": false\n", new String(Files.readAllBytes(directory.resolve("config.yml")), StandardCharsets.UTF_8));
        }
    }

    @Test void secondFileWriteFailureRestoresFirstFileAndTimestamp() throws Exception {
        write("config.yml", "disabled-worlds: [lobby]\n");
        Path config = directory.resolve("config.yml");
        byte[] original = Files.readAllBytes(config);
        FileTime stamp = FileTime.fromMillis(1234567890000L);
        Files.setLastModifiedTime(config, stamp);
        ConfigurationMigration.Prepared prepared = prepare();
        // The language file was absent when staged. A new ordinary file now
        // prevents its parent directory from being created after config commits.
        write("Lang", "owner-created file");
        assertThrows(IOException.class, prepared::commit);
        assertArrayEquals(original, Files.readAllBytes(config));
        assertEquals(stamp, Files.getLastModifiedTime(config));
        assertEquals("owner-created file", new String(Files.readAllBytes(directory.resolve("Lang")), StandardCharsets.UTF_8));
    }

    @Test void freshInstallationCreatesEveryBundledLanguageWithoutLegacyEnglishFile() throws Exception {
        ConfigurationMigration.Prepared prepared = prepare();
        assertFalse(Files.exists(directory.resolve("config.yml")));
        assertFalse(Files.exists(directory.resolve("Lang/English.yml")));
        accept(prepared);
        assertTrue(Files.isRegularFile(directory.resolve("config.yml")));
        assertFalse(Files.exists(directory.resolve("Lang/English.yml")));
        assertEquals(50, LanguageCatalog.codes().size());
        assertEquals(50, new HashSet<>(LanguageCatalog.codes()).size());
        for (String code : LanguageCatalog.codes())
            assertTrue(Files.isRegularFile(directory.resolve(LanguageCatalog.resource(code))), code);
        try (Stream<Path> files = Files.list(directory.resolve("Lang"))) { assertEquals(50, files.count()); }
        assertEquals("en", prepared.settings().getString("Language"));
        assertFalse(Files.exists(directory.resolve("config.yml.pre-PascalCase.bak")));
        assertNotNull(prepare().language().getBlock("NoPermission"));
    }

    @Test void languageCodesNormalizeCaseUnderscoresAndEnglishAliasButRejectOtherPaths() throws Exception {
        assertEquals("pt-BR", LanguageCatalog.canonical("PT_br"));
        assertEquals("en-US", LanguageCatalog.canonical("en_us"));
        assertEquals("fil-PH", LanguageCatalog.canonical("FIL-ph"));
        assertEquals("en", LanguageCatalog.canonical("English"));
        for (Object invalid : Arrays.asList("../en", "en/../../config", "en.yml", "zz", "", false, 7,
                Collections.singletonList("en"))) assertThrows(IllegalArgumentException.class, () -> LanguageCatalog.canonical(invalid));
        write("config.yml", "Language: '../en'\n");
        byte[] original = Files.readAllBytes(directory.resolve("config.yml"));
        assertThrows(IllegalArgumentException.class, this::prepare);
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml")));
        assertFalse(Files.exists(directory.resolve("Lang")));
    }

    @Test void selectedLocaleUsesItsOwnDefaultsAndPreservesCustomMessages() throws Exception {
        write("config.yml", "Language: PT_br\n");
        write("Lang/pt-BR.yml", "prefix: 'Custom selected prefix'\nOwnerText: 'preserved'\n");
        write("Lang/English.yml", "NoPermission: 'Owner English only'\n");
        ConfigurationMigration.Prepared prepared = prepare();
        assertEquals("pt-BR", prepared.settings().getString("Language"));
        assertEquals("Custom selected prefix", prepared.language().getString("Prefix"));
        assertEquals("preserved", prepared.language().getString("OwnerText"));
        YamlConfiguration defaults;
        try (InputStream input = getClass().getResourceAsStream("/Lang/pt-BR.yml")) {
            assertNotNull(input);
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        assertEquals(defaults.getString("NoPermission"), prepared.language().getString("NoPermission"));
        assertNotEquals("Owner English only", prepared.language().getString("NoPermission"));
        accept(prepared);
        assertEquals("Owner English only", YamlConfiguration.loadConfiguration(directory.resolve("Lang/en.yml").toFile()).getString("NoPermission"));
    }

    @Test void canonicalEnglishWinsAndMissingLegacyMessagesMigrateWithoutChangingOriginal() throws Exception {
        write("Lang/English.yml", "prefix: 'Legacy prefix'\nHelp: |-\n  owner line one\n  owner line two\nOwnerOption: kept\n");
        write("Lang/en.yml", "Prefix: 'Canonical prefix'\n");
        byte[] original = Files.readAllBytes(directory.resolve("Lang/English.yml"));
        FileTime stamp = FileTime.fromMillis(1234567890000L);
        Files.setLastModifiedTime(directory.resolve("Lang/English.yml"), stamp);
        ConfigurationMigration.Prepared prepared = prepare();
        assertEquals("Canonical prefix", prepared.language().getString("Prefix"));
        assertEquals("owner line one\nowner line two", prepared.language().getString("Help"));
        assertEquals("kept", prepared.language().getString("OwnerOption"));
        accept(prepared);
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("Lang/English.yml")));
        assertEquals(stamp, Files.getLastModifiedTime(directory.resolve("Lang/English.yml")));
        assertFalse(Files.exists(directory.resolve("Lang/English.yml.pre-PascalCase.bak")));
        assertTrue(Files.isRegularFile(directory.resolve("Lang/en.yml.pre-PascalCase.bak")));
    }

    @Test void unavailableBundledLocaleAndInvalidSelectedMessagesPreventAllWrites() throws Exception {
        write("config.yml", "Language: hr\n");
        write("Lang/hr.yml", "NoPermission: [invalid, message]\n");
        byte[] original = Files.readAllBytes(directory.resolve("config.yml"));
        assertThrows(IllegalArgumentException.class, this::prepare);
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml")));
        assertFalse(Files.exists(directory.resolve("Lang/en.yml")));
        write("Lang/hr.yml", "NoPermission: 'Owner message'\n");
        assertThrows(IOException.class, () -> ConfigurationMigration.prepare(directory.toFile(), name ->
                name.equals("Lang/hr.yml") ? null : getClass().getClassLoader().getResourceAsStream(name)));
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml")));
        assertFalse(Files.exists(directory.resolve("Lang/ar.yml")));
        write("Lang/de.yml", "Prefix: [unterminated\n");
        InvalidConfigurationException malformed = assertThrows(InvalidConfigurationException.class, this::prepare);
        assertTrue(malformed.getMessage().contains("Lang/de.yml"));
        assertNotNull(malformed.getCause());
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml")));
    }

    @Test void shortArmInstructionsRefreshEvenWithoutOldAliases() throws Exception {
        write("config.yml", "# Retain unrelated owner notes.\nEnabled: true\nOffhandArm:\n"
                + "  # Obsolete enormous arm instructions.\n  Enabled: true\n");
        accept(prepare());
        String written = new String(Files.readAllBytes(directory.resolve("config.yml")), StandardCharsets.UTF_8);
        assertFalse(written.contains("Obsolete enormous arm instructions."));
        assertTrue(written.contains("Retain unrelated owner notes."));
        assertTrue(prepare().settings().getBoolean("OffhandArm.Enabled"));
    }

    @Test void legacyArmRequiresBothFlagsAndMissingFlagsStayDisabled() throws Exception {
        for (Boolean enabled : Arrays.asList(null, false, true)) {
            for (Boolean firstPerson : Arrays.asList(null, false, true)) {
                String yaml = "Placeholder:" + (enabled == null && firstPerson == null ? " {}\n" : "\n");
                if (enabled != null) yaml += "  Enabled: " + enabled + "\n";
                if (firstPerson != null) yaml += "  FirstPersonArm: " + firstPerson + "\n";
                write("config.yml", yaml);
                ConfigurationMigration.Prepared prepared = prepare();
                assertEquals(Boolean.TRUE.equals(enabled) && Boolean.TRUE.equals(firstPerson),
                        prepared.settings().getBoolean("OffhandArm.Enabled"), yaml);
                assertFalse(prepared.settings().contains("Placeholder"));
                assertFalse(prepared.settings().contains("OffhandArm.FirstPersonArm"));
            }
        }
    }

    @Test void explicitNewArmSwitchWinsAndPartialNewSectionKeepsUnknownLegacyOptions() throws Exception {
        for (boolean enabled : new boolean[]{false, true}) {
            write("config.yml", "offhandArm:\n  enabled: " + enabled + "\nplaceholder:\n  enabled: "
                    + !enabled + "\n  first-person-arm: " + !enabled + "\n");
            assertEquals(enabled, prepare().settings().getBoolean("OffhandArm.Enabled"));
        }
        write("config.yml", "OffhandArm:\n  owner:\n    preferred: new\nPlaceholder:\n  Enabled: true\n"
                + "  FirstPersonArm: true\n  owner:\n    preferred: old\n    retained: old extra\n"
                + "  # Preserve the owner's arm note.\n  ownerOption: kept\n");
        byte[] original = Files.readAllBytes(directory.resolve("config.yml"));
        ConfigurationMigration.Prepared prepared = prepare();
        assertTrue(prepared.settings().getBoolean("OffhandArm.Enabled"));
        assertEquals("new", prepared.settings().getString("OffhandArm.owner.preferred"));
        assertEquals("old extra", prepared.settings().getString("OffhandArm.owner.retained"));
        assertEquals("kept", prepared.settings().getString("OffhandArm.ownerOption"));
        assertFalse(prepared.settings().contains("Placeholder"));
        accept(prepared);
        Path config = directory.resolve("config.yml");
        byte[] migrated = Files.readAllBytes(config);
        assertTrue(new String(migrated, StandardCharsets.UTF_8).contains("Preserve the owner's arm note."));
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml.pre-PascalCase.bak")));
        FileTime stamp = FileTime.fromMillis(1234567890000L);
        Files.setLastModifiedTime(config, stamp);
        accept(prepare());
        assertArrayEquals(migrated, Files.readAllBytes(config));
        assertEquals(stamp, Files.getLastModifiedTime(config));
        assertFalse(Files.exists(directory.resolve("config.yml.pre-PascalCase.1.bak")));
    }

    @Test void legacyArmAliasesRetainSectionFirstPrecedence() throws Exception {
        for (String firstPerson : Arrays.asList("FirstPersonArm", "firstPersonArm", "first-person-arm")) {
            write("config.yml", "placeholder:\n  enabled: true\n  " + firstPerson + ": true\n");
            assertTrue(prepare().settings().getBoolean("OffhandArm.Enabled"));
        }
        write("config.yml", "Placeholder:\n  enabled: true\n  first-person-arm: false\n"
                + "placeholder:\n  Enabled: false\n  FirstPersonArm: true\n  ownerOption: retained\n");
        ConfigurationMigration.Prepared prepared = prepare();
        assertFalse(prepared.settings().getBoolean("OffhandArm.Enabled"));
        assertEquals("retained", prepared.settings().getString("OffhandArm.ownerOption"));
        assertFalse(prepared.settings().contains("Placeholder"));
        assertFalse(prepared.settings().contains("placeholder"));
    }

    @Test void invalidLegacyArmBooleansRejectBeforeWritingEvenWithANewSwitch() throws Exception {
        for (String invalidKey : Arrays.asList("Enabled", "FirstPersonArm")) {
            write("config.yml", "OffhandArm:\n  Enabled: true\nPlaceholder:\n  " + invalidKey + ": 'invalid'\n");
            byte[] original = Files.readAllBytes(directory.resolve("config.yml"));
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, this::prepare);
            assertTrue(failure.getMessage().contains("Placeholder." + invalidKey));
            assertArrayEquals(original, Files.readAllBytes(directory.resolve("config.yml")));
            assertFalse(Files.exists(directory.resolve("config.yml.pre-PascalCase.bak")));
            assertFalse(Files.exists(directory.resolve("Lang")));
        }
    }
}
