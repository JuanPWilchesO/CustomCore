package com.juanp.custommobs.ui;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Avisos de estado sin usar el chat: una boss bar por estado activo (maximo configurable)
 * y una barra transitoria para avisos puntuales y cooldowns.
 *
 * <p>La action bar queda libre para el HUD de faccion. Los mensajes de error y de
 * comandos siguen yendo al chat.
 */
public final class StatusService {

    private record Entry(String text, long endsAt, long total, BarColor color,
                         BarStyle style, int priority, boolean progress) {
    }

    private final CustomMobsPlugin plugin;
    private final Map<UUID, Map<String, Entry>> entries = new HashMap<>();
    private final Map<UUID, Map<String, BossBar>> bars = new HashMap<>();
    private BukkitTask task;

    public StatusService(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    /** Muestra o refresca un estado con duracion. */
    public void show(Player player, String id, String text, double seconds,
                     BarColor color, BarStyle style, int priority, boolean progress) {
        if (player == null || id == null || seconds <= 0.0D) {
            return;
        }
        long now = System.currentTimeMillis();
        long total = (long) Math.ceil(seconds * 1000.0D);
        Entry entry = new Entry(Texts.color(text), now + total, total, color, style,
                priority, progress);
        this.entries.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>())
                .put(id, entry);
        this.render(player);
    }

    /** Aviso puntual, sin cuenta atras: se va solo. */
    public void notice(Player player, String text, BarColor color) {
        this.show(player, "notice", text, this.plugin.config().uiNoticeSeconds(),
                color, BarStyle.SOLID, 40, false);
    }

    /** Aviso de cooldown con prioridad maxima; muestra el tiempo real del cooldown. */
    public void cooldown(Player player, String text, double seconds) {
        double duration = seconds > 0.0D
                ? seconds : this.plugin.config().uiCooldownNoticeSeconds();
        this.show(player, "cooldown", text, duration,
                BarColor.YELLOW, BarStyle.SEGMENTED_10, 100, true);
    }

    public void clear(Player player, String id) {
        if (player == null) {
            return;
        }
        this.entries.getOrDefault(player.getUniqueId(), Map.of()).remove(id);
        Map<String, BossBar> playerBars = this.bars.get(player.getUniqueId());
        if (playerBars != null) {
            BossBar bar = playerBars.remove(id);
            if (bar != null) {
                bar.removeAll();
            }
        }
    }

    public void clearAll(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        this.entries.remove(id);
        Map<String, BossBar> playerBars = this.bars.remove(id);
        if (playerBars != null) {
            playerBars.values().forEach(BossBar::removeAll);
        }
    }

    /** Titulo grande y centrado, para el recall. */
    public void title(Player player, String title, String subtitle) {
        if (player == null) {
            return;
        }
        player.sendTitle(Texts.color(title),
                subtitle == null ? "" : Texts.color(subtitle),
                this.plugin.config().uiTitleFadeIn(),
                this.plugin.config().uiTitleStay(),
                this.plugin.config().uiTitleFadeOut());
    }

    public void shutdown() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        for (Map<String, BossBar> playerBars : this.bars.values()) {
            playerBars.values().forEach(BossBar::removeAll);
        }
        this.bars.clear();
        this.entries.clear();
    }

    private void tick() {
        for (UUID id : new ArrayList<>(this.entries.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                this.clearAll(id);
                continue;
            }
            this.render(player);
        }
    }

    private void clearAll(UUID id) {
        this.entries.remove(id);
        Map<String, BossBar> playerBars = this.bars.remove(id);
        if (playerBars != null) {
            playerBars.values().forEach(BossBar::removeAll);
        }
    }

    private void render(Player player) {
        UUID id = player.getUniqueId();
        Map<String, Entry> map = this.entries.get(id);
        if (map == null || map.isEmpty()) {
            this.clearAll(id);
            return;
        }
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
        if (map.isEmpty()) {
            this.clearAll(id);
            return;
        }
        List<Map.Entry<String, Entry>> sorted = new ArrayList<>(map.entrySet());
        sorted.sort(Comparator.comparingInt(
                (Map.Entry<String, Entry> entry) -> entry.getValue().priority()).reversed());
        int max = Math.max(1, this.plugin.config().uiMaxBars());
        Map<String, BossBar> playerBars = this.bars.computeIfAbsent(id,
                key -> new LinkedHashMap<>());
        Map<String, Boolean> visible = new HashMap<>();
        for (int index = 0; index < sorted.size() && index < max; index++) {
            String key = sorted.get(index).getKey();
            Entry entry = sorted.get(index).getValue();
            visible.put(key, Boolean.TRUE);
            BossBar bar = playerBars.computeIfAbsent(key, ignored -> {
                BossBar created = Bukkit.createBossBar(entry.text(), entry.color(), entry.style());
                created.addPlayer(player);
                return created;
            });
            bar.setColor(entry.color());
            bar.setStyle(entry.style());
            long remaining = Math.max(0L, entry.endsAt() - now);
            String suffix = entry.progress()
                    ? org.bukkit.ChatColor.DARK_GRAY + " · "
                      + org.bukkit.ChatColor.GRAY
                      + Math.max(1L, (remaining + 999L) / 1000L) + "s" : "";
            bar.setTitle(entry.text() + suffix);
            bar.setProgress(entry.progress()
                    ? Math.min(1.0F, Math.max(0.0F, (float) remaining / (float) entry.total()))
                    : 1.0F);
            bar.setVisible(true);
        }
        for (Map.Entry<String, BossBar> entry : playerBars.entrySet()) {
            if (!visible.containsKey(entry.getKey())) {
                entry.getValue().setVisible(false);
            }
        }
        playerBars.entrySet().removeIf(entry -> {
            if (map.containsKey(entry.getKey())) {
                return false;
            }
            entry.getValue().removeAll();
            return true;
        });
    }
}
