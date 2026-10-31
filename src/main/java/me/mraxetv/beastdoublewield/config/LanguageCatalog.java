package me.mraxetv.beastdoublewield.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The plugin's bundled locales; configuration values never become arbitrary paths. */
public final class LanguageCatalog {
    public static final String DEFAULT = "en";
    private static final List<String> CODES = Collections.unmodifiableList(Arrays.asList(
            "ar", "bg", "bs", "ca", "cs", "da", "de", "el", "en", "en-GB", "en-US", "es", "et", "eu",
            "fi", "fil-PH", "fr", "ga-IE", "gv-IM", "he", "hi-IN", "hr", "hu", "is-IS", "it", "ja", "ko",
            "lol-US", "lt", "lv-LV", "mn-MN", "nl", "no", "pl", "pt", "pt-BR", "ro", "ru", "si-LK",
            "sk", "sr", "sr-CS", "sv", "th", "tr", "uk", "vi", "zh", "zh-HK", "zh-TW"));

    private LanguageCatalog() { }

    public static List<String> codes() { return CODES; }

    public static String canonical(Object configured) {
        if (!(configured instanceof String)) throw new IllegalArgumentException("Language must be a language code");
        String normalized = ((String) configured).trim().replace('_', '-').toLowerCase(Locale.ROOT);
        if ("english".equals(normalized)) return DEFAULT;
        for (String code : CODES) if (code.toLowerCase(Locale.ROOT).equals(normalized)) return code;
        throw new IllegalArgumentException("Language must be one of the bundled language codes: " + String.join(", ", CODES));
    }

    public static String resource(String code) { return "Lang/" + canonical(code) + ".yml"; }
}
