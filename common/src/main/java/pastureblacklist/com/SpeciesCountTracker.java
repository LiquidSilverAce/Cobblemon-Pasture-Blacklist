package pastureblacklist.com;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.*;

/**
 * Maintains a registry of all currently-loaded {@link PokemonPastureBlockEntity} instances
 * and counts how many Pokémon of a given species a given player currently has tethered
 * across ALL loaded pastures (GLOBAL_PER_SPECIES semantics).
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
 * {@code PokemonPastureBlockEntityMixin.setRemoved()} when the chunk is unloaded or the
 * block is destroyed.
 *
 * <p>All calls happen on the server thread, so no synchronization is required.
 *
 * <p>NOTE: Only Pokémon in <em>currently loaded</em> pastures are counted. Pokémon in
 * unloaded chunks are not visible to this tracker. This is an accepted limitation.
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
     * Unregisters a pasture block entity when it is removed from the world or its chunk is
     * unloaded.
     *
     * @param pasture the pasture to unregister
     */
    public static void unregister(PokemonPastureBlockEntity pasture) {
        activePastures.remove(pasture);
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
