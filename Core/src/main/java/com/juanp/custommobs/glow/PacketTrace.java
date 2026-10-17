package com.juanp.custommobs.glow;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.juanp.custommobs.CustomMobsPlugin;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Diagnostico: guarda los ultimos paquetes enviados a cada jugador y los vuelca al log cuando
 * se desconecta.
 *
 * <p>Existe porque un "Error de protocolo de red" del cliente no deja nada en el log del
 * servidor: el cliente se corta solo. Con esto, la proxima vez que alguien caiga sabremos que
 * paquete le mandamos justo antes. Solo se activa con {@code debug-packets: true}.
 */
public final class PacketTrace extends PacketListenerAbstract {

    private static final int KEEP = 80;

    private final CustomMobsPlugin plugin;
    private final Map<UUID, Deque<String>> recent = new ConcurrentHashMap<>();

    public PacketTrace(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getUser() == null || event.getUser().getUUID() == null) {
            return;
        }
        UUID id = event.getUser().getUUID();
        Deque<String> deque = this.recent.computeIfAbsent(id, key -> new ConcurrentLinkedDeque<>());
        PacketTypeCommon type = event.getPacketType();
        deque.addLast((System.currentTimeMillis() % 100000L) + " "
                + (type == null ? "?" : type.getName()));
        while (deque.size() > KEEP) {
            deque.pollFirst();
        }
    }

    /** Vuelca y olvida la traza de ese jugador. */
    public void dump(Player player) {
        Deque<String> deque = this.recent.remove(player.getUniqueId());
        if (deque == null || deque.isEmpty()) {
            return;
        }
        this.plugin.getLogger().warning("[trace] Ultimos " + deque.size()
                + " paquetes a " + player.getName() + " antes de desconectarse:");
        for (String line : deque) {
            this.plugin.getLogger().warning("  " + line);
        }
    }
}
