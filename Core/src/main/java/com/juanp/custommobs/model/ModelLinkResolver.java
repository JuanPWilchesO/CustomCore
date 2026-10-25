package com.juanp.custommobs.model;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Elige el enlace de modelos segun si BetterModel esta instalado y activo. */
public final class ModelLinkResolver {

    private ModelLinkResolver() {
    }

    public static ModelLink resolve(Plugin plugin, boolean requireHitBox) {
        Plugin betterModel = Bukkit.getPluginManager().getPlugin("BetterModel");
        if (betterModel != null && betterModel.isEnabled()) {
            try {
                return new BetterModelLink(plugin, requireHitBox);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("BetterModel presente pero no se pudo enlazar ("
                        + throwable.getClass().getSimpleName() + "). Se seguira sin modelos.");
            }
        }
        return new NoModelLink();
    }
}
