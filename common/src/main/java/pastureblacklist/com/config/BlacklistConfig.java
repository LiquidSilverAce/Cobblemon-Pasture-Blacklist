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
 *   <li>{@code speciesCaps} – a JSON object mapping species IDs to maximum counts per player,
 *       e.g. {@code "charizard": 2}.  Both bare names ({@code "charizard"}) and full resource
 *       IDs ({@code "cobblemon:charizard"}) are accepted and matched case-insensitively.</li>
 *   <li>{@code capExceededMessage} – message shown when a species-cap placement is denied.</li>
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

    /** Maps a normalized (lower-cased) species key to its per-player cap. */
    private final Map<String, Integer> speciesCaps = new HashMap<>();
    private String capExceededMessage =
            "You already have the maximum allowed of that Pokémon in pastures.";

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
            // Populate all defaults in memory so the mod works correctly on the very first launch,
            // before the server is restarted and the written file is read back.
            config.blacklistedSpecies.add("thievul");
            config.blacklistedSpecies.add("gholdengo");
            config.blacklistedSpecies.add("cetoddle");
            config.blacklistedSpecies.add("wailmer");
            config.blacklistedLabels.add("legendary");
            config.blacklistedLabels.add("mythical");
            config.speciesCaps.put("gimmighoul", 30);
            config.speciesCaps.put("hydrapple", 30);
            config.speciesCaps.put("sableye", 30);
            config.speciesCaps.put("cetitan", 15);
            config.speciesCaps.put("wailord", 15);
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
                if (json != null && json.has("speciesCaps")) {
                    JsonObject capsObj = json.getAsJsonObject("speciesCaps");
                    for (Map.Entry<String, JsonElement> entry : capsObj.entrySet()) {
                        config.speciesCaps.put(
                                entry.getKey().toLowerCase(),
                                entry.getValue().getAsInt());
                    }
                }
                if (json != null && json.has("capExceededMessage")) {
                    config.capExceededMessage = json.get("capExceededMessage").getAsString();
                }

                LOGGER.info("[PastureBlacklist] Loaded config from {}: {} species, {} labels blacklisted, {} species caps",
                        configFile,
                        config.blacklistedSpecies.size(),
                        config.blacklistedLabels.size(),
                        config.speciesCaps.size());
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
            Path parent = configFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            JsonObject json = new JsonObject();

            JsonArray speciesArray = new JsonArray();
            speciesArray.add("thievul");
            speciesArray.add("gholdengo");
            speciesArray.add("cetoddle");
            speciesArray.add("wailmer");
            json.add("blacklistedSpecies", speciesArray);

            JsonArray labelsArray = new JsonArray();
            labelsArray.add("legendary");
            labelsArray.add("mythical");
            json.add("blacklistedLabels", labelsArray);

            json.addProperty("blockedMessage", blockedMessage);

            JsonObject capsObj = new JsonObject();
            capsObj.addProperty("gimmighoul", 30);
            capsObj.addProperty("hydrapple", 30);
            capsObj.addProperty("sableye", 30);
            capsObj.addProperty("cetitan", 15);
            capsObj.addProperty("wailord", 15);
            json.add("speciesCaps", capsObj);

            json.addProperty("capExceededMessage", capExceededMessage);

            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(configFile), StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
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

    /**
     * Returns the per-player cap for the given species, or {@link Optional#empty()} if no cap
     * is configured for it.
     *
     * <p>Matching is <b>case-insensitive</b>. The {@code normalizedSpeciesId} should be the
     * lower-cased full resource ID returned by Cobblemon (e.g. {@code "cobblemon:charizard"}).
     * Config entries may be provided as either a full ID (e.g. {@code "cobblemon:charizard"})
     * or a bare name (e.g. {@code "charizard"}); both forms match.
     *
     * @param normalizedSpeciesId lower-cased full resource ID of the species to check
     * @return the maximum number of that species allowed per player, or empty if uncapped
     */
    public Optional<Integer> getSpeciesCap(String normalizedSpeciesId) {
        // Try exact full-ID match first.
        if (speciesCaps.containsKey(normalizedSpeciesId)) {
            return Optional.of(speciesCaps.get(normalizedSpeciesId));
        }
        // Fall back to bare-name match (strip the "namespace:" prefix).
        int colon = normalizedSpeciesId.indexOf(':');
        if (colon >= 0) {
            String bareName = normalizedSpeciesId.substring(colon + 1);
            if (speciesCaps.containsKey(bareName)) {
                return Optional.of(speciesCaps.get(bareName));
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the message sent to the player when a species-cap placement is denied.
     */
    public String getCapExceededMessage() {
        return capExceededMessage;
    }

    public Map<String, Integer> getSpeciesCaps() {
        return Collections.unmodifiableMap(speciesCaps);
    }
}
