package com.ctt.healthdisplay.client;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Keywords and their popups, loaded from assets/ctt-health-display/lore/entries/<language>.json. */
public final class LoreRegistry {
    public record Entry(String word, List<String> lines) { }

    public record Match(int start, int end, String word) { }

    private static final String NAMESPACE = "ctt-health-display";
    private static final String DIRECTORY = "lore/entries";
    private static final Logger LOGGER = LoggerFactory.getLogger(LoreRegistry.class);
    private record Index(Map<String, Entry> entries, List<String> words) { }
    private static volatile Index index = new Index(Map.of(), List.of());
    private static Map<String, Map<String, String>> byLanguage = Map.of();
    private static String loadedLang;
    private static volatile long revision;

    private LoreRegistry() { }

    public static void registerReloadListener() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "lore_entries"),
                (ResourceManagerReloadListener) LoreRegistry::loadResources);
    }

    private static void loadResources(ResourceManager resources) {
        Map<String, Map<String, String>> languages = new TreeMap<>();
        resources.listResourceStacks(DIRECTORY, LoreRegistry::isEntryFile).forEach((id, stack) -> {
            String path = id.getPath();
            String language = path.substring(DIRECTORY.length() + 1, path.length() - 5);
            Map<String, String> terms = languages.computeIfAbsent(language, ignored -> new LinkedHashMap<>());
            // Read bottom to top: a higher-priority pack overrides only the terms it defines.
            for (Resource resource : stack) readEntries(terms, id, resource);
        });
        byLanguage = languages;
        rebuild(currentLanguage());
    }

    private static boolean isEntryFile(Identifier id) {
        if (!NAMESPACE.equals(id.getNamespace())) return false;
        String path = id.getPath();
        return path.startsWith(DIRECTORY + "/") && path.endsWith(".json")
                && path.length() > DIRECTORY.length() + 6
                && path.indexOf('/', DIRECTORY.length() + 1) == -1;
    }

    private static void readEntries(Map<String, String> terms, Identifier id, Resource resource) {
        try (var reader = resource.openAsReader()) {
            var parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                LOGGER.warn("Expected a keyword object in {} from {}", id, resource.sourcePackId());
                return;
            }
            parsed.getAsJsonObject().entrySet().forEach(entry -> {
                var value = entry.getValue();
                if (!entry.getKey().isBlank() && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    terms.put(entry.getKey(), value.getAsString());
                }
            });
        } catch (IOException | com.google.gson.JsonParseException e) {
            LOGGER.warn("Could not read keyword file {} from {}", id, resource.sourcePackId(), e);
        }
    }

    private static String currentLanguage() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.options != null ? client.options.languageCode : "en_us";
    }

    public static long revision() { return revision; }

    public static void tickReload() {
        String language = currentLanguage();
        if (!language.equals(loadedLang)) rebuild(language);
    }

    private static void rebuild(String language) {
        Map<String, String> terms = new LinkedHashMap<>();
        byLanguage.forEach((code, words) -> {
            if (!code.equals(language) && !code.equals("en_us")) terms.putAll(words);
        });
        terms.putAll(byLanguage.getOrDefault("en_us", Map.of()));
        terms.putAll(byLanguage.getOrDefault(language, Map.of()));
        Map<String, Entry> entries = new LinkedHashMap<>();
        terms.forEach((word, text) -> entries.put(word, new Entry(word, List.of(text.split("\n", -1)))));
        List<String> words = new ArrayList<>(entries.keySet());
        words.sort((a, b) -> Integer.compare(b.length(), a.length()));
        index = new Index(Map.copyOf(entries), List.copyOf(words));
        revision++;
        loadedLang = language;
    }

    public static Entry get(String word) {
        return index.entries().get(word);
    }

    public static boolean containsAny(String text) {
        for (String word : index.words()) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    /** Left-to-right, preferring the longest match at each position. */
    public static List<Match> findMatches(String text) {
        List<Match> matches = new ArrayList<>();
        List<String> words = index.words();
        if (words.isEmpty()) return matches;
        int i = 0;
        outer:
        while (i < text.length()) {
            for (String word : words) {
                if (text.startsWith(word, i)) {
                    matches.add(new Match(i, i + word.length(), word));
                    i += word.length();
                    continue outer;
                }
            }
            i++;
        }
        return matches;
    }
}
