package com.juanp.custommobs.skill;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.item.UseSpec;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;

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
 *
 * <p>El tridente es la excepcion al cooldown: se lanza siempre y el cooldown del objeto
 * se comprueba al impactar, para no bloquear su uso vanilla.
 */
public final class ProjectileService {

    /** Un proyectil vive como mucho esto; despues se descarta. */
    private static final long LIFETIME_MILLIS = 30_000L;

    /** Ventana para agrupar las flechas de un mismo disparo (multishot). */
    private static final long SHOT_WINDOW_MILLIS = 250L;

    private record Pending(UUID shooterId, List<SkillSpec> skills, String itemName,
                           String shotKey, long expiresAt, boolean cooldownAtHit) {
    }

    private final CustomMobsPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, Long> spent = new ConcurrentHashMap<>();

    public ProjectileService(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Arma un proyectil con las skills de impacto del objeto que lo lanzo. */
    public void arm(Projectile projectile, Player shooter, String itemName, List<SkillSpec> skills,
                    boolean customAmmo) {
        if (projectile == null || shooter == null || skills == null || skills.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        String shotKey = shooter.getUniqueId() + ":" + itemName + ":"
                + (now / SHOT_WINDOW_MILLIS);
        if (customAmmo && projectile instanceof org.bukkit.entity.AbstractArrow arrow) {
            // Solo las flechas custom quedan como de infinidad: no se recogen.
            arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        }
        this.pending.put(projectile.getUniqueId(), new Pending(shooter.getUniqueId(),
                List.copyOf(skills), itemName, shotKey, now + LIFETIME_MILLIS,
                projectile instanceof Trident));
    }

    /**
     * Resuelve el impacto.
     *
     * @return {@code true} si el proyectil llevaba skills de impacto (aunque el cooldown
     *         del tridente saltara el efecto)
     */
    public boolean hit(Projectile projectile, Location where, LivingEntity hit) {
        Pending shot = this.pending.remove(projectile.getUniqueId());
        if (shot == null) {
            return false;
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
        // El tridente se lanza siempre; su cooldown se decide aqui, al resolver el efecto.
        if (shot.cooldownAtHit() && !this.startImpactCooldown(shooter, shot.itemName())) {
            return true;
        }
        Location origin = where != null ? where : projectile.getLocation();
        for (SkillSpec skill : shot.skills()) {
            skills.castImpact(shooter, skill, origin, hit);
        }
        return true;
    }

    /**
     * Cooldown del objeto al impactar, para proyectiles cuyo lanzamiento no puede
     * bloquearse (tridente). Muestra el aviso de inicio solo si el efecto va a salir.
     *
     * @return {@code false} si el objeto sigue en espera
     */
    private boolean startImpactCooldown(Player shooter, String itemName) {
        var effects = this.plugin.effectListener();
        if (effects == null || itemName == null || itemName.isBlank()) {
            return true;
        }
        var catalog = this.plugin.drops().catalog();
        UseSpec use = catalog.useOf(itemName).orElse(null);
        if (use == null) {
            return true;
        }
        if (!effects.tryImpactCooldown(shooter, itemName, use)) {
            return false;
        }
        if (use.hasStartMessage()) {
            this.plugin.status().notice(shooter, use.startMessage()
                    .replace("{jugador}", shooter.getName()), BarColor.WHITE);
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
