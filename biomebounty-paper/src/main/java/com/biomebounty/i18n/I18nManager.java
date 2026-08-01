package com.biomebounty.i18n;

import com.biomebounty.BiomeBountyPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.util.*;

public class I18nManager {
    private final BiomeBountyPlugin plugin;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();
    private String defaultLang = "zh_CN";

    public I18nManager(BiomeBountyPlugin plugin) { this.plugin = plugin; }

    public void loadMessages() {
        languages.clear();
        defaultLang = plugin.getConfig().getString("settings.language", "zh_CN");
        for (String lang : new String[]{"zh_CN", "en"}) {
            File file = new File(plugin.getDataFolder(), "messages/" + lang + ".yml");
            if (!file.exists()) plugin.saveResource("messages/" + lang + ".yml", false);
            languages.put(lang, YamlConfiguration.loadConfiguration(file));
        }
    }

    public String getMessage(String key) { return getMessage(key, new HashMap<>()); }

    public String getMessage(String key, Map<String, String> placeholders) {
        YamlConfiguration config = languages.getOrDefault(defaultLang, languages.values().iterator().next());
        String msg = config != null ? config.getString(key, key) : key;
        for (Map.Entry<String, String> e : placeholders.entrySet()) msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public String getPrefixedMessage(String key) { return getPrefixedMessage(key, new HashMap<>()); }
    public String getPrefixedMessage(String key, Map<String, String> ph) { return getMessage("messages.prefix") + getMessage(key, ph); }

    /**
     * 查自定义翻译表（translations.xxx）
     * 找不到返回 null
     */
    public String getCustomTranslation(String translationKey) {
        YamlConfiguration config = languages.getOrDefault(defaultLang, languages.values().iterator().next());
        if (config == null) return null;
        return config.getString("translations." + translationKey, null);
    }
}
