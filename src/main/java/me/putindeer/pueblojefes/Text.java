package me.putindeer.pueblojefes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Mensajes en MiniMessage con el prefijo de la config. */
public class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final PuebloJefes plugin;

    public Text(PuebloJefes plugin) {
        this.plugin = plugin;
    }

    public Component parse(String miniMessage) {
        return MM.deserialize(miniMessage == null ? "" : miniMessage);
    }

    /** Reemplaza %clave% por su valor: fill("Hola %name%", "name", "Ana"). */
    public static String fill(String text, Object... replacements) {
        if (text == null) return "";
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("%" + replacements[i] + "%", String.valueOf(replacements[i + 1]));
        }
        return text;
    }

    public void send(CommandSender to, String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) return;
        to.sendMessage(parse(plugin.getConfig().getString("prefix", "") + miniMessage));
    }

    public void broadcast(String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) return;
        Bukkit.broadcast(parse(plugin.getConfig().getString("prefix", "") + miniMessage));
    }

    public void titleAll(String title, String subtitle, Sound sound) {
        Title t = Title.title(parse(title), parse(subtitle),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(t);
            if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
        }
    }
}
