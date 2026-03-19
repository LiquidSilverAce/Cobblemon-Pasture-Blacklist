package pastureblacklist.com.mixin;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pastureblacklist.com.PastureBlacklistMod;
import pastureblacklist.com.SpeciesCountTracker;

/**
 * Injects blacklist and species-cap checks into {@link PokemonPastureBlockEntity#tether}
 * before a Pokémon is spawned into the world and linked to the pasture.
 *
 * <p>If the Pokémon is blacklisted:
 * <ol>
 *   <li>The player receives a chat message: "That Pokémon cannot be pastured."</li>
 *   <li>The tether operation is cancelled (returns {@code false}), so the Pokémon remains
 *       in its original PC box/slot.</li>
 * </ol>
 *
 * <p>If the player has already reached the per-player species cap for that Pokémon:
 * <ol>
 *   <li>The player receives the cap-exceeded message.</li>
 *   <li>The tether operation is cancelled in the same way.</li>
 * </ol>
 *
 * <p>This class also maintains the {@link SpeciesCountTracker} registry by:
 * <ul>
 *   <li>Registering the pasture when it loads from disk ({@code loadAdditional}) or when
 *       the first Pokémon is tethered to it ({@code tether} HEAD).</li>
 *   <li>Deregistering the pasture when the block is explicitly <em>broken</em>
 *       ({@code onBroken}).  Chunk-unloaded pastures intentionally remain registered:
 *       their tethering data is still accessible through Cobblemon's server-side PC
 *       storage, so they contribute correctly to the GLOBAL_PER_SPECIES count.</li>
 * </ul>
 *
 * <p>NOTE: If Cobblemon renames {@code PokemonPastureBlockEntity} or changes the
 * {@code tether} method signature in a future version, this mixin target and the
 * method descriptor below must be updated.
 *
 * <p>Target: {@code com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity#tether(
 *   net.minecraft.server.level.ServerPlayer,
 *   com.cobblemon.mod.common.pokemon.Pokemon,
 *   net.minecraft.core.Direction) : boolean}
 */
@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {

    /**
     * Runs at the very start of {@code tether()}.
     *
     * <ol>
     *   <li>Registers this pasture in {@link SpeciesCountTracker} (idempotent).</li>
     *   <li>If the Pokémon is on the blacklist, cancels the tether and notifies the player.</li>
     *   <li>If the player has reached the species cap for this Pokémon, cancels the tether
     *       and notifies the player with a separate cap-exceeded message.</li>
     * </ol>
     */
    @Inject(method = "tether", at = @At("HEAD"), cancellable = true)
    private void pastureBlacklist$onTether(
            ServerPlayer player,
            Pokemon pokemon,
            Direction directionToBehind,
            CallbackInfoReturnable<Boolean> cir) {

        // Ensure this pasture is tracked so that species counts are accurate.
        // This is a safety net for newly-placed pastures that have not yet been
        // serialized (and therefore won't have had loadAdditional called).
        SpeciesCountTracker.register((PokemonPastureBlockEntity) (Object) this);

        if (PastureBlacklistMod.isBlacklisted(pokemon)) {
            // Use a literal string so the message renders correctly even on a vanilla client
            // that does not have this mod installed. The message text is configurable in
            // config/cobblemon_pasture_blacklist.json via the "blockedMessage" field.
            player.sendSystemMessage(
                    Component.literal(PastureBlacklistMod.getBlockedMessage()), true);
            cir.setReturnValue(false);
            return;
        }

        if (PastureBlacklistMod.isCapExceeded(player, pokemon)) {
            player.sendSystemMessage(
                    Component.literal(PastureBlacklistMod.getCapExceededMessage()), true);
            cir.setReturnValue(false);
        }
    }

    /**
     * Registers this pasture in {@link SpeciesCountTracker} after its NBT data has been
     * loaded (i.e. after {@code tetheredPokemon} has been populated). This covers the
     * server-restart / chunk-load case where existing tethered Pokémon need to be visible
     * to the cap-check scan.
     */
    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void pastureBlacklist$onLoadAdditional(
            CompoundTag nbt,
            HolderLookup.Provider registryLookup,
            CallbackInfo ci) {
        SpeciesCountTracker.register((PokemonPastureBlockEntity) (Object) this);
    }

    /**
     * Deregisters this pasture from {@link SpeciesCountTracker} when the pasture block is
     * broken by a player or the world. Cobblemon's own {@code onBroken()} implementation
     * has already released all tethered Pokémon by the time this injection runs, but we
     * still remove the instance from the registry so it is not scanned in future cap checks.
     *
     * <p>We do <em>not</em> hook {@code setRemoved()} for this purpose: that method lives on
     * the {@code BlockEntity} base class and is <b>not overridden</b> in
     * {@code PokemonPastureBlockEntity}, so Mixin would fail to find it as a target with
     * {@code defaultRequire = 1}, crashing the game on load.
     */
    @Inject(method = "onBroken", at = @At("HEAD"))
    private void pastureBlacklist$onBroken(CallbackInfo ci) {
        SpeciesCountTracker.unregister((PokemonPastureBlockEntity) (Object) this);
    }
}
