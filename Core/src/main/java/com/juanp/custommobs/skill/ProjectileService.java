package com.juanp.custommobs.skill;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.item.UseSpec;
import com.juanp.custommobs.mob.CustomMob;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageEvent;

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

    /**
     * Radio de la red de seguridad: con el cuerpo oculto, lo que golpea la flecha es la
     * caja del modelo, no la entidad. Se busca el mob custom con modelo mas cercano al
     * punto de impacto dentro de este margen, que cubre la caja pegada al cuerpo.
     */
    private static final double MODEL_BOX_RADIUS = 1.5D;

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
     * <p>Si lo golpeado no es una entidad custom (la caja del modelo de BetterModel, un
     * armor stand, una entidad de otro plugin), el disparo NO se gasta: se espera al golpe
     * real, que llega por {@link #hitEntity}. Si ese golpe no llega, la red de seguridad
     * busca el mob con modelo pegado al punto de impacto.
     *
     * @return {@code true} si el proyectil llevaba skills de impacto (aunque el cooldown
     *         del tridente saltara el efecto)
     */
    public boolean hit(Projectile projectile, Location where, LivingEntity hit) {
        Pending shot = this.pending.get(projectile.getUniqueId());
        if (shot == null) {
            return false;
        }
        this.trace("impacto de " + projectile.getType() + " con item=" + shot.itemName()
                + " golpeado=" + (hit == null ? "ninguno" : hit.getType().toString()));
        if (hit != null && this.isCustom(hit)) {
            return this.resolve(projectile, shot, hit, where);
        }
        // La caja del modelo se ha comido el primer aviso: no se gasta el disparo todavia.
        // La entidad real (o la red de seguridad) decide.
        LivingEntity nearby = this.nearbyModel(hit, where);
        if (nearby != null) {
            return this.resolve(projectile, this.pending.remove(projectile.getUniqueId()),
                    nearby, where);
        }
        return false;
    }

    /**
     * Golpe real sobre una entidad custom, avisado por {@code EntityDamageByEntityEvent}.
     *
     * <p>Existe porque, con el cuerpo oculto, el proyectil toca primero la caja del modelo
     * y el mob recibe el dano un instante despues. Sin este camino la skill se quedaba en
     * la caja.
     *
     * @return {@code true} si se aplico la skill
     */
    public boolean hitEntity(Projectile projectile, LivingEntity target) {
        Pending shot = this.pending.get(projectile.getUniqueId());
        if (shot == null || target == null || !this.isCustom(target)) {
            return false;
        }
        this.trace("golpe real de " + projectile.getType() + " con item=" + shot.itemName()
                + " sobre " + target.getType());
        return this.resolve(projectile, shot, target, projectile.getLocation());
    }

    /**
     * Aplica las skills del disparo a la entidad, una sola vez por disparo.
     *
     * @param shot el disparo ya sacado del registro, o {@code null} si otro camino lo resolvio
     * @return {@code true} si se aplico
     */
    private boolean resolve(Projectile projectile, Pending shot, LivingEntity target,
                            Location where) {
        if (shot == null) {
            return false;
        }
        // El primer camino que resuelve se lleva el proyectil: la caja y el golpe real no
        // pueden disparar la skill dos veces.
        this.pending.remove(projectile.getUniqueId());
        long now = System.currentTimeMillis();
        if (shot.expiresAt() < now) {
            return false;
        }
        Long until = this.spent.get(shot.shotKey());
        if (until != null && until > now) {
            return false;
        }
        // Un mob invulnerable no se ve afectado por el golpe, asi que tampoco se aturde: la
        // red de seguridad debe respetar lo mismo que el dano.
        if (!this.canBeAffected(target)) {
            this.trace("objetivo " + target.getType() + " invulnerable: la skill no se aplica");
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
            skills.castImpact(shooter, skill, origin, target);
        }
        return true;
    }

    /** {@code true} si esa entidad es un mob custom del plugin. */
    private boolean isCustom(LivingEntity entity) {
        return this.plugin.mobs() != null && this.plugin.mobs().find(entity.getUniqueId()).isPresent();
    }

    /**
     * Red de seguridad: el mob custom con modelo mas cercano al punto de impacto.
     *
     * <p>Solo mobs con modelo: son los unicos cuyo cuerpo puede estar oculto detras de una
     * caja. Un armor stand de decoracion, sin un mob custom pegado, no redirige nada.
     */
    private LivingEntity nearbyModel(LivingEntity hit, Location where) {
        if (where == null || where.getWorld() == null || this.plugin.mobs() == null) {
            return null;
        }
        // Se mide desde la caja golpeada si la hay: es el punto que el cliente vio.
        Location from = hit != null ? hit.getLocation() : where;
        CustomMob best = null;
        double bestDistance = MODEL_BOX_RADIUS;
        for (CustomMob customMob : this.plugin.mobs().active()) {
            if (!(customMob.entity() instanceof LivingEntity living) || !living.isValid()
                    || living.isDead() || living.equals(hit)) {
                continue;
            }
            if (customMob.definition().model() == null
                    || !customMob.definition().model().active()) {
                continue;
            }
            if (!living.getWorld().equals(from.getWorld())) {
                continue;
            }
            // Se mide al centro del cuerpo, no a los pies: la caja puede estar a un lado.
            Location center = living.getLocation().add(0.0D, living.getHeight() / 2.0D, 0.0D);
            double distance = center.distance(from);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = customMob;
            }
        }
        return best == null ? null : (LivingEntity) best.entity();
    }

    /**
     * {@code true} si el mob puede recibir el efecto: no es invulnerable y no tiene la
     * mecanica {@code invulnerabilidad} activa.
     */
    private boolean canBeAffected(LivingEntity entity) {
        CustomMob customMob = this.plugin.mobs() == null
                ? null : this.plugin.mobs().find(entity.getUniqueId()).orElse(null);
        if (customMob != null
                && customMob.definition().ignores(EntityDamageEvent.DamageCause.PROJECTILE)) {
            return false;
        }
        var effects = this.plugin.effects();
        return effects == null || !effects.hasMechanic(entity, "invulnerabilidad");
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

    /** Traza del impacto con {@code debug: true}: saber si el proyectil llego armado. */
    private void trace(String message) {
        if (this.plugin.config() != null && this.plugin.config().debug()) {
            this.plugin.getLogger().info("[skill] " + message);
        }
    }

    /** Limpia proyectiles que nunca impactaron y marcas de disparo viejas. */
    public void tick() {
        long now = System.currentTimeMillis();
        this.pending.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        this.spent.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
