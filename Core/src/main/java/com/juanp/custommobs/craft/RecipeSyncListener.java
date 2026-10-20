package com.juanp.custommobs.craft;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Al entrar, manda al cliente las recetas del plugin (incluidas las de pociones). */
public final class RecipeSyncListener implements Listener {

    private final CraftService craft;

    public RecipeSyncListener(CraftService craft) {
        this.craft = craft;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.craft.syncRecipes(event.getPlayer());
    }
}
