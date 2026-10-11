package com.juanp.custommobs.skill;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.economy.EconomyLink;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lleva las conversaciones de las cadenas de interaccion.
 *
 * <p>Una conversacion es de <b>un jugador</b>: empezar otra cierra la anterior. El guion vive en
 * la definicion del mob; aqui solo se lleva <b>por donde va cada uno</b>, y se olvida al
 * terminar (una charla no necesita sobrevivir a un reinicio).
 *
 * <p>El avance lo dispara un clic en el chat: el boton manda un comando interno con el id de la
 * conversacion y el numero de opcion. <b>El paso no viaja en el clic</b>: se usa el que el
 * servidor tiene apuntado, para que nadie pueda saltarse un pago escribiendo el comando a mano.
 */
public final class ChainService {

    /** Tope de pasos encadenados sin esperar a nadie, por si un guion se muerde la cola. */
    private static final int MAX_AUTO_STEPS = 20;

    private final CustomMobsPlugin plugin;
    private final MobService service;
    private final EconomyLink economy;
    private final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();

    private record Conversation(UUID id, UUID mobId, String skillId, String stepId) {
    }

    public ChainService(CustomMobsPlugin plugin, MobService service, EconomyLink economy) {
        this.plugin = plugin;
        this.service = service;
        this.economy = economy;
    }

    /** Arranca una conversacion: lo llama el clic derecho sobre el mob. */
    public void start(CustomMob mob, Player player, SkillSpec skill) {
        ChainSpec chain = skill.chain();
        ChainStep first = chain == null ? null : chain.first().orElse(null);
        if (first == null) {
            return;
        }
        this.enter(mob, player, skill, UUID.randomUUID(), first, 0);
    }

    /** Avanza la conversacion: lo llama el comando interno que manda el boton. */
    public void choose(Player player, UUID conversationId, int optionIndex) {
        Conversation conversation = this.conversations.get(player.getUniqueId());
        if (conversation == null || !conversation.id().equals(conversationId)) {
            return;
        }
        CustomMob mob = this.service.find(conversation.mobId()).orElse(null);
        SkillSpec skill = mob == null ? null : this.skillOf(mob, conversation.skillId());
        ChainStep step = skill == null ? null : this.stepOf(skill, conversation.stepId());
        if (step == null || optionIndex < 0 || optionIndex >= step.options().size()) {
            this.conversations.remove(player.getUniqueId());
            return;
        }
        ChainOption option = step.options().get(optionIndex);
        if (option.end()) {
            this.conversations.remove(player.getUniqueId());
            return;
        }
        this.goTo(mob, player, skill, conversationId, option.next(), 0);
    }

    /** Cierra la conversacion de un jugador, si la tiene. */
    public void forget(UUID playerId) {
        this.conversations.remove(playerId);
    }

    // ------------------------------------------------------------------ motor

    private void enter(CustomMob mob, Player player, SkillSpec skill, UUID conversationId,
                       ChainStep step, int depth) {
        if (depth > MAX_AUTO_STEPS) {
            this.conversations.remove(player.getUniqueId());
            return;
        }
        this.conversations.put(player.getUniqueId(),
                new Conversation(conversationId, mob.entity().getUniqueId(), skill.id(), step.id()));

        // 1) lo que dice.
        if (step.message() != null && !step.message().isBlank()) {
            player.sendMessage(this.text(mob, player, step.message()));
        }
        // 2) lo que ejecuta. Los comandos de una cadena son de una interaccion, nunca de un
        //    combate: la cadena entera nace de un clic.
        if (step.consoleCommand() != null && !step.consoleCommand().isBlank()) {
            this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(),
                    this.text(mob, player, step.consoleCommand()));
        }
        if (step.playerCommand() != null && !step.playerCommand().isBlank()) {
            player.performCommand(this.text(mob, player, step.playerCommand()));
        }
        // 3) lo que cobra. Si no puede pagar, se va por donde diga 'no-pay'.
        if (step.charges()) {
            if (this.economy.charge(player, step.pay())) {
                this.goTo(mob, player, skill, conversationId, step.next(), depth + 1);
            } else {
                this.goTo(mob, player, skill, conversationId, step.noPay(), depth + 1);
            }
            return;
        }
        // 3b) la faccion: si no la cumple, se va por donde diga 'no-faction'.
        if (step.checksFaction() && !this.inFaction(player, step.faction())) {
            this.goTo(mob, player, skill, conversationId, step.noFaction(), depth + 1);
            return;
        }
        // 4) la pregunta: se mandan los botones y se espera el clic.
        if (step.asks()) {
            for (int index = 0; index < step.options().size(); index++) {
                player.sendMessage(this.button(mob, player, step.options().get(index).label(),
                        conversationId, index));
            }
            return;
        }
        // 5) y si no pregunta nada, sigue solo —o cierra—.
        if (step.end()) {
            this.conversations.remove(player.getUniqueId());
            return;
        }
        this.goTo(mob, player, skill, conversationId, step.next(), depth + 1);
    }

    private void goTo(CustomMob mob, Player player, SkillSpec skill, UUID conversationId,
                      String stepId, int depth) {
        ChainStep next = skill.chain().byId(stepId).orElse(null);
        if (next == null) {
            this.conversations.remove(player.getUniqueId());
            return;
        }
        this.enter(mob, player, skill, conversationId, next, depth);
    }

    /**
     * {@code true} si el jugador pertenece a esa faccion.
     *
     * <p>{@code *} pide simplemente que tenga alguna. Si se nombra una, valen tambien sus
     * <b>aliadas</b>: quien sirve a los orcos sirve tambien a los goblins, que son su bando.
     */
    private boolean inFaction(Player player, String wanted) {
        String faction = this.plugin.playerFactions().of(player.getUniqueId()).orElse(null);
        if (faction == null) {
            return false;
        }
        if ("*".equals(wanted)) {
            return true;
        }
        return faction.equals(wanted) || this.plugin.factions().isAlly(wanted, faction);
    }

    private SkillSpec skillOf(CustomMob mob, String skillId) {
        return mob.definition().skills().stream()
                .filter(skill -> skill.id().equals(skillId))
                .findFirst().orElse(null);
    }

    private ChainStep stepOf(SkillSpec skill, String stepId) {
        return skill.chain() == null ? null : skill.chain().byId(stepId).orElse(null);
    }

    /** El texto con {@code {mob}} y {@code {jugador}} sustituidos. */
    private String text(CustomMob mob, Player player, String raw) {
        return raw.replace("{mob}", mob.definition().displayName())
                .replace("{jugador}", player.getName());
    }

    /** Un boton: el texto que se pulsa, y el comando interno que lleva al paso siguiente. */
    private Component button(CustomMob mob, Player player, String label, UUID conversationId,
                            int index) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(this.text(mob, player, label))
                .clickEvent(ClickEvent.runCommand("/custommobs _chain " + conversationId + " " + index))
                .hoverEvent(HoverEvent.showText(Component.text("Pulsa para elegir")));
    }
}
