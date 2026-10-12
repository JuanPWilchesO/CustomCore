package com.juanp.custommobs.disguise;

import com.juanp.custommobs.mob.DisguiseSpec;
import com.juanp.custommobs.mob.MobDefinition;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.PlayerDisguise;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enlace con LibsDisguises. Aislado en su propia clase para que el resto del plugin
 * cargue aunque LibsDisguises no este instalado.
 */
public final class LibsDisguisesLink implements DisguiseLink {

    private final Plugin plugin;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public LibsDisguisesLink(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "LibsDisguises";
    }

    @Override
    public void applyNext(MobDefinition definition) {
        Disguise disguise = this.build(definition);
        if (disguise == null) {
            return;
        }
        try {
            DisguiseAPI.disguiseNextEntity(disguise);
        } catch (Throwable throwable) {
            this.warn("No se pudo preparar el disfraz de '" + definition.id() + "': " + throwable.getMessage());
        }
    }

    @Override
    public void apply(LivingEntity entity, MobDefinition definition) {
        if (DisguiseAPI.isDisguised(entity)) {
            // Ya venia disfrazado desde la invocacion.
            return;
        }
        Disguise disguise = this.build(definition);
        if (disguise == null) {
            return;
        }
        try {
            DisguiseAPI.disguiseToAll(entity, disguise);
        } catch (Throwable throwable) {
            this.warn("No se pudo aplicar el disfraz de '" + definition.id() + "': " + throwable.getMessage());
        }
    }

    @Override
    public void refresh(LivingEntity entity, MobDefinition definition) {
        Disguise disguise = this.build(definition);
        if (disguise == null) {
            return;
        }
        try {
            // Se reenvia sin preguntar: tras un cambio de mundo la marca sigue puesta pero
            // el cliente ya no ve el disfraz, asi que 'apply' no haria nada.
            DisguiseAPI.disguiseToAll(entity, disguise);
        } catch (Throwable throwable) {
            this.warn("No se pudo reenviar el disfraz de '" + definition.id() + "': "
                    + throwable.getMessage());
        }
    }

    @Override
    public boolean isDisguised(LivingEntity entity) {
        try {
            return DisguiseAPI.isDisguised(entity);
        } catch (Throwable throwable) {
            return false;
        }
    }

    private Disguise build(MobDefinition definition) {
        DisguiseSpec spec = definition.disguise();
        if (spec == null || !spec.enabled()) {
            return null;
        }

        if (spec.player()) {
            String skin = spec.skin() != null ? spec.skin() : spec.skinUrl();
            if (skin == null) {
                this.warn("Disfraz de jugador sin 'skin' ni 'skin-url'; se omite en " + definition.id() + ".");
                return null;
            }
            PlayerDisguise disguise = new PlayerDisguise(skin);
            // Un disfraz de jugador usa el nombre de la skin en el cartel; sin esto
            // taparia el display-name del mob.
            if (spec.showName()) {
                // Si su faccion le da color al nombre (factions.yml), el nombre se deja SIN
                // color: asi el color de la faccion se ve de verdad, tambien en el disfraz.
                // A un disfraz no llega el color por espectador (LibsDisguises dibuja el nombre
                // por su cuenta), asi que el color de faccion se mete DENTRO del nombre.
                disguise.setName(this.factionName(definition));
            }
            disguise.setNameVisible(spec.showName());
            // La skin se fija al final: setName no debe alterarla.
            disguise.setSkin(skin);
            return disguise;
        }

        if (spec.type() == null) {
            return null;
        }
        try {
            return new MobDisguise(DisguiseType.valueOf(spec.type().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            this.warn("Tipo de disfraz desconocido '" + spec.type() + "' en " + definition.id() + ".");
            return null;
        }
    }

    /** El nombre que se le pone al disfraz: con el color de su faccion, si lo declara. */
    private String factionName(MobDefinition definition) {
        String name = definition.displayName();
        if (!definition.server() || definition.faction() == null
                || !(this.plugin instanceof com.juanp.custommobs.CustomMobsPlugin owner)) {
            return name;
        }
        java.util.Optional<String> raw = owner.factions().nameColor(definition.faction());
        if (raw.isEmpty()) {
            return name;
        }
        org.bukkit.ChatColor color = com.juanp.custommobs.style.MobStyle.color(raw.get());
        return color == null ? name : color + org.bukkit.ChatColor.stripColor(name);
    }

    private void warn(String message) {
        if (this.warned.add(message)) {
            this.plugin.getLogger().warning(message);
        }
    }
}
