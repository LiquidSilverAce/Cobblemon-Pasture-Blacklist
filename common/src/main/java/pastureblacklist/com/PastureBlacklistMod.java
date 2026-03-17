package pastureblacklist.com;

import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.platform.Platform;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pastureblacklist.com.config.BlacklistConfig;

import java.util.Optional;

/**
 * Common entry point for the Cobblemon Pasture Blacklist mod.
 *
 * <p>This mod prevents blacklisted Pokémon from being placed into a Pasture Block.
 * The blacklist is configured in {@code config/cobblemon_pasture_blacklist.json} and
 * supports both explicit species IDs (e.g. {@code "cobblemon:zacian"}) and label-based
 * rules (e.g. {@code "legendary"}, {@code "mythical"}).
 *
 * <p>The check is enforced server-side via a Mixin on
 * {@code PokemonPastureBlockEntity.tether()}, so it works with a vanilla client.
 *
 * <p>NOTE: If Cobblemon changes the name of {@code PokemonPastureBlockEntity} or the
 * {@code tether} method in a future version, the mixin in
 * {@code pastureblacklist.com.mixin.PokemonPastureBlockEntityMixin} will need to be updated.
 */
public final class PastureBlacklistMod {
    public static final String MOD_ID = "cobblemon_pasture_blacklist";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static BlacklistConfig config;

    public static void init() {
        config = BlacklistConfig.load(Platform.getConfigFolder());
        // Clear any stale pasture registrations from a previous server session so that the
        // first chunk loads of the new session populate SpeciesCountTracker cleanly.
        LifecycleEvent.SERVER_STARTING.register(server -> SpeciesCountTracker.reset());
        LOGGER.info("[PastureBlacklist] Mod initialized.");
    }

    /**
     * Returns {@code true} if the given Pokémon is blacklisted from entering a Pasture Block.
     *
     * <p>Checks both the explicit species ID list and the label list from the config.
     *
     * <p>NOTE: If Cobblemon changes how {@code Pokemon.getSpecies()} or
     * {@code Species.getResourceIdentifier()} / {@code Species.getLabels()} work in a future
     * version, this method may need to be updated.
     *
     * @param pokemon the Pokémon to check
     * @return {@code true} if the Pokémon should be blocked from the pasture
     */
    public static boolean isBlacklisted(Pokemon pokemon) {
        if (config == null) {
            LOGGER.warn("[PastureBlacklist] isBlacklisted() called before config was loaded; " +
                    "blocking nothing. Check that init() ran successfully.");
            return false;
        }

        String speciesId = pokemon.getSpecies().getResourceIdentifier().toString();
        if (config.isSpeciesBlacklisted(speciesId)) {
            return true;
        }

        return config.isLabelBlacklisted(pokemon.getSpecies().getLabels());
    }

    /**
     * Returns the message sent to the player when a placement is blocked.
     * The message is loaded from config and defaults to "That Pokémon cannot be pastured."
     */
    public static String getBlockedMessage() {
        return config != null ? config.getBlockedMessage() : "That Pokémon cannot be pastured.";
    }

    /**
     * Returns {@code true} if the given player has already reached the per-player species cap
     * for the given Pokémon's species across all currently loaded pastures.
     *
     * <p>The cap is read from the {@code speciesCaps} section of the config. If no cap is
     * configured for the species, this method always returns {@code false}.
     *
     * @param player  the player attempting to tether the Pokémon
     * @param pokemon the Pokémon being tethered
     * @return {@code true} if the placement should be denied due to the species cap
     */
    public static boolean isCapExceeded(ServerPlayer player, Pokemon pokemon) {
        if (config == null) {
            LOGGER.warn("[PastureBlacklist] isCapExceeded() called before config was loaded; " +
                    "skipping cap check.");
            return false;
        }
        String speciesId = pokemon.getSpecies().getResourceIdentifier().toString().toLowerCase();
        Optional<Integer> cap = config.getSpeciesCap(speciesId);
        if (!cap.isPresent()) {
            return false;
        }
        int currentCount = SpeciesCountTracker.countForPlayer(player.getUUID(), speciesId);
        return currentCount >= cap.get();
    }

    /**
     * Returns the message sent to the player when a species-cap placement is denied.
     */
    public static String getCapExceededMessage() {
        return config != null ? config.getCapExceededMessage()
                : "You already have the maximum allowed of that Pokémon in pastures.";
    }
}
