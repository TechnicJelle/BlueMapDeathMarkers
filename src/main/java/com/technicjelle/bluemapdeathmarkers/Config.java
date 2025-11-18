package com.technicjelle.bluemapdeathmarkers;

import com.technicjelle.MCUtils.ConfigUtils;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Config {
	public static final String MARKER_SET_ID = "death-markers";

	private final BlueMapDeathMarkers plugin;

	public String markerSetName;
	public String tooltipSuffix;
	public boolean toggleable;
	public boolean defaultHidden;
	public boolean keepAllMarkers;
	public long expireTimeInMinutes;
	public List<GameMode> hiddenGameModes;

	public Config(BlueMapDeathMarkers plugin) {
		this.plugin = plugin;

		try {
			ConfigUtils.copyPluginResourceToConfigDir(plugin, "config.yml", "config.yml", false);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		//Load config from disk
		plugin.reloadConfig();

		//Load config values into variables
		markerSetName = configFile().getString("MarkerSetName");
		tooltipSuffix = configFile().getString("TooltipSuffix");
		toggleable = configFile().getBoolean("Toggleable");
		defaultHidden = configFile().getBoolean("DefaultHidden");
		keepAllMarkers = configFile().getBoolean("KeepAllMarkers");
		expireTimeInMinutes = configFile().getLong("ExpireTimeInMinutes");
		hiddenGameModes = parseGameModes(configFile().getStringList("HiddenGameModes"));
	}

	private List<GameMode> parseGameModes(List<String> hiddenGameModesStrings) {
		ArrayList<GameMode> gameModes = new ArrayList<>();
		for (String gm : hiddenGameModesStrings) {
			try {
				gameModes.add(GameMode.valueOf(gm.toUpperCase()));
			} catch (IllegalArgumentException e) {
				plugin.getLogger().warning("Invalid Game Mode: " + gm);
			}
		}
		return gameModes;
	}

	private FileConfiguration configFile() {
		return plugin.getConfig();
	}
}
