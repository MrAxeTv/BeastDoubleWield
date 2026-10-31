package me.mraxetv.beastdoublewield.config;

import me.mraxetv.beastlib.lib.boostedyaml.YamlDocument;
import me.mraxetv.beastlib.lib.boostedyaml.block.Block;
import me.mraxetv.beastlib.lib.boostedyaml.block.implementation.Section;
import me.mraxetv.beastlib.lib.boostedyaml.settings.loader.LoaderSettings;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Stages owned YAML files before defaults or disk writes can mask old values. */
public final class ConfigurationMigration {
    private static final LoaderSettings LOADER = LoaderSettings.builder()
            .setAutoUpdate(false).setAllowDuplicateKeys(false).build();

    private ConfigurationMigration() { }

    public static Prepared prepare(File directory, Function<String, InputStream> resources)
            throws IOException, InvalidConfigurationException {
        FileChange config = prepareFile(directory.toPath(), "config.yml", resources, true, null);
        YamlConfiguration settings = new YamlConfiguration();
        settings.loadFromString(new String(config.replacement, StandardCharsets.UTF_8));
        List<FileChange> files = new ArrayList<>();
        files.add(config);
        // English.yml stays untouched as the owner's original. Include its snapshot
        // in commit validation because it can supply values to the new en.yml.
        Path oldEnglishPath = directory.toPath().resolve("Lang/English.yml");
        byte[] oldEnglish = Files.exists(oldEnglishPath) ? Files.readAllBytes(oldEnglishPath) : null;
        files.add(new FileChange(oldEnglishPath, oldEnglish, oldEnglish, null));
        String selected = settings.getString("Language");
        YamlDocument language = null;
        for (String code : LanguageCatalog.codes()) {
            FileChange translation = prepareFile(directory.toPath(), LanguageCatalog.resource(code), resources,
                    false, LanguageCatalog.DEFAULT.equals(code) ? oldEnglish : null);
            files.add(translation);
            if (code.equals(selected)) language = translation.document;
        }
        return new Prepared(settings, language, files);
    }

    private static FileChange prepareFile(Path directory, String resource, Function<String, InputStream> resources,
                                          boolean config, byte[] legacyEnglish)
            throws IOException, InvalidConfigurationException {
        Path path = directory.resolve(resource);
        byte[] original = Files.exists(path) ? Files.readAllBytes(path) : null;
        byte[] defaults;
        try (InputStream input = resources.apply(resource)) {
            if (input == null) throw new IOException("Missing bundled resource: " + resource);
            defaults = readBytes(input);
        }
        YamlDocument template = parse(defaults, resource + " defaults");
        YamlDocument document = parse(original == null ? (legacyEnglish == null ? defaults : legacyEnglish) : original,
                original == null && legacyEnglish != null ? "Lang/English.yml" : resource);
        // Normalize only keys owned by the bundled schema, before defaults can
        // conceal an explicit false, empty list or custom message in an old file.
        boolean changed = normalize(document, template, "", config);
        if (config) changed |= migrateOffhandArm(document);
        if (legacyEnglish != null && original != null) {
            YamlDocument legacy = parse(legacyEnglish, "Lang/English.yml");
            normalize(legacy, template, "", false);
            changed |= mergeExplicit(document, legacy);
        }
        changed |= mergeMissing(document, template, "");
        if (config) {
            Block<?> configured = document.getBlock("Language");
            String selected = LanguageCatalog.canonical(configured == null ? LanguageCatalog.DEFAULT : configured.getStoredValue());
            if (configured == null || !selected.equals(configured.getStoredValue())) {
                document.set("Language", selected);
                changed = true;
            }
            // These are the user-requested short arm instructions. Other custom
            // comments, including comments on unknown options, stay with their blocks.
            for (String route : Arrays.asList("OffhandArm", "OffhandArm.Enabled")) {
                Block<?> current = document.getBlock(route), bundled = template.getBlock(route);
                if (current != null && bundled != null && !java.util.Objects.equals(current.getComments(), bundled.getComments())) {
                    current.setComments(bundled.getComments());
                    changed = true;
                }
            }
        } else {
            for (Object rawKey : template.getKeys()) {
                String key = String.valueOf(rawKey);
                Block<?> message = document.getBlock(key);
                if (message == null || !(message.getStoredValue() instanceof String))
                    throw new IllegalArgumentException(resource + ": " + key + " must be a string");
            }
        }
        byte[] replacement = original != null && !changed ? original
                : document.dump().getBytes(StandardCharsets.UTF_8);
        return new FileChange(path, original, replacement, document);
    }

    private static boolean migrateOffhandArm(YamlDocument document) throws IOException, InvalidConfigurationException {
        if (document.getBlock("Placeholder") == null && document.getBlock("placeholder") == null) return false;
        // This historical schema deliberately has no defaults attached: a missing
        // old flag means false, even if a later version changes the arm default.
        YamlDocument legacySchema = parse(("Placeholder:\n  Enabled: false\n  FirstPersonArm: false\n")
                .getBytes(StandardCharsets.UTF_8), "legacy arm schema");
        boolean changed = normalize(document, legacySchema, "", true);
        Block<?> block = document.getBlock("Placeholder");
        if (block == null) return changed;
        Section legacy = (Section) block; // normalize rejects non-section values.
        boolean oldEnabled = legacyArmFlag(legacy, "Enabled");
        boolean oldFirstPersonArm = legacyArmFlag(legacy, "FirstPersonArm");
        if (document.getBlock("OffhandArm.Enabled") == null)
            document.set("OffhandArm.Enabled", oldEnabled && oldFirstPersonArm);
        legacy.remove("Enabled");
        legacy.remove("FirstPersonArm");
        // Preserve owner additions, while explicit options in the new section win.
        mergeExplicit((Section) document.getBlock("OffhandArm"), legacy);
        document.remove("Placeholder");
        return true;
    }

    private static boolean legacyArmFlag(Section section, String key) {
        Block<?> value = section.getBlock(key);
        if (value == null) return false;
        if (!(value.getStoredValue() instanceof Boolean))
            throw new IllegalArgumentException("Placeholder." + key + " must be true or false");
        return (Boolean) value.getStoredValue();
    }

    private static boolean normalize(Section current, Section schema, String prefix, boolean kebabAliases) {
        boolean changed = false;
        for (Object rawKey : new ArrayList<>(schema.getKeys())) {
            String key = String.valueOf(rawKey);
            Block<?> bundled = schema.getBlock(key);
            List<String> variants = new ArrayList<>();
            variants.add(key);
            String camel = Character.toLowerCase(key.charAt(0)) + key.substring(1);
            if (!camel.equals(key)) variants.add(camel);
            String kebab = camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT);
            if (kebabAliases && !variants.contains(kebab)) variants.add(kebab);
            for (String variant : variants) {
                Block<?> candidate = current.getBlock(variant);
                if (candidate == null) continue;
                Block<?> canonical = current.getBlock(key);
                if (bundled instanceof Section) {
                    if (candidate instanceof Section) {
                        // Normalize each candidate before merging partial sections.
                        // This keeps values and unknown owner options from either form.
                        changed |= normalize((Section) candidate, (Section) bundled, prefix + key + ".", kebabAliases);
                    } else if (canonical == null || variant.equals(key)) {
                        throw new IllegalArgumentException(prefix + key + " must be a YAML section");
                    }
                }
                if (variant.equals(key)) continue;
                if (canonical == null) current.move(variant, key);
                else {
                    if (canonical instanceof Section && candidate instanceof Section)
                        mergeExplicit((Section) canonical, (Section) candidate);
                    current.remove(variant);
                }
                changed = true;
            }
        }
        return changed;
    }

    /** Merge only explicit entries; the earlier spelling wins collisions. */
    private static boolean mergeExplicit(Section preferred, Section other) {
        boolean changed = false;
        for (Object rawKey : new ArrayList<>(other.getKeys())) {
            String key = String.valueOf(rawKey);
            Block<?> existing = preferred.getBlock(key), additional = other.getBlock(key);
            if (existing == null) { preferred.set(key, additional); changed = true; }
            else if (existing instanceof Section && additional instanceof Section)
                changed |= mergeExplicit((Section) existing, (Section) additional);
        }
        return changed;
    }

    private static boolean mergeMissing(Section current, Section defaults, String prefix) {
        boolean changed = false;
        for (Object rawKey : new ArrayList<>(defaults.getKeys())) {
            String key = String.valueOf(rawKey);
            Block<?> existing = current.getBlock(key), bundled = defaults.getBlock(key);
            if (existing == null) {
                current.set(key, bundled);
                changed = true;
            } else if (existing instanceof Section && bundled instanceof Section) {
                changed |= mergeMissing((Section) existing, (Section) bundled, prefix + key + ".");
            } else if (bundled instanceof Section) {
                throw new IllegalArgumentException(prefix + key + " must be a YAML section");
            }
        }
        return changed;
    }

    private static YamlDocument parse(byte[] bytes, String label) throws IOException, InvalidConfigurationException {
        try {
            // Keep Bukkit's checked failure, but name the file among the bundled locales.
            new YamlConfiguration().loadFromString(new String(bytes, StandardCharsets.UTF_8));
            return YamlDocument.create(new ByteArrayInputStream(bytes), LOADER);
        } catch (InvalidConfigurationException | RuntimeException invalid) {
            throw new InvalidConfigurationException("Invalid YAML in " + label, invalid);
        }
    }

    private static byte[] readBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    public static final class Prepared {
        private final YamlConfiguration settings;
        private final YamlDocument language;
        private final List<FileChange> files;
        private Prepared(YamlConfiguration settings, YamlDocument language, List<FileChange> files) {
            this.settings = settings; this.language = language; this.files = files;
        }
        public YamlConfiguration settings() { return settings; }
        public YamlDocument language() { return language; }

        /** Call only after validation. Closing without accept restores all original files. */
        public Commit commit() throws IOException {
            Commit commit = new Commit();
            try {
                for (FileChange file : files) file.verifyOriginal();
                for (FileChange file : files) if (file.changed()) {
                    Files.createDirectories(file.path.getParent());
                    file.backup();
                    file.verifyOriginal();
                    replace(file.path, file.replacement);
                    commit.written.add(file);
                }
                return commit;
            } catch (IOException failure) {
                try { commit.close(); } catch (IOException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    public static final class Commit implements AutoCloseable {
        private final List<FileChange> written = new ArrayList<>();
        private boolean accepted;
        public void accept() { accepted = true; }
        @Override public void close() throws IOException {
            if (accepted) return;
            IOException failure = null;
            for (int i = written.size() - 1; i >= 0; i--) {
                FileChange file = written.get(i);
                try {
                    if (!Files.exists(file.path) || !Arrays.equals(Files.readAllBytes(file.path), file.replacement))
                        throw new IOException("File changed during reload; refusing to overwrite it: " + file.path);
                    if (file.original == null) Files.delete(file.path);
                    else {
                        replace(file.path, file.original);
                        Files.setLastModifiedTime(file.path, file.originalModified);
                    }
                } catch (IOException rollback) {
                    if (failure == null) failure = rollback; else failure.addSuppressed(rollback);
                }
            }
            written.clear();
            if (failure != null) throw failure;
        }
    }

    private static final class FileChange {
        final Path path;
        final byte[] original, replacement;
        final YamlDocument document;
        final FileTime originalModified;
        FileChange(Path path, byte[] original, byte[] replacement, YamlDocument document) throws IOException {
            this.path = path; this.original = original; this.replacement = replacement; this.document = document;
            this.originalModified = original == null ? null : Files.getLastModifiedTime(path);
        }
        boolean changed() { return !Arrays.equals(original, replacement); }
        void verifyOriginal() throws IOException {
            byte[] current = Files.exists(path) ? Files.readAllBytes(path) : null;
            if (!Arrays.equals(original, current)) throw new IOException("File changed while preparing reload: " + path);
        }
        void backup() throws IOException {
            if (original == null) return;
            for (int index = 0; ; index++) {
                String suffix = ".pre-PascalCase" + (index == 0 ? "" : "." + index) + ".bak";
                Path backup = path.resolveSibling(path.getFileName().toString() + suffix);
                if (Files.exists(backup)) {
                    if (Arrays.equals(Files.readAllBytes(backup), original)) return;
                } else {
                    Files.write(backup, original, java.nio.file.StandardOpenOption.CREATE_NEW);
                    return;
                }
            }
        }
    }

    private static void replace(Path path, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString() + ".", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
