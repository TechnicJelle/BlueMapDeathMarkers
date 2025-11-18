package com.technicjelle.bluemapdeathmarkers;

import com.flowpowered.math.vector.Vector2i;
import com.technicjelle.BMUtils.BMCopy;
import com.technicjelle.UpdateChecker;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BlueMapDeathMarkers extends JavaPlugin implements Listener {
	final String viewBoxRegex = "viewBox\\s*=\\s*\"\\d+\\s\\d+\\s(\\d+)\\s(\\d+)\\s*\"";
	final Pattern viewBoxPattern = Pattern.compile(viewBoxRegex);

	final String widthRegex = "width\\s*=\\s*\"(\\d+)\\s*\"";
	final Pattern widthPattern = Pattern.compile(widthRegex);

	final String heightRegex = "height\\s*=\\s*\"(\\d+)\\s*\"";
	final Pattern heightPattern = Pattern.compile(heightRegex);

	private UpdateChecker updateChecker;
	private Config config;

	String iconName;
	private Vector2i anchor;

	@Override
	public void onEnable() {
		new Metrics(this, 18983);

		updateChecker = new UpdateChecker("TechnicJelle", "BlueMapDeathMarkers", getDescription().getVersion());
		updateChecker.checkAsync();

		BlueMapAPI.onEnable(onEnable);

		getServer().getPluginManager().registerEvents(this, this);
	}

	Consumer<BlueMapAPI> onEnable = api -> {
		updateChecker.logUpdateMessage(getLogger());

		config = new Config(this);

		File iconFile = findIconFile(api);
		if (iconFile != null) {
			iconName = iconFile.getName();
			anchor = findAnchor(iconFile);
		}

		getLogger().info("BlueMapDeathMarkers has been enabled!");
	};

	@Nullable File findIconFile(@NotNull BlueMapAPI api) {
		final String ICON_NAME = "death-marker-icon";

		//find all icon files
		File[] iconFiles = Paths.get(api.getWebApp().getWebRoot().toString(), "assets").toFile().listFiles((File dir, String name) -> name.startsWith(ICON_NAME));
		if (iconFiles == null) {
			getLogger().severe("Failed to find icon file! Please report this on GitHub!");
			return null;
		}

		if (iconFiles.length == 0) {
			//copy default icon from jar resources
			String toName = ICON_NAME + ".svg";
			try {
				BMCopy.jarResourceToWebApp(api, getClassLoader(), "skull.svg", toName, false);
			} catch (IOException e) {
				getLogger().log(Level.SEVERE, "Failed to copy resources to BlueMap webapp!", e);
			}

			return Paths.get(api.getWebApp().getWebRoot().toString(), "assets", toName).toFile();
		}

		if (iconFiles.length > 1) {
			getLogger().warning("Multiple " + ICON_NAME + " files found! Using the first one...");
		}

		return iconFiles[0];
	}

	Vector2i findAnchor(@NotNull File iconFile) {
		if (getExtension(iconFile.getName()).equals("svg")) {
			try {
				String svg = Files.readString(iconFile.toPath());
				Matcher viewBoxMatcher = viewBoxPattern.matcher(svg);
				if (viewBoxMatcher.find()) {
					return new Vector2i(Integer.parseInt(viewBoxMatcher.group(1)) / 2, Integer.parseInt(viewBoxMatcher.group(2)) / 2);
				}

				Matcher widthMatcher = widthPattern.matcher(svg);
				Matcher heightMatcher = heightPattern.matcher(svg);
				if (widthMatcher.find() && heightMatcher.find()) {
					return new Vector2i(Integer.parseInt(widthMatcher.group(1)) / 2, Integer.parseInt(heightMatcher.group(1)) / 2);
				}
			} catch (IOException e) {
				getLogger().log(Level.SEVERE, "Failed to read svg file!", e);
			} catch (NumberFormatException e) {
				getLogger().log(Level.SEVERE, "Failed to parse svg file!", e);
			}
		} else {
			try {
				BufferedImage image = ImageIO.read(iconFile);
				return new Vector2i(image.getWidth() / 2, image.getHeight() / 2);
			} catch (IOException e) {
				getLogger().log(Level.SEVERE, "Failed to read image file!", e);
			}
		}

		getLogger().log(Level.SEVERE, "Failed to find anchor for " + iconFile.getName() + "!");
		return Vector2i.ZERO;
	}

	public String getExtension(String filename) {
		return filename.substring(filename.lastIndexOf(".") + 1);
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		Optional<BlueMapAPI> oApi = BlueMapAPI.getInstance();
		if (oApi.isEmpty()) return;
		BlueMapAPI api = oApi.get();

		Player player = event.getEntity();

		// If this player's visibility is disabled on the map, don't add the marker.
		if (!api.getWebApp().getPlayerVisibility(player.getUniqueId())) return;

		// If this player's game mode is disabled on the map, don't add the marker.
		if (config.hiddenGameModes.contains(player.getGameMode())) return;

		// Get BlueMapWorld for the location
		Location location = event.getEntity().getLocation();
		BlueMapWorld blueMapWorld = api.getWorld(location.getWorld()).orElse(null);
		if (blueMapWorld == null) return;

		// Create marker-template
		// (add 1.8 to y to place the marker at the head-position of the player, like BlueMap does with its player-markers)
		POIMarker.Builder markerBuilder = POIMarker.builder()
				.label(player.getName())
				.detail(formatPopup(player.getName(), location))
				.icon("assets/" + iconName, anchor)
				.position(location.getX(), location.getY() + 1.8, location.getZ());

		// Create an icon and marker for each map of this world
		for (BlueMapMap map : blueMapWorld.getMaps()) {
			// Get MarkerSet (or create new MarkerSet if none is found)
			MarkerSet markerSet = map.getMarkerSets().computeIfAbsent(Config.MARKER_SET_ID,
					id -> MarkerSet.builder()
							.label(config.markerSetName)
							.toggleable(config.toggleable)
							.defaultHidden(config.defaultHidden)
							.build()
			);

			String uuid = player.getUniqueId().toString();
			String key = config.keepAllMarkers ? (uuid + "@" + System.currentTimeMillis()) : uuid;

			// Add marker
			markerSet.put(key, markerBuilder.build());

			// Wait seconds and remove the marker
			Bukkit.getScheduler().runTaskLater(this,
					() -> markerSet.remove(key),
					config.expireTimeInMinutes * 20L * 60L);
		}
	}

	private String formatPopup(String playerName, Location location) {
		return "<div style='line-height: 1.2em;'>" +
					"<div style='position: relative;top: 0;left: .5em;margin: 0 .5em;font-size: .9em;'>" + playerName +  config.tooltipSuffix + "</div>" +
					"<div style='display: flex;justify-content: center;min-width: 9em'>" +
						"<div style='margin: 0 .5em;'>" +
							"<span style='color: var(--theme-fg-light);'>x:</span>" +
							"<span class='value'> " + location.getBlockX() + "</span>" +
						"</div>" +
						"<div style='margin: 0 .5em;'>" +
							"<span style='color: var(--theme-fg-light);'>y:</span>" +
							"<span class='value'> " + location.getBlockY() + "</span>" +
						"</div>" +
						"<div style='margin: 0 .5em;'>" +
							"<span style='color: var(--theme-fg-light);'>z:</span>" +
							"<span class='value'> " + location.getBlockZ() + "</span>" +
						"</div>" +
					"</div>" +
				"</div>";
	}
}
