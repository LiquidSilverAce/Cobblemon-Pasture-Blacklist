package pastureblacklist.com.config;

import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Loads and holds the pasture blacklist configuration from
 * {@code config/cobblemon_pasture_blacklist.json}.
 *
 * <p>Config fields:
 * <ul>
 *   <li>{@code blacklistedSpecies} – explicit species resource IDs,
 *       e.g. {@code "cobblemon:zacian"}</li>
 *   <li>{@code blacklistedLabels} – species label strings,
 *       e.g. {@code "legendary"}, {@code "mythical"}, {@code "ultra_beast"}, {@code "paradox"}.
 *       Label constants are defined in {@code CobblemonPokemonLabels}.</li>
 * </ul>
 *
 * <p>NOTE: If Cobblemon changes its species ID format or label naming convention in a
 * future version, the config file values may need to be updated accordingly.
 */
public final class BlacklistConfig {

    private static final Logger LOGGER = LogManager.getLogger("cobblemon_pasture_blacklist");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "cobblemon_pasture_blacklist.json";

    private final Set<String> blacklistedSpecies = new HashSet<>();
    private final Set<String> blacklistedLabels = new HashSet<>();
    private String blockedMessage = "That Pokémon cannot be pastured.";

    private BlacklistConfig() {}

    /**
     * Loads the config from {@code configDir/cobblemon_pasture_blacklist.json}.
     * If the file does not exist a default config (legendary + mythical blacklisted) is written.
     *
     * @param configDir the Minecraft config directory (e.g. {@code .minecraft/config})
     * @return a loaded {@link BlacklistConfig}
     */
    public static BlacklistConfig load(Path configDir) {
        BlacklistConfig config = new BlacklistConfig();
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configFile)) {
            config.blacklistedLabels.add("legendary");
            config.blacklistedLabels.add("mythical");
            config.saveDefault(configFile);
            LOGGER.info("[PastureBlacklist] Created default config at {}", configFile);
        } else {
            try (Reader reader = new InputStreamReader(
                    Files.newInputStream(configFile), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                if (json != null && json.has("blacklistedSpecies")) {
                    for (JsonElement element : json.getAsJsonArray("blacklistedSpecies")) {
                        config.blacklistedSpecies.add(element.getAsString().toLowerCase());
                    }
                }
                if (json != null && json.has("blacklistedLabels")) {
                    for (JsonElement element : json.getAsJsonArray("blacklistedLabels")) {
                        config.blacklistedLabels.add(element.getAsString().toLowerCase());
                    }
                }
                if (json != null && json.has("blockedMessage")) {
                    config.blockedMessage = json.get("blockedMessage").getAsString();
                }

                LOGGER.info("[PastureBlacklist] Loaded config from {}: {} species, {} labels blacklisted",
                        configFile,
                        config.blacklistedSpecies.size(),
                        config.blacklistedLabels.size());
            } catch (IOException | JsonParseException e) {
                LOGGER.error("[PastureBlacklist] Failed to load config, falling back to defaults", e);
                config.blacklistedLabels.add("legendary");
                config.blacklistedLabels.add("mythical");
            }
        }

        return config;
    }

    private void saveDefault(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());

            JsonObject json = new JsonObject();

            JsonArray speciesArray = new JsonArray();
            // Default: no explicit species IDs (labels cover the common cases).
            json.add("blacklistedSpecies", speciesArray);

            JsonArray labelsArray = new JsonArray();
            for (String label : blacklistedLabels) {
                labelsArray.add(label);
            }
            json.add("blacklistedLabels", labelsArray);

            json.addProperty("blockedMessage", blockedMessage);

            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(configFile), StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[PastureBlacklist] Failed to write default config", e);
        }
    }

    /**
     * Returns {@code true} if the given species resource ID is explicitly blacklisted.
     *
     * <p>Matching is <b>case-insensitive</b>. Config entries may be provided as either a full
     * resource ID (e.g. {@code "cobblemon:charizard"}) or as a bare species name without the
     * namespace (e.g. {@code "Charizard"} or {@code "charizard"}). Both forms will correctly
     * match {@code "cobblemon:charizard"} at runtime.
     *
     * @param speciesId the full resource ID returned by Cobblemon, e.g. {@code "cobblemon:rayquaza"}
     */
    public boolean isSpeciesBlacklisted(String speciesId) {
        String normalized = speciesId.toLowerCase();
        // Match full resource ID (e.g. "cobblemon:charizard").
        if (blacklistedSpecies.contains(normalized)) {
            return true;
        }
        // Also match just the path/name part (e.g. "charizard" from "cobblemon:charizard"),
        // so that users can enter bare species names in the config without the namespace prefix.
        int colon = normalized.indexOf(':');
        return colon >= 0 && blacklistedSpecies.contains(normalized.substring(colon + 1));
    }

    /**
     * Returns {@code true} if any of the species' labels match a blacklisted label.
     *
     * <p>Matching is <b>case-insensitive</b>, so a config entry of {@code "Legendary"} will
     * correctly match the Cobblemon label {@code "legendary"}.
     *
     * @param labels the set of labels on a Pokémon species
     */
    public boolean isLabelBlacklisted(Set<String> labels) {
        for (String label : labels) {
            if (blacklistedLabels.contains(label.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public Set<String> getBlacklistedSpecies() {
        return Collections.unmodifiableSet(blacklistedSpecies);
    }

    public Set<String> getBlacklistedLabels() {
        return Collections.unmodifiableSet(blacklistedLabels);
    }

    public String getBlockedMessage() {
        return blockedMessage;
    }
}
