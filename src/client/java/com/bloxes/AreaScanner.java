package com.bloxes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Counts the blocks inside the selected area.
 *
 * The client only knows the chunks it has loaded, so each chunk is counted when it loads and the result is kept.
 * Chunks close to you are counted again every few ticks, so the numbers follow you while you mine.
 */
public final class AreaScanner {
	public static final AreaScanner INSTANCE = new AreaScanner();

	/** One block type and how many of it are in the area. */
	public record Entry(Block block, String name, int count) { }

	private static final long BUDGET_NANOS = 3_000_000L; // time per tick for first-time scans
	private static final int REFRESH_RADIUS = 3; // chunks around you that are counted again while you mine
	private static final int REFRESH_EVERY_TICKS = 5;

	/** Chunk key -> block counts for the part of that chunk that is inside the area. */
	private final Long2ObjectOpenHashMap<Object2IntOpenHashMap<Block>> chunks = new Long2ObjectOpenHashMap<>();
	private final Map<Block, Boolean> mineableCache = new HashMap<>();

	private long[] chunkKeys = new long[0]; // every chunk that has at least one column inside the area
	private String signature = "";
	private ClientLevel lastLevel;
	private int version; // goes up whenever the counts change
	private int scanCursor;
	private int tickCounter;
	private int refreshCursor;

	// the summed counts, rebuilt when something changed
	private int aggVersion = -1;
	private boolean aggMineable;
	private long aggTime;
	private List<Entry> entries = List.of();
	private long total;

	private AreaScanner() { }

	// ------------------------------------------------------------------ geometry

	/** Is this block column inside the selected area? */
	public static boolean contains(BloxConfig cfg, int x, int z) {
		if (cfg.shape == BloxConfig.Shape.SQUARE) {
			int minX = squareMin(cfg.centerX, cfg.diameter);
			int minZ = squareMin(cfg.centerZ, cfg.diameter);
			return x >= minX && x < minX + cfg.diameter && z >= minZ && z < minZ + cfg.diameter;
		}
		double r = cfg.diameter / 2.0;
		double dx = x + 0.5 - cfg.centerX;
		double dz = z + 0.5 - cfg.centerZ;
		return dx * dx + dz * dz <= r * r;
	}

	/** Lowest block coordinate of a square area. */
	public static int squareMin(int center, int diameter) {
		return center - diameter / 2;
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	// ------------------------------------------------------------------ results

	public boolean isScanning() {
		return chunks.size() < chunkKeys.length;
	}

	public int scannedChunks() {
		return Math.min(chunks.size(), chunkKeys.length);
	}

	public int totalChunks() {
		return chunkKeys.length;
	}

	/** Total number of blocks (without air) inside the area. */
	public long total(BloxConfig cfg) {
		refreshAggregate(cfg.mineableOnly);
		return total;
	}

	/** Block types in the area, biggest first. */
	public List<Entry> entries(BloxConfig cfg) {
		refreshAggregate(cfg.mineableOnly);
		return entries;
	}

	/** Forget everything and count again. */
	public void reset() {
		chunks.clear();
		scanCursor = 0;
		version++;
	}

	// ------------------------------------------------------------------ ticking

	public void tick(Minecraft mc) {
		BloxConfig cfg = BloxConfig.get();
		ClientLevel level = mc.level;

		if (level != lastLevel) { // joined another world: nothing we counted applies any more
			lastLevel = level;
			chunks.clear();
			mineableCache.clear();
			signature = "";
			chunkKeys = new long[0];
			version++;
		}
		if (!cfg.areaActive || level == null || mc.player == null) return;
		if (!cfg.dimension.equals(level.dimension().toString())) return; // area belongs to another dimension

		String sig = cfg.shape + ":" + cfg.centerX + ":" + cfg.centerZ + ":" + cfg.diameter;
		if (!sig.equals(signature)) {
			signature = sig;
			rebuild(cfg);
		}

		// First-time counts of chunks that are loaded now.
		int n = chunkKeys.length;
		if (n > 0 && chunks.size() < n) {
			long start = System.nanoTime();
			int checked = 0;
			while (checked < n && System.nanoTime() - start < BUDGET_NANOS) {
				if (scanCursor >= n) scanCursor = 0;
				long k = chunkKeys[scanCursor++];
				checked++;
				if (!chunks.containsKey(k)) {
					scan(level, cfg, k);
				}
			}
		}

		// Count the chunks around you again, so mining shows up.
		tickCounter++;
		if (tickCounter % REFRESH_EVERY_TICKS == 0) {
			refreshNear(level, cfg, mc);
		}
	}

	private void rebuild(BloxConfig cfg) {
		chunks.clear();
		scanCursor = 0;
		int half = cfg.diameter / 2 + 1;
		int minCx = (cfg.centerX - half) >> 4;
		int maxCx = (cfg.centerX + half) >> 4;
		int minCz = (cfg.centerZ - half) >> 4;
		int maxCz = (cfg.centerZ + half) >> 4;

		List<Long> keys = new ArrayList<>();
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				if (touches(cfg, cx, cz)) {
					keys.add(key(cx, cz));
				}
			}
		}
		chunkKeys = new long[keys.size()];
		for (int i = 0; i < chunkKeys.length; i++) {
			chunkKeys[i] = keys.get(i);
		}
		version++;
	}

	private static boolean touches(BloxConfig cfg, int cx, int cz) {
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				if (contains(cfg, (cx << 4) + lx, (cz << 4) + lz)) return true;
			}
		}
		return false;
	}

	private boolean scan(ClientLevel level, BloxConfig cfg, long k) {
		int cx = (int) (k >> 32);
		int cz = (int) k;
		LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
		if (chunk == null) return false; // not loaded
		chunks.put(k, countChunk(cfg, chunk, cx, cz));
		version++;
		return true;
	}

	private void refreshNear(ClientLevel level, BloxConfig cfg, Minecraft mc) {
		int pcx = mc.player.getBlockX() >> 4;
		int pcz = mc.player.getBlockZ() >> 4;
		List<Long> near = new ArrayList<>();
		for (int dx = -REFRESH_RADIUS; dx <= REFRESH_RADIUS; dx++) {
			for (int dz = -REFRESH_RADIUS; dz <= REFRESH_RADIUS; dz++) {
				long k = key(pcx + dx, pcz + dz);
				if (chunks.containsKey(k)) near.add(k);
			}
		}
		if (near.isEmpty()) return;
		refreshCursor = (refreshCursor + 1) % near.size();
		scan(level, cfg, near.get(refreshCursor));
	}

	private static Object2IntOpenHashMap<Block> countChunk(BloxConfig cfg, LevelChunk chunk, int cx, int cz) {
		boolean[] inside = new boolean[256];
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				inside[(lz << 4) | lx] = contains(cfg, (cx << 4) + lx, (cz << 4) + lz);
			}
		}

		Object2IntOpenHashMap<Block> counts = new Object2IntOpenHashMap<>();
		LevelChunkSection[] sections = chunk.getSections();
		for (LevelChunkSection section : sections) {
			if (section == null || section.hasOnlyAir()) continue;
			for (int ly = 0; ly < 16; ly++) {
				for (int lz = 0; lz < 16; lz++) {
					for (int lx = 0; lx < 16; lx++) {
						if (!inside[(lz << 4) | lx]) continue;
						BlockState state = section.getBlockState(lx, ly, lz);
						if (state.isAir()) continue;
						counts.addTo(state.getBlock(), 1);
					}
				}
			}
		}
		return counts;
	}

	// ------------------------------------------------------------------ summing

	private void refreshAggregate(boolean mineableOnly) {
		if (aggVersion == version && aggMineable == mineableOnly) return;
		long now = System.currentTimeMillis();
		if (aggMineable == mineableOnly && now - aggTime < 200) return; // don't rebuild every frame while scanning

		Object2IntOpenHashMap<Block> sum = new Object2IntOpenHashMap<>();
		for (Object2IntOpenHashMap<Block> chunkCounts : chunks.values()) {
			for (Object2IntMap.Entry<Block> e : chunkCounts.object2IntEntrySet()) {
				sum.addTo(e.getKey(), e.getIntValue());
			}
		}

		List<Entry> list = new ArrayList<>();
		long t = 0;
		for (Object2IntMap.Entry<Block> e : sum.object2IntEntrySet()) {
			Block block = e.getKey();
			if (mineableOnly && !isMineable(block)) continue;
			int count = e.getIntValue();
			list.add(new Entry(block, block.getName().getString(), count));
			t += count;
		}
		list.sort((a, b) -> Integer.compare(b.count(), a.count()));

		entries = list;
		total = t;
		aggVersion = version;
		aggMineable = mineableOnly;
		aggTime = now;
	}

	/** Bedrock, barriers, portals frames, fluids and the like cannot be mined. */
	private boolean isMineable(Block block) {
		Boolean cached = mineableCache.get(block);
		if (cached != null) return cached;
		boolean result;
		if (block instanceof LiquidBlock) {
			result = false;
		} else if (lastLevel == null) {
			return true;
		} else {
			result = block.defaultBlockState().getDestroySpeed(lastLevel, BlockPos.ZERO) >= 0.0F;
		}
		mineableCache.put(block, result);
		return result;
	}
}
