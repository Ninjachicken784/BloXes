package com.bloxes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * All saved settings. Stored as JSON in the Minecraft config folder (bloxes.json).
 */
public final class BloxConfig {
	public enum Shape {
		CIRCLE, SQUARE;

		public Shape next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	// The selected area
	public boolean areaActive = false;
	public Shape shape = Shape.CIRCLE;
	public int centerX = 0;
	public int centerZ = 0;
	public int diameter = 256; // blocks across (a 256x256 square, or a circle 256 wide)
	public String dimension = ""; // the dimension the area was set in

	// Counting
	public boolean mineableOnly = true; // leaves out bedrock, barriers, fluids and other unbreakable blocks

	// Outline in the world
	public boolean showOutline = true;
	public int outlineHeight = 48; // how tall the wall is drawn around you, in blocks
	public int outlineColor = 0x55FFFF; // RGB

	// HUD
	public boolean showHud = true;
	public int hudTopBlocks = 0; // how many of the biggest block types to list on the HUD (0 = only the total)
	public int hudX = 4;
	public int hudY = 4;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static BloxConfig instance = new BloxConfig();

	public static BloxConfig get() {
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("bloxes.json");
	}

	public static void load() {
		Path path = file();
		if (Files.exists(path)) {
			try {
				BloxConfig loaded = GSON.fromJson(Files.readString(path), BloxConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				System.err.println("[bloxes] Could not read config, using defaults: " + e);
			}
		}
		instance.sanitize();
		save();
	}

	public static void save() {
		try {
			Files.writeString(file(), GSON.toJson(instance));
		} catch (IOException e) {
			System.err.println("[bloxes] Could not save config: " + e);
		}
	}

	private void sanitize() {
		if (shape == null) shape = Shape.CIRCLE;
		if (dimension == null) dimension = "";
		diameter = Math.max(16, Math.min(4096, diameter));
		outlineHeight = Math.max(8, Math.min(512, outlineHeight));
		hudTopBlocks = Math.max(0, Math.min(10, hudTopBlocks));
		outlineColor &= 0xFFFFFF;
		hudX = Math.max(0, hudX);
		hudY = Math.max(0, hudY);
	}
}
