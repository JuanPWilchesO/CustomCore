package com.juanp.custommobs.skill;

import com.juanp.custommobs.craft.CraftService;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Dispara las skills de interaccion: las que un jugador activa con clic derecho sobre el
 * mob.
 *
 * <p>Si el jugador trae un huevo custom en la mano, el clic es del huevo —recoger o
 * colocar— y aqui no se toca: el huevo tiene su propio listener.
 */
public final class SkillInteractListener implements Listener {

    private final CraftService craft;
    private final MobService service;
    private final SkillService skills;

    public SkillInteractListener(CraftService craft, MobService service, SkillService skills) {
        this.craft = craft;
        this.service = service;
        this.skills = skills;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = event.getRightClicked();
        CustomMob customMob = this.service.find(clicked.getUniqueId()).orElse(null);
        if (customMob == null) {
            this.debug("Clic sobre " + clicked.getType() + ": no es un mob nuestro; se ignora.");
            return;
        }
        // En una montura el clic derecho sirve para montarse: no se dispara dialogo.
        if (customMob.definition().mountable()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (this.craft.definitionOf(item).isPresent()) {
            this.debug("Clic sobre " + customMob.definition().id() + " con un huevo en la mano: ese clic es del huevo.");
            return;
        }
        long interactivas = customMob.definition().skills().stream()
                .filter(skill -> skill.mode() == SkillMode.INTERACT).count();
        this.debug("Clic sobre " + customMob.definition().id() + " de " + player.getName()
                + ": skills de interaccion = " + interactivas + ".");
        this.skills.interact(customMob, player);
    }

    /** Traza de depuracion: solo escribe si 'debug' esta activo en el config. */
    private void debug(String message) {
        if (this.service.config().debug()) {
            this.service.plugin().getLogger().info("[skill] " + message);
        }
    }
}
