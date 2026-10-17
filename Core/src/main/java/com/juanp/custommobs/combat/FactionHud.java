package com.juanp.custommobs.combat;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Avisa en la action bar de quien mira a un mob custom si es <b>Enemigo</b>, <b>Aliado</b> o
 * <b>Neutral</b>.
 *
 * <p>El color del brillo por espectador se cayo: metia paquetes de entidad que tiraban a los
 * jugadores con "Error de protocolo de red". La informacion va por <b>texto</b>, un componente
 * por jugador, sin tocar ninguna entidad: es la unica forma de dar algo por espectador sin
 * riesgo.
 */
public final class FactionHud extends BukkitRunnable {

    private static final String ENEMY = "Enemigo";
    private static final String ALLY = "Aliado";
    private static final String NEUTRAL = "Neutral";
    private static final String NONE = "Sin faccion";

    private final MobService service;

    public FactionHud(MobService service) {
        this.service = service;
    }

    @Override
    public void run() {
        double range = Math.max(1.0D, this.service.plugin().getConfig()
                .getDouble("faction-info.range", 8.0D));
        int reach = (int) Math.ceil(range);
        for (Player player : this.service.plugin().getServer().getOnlinePlayers()) {
            if (!this.service.worldEnabled(player.getWorld())) {
                continue;
            }
            Entity aimed = player.getTargetEntity(reach);
            CustomMob mob = aimed == null ? null
                    : this.service.find(aimed.getUniqueId()).orElse(null);
            if (mob == null) {
                continue;
            }
            String relation = this.relation(player, mob);
            String name = ChatColor.stripColor(mob.definition().displayName());
            NamedTextColor color = switch (relation) {
                case ENEMY -> NamedTextColor.RED;
                case ALLY -> NamedTextColor.GREEN;
                case NEUTRAL -> NamedTextColor.GRAY;
                default -> NamedTextColor.WHITE;
            };
            player.sendActionBar(Component.text(name).color(color)
                    .append(Component.text(" · " + relation).color(color)));
        }
    }

    /** La relacion del mob con quien lo mira. */
    private String relation(Player viewer, CustomMob mob) {
        String mine = this.service.factionOf(viewer);
        String theirs = this.service.factionOf(mob);
        if (mine == null || theirs == null) {
            return NONE;
        }
        if (this.service.sameSide(mine, theirs)) {
            return ALLY;
        }
        return this.service.plugin().factions().isEnemy(mine, theirs) ? ENEMY : NEUTRAL;
    }
}
