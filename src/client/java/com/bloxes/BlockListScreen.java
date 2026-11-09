package com.bloxes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The block menu: every block type in the area with its count, biggest first.
 * Type in the box to filter by name, scroll with the mouse wheel.
 */
public class BlockListScreen extends Screen {
	private static final int ROW_H = 12;
	private static final int LIST_TOP = 56;
	private static final int BOTTOM_MARGIN = 36;
	private static final int LIST_W = 300;

	private final Screen parent;
	private final BloxConfig cfg = BloxConfig.get();
	private EditBox filterBox;
	private Button mineableButton;
	private int scroll; // first visible row

	public BlockListScreen(Screen parent) {
		super(Component.literal("Blocks in the area"));
		this.parent = parent;
	}

	private Component mineableText() {
		return Component.literal("Mineable only: " + (cfg.mineableOnly ? "ON" : "OFF"));
	}

	@Override
	protected void init() {
		int left = this.width / 2 - LIST_W / 2;

		this.filterBox = this.addRenderableWidget(new EditBox(this.font, left, 28, 140, 20, Component.literal("Filter")));
		this.filterBox.setMaxLength(40);
		this.filterBox.setHint(Component.literal("Filter by name"));
		this.filterBox.setResponder(value -> this.scroll = 0);

		this.mineableButton = this.addRenderableWidget(Button.builder(mineableText(), b -> {
			cfg.mineableOnly = !cfg.mineableOnly;
			b.setMessage(mineableText());
		}).pos(left + LIST_W - 150, 28).size(150, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
				.pos(this.width / 2 - 100, this.height - 28).size(200, 20).build());
	}

	private List<AreaScanner.Entry> visibleEntries() {
		String filter = this.filterBox == null ? "" : this.filterBox.getValue().trim().toLowerCase(Locale.ROOT);
		List<AreaScanner.Entry> all = AreaScanner.INSTANCE.entries(cfg);
		if (filter.isEmpty()) return all;
		List<AreaScanner.Entry> result = new ArrayList<>();
		for (AreaScanner.Entry entry : all) {
			if (entry.name().toLowerCase(Locale.ROOT).contains(filter)) {
				result.add(entry);
			}
		}
		return result;
	}

	private int rowsOnScreen() {
		return Math.max(1, (this.height - LIST_TOP - BOTTOM_MARGIN) / ROW_H);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.extractBackground(graphics, mouseX, mouseY, delta);

		AreaScanner scanner = AreaScanner.INSTANCE;
		int left = this.width / 2 - LIST_W / 2;
		long total = scanner.total(cfg);

		graphics.centeredText(this.font, "Blocks in the area", this.width / 2, 10, 0xFFFFFFFF);
		graphics.text(this.font, "Total: " + Fmt.full(total) + " (" + Fmt.count(total) + ")"
				+ (scanner.isScanning() ? "  -  scanned " + Fmt.percent(scanner.scannedChunks() / (double) Math.max(1, scanner.totalChunks())) : ""),
				left, 51, 0xFFAAAAAA);

		List<AreaScanner.Entry> rows = visibleEntries();
		int visible = rowsOnScreen();
		this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, rows.size() - visible)));

		int maxCount = rows.isEmpty() ? 1 : Math.max(1, rows.get(0).count());
		int y = LIST_TOP + 8;
		for (int i = this.scroll; i < Math.min(rows.size(), this.scroll + visible); i++) {
			AreaScanner.Entry entry = rows.get(i);

			// a bar behind the row, as long as the count compared with the biggest one
			int barW = (int) ((long) LIST_W * entry.count() / maxCount);
			graphics.fill(left, y - 1, left + Math.max(1, barW), y + ROW_H - 2, 0x44336688);

			graphics.text(this.font, entry.name(), left + 3, y, 0xFFFFFFFF);

			String share = total > 0 ? Fmt.percent(entry.count() / (double) total) : "";
			String count = Fmt.count(entry.count());
			int countX = left + LIST_W - 3 - this.font.width(count);
			graphics.text(this.font, count, countX, y, 0xFFFFFFFF);
			graphics.text(this.font, share, countX - 8 - this.font.width(share), y, 0xFFAAAAAA);
			y += ROW_H;
		}

		if (rows.isEmpty()) {
			graphics.centeredText(this.font, cfg.areaActive ? "No blocks counted yet" : "No area selected - use /bloxes",
					this.width / 2, LIST_TOP + 20, 0xFFAAAAAA);
		}

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			this.scroll -= (int) Math.signum(scrollY) * 3;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		BloxConfig.save();
		this.minecraft.gui.setScreen(this.parent);
	}
}
