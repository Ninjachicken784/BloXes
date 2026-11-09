package com.bloxes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

/**
 * Draws the edge of the selected area in the world: a see-through wall around you,
 * with solid rails at the top, at your feet and at the bottom of the wall.
 */
public final class OutlineRenderer {
	private static final float WALL_ALPHA = 0.16f;
	private static final float RAIL_ALPHA = 0.95f;
	private static final double RAIL_HALF_WIDTH = 0.07;
	private static final double SEGMENT_LENGTH = 3.0; // blocks per piece of a circle
	private static final double DRAW_DISTANCE = 160.0; // pieces of a circle further away than this are skipped

	private OutlineRenderer() { }

	/** Everything the drawing needs, copied for this frame (the drawing itself runs later, from the render queue). */
	private record Frame(BloxConfig.Shape shape, int centerX, int centerZ, int diameter,
			double camX, double camY, double camZ, double y0, double y1, double yFeet,
			int wallColor, int railColor) { }

	public static void render(LevelRenderContext context) {
		BloxConfig cfg = BloxConfig.get();
		if (!cfg.areaActive || !cfg.showOutline) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		if (!cfg.dimension.equals(mc.level.dimension().toString())) return;

		Vec3 camera = context.levelState().cameraRenderState.pos;

		double py = mc.player.getY();
		double levelBottom = mc.level.getMinY();
		double levelTop = levelBottom + mc.level.getHeight();
		double y0 = Math.max(levelBottom, py - cfg.outlineHeight / 2.0);
		double y1 = Math.min(levelTop, py + cfg.outlineHeight / 2.0);
		double yFeet = Math.floor(py) + 0.02;

		int rgb = cfg.outlineColor;
		float red = ((rgb >> 16) & 0xFF) / 255.0f;
		float green = ((rgb >> 8) & 0xFF) / 255.0f;
		float blue = (rgb & 0xFF) / 255.0f;

		Frame frame = new Frame(cfg.shape, cfg.centerX, cfg.centerZ, cfg.diameter,
				camera.x, camera.y, camera.z, y0, y1, yFeet,
				ARGB.colorFromFloat(WALL_ALPHA, red, green, blue),
				ARGB.colorFromFloat(RAIL_ALPHA, red, green, blue));

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(),
				(pose, buffer) -> draw(pose, buffer, frame));
		poseStack.popPose();
	}

	private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Frame f) {
		if (f.shape() == BloxConfig.Shape.SQUARE) {
			double minX = AreaScanner.squareMin(f.centerX(), f.diameter());
			double minZ = AreaScanner.squareMin(f.centerZ(), f.diameter());
			double maxX = minX + f.diameter();
			double maxZ = minZ + f.diameter();
			double[][] corners = { { minX, minZ }, { maxX, minZ }, { maxX, maxZ }, { minX, maxZ } };
			for (int i = 0; i < 4; i++) {
				double[] a = corners[i];
				double[] c = corners[(i + 1) % 4];
				piece(pose, buffer, f, a[0], a[1], c[0], c[1]);
			}
			return;
		}

		double radius = f.diameter() / 2.0;
		int segments = (int) Math.max(48, Math.min(1024, Math.ceil(2.0 * Math.PI * radius / SEGMENT_LENGTH)));
		double prevX = f.centerX() + radius;
		double prevZ = f.centerZ();
		for (int i = 1; i <= segments; i++) {
			double angle = 2.0 * Math.PI * i / segments;
			double x = f.centerX() + Math.cos(angle) * radius;
			double z = f.centerZ() + Math.sin(angle) * radius;

			double midX = (prevX + x) / 2.0 - f.camX();
			double midZ = (prevZ + z) / 2.0 - f.camZ();
			if (midX * midX + midZ * midZ <= DRAW_DISTANCE * DRAW_DISTANCE) {
				piece(pose, buffer, f, prevX, prevZ, x, z);
			}
			prevX = x;
			prevZ = z;
		}
	}

	/** One straight piece of the edge: the wall plus its three rails. */
	private static void piece(PoseStack.Pose pose, VertexConsumer buffer, Frame f,
			double x1, double z1, double x2, double z2) {
		wall(pose, buffer, f, x1, z1, x2, z2);
		rail(pose, buffer, f, x1, z1, x2, z2, f.y0());
		rail(pose, buffer, f, x1, z1, x2, z2, f.y1());
		if (f.yFeet() > f.y0() && f.yFeet() < f.y1()) {
			rail(pose, buffer, f, x1, z1, x2, z2, f.yFeet());
		}
	}

	/** A vertical wall between two points. */
	private static void wall(PoseStack.Pose pose, VertexConsumer buffer, Frame f,
			double x1, double z1, double x2, double z2) {
		int color = f.wallColor();
		double y0 = f.y0();
		double y1 = f.y1();
		// Both windings so the wall is visible from either side.
		quad(pose, buffer, f, color, x1, y0, z1, x2, y0, z2, x2, y1, z2, x1, y1, z1);
		quad(pose, buffer, f, color, x1, y1, z1, x2, y1, z2, x2, y0, z2, x1, y0, z1);
	}

	/** A thin flat strip along the edge at one height. */
	private static void rail(PoseStack.Pose pose, VertexConsumer buffer, Frame f,
			double x1, double z1, double x2, double z2, double y) {
		double dx = x2 - x1;
		double dz = z2 - z1;
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-6) return;
		double px = -dz / length * RAIL_HALF_WIDTH; // sideways in the ground plane
		double pz = dx / length * RAIL_HALF_WIDTH;
		int color = f.railColor();

		quad(pose, buffer, f, color, x1 + px, y, z1 + pz, x2 + px, y, z2 + pz, x2 - px, y, z2 - pz, x1 - px, y, z1 - pz);
		quad(pose, buffer, f, color, x1 - px, y, z1 - pz, x2 - px, y, z2 - pz, x2 + px, y, z2 + pz, x1 + px, y, z1 + pz);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Frame f, int color,
			double ax, double ay, double az, double bx, double by, double bz,
			double cx, double cy, double cz, double dx, double dy, double dz) {
		vertex(pose, buffer, f, color, ax, ay, az);
		vertex(pose, buffer, f, color, bx, by, bz);
		vertex(pose, buffer, f, color, cx, cy, cz);
		vertex(pose, buffer, f, color, dx, dy, dz);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Frame f, int color, double x, double y, double z) {
		// Positions are made relative to the camera in double precision first, so far from the origin nothing jitters.
		buffer.addVertex(pose, (float) (x - f.camX()), (float) (y - f.camY()), (float) (z - f.camZ())).setColor(color);
	}
}
