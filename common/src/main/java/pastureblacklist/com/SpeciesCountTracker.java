package pastureblacklist.com;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Maintains a registry of all currently-loaded {@link PokemonPastureBlockEntity} instances
 * and counts how many Pokémon of a given species a given player currently has tethered
 * across ALL registered pastures (GLOBAL_PER_SPECIES semantics).
 *
 * <p>Pastures register themselves:
 * <ul>
 *   <li>Via {@link #register} in {@code PokemonPastureBlockEntityMixin.loadAdditional()} when
 *       an existing pasture is loaded from disk (server start / chunk load).</li>
 *   <li>Via {@link #register} in {@code PokemonPastureBlockEntityMixin.tether()} as a
 *       belt-and-suspenders safeguard for newly-placed, previously-empty pastures that have
 *       not yet been serialized.</li>
 * </ul>
 *
 * <p>Pastures deregister themselves via {@link #unregister} in
 * {@code PokemonPastureBlockEntityMixin.onBroken()} when the block is explicitly broken.
 * Pastures in unloaded chunks are intentionally <em>kept</em> in the registry: their
 * tethering data persists in Cobblemon's server-side PC storage and can still be read by
 * {@link PokemonPastureBlockEntity.Tethering#getPokemon()}, so they contribute correctly
 * to the GLOBAL_PER_SPECIES count without requiring chunk-load awareness.
 *
 * <p>On each server start, {@link #reset()} clears any stale references from a previous
 * session so that subsequent {@code loadAdditional} calls repopulate the registry freshly.
 *
 * <p>All calls happen on the server thread, so no synchronization is required.
 *
 * <p>NOTE: {@code setRemoved()} on {@code BlockEntity} is intentionally <b>not</b> used as
 * the deregistration hook because {@code PokemonPastureBlockEntity} does not override it;
 * targeting an inherited, non-overridden method via Mixin with {@code defaultRequire = 1}
 * would crash the game on load.
 */
public final class SpeciesCountTracker {

    /**
     * All currently-loaded pasture block entities.
     *
     * <p>Uses an {@link IdentityHashMap} as the backing map so membership is determined by
     * reference equality, which is correct for block-entity instances and avoids relying on
     * any {@code equals}/{@code hashCode} implementation they may have.
     */
    private static final Set<PokemonPastureBlockEntity> activePastures =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private SpeciesCountTracker() {}

    /**
     * Registers a pasture block entity. Safe to call multiple times for the same instance
     * (subsequent calls are no-ops).
     *
     * @param pasture the pasture to register
     */
    public static void register(PokemonPastureBlockEntity pasture) {
        activePastures.add(pasture);
    }

    /**
     * Unregisters a pasture block entity when its block is broken.
     *
     * @param pasture the pasture to unregister
     */
    public static void unregister(PokemonPastureBlockEntity pasture) {
        activePastures.remove(pasture);
    }

    /**
     * Counts the total number of Pokémon the given player currently has tethered across
     * all pastures, regardless of species or chunk-load state.
     *
     * <p>Uses Cobblemon's own PC storage rather than the {@link #activePastures} registry so
     * that pokemon in pastures whose chunks have not been loaded since the last server start
     * are still counted correctly.  A pokemon is considered "tethered" when its
     * {@code tetheringId} field is non-null, which Cobblemon sets in
     * {@code PokemonPastureBlockEntity.tether()} and clears in {@code releasePokemon()}.
     *
     * @param player the server player to count for
     * @return the total number of Pokémon tethered by that player
     */
    @SuppressWarnings("unchecked")
    public static int countAllForPlayer(ServerPlayer player) {
        int count = 0;
        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
        // PCStore is a Kotlin class whose Iterable<Pokemon> compiles to java.lang.Iterable at
        // the bytecode level, but the Java compiler requires kotlin-stdlib on the classpath to
        // verify the KMappedMarker annotation at compile time.  Casting through Object bypasses
        // that compile-time check without affecting runtime behaviour.
        Iterable<Pokemon> pcIterable = (Iterable<Pokemon>) (Object) pc;
        for (Pokemon pokemon : pcIterable) {
            if (pokemon != null && pokemon.getTetheringId() != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts how many Pokémon of the given species the given player currently has tethered
     * across all loaded pastures.
     *
     * @param playerId              the UUID of the player
     * @param normalizedFullSpeciesId lower-cased full resource ID of the species to count,
     *                              e.g. {@code "cobblemon:charizard"}
     * @return the total number of that species currently tethered by the player
     */
    public static int countForPlayer(UUID playerId, String normalizedFullSpeciesId) {
        int count = 0;
        // Snapshot the set to avoid ConcurrentModificationException if a pasture
        // is loaded or unloaded mid-iteration (shouldn't happen on a single server thread,
        // but defensive copy is cheap).
        for (PokemonPastureBlockEntity pasture : new ArrayList<>(activePastures)) {
            for (PokemonPastureBlockEntity.Tethering tethering : pasture.getTetheredPokemon()) {
                if (!tethering.getPlayerId().equals(playerId)) {
                    continue;
                }
                Pokemon pokemon = tethering.getPokemon();
                if (pokemon == null) {
                    continue;
                }
                String sid = pokemon.getSpecies().getResourceIdentifier().toString().toLowerCase();
                if (sid.equals(normalizedFullSpeciesId)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Clears the registry. Should be called at the start of each server session (via
     * {@code LifecycleEvent.SERVER_STARTING}) so that stale references from a previous
     * session do not carry over in environments where the JVM is reused between runs
     * (e.g. integrated single-player world).
     */
    public static void reset() {
        activePastures.clear();
    }
}
