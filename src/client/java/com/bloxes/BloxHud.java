package com.bloxes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * The small block-count box on the screen: the area, the total and (optionally) the biggest block types.
 */
public final class BloxHud {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int PAD = 3;
	private static final int LINE_GAP = 2;

	private BloxHud() { }

	public static void register() {
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.SUBTITLES,
				Identifier.fromNamespaceAndPath("bloxes", "hud"),
				BloxHud::render);
	}

	private record Line(String text, int color) { }

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		BloxConfig cfg = BloxConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.areaActive || !cfg.showHud || mc.player == null || mc.level == null) return;
		if (!cfg.dimension.equals(mc.level.dimension().toString())) return;

		AreaScanner scanner = AreaScanner.INSTANCE;
		List<Line> lines = new ArrayList<>();

		String shape = cfg.shape == BloxConfig.Shape.CIRCLE ? "Circle" : "Square";
		lines.add(new Line(shape + " " + cfg.diameter + "x" + cfg.diameter + (cfg.mineableOnly ? " (mineable only)" : ""), GRAY));
		lines.add(new Line("Total blocks: " + Fmt.count(scanner.total(cfg)), WHITE));

		if (scanner.isScanning()) {
			int done = scanner.scannedChunks();
			int all = Math.max(1, scanner.totalChunks());
			lines.add(new Line("Scanned: " + Fmt.percent(done / (double) all) + " of the area", GRAY));
		}

		int shown = 0;
		for (AreaScanner.Entry entry : scanner.entries(cfg)) {
			if (shown >= cfg.hudTopBlocks) break;
			lines.add(new Line(entry.name() + ": " + Fmt.count(entry.count()), WHITE));
			shown++;
		}

		Font font = mc.font;
		int width = 0;
		for (Line line : lines) {
			width = Math.max(width, font.width(line.text()));
		}
		int height = lines.size() * (font.lineHeight + LINE_GAP) - LINE_GAP;

		int x = cfg.hudX;
		int y = cfg.hudY;
		graphics.fill(x - PAD, y - PAD, x + width + PAD, y + height + PAD, 0x66000000);
		int cursorY = y;
		for (Line line : lines) {
			graphics.text(font, line.text(), x, cursorY, line.color());
			cursorY += font.lineHeight + LINE_GAP;
		}
	}
}
