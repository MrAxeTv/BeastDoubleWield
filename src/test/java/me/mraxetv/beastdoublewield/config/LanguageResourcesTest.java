package me.mraxetv.beastdoublewield.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LanguageResourcesTest {
    private static final Pattern TOKENS = Pattern.compile("%[A-Za-z]+%|\\{[A-Za-z]+\\}|&[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMANDS = Pattern.compile("/bdw [a-z]+(?: \\[on\\|off\\])?");

    @Test void catalogContainsAllFiftyDistinctRequestedLocales() {
        assertEquals(50, LanguageCatalog.codes().size());
        assertEquals(50, new HashSet<>(LanguageCatalog.codes()).size());
    }

    @TestFactory Stream<DynamicTest> everyTranslationPreservesItsMessageContract() throws Exception {
        YamlConfiguration english = read("en");
        assertEquals(20, english.getKeys(false).size());
        return LanguageCatalog.codes().stream().map(code -> DynamicTest.dynamicTest(code, () -> {
            YamlConfiguration translated = read(code);
            assertEquals(english.getKeys(false), translated.getKeys(false), "Message keys: " + code);
            assertEquals(english.getString("Prefix"), translated.getString("Prefix"), "Plugin branding: " + code);
            for (String key : english.getKeys(false)) {
                assertTrue(translated.isString(key), code + ": " + key + " must be text");
                String text = translated.getString(key);
                assertFalse(text.trim().isEmpty(), code + ": " + key + " is empty");
                assertFalse(text.contains("\uFFFD"), code + ": " + key + " has damaged Unicode");
                assertEquals(matches(TOKENS, english.getString(key)), matches(TOKENS, text), code + ": " + key + " placeholders/colors");
                assertEquals(matches(COMMANDS, english.getString(key)), matches(COMMANDS, text), code + ": " + key + " commands");
            }
        }));
    }

    private static YamlConfiguration read(String code) throws Exception {
        InputStream input = LanguageResourcesTest.class.getResourceAsStream("/" + LanguageCatalog.resource(code));
        assertNotNull(input, "Missing locale " + code);
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(reader);
            return configuration;
        }
    }

    private static Map<String, Integer> matches(Pattern pattern, String value) {
        Map<String, Integer> result = new TreeMap<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) result.merge(matcher.group(), 1, Integer::sum);
        return result;
    }
}
