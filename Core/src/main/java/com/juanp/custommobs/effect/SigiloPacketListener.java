package com.juanp.custommobs.effect;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Sanitiza los paquetes de un jugador en sigilo.
 *
 * <p>En los metadatos limpia el flag de sprint: la invisibilidad oculta el cuerpo, pero el
 * cliente sigue dibujando las particulas de correr si ve ese bit. El de invisibilidad se
 * deja puesto. En el equipamiento cambia cada pieza por vacio, para que el servidor no
 * pueda mostrar la armadura real entre refrescos. Los sonidos no pasan por aqui.
 */
public final class SigiloPacketListener extends PacketListenerAbstract {

    /** Flag de la entidad: esta invisible. */
    private static final byte INVISIBLE_FLAG = 0x20;
    /** Flag de la entidad: esta corriendo. */
    private static final byte SPRINTING_FLAG = 0x08;

    private final SigiloService sigilo;

    public SigiloPacketListener(SigiloService sigilo) {
        this.sigilo = sigilo;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            this.metadata(event);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            this.equipment(event);
        }
    }

    private void metadata(PacketSendEvent event) {
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        if (!this.sigilo.isHiddenEntity(packet.getEntityId())) {
            return;
        }
        boolean changed = false;
        for (EntityData<?> data : packet.getEntityMetadata()) {
            if (data.getIndex() != 0 || !(data.getValue() instanceof Byte current)) {
                continue;
            }
            byte next = (byte) ((current | INVISIBLE_FLAG) & ~SPRINTING_FLAG);
            if (next != current) {
                @SuppressWarnings("unchecked")
                EntityData<Byte> flags = (EntityData<Byte>) data;
                flags.setValue(next);
                changed = true;
            }
        }
        if (changed) {
            event.markForReEncode(true);
        }
    }

    private void equipment(PacketSendEvent event) {
        WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
        if (!this.sigilo.isHiddenEntity(packet.getEntityId())) {
            return;
        }
        // Al propio oculto no se le toca: su cliente ya conoce su equipo y en F5 debe
        // seguir viendo su armadura como en la invisibilidad de vanilla.
        if (event.getUser() != null && event.getUser().getUUID() != null) {
            var viewer = org.bukkit.Bukkit.getPlayer(event.getUser().getUUID());
            if (viewer != null && viewer.getEntityId() == packet.getEntityId()) {
                return;
            }
        }
        List<Equipment> empty = new ArrayList<>();
        for (Equipment equipment : packet.getEquipment()) {
            empty.add(new Equipment(equipment.getSlot(), ItemStack.EMPTY));
        }
        packet.setEquipment(empty);
        event.markForReEncode(true);
    }
}
