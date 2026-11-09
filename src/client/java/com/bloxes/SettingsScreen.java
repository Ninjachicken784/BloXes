package com.bloxes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings menu, opened with /bloxes.
 * Left column: the area. Right column: counting, outline, HUD and the block list.
 */
public class SettingsScreen extends Screen {
	private static final int COLUMN_W = 150;
	private static final int ROW = 24;
	private static final int TOP = 40;

	private final BloxConfig cfg = BloxConfig.get();
	private EditBox sizeBox;
	private Button centerLabel;

	public SettingsScreen() {
		super(Component.literal("BloXes"));
	}

	private static Component onOff(String label, boolean value) {
		return Component.literal(label + ": " + (value ? "ON" : "OFF"));
	}

	private static Component text(String value) {
		return Component.literal(value);
	}

	/** Puts the area on the player's position in the current dimension. */
	public static void centerOnPlayer() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		BloxConfig cfg = BloxConfig.get();
		cfg.centerX = mc.player.getBlockX();
		cfg.centerZ = mc.player.getBlockZ();
		cfg.dimension = mc.level.dimension().toString();
		cfg.areaActive = true;
		BloxConfig.save();
	}

	@Override
	protected void init() {
		int left = this.width / 2 - COLUMN_W - 5;
		int right = this.width / 2 + 5;

		// ---- left column: the area
		this.addRenderableWidget(Button.builder(onOff("Area", cfg.areaActive), b -> {
			if (!cfg.areaActive && cfg.dimension.isEmpty()) {
				centerOnPlayer(); // first time: start on the spot you are standing
			} else {
				cfg.areaActive = !cfg.areaActive;
			}
			b.setMessage(onOff("Area", cfg.areaActive));
			refreshCenterLabel();
		}).pos(left, TOP).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(text("Shape: " + cfg.shape), b -> {
			cfg.shape = cfg.shape.next();
			b.setMessage(text("Shape: " + cfg.shape));
		}).pos(left, TOP + ROW).size(COLUMN_W, 20).build());

		// Size: [-] box [+]
		this.sizeBox = this.addRenderableWidget(new EditBox(this.font, left + 24, TOP + ROW * 2, COLUMN_W - 48, 20, text("Size")));
		this.sizeBox.setMaxLength(4);
		this.sizeBox.setFilter(value -> value.matches("\\d*"));
		this.sizeBox.setValue(Integer.toString(cfg.diameter));
		this.sizeBox.setResponder(value -> {
			if (!value.isEmpty()) {
				try {
					cfg.diameter = Math.max(16, Math.min(4096, Integer.parseInt(value)));
				} catch (NumberFormatException ignored) {
					// not a number yet
				}
			}
		});
		this.addRenderableWidget(Button.builder(text("-"), b -> changeSize(-16))
				.pos(left, TOP + ROW * 2).size(20, 20).build());
		this.addRenderableWidget(Button.builder(text("+"), b -> changeSize(16))
				.pos(left + COLUMN_W - 20, TOP + ROW * 2).size(20, 20).build());

		this.addRenderableWidget(Button.builder(text("Set center to me"), b -> {
			centerOnPlayer();
			refreshCenterLabel();
		}).pos(left, TOP + ROW * 3).size(COLUMN_W, 20).build());

		this.centerLabel = this.addRenderableWidget(Button.builder(centerText(), b -> { })
				.pos(left, TOP + ROW * 4).size(COLUMN_W, 20).build());
		this.centerLabel.active = false;

		// ---- right column: counting, outline, HUD
		this.addRenderableWidget(Button.builder(onOff("Mineable only", cfg.mineableOnly), b -> {
			cfg.mineableOnly = !cfg.mineableOnly;
			b.setMessage(onOff("Mineable only", cfg.mineableOnly));
		}).pos(right, TOP).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(onOff("Outline", cfg.showOutline), b -> {
			cfg.showOutline = !cfg.showOutline;
			b.setMessage(onOff("Outline", cfg.showOutline));
		}).pos(right, TOP + ROW).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(onOff("HUD", cfg.showHud), b -> {
			cfg.showHud = !cfg.showHud;
			b.setMessage(onOff("HUD", cfg.showHud));
		}).pos(right, TOP + ROW * 2).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(topText(), b -> {
			cfg.hudTopBlocks = cfg.hudTopBlocks == 0 ? 3 : cfg.hudTopBlocks == 3 ? 5 : cfg.hudTopBlocks == 5 ? 10 : 0;
			b.setMessage(topText());
		}).pos(right, TOP + ROW * 3).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(text("Block list"), b -> {
			BloxConfig.save();
			this.minecraft.setScreen(new BlockListScreen(this));
		}).pos(right, TOP + ROW * 4).size(COLUMN_W, 20).build());

		this.addRenderableWidget(Button.builder(text("Count again"), b -> AreaScanner.INSTANCE.reset())
				.pos(right, TOP + ROW * 5).size(COLUMN_W, 20).build());

		// ---- bottom
		this.addRenderableWidget(Button.builder(text("Done"), b -> this.onClose())
				.pos(this.width / 2 - 100, this.height - 28).size(200, 20).build());
	}

	private void changeSize(int delta) {
		cfg.diameter = Math.max(16, Math.min(4096, cfg.diameter + delta));
		this.sizeBox.setValue(Integer.toString(cfg.diameter));
	}

	private Component centerText() {
		return text(cfg.dimension.isEmpty() ? "Center: not set" : "Center: " + cfg.centerX + ", " + cfg.centerZ);
	}

	private void refreshCenterLabel() {
		this.centerLabel.setMessage(centerText());
	}

	private Component topText() {
		return text("HUD list: " + (cfg.hudTopBlocks == 0 ? "total only" : "top " + cfg.hudTopBlocks));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.extractBackground(graphics, mouseX, mouseY, delta);

		graphics.centeredText(this.font, "BloXes", this.width / 2, 12, 0xFFFFFFFF);
		graphics.text(this.font, "Area (blocks across)", this.width / 2 - COLUMN_W - 5, 28, 0xFFAAAAAA);
		graphics.text(this.font, "Counting and display", this.width / 2 + 5, 28, 0xFFAAAAAA);

		AreaScanner scanner = AreaScanner.INSTANCE;
		if (cfg.areaActive) {
			String summary = "Total: " + Fmt.count(scanner.total(cfg)) + " blocks  -  scanned "
					+ scanner.scannedChunks() + "/" + scanner.totalChunks() + " chunks";
			graphics.centeredText(this.font, summary, this.width / 2, TOP + ROW * 6 + 8, 0xFFFFFFFF);
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		BloxConfig.save();
		super.onClose();
	}
}
