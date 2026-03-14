package pastureblacklist.com.mixin;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pastureblacklist.com.PastureBlacklistMod;

/**
 * Injects a blacklist check into {@link PokemonPastureBlockEntity#tether} before a Pokémon
 * is spawned into the world and linked to the pasture.
 *
 * <p>If the Pokémon is blacklisted:
 * <ol>
 *   <li>The player receives a chat message: "That Pokémon cannot be pastured."</li>
 *   <li>The tether operation is cancelled (returns {@code false}), so the Pokémon remains
 *       in its original PC box/slot.</li>
 * </ol>
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
     * Runs at the very start of {@code tether()}. If the Pokémon is on the blacklist the
     * method is cancelled and the return value is set to {@code false} (failure).
     */
    @Inject(method = "tether", at = @At("HEAD"), cancellable = true)
    private void pastureBlacklist$onTether(
            ServerPlayer player,
            Pokemon pokemon,
            Direction directionToBehind,
            CallbackInfoReturnable<Boolean> cir) {

        if (PastureBlacklistMod.isBlacklisted(pokemon)) {
            // Use a literal string so the message renders correctly even on a vanilla client
            // that does not have this mod installed. The message text is configurable in
            // config/cobblemon_pasture_blacklist.json via the "blockedMessage" field.
            player.sendSystemMessage(
                    Component.literal(PastureBlacklistMod.getBlockedMessage()), true);
            cir.setReturnValue(false);
        }
    }
}
