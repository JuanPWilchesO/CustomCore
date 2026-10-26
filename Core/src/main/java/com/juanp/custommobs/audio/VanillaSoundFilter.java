package com.juanp.custommobs.audio;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.sound.Sound;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntitySoundEffect;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.mob.MobSounds;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Calla el sonido vanilla de los tipos que el mob tiene configurados en su yml.
 *
 * <p>Se hace en el <b>paquete</b>, no en el evento de dano, igual que LibsDisguises: el
 * servidor emite {@code ENTITY_SOUND_EFFECT} con el sonido vanilla y aqui se cancela antes
 * de salir hacia el cliente. El dano no se toca en ningun caso.
 *
 * <p>Cancelar el evento de dano para callar el sonido (el intento anterior) anulaba el
 * golpe entero: eso dejaba a los mobs invulnerables.
 *
 * <p>Como el sonido propio lo emite {@link SoundService} por su cuenta, basta con cancelar el
 * de vanilla del mismo tipo. Y al ir por paquete se pueden callar los cuatro tipos, incluidos
 * {@code death} y {@code ambient}, que no tienen evento previo en Bukkit.
 *
 * <p>Si PacketEvents no esta, no se registra: los mobs suenan con su tipo y con el suyo,
 * como antes de esta funcion.
 */
public final class VanillaSoundFilter extends PacketListenerAbstract {

    private final MobService service;
    private final SoundService sounds;
    /** Id de red -> UUID del mob. Se rehace cuando cambia cuantos mobs hay. */
    private final Map<Integer, UUID> netIds = new HashMap<>();
    /** Cuantos mobs habia cuando se construyo el indice. */
    private int indexed = -1;

    public VanillaSoundFilter(MobService service, SoundService sounds) {
        this.service = service;
        this.sounds = sounds;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_SOUND_EFFECT) {
            return;
        }
        WrapperPlayServerEntitySoundEffect packet = new WrapperPlayServerEntitySoundEffect(event);
        Entity entity = this.entityById(packet.getEntityId());
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        CustomMob customMob = this.service.find(living.getUniqueId()).orElse(null);
        if (customMob == null) {
            return;
        }
        String key = keyOf(packet.getSound());
        if (key == null) {
            return;
        }
        // Un sonido que emitio el propio plugin NO se filtra: es justo el configurado.
        if (this.sounds.isOwn(key)) {
            return;
        }
        if (!this.isConfigured(customMob.definition().sounds(), key)) {
            return;
        }
        // Vanilla iba a sonar este: el del yml ya lo pone SoundService.
        event.setCancelled(true);
    }

    /**
     * La entidad de Bukkit con ese id de red, si es un mob custom.
     *
     * <p>No recorre el mundo: lleva un indice id de red -> mob, que se mantiene al dia solo
     * con los mobs registrados (son pocos) al cambiar de entidad. Recorrer todas las entidades
     * en cada sonido seria carisimo.
     */
    private Entity entityById(int entityId) {
        // Solo se rehace el indice si cambio el numero de mobs, no cada vez que un sonido de
        // una entidad ajena (un zombi vanilla, una flecha) no esta en el. Sino, cada sonido del
        // servidor recorria todos los mobs.
        Collection<CustomMob> active = this.service.active();
        if (active.size() != this.indexed) {
            this.rebuildIndex(active);
        }
        UUID uniqueId = this.netIds.get(entityId);
        if (uniqueId == null) {
            return null;
        }
        return this.service.find(uniqueId).map(CustomMob::entity).orElse(null);
    }

    /** Rehace el indice desde los mobs activos. Barato: solo los mobs del plugin. */
    private void rebuildIndex(Collection<CustomMob> active) {
        this.netIds.clear();
        for (CustomMob customMob : active) {
            this.netIds.put(customMob.entity().getEntityId(), customMob.entity().getUniqueId());
        }
        this.indexed = active.size();
    }

    /** {@code minecraft:entity.zombie.hurt}, sin el prefijo. */
    private static String keyOf(Sound sound) {
        if (sound == null) {
            return null;
        }
        String raw = sound.getSoundId().toString().toLowerCase(Locale.ROOT);
        int colon = raw.indexOf(':');
        return colon < 0 ? raw : raw.substring(colon + 1);
    }

    /**
     * {@code true} si el sonido vanilla corresponde a un tipo configurado.
     *
     * <p>Vanilla no dice de que tipo es, asi que se mira el nombre: los de dano llevan
     * {@code .hurt}, los de muerte {@code .death}, los de ambiente {@code .ambient} y los de
     * golpe {@code .attack}. Un tipo sin configurar en el yml no se filtra.
     */
    private boolean isConfigured(MobSounds sounds, String key) {
        if (sounds.attack() != null && key.endsWith(".attack")) {
            return true;
        }
        if (sounds.hurt() != null && key.endsWith(".hurt")) {
            return true;
        }
        if (sounds.death() != null && key.endsWith(".death")) {
            return true;
        }
        return sounds.hasAmbient() && (key.endsWith(".ambient") || key.endsWith(".idle"));
    }
}
