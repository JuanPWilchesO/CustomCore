package com.juanp.custommobs.skill;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.economy.EconomyLink;
import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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
 * conversacion, <b>el paso</b> y el numero de opcion. El paso viaja en el clic a proposito: un
 * boton viejo —de una pregunta que ya se contesto— no debe mover la conversacion actual. Como
 * solo se acepta el paso que el servidor tiene apuntado, sigue sin poder saltarse un pago
 * escribiendo el comando a mano.
 */
public final class ChainService {

    /** Tope de pasos encadenados sin esperar a nadie, por si un guion se muerde la cola. */
    private static final int MAX_AUTO_STEPS = 20;

    private final CustomMobsPlugin plugin;
    private final MobService service;
    private final EconomyLink economy;
    private final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();

    private record Conversation(UUID id, UUID mobId, String skillId, String stepId,
                                long lastTouched) {

        Conversation touched() {
            return new Conversation(this.id, this.mobId, this.skillId, this.stepId,
                    System.currentTimeMillis());
        }
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

    /**
     * Avanza la conversacion: lo llama el comando interno que manda el boton.
     *
     * @param stepId paso en el que se mostro el boton. Si ya no es el actual, el clic es de una
     *               pregunta vieja y se ignora sin cerrar nada
     */
    public void choose(Player player, UUID conversationId, String stepId, int optionIndex) {
        Conversation conversation = this.conversations.get(player.getUniqueId());
        if (conversation == null || !conversation.id().equals(conversationId)) {
            return;
        }
        if (stepId == null || !stepId.equals(conversation.stepId())) {
            // Boton de una pregunta anterior: el jugador ya paso de esa. No se cierra la
            // conversacion por un clic atrasado.
            return;
        }
        long timeout = this.plugin.config().chainTimeoutSeconds();
        if (timeout > 0 && System.currentTimeMillis() - conversation.lastTouched() > timeout * 1000L) {
            this.conversations.remove(player.getUniqueId());
            player.sendMessage(com.juanp.custommobs.mob.Texts.color(
                    "&7Esa conversacion ya caduco. Vuelve a hablar con el mob."));
            return;
        }
        CustomMob mob = this.service.find(conversation.mobId()).orElse(null);
        if (mob != null && !this.inReach(player, mob)) {
            // El mob se fue o el jugador se alejo: no se puede seguir hablando con la pared.
            this.conversations.remove(player.getUniqueId());
            player.sendMessage(com.juanp.custommobs.mob.Texts.color(
                    "&7Ya no estas lo bastante cerca para seguir esta conversacion."));
            return;
        }
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
                new Conversation(conversationId, mob.entity().getUniqueId(), skill.id(), step.id(),
                        System.currentTimeMillis()));

        // 1) lo que dice.
        if (step.message() != null && !step.message().isBlank()) {
            // Mismo detalle que en las skills: el '&' hay que traducirlo a mano.
            player.sendMessage(com.juanp.custommobs.mob.Texts.color(
                    this.text(mob, player, step.message())));
        }
        // 2) los requisitos, ANTES de cobrar o de ejecutar nada: si el jugador no es de la
        //    faccion o de la clase, no debe pagar ni ver correr los comandos del paso.
        //    Primero la faccion y luego la clase, para que el primer fallo decida el 'no-...'.
        if (step.checksFaction() && !this.inFaction(player, step.faction())) {
            this.goTo(mob, player, skill, conversationId, step.noFaction(), depth + 1);
            return;
        }
        if (step.checksClass() && !this.inClass(player, step.clazz())) {
            this.goTo(mob, player, skill, conversationId, step.noClass(), depth + 1);
            return;
        }
        // 3) lo que cobra. Si no puede pagar, se va por donde diga 'no-pay' y no se
        //    ejecuta nada del paso.
        if (step.charges()) {
            if (this.pay(player, step)) {
                this.goTo(mob, player, skill, conversationId, step.next(), depth + 1);
            } else {
                this.goTo(mob, player, skill, conversationId, step.noPay(), depth + 1);
            }
            return;
        }
        // 3b) lo que ejecuta. Los comandos de una cadena son de una interaccion, nunca de un
        //     combate: la cadena entera nace de un clic.
        if (step.consoleCommand() != null && !step.consoleCommand().isBlank()) {
            this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(),
                    this.text(mob, player, step.consoleCommand()));
        }
        if (step.playerCommand() != null && !step.playerCommand().isBlank()) {
            player.performCommand(this.text(mob, player, step.playerCommand()));
        }
        // 4) la pregunta: se mandan los botones y se espera el clic.
        if (step.asks()) {
            for (int index = 0; index < step.options().size(); index++) {
                player.sendMessage(this.button(mob, player, step.options().get(index).label(),
                        conversationId, step.id(), index));
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

    /**
     * Cobra el paso. Si hay varias opciones configuradas son **alternativas**: se intenta
     * dinero, luego objeto y luego niveles; la primera que se pueda pagar se consume.
     */
    private boolean pay(Player player, ChainStep step) {
        if (step.pay() > 0.0D
                && this.economy.charge(player, step.pay()) == EconomyLink.Charge.CHARGED) {
            return true;
        }
        if (step.payItem() != null
                && this.takeItem(player, step.payItem(), step.payItemCount())) {
            return true;
        }
        if (step.payXp() > 0 && player.getLevel() >= step.payXp()) {
            player.setLevel(player.getLevel() - step.payXp());
            return true;
        }
        return false;
    }

    /** Quita del inventario los objetos del pago, si hay suficientes. */
    private boolean takeItem(Player player, String raw, int amount) {
        String wanted = raw == null ? "" : raw.trim();
        int count = Math.max(1, amount);
        var catalog = this.plugin.drops().catalog();
        boolean fromCatalog = catalog.get(wanted).isPresent();
        Material material = fromCatalog ? null : Material.matchMaterial(wanted);
        if (!fromCatalog && material == null) {
            return false;
        }

        ItemStack[] contents = player.getInventory().getStorageContents();
        int total = 0;
        for (ItemStack stack : contents) {
            if (this.matches(stack, wanted, fromCatalog, catalog)) {
                total += stack.getAmount();
            }
        }
        if (total < count) {
            return false;
        }

        int remaining = count;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack stack = contents[index];
            if (!this.matches(stack, wanted, fromCatalog, catalog)) {
                continue;
            }
            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[index] = null;
            }
            remaining -= take;
        }
        player.getInventory().setStorageContents(contents);
        return true;
    }

    private boolean matches(ItemStack stack, String raw, boolean fromCatalog,
                            com.juanp.custommobs.drop.ItemCatalog catalog) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (fromCatalog) {
            if (!stack.hasItemMeta()) {
                return false;
            }
            String name = stack.getItemMeta().getPersistentDataContainer()
                    .get(catalog.nameKey(), PersistentDataType.STRING);
            return name != null && raw.equalsIgnoreCase(name);
        }
        Material material = Material.matchMaterial(raw);
        return material != null && stack.getType() == material;
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
    /** {@code true} si el jugador es de esa clase ({@code *} = cualquiera). */
    private boolean inClass(Player player, String wanted) {
        String clazz = this.plugin.playerClasses().of(player.getUniqueId()).orElse(null);
        return clazz != null && ("*".equals(wanted) || clazz.equals(wanted));
    }

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
                            String stepId, int index) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(this.text(mob, player, label))
                .clickEvent(ClickEvent.runCommand("/customcore _chain " + conversationId + " "
                        + stepId + " " + index))
                .hoverEvent(HoverEvent.showText(Component.text("Pulsa para elegir")));
    }

    /**
     * {@code true} si el jugador sigue lo bastante cerca del mob para continuar la charla.
     *
     * <p>La distancia sale de {@code chains.choice-distance}. Un solo mundo y una sola
     * comprobacion por clic: no hay coste en el ciclo.
     */
    private boolean inReach(Player player, CustomMob mob) {
        double distance = this.plugin.config().chainChoiceDistance();
        if (distance <= 0.0D) {
            return true;
        }
        var entity = mob.entity();
        if (!entity.isValid() || !player.getWorld().equals(entity.getWorld())) {
            return false;
        }
        return player.getLocation().distanceSquared(entity.getLocation()) <= distance * distance;
    }
}
