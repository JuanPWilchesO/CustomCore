package com.juanp.custommobs.economy;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;

/**
 * Cobrar dinero, hablando con la API de <b>EssentialsX</b> por reflexion.
 *
 * <p>Se hace por reflexion a proposito: asi el plugin no depende de EssentialsX para compilar y
 * sigue funcionando —ignorando los pagos— en servidores que no lo tengan.
 *
 * <p><b>Aviso importante:</b> las skills de pago ({@code pay}) <b>no funcionan sin un plugin de
 * economia</b>. Sin EssentialsX, el pago siempre falla: la cadena tomara la rama de
 * {@code no-pay} y el jugador no pagara nada.
 */
public final class EconomyLink {

    private final boolean available;
    private final Method hasEnough;
    private final Method withdraw;

    private EconomyLink(boolean available, Method hasEnough, Method withdraw) {
        this.available = available;
        this.hasEnough = hasEnough;
        this.withdraw = withdraw;
    }

    /** Engancha con EssentialsX si esta; si no, devuelve un cobrador que siempre falla. */
    public static EconomyLink create(Plugin plugin) {
        boolean installed = plugin.getServer().getPluginManager().getPlugin("Essentials") != null
                || plugin.getServer().getPluginManager().getPlugin("EssentialsX") != null;
        if (!installed) {
            plugin.getLogger().warning("Sin EssentialsX instalado: las skills de pago (pay) NO"
                    + " funcionaran. La cadena tomara la rama de 'no-pay'.");
            return new EconomyLink(false, null, null);
        }
        try {
            Class<?> economy = Class.forName("com.earth2me.essentials.api.Economy");
            Method hasEnough = economy.getMethod("hasEnough", String.class, BigDecimal.class);
            Method withdraw = economy.getMethod("subtract", String.class, BigDecimal.class);
            plugin.getLogger().info("Economia enganchada con EssentialsX: las skills de pago"
                    + " funcionan.");
            return new EconomyLink(true, hasEnough, withdraw);
        } catch (Throwable failure) {
            plugin.getLogger().warning("EssentialsX esta, pero no se pudo enganchar su economia ("
                    + failure.getClass().getSimpleName() + "): las skills de pago NO funcionaran.");
            return new EconomyLink(false, null, null);
        }
    }

    /** {@code true} si hay economia con la que cobrar. */
    public boolean available() {
        return this.available;
    }

    /**
     * Cobra el importe al jugador.
     *
     * @return {@code true} si el cobro se hizo; {@code false} si no tiene bastante, o si no hay
     *         economia instalada
     */
    public boolean charge(Player player, double amount) {
        if (!this.available || amount <= 0.0D) {
            return false;
        }
        BigDecimal money = BigDecimal.valueOf(amount);
        try {
            String name = player.getName();
            if (!(Boolean) this.hasEnough.invoke(null, name, money)) {
                return false;
            }
            this.withdraw.invoke(null, name, money);
            return true;
        } catch (Throwable failure) {
            // Sin fondos, o la API no responde: en cualquier caso, no se cobro.
            return false;
        }
    }
}
