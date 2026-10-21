package com.juanp.custommobs.skill;

import com.juanp.custommobs.CustomMobsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puente entre el disparo y el impacto de un proyectil.
 *
 * <p>Al salir el proyectil se guarda que jugador lo lanzo y que skills de impacto lleva.
 * Al impactar se recupera y se castean con el jugador como lanzador y el punto de impacto
 * como origen. El registro se borra al primer impacto: un multishot de ballesta no
 * dispara la skill tres veces.
 *
 * <p>Un proyectil que nunca impacta (despawn, chunk descargado, jugador que se va) se
 * limpia por tiempo, para que el mapa no crezca sin techo.
 */
public final class ProjectileService {

    /** Un proyectil vive como mucho esto; despues se descarta. */
    private static final long LIFETIME_MILLIS = 30_000L;

    /** Ventana para agrupar las flechas de un mismo disparo (multishot). */
    private static final long SHOT_WINDOW_MILLIS = 250L;

    private record Pending(UUID shooterId, List<SkillSpec> skills, String shotKey,
                           long expiresAt) {
    }

    private final CustomMobsPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, Long> spent = new ConcurrentHashMap<>();

    public ProjectileService(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Arma un proyectil con las skills de impacto del objeto que lo lanzo. */
    public void arm(Projectile projectile, Player shooter, String itemName, List<SkillSpec> skills) {
        if (projectile == null || shooter == null || skills == null || skills.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        String shotKey = shooter.getUniqueId() + ":" + itemName + ":"
                + (now / SHOT_WINDOW_MILLIS);
        if (projectile instanceof org.bukkit.entity.AbstractArrow arrow) {
            // Una flecha custom no se recoge: se destruye al impactar.
            arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        }
        this.pending.put(projectile.getUniqueId(), new Pending(shooter.getUniqueId(),
                List.copyOf(skills), shotKey, now + LIFETIME_MILLIS));
    }

    /**
     * Resuelve el impacto.
     *
     * @return {@code true} si el proyectil llevaba skills y se ejecutaron
     */
    public boolean hit(Projectile projectile, Location where, LivingEntity hit) {
        Pending shot = this.pending.remove(projectile.getUniqueId());
        if (shot == null) {
            return false;
        }
        if (projectile instanceof org.bukkit.entity.AbstractArrow arrow) {
            // Queda como una flecha de infinidad: no se recoge y el juego la borra sola.
            arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        }
        long now = System.currentTimeMillis();
        if (shot.expiresAt() < now) {
            return false;
        }
        Long until = this.spent.get(shot.shotKey());
        if (until != null && until > now) {
            return false;
        }
        this.spent.put(shot.shotKey(), now + 1_000L);
        Player shooter = this.plugin.getServer().getPlayer(shot.shooterId());
        if (shooter == null || !shooter.isOnline()) {
            return false;
        }
        SkillService skills = this.plugin.skillService();
        if (skills == null) {
            return false;
        }
        Location origin = where != null ? where : projectile.getLocation();
        for (SkillSpec skill : shot.skills()) {
            skills.castImpact(shooter, skill, origin, hit);
        }
        return true;
    }

    /** Limpia proyectiles que nunca impactaron y marcas de disparo viejas. */
    public void tick() {
        long now = System.currentTimeMillis();
        this.pending.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        this.spent.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
