package pastureblacklist.com.fabric;

import net.fabricmc.api.ModInitializer;
import pastureblacklist.com.PastureBlacklistMod;

public final class ExampleModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PastureBlacklistMod.init();
    }
}
