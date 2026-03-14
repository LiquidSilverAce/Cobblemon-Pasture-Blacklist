package pastureblacklist.com.neoforge;

import net.neoforged.fml.common.Mod;

import pastureblacklist.com.ExampleMod;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModNeoForge {
    public ExampleModNeoForge() {
        // Run our common setup.
        ExampleMod.init();
    }
}
