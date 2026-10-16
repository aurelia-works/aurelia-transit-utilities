package com.aureliatransit.utilities.client.perf;

import com.aureliatransit.utilities.mixin.mtr.OptimizedModelWrapperAccess;
import com.aureliatransit.utilities.mixin.mtr.RailSchemaAccess;
import com.aureliatransit.utilities.perf.Perf;
import com.aureliatransit.utilities.perf.PerfSettings;
import com.aureliatransit.utilities.perf.RailLod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.holder.LightType;
import org.mtr.mapping.holder.LightmapTextureManager;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.render.MainRenderer;
import org.mtr.mod.render.QueuedRenderLayer;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.RailResource;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Rail LOD ({@link RailLod}) inside MTR's per-piece 3D rail callback ({@code RenderRails.lambda$renderRailStandard$16}).
 * Far pieces become flat strips drawn like MTR's own 2D rails (same texture, height and light), which MTR batches
 * with all other 2D rail quads instead of issuing one draw call per piece. Render thread only.
 */
public final class RailLodClient {

	/** MTR's {@code RenderRails.RAIL_TEXTURE}, height and half width of a 2D train rail. */
	private static final Identifier RAIL_TEXTURE = new Identifier("textures/block/rail.png");
	private static final double Y_OFFSET = 0.065625;
	private static final double HALF_WIDTH = 1;
	private static final double EPSILON = 0.0031250000465661287;

	/** Model height (blocks) per uploaded model, measured from the OBJ bounds when MTR builds it. */
	private static final Map<OptimizedModel, Float> HEIGHTS = new WeakHashMap<>();
	private static Position railStart;

	private RailLodClient() {
	}

	public static void recordHeight(OptimizedModel model, float height) {
		if (model != null && Float.isFinite(height)) {
			HEIGHTS.put(model, height);
		}
	}

	/** Before MTR walks one rail for one 3D style. */
	public static void beginRail(Rail rail) {
		railStart = ((RailSchemaAccess) (Object) rail).atu$position1();
	}

	/**
	 * One 3D rail piece from (x1, z1, y1) to (x3, z3, y2). Returns true when ATU drew it (or skipped it on purpose) and
	 * MTR's own call must not run.
	 */
	public static boolean renderPiece(ClientWorld world, RailResource resource, boolean flip, boolean[] renderType, BlockPos blockPos, double x1, double z1, double x3, double z3, double y1, double y2) {
		final PerfSettings settings = Perf.settings();
		final boolean shadowPass = settings.cheapShadows && ShadowPass.active();
		if (settings.railSimpleChunks <= 0 && settings.railFarChunks <= 0 && !shadowPass) {
			return false;
		}
		final Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		final RailLod.Tier tier = RailLod.tier(Math.hypot(x1 - camera.x, z1 - camera.z), settings.railSimpleChunks, settings.railFarChunks, shadowPass);
		if (tier == RailLod.Tier.FULL) {
			return false;
		}
		final float height = height(resource);
		if (Float.isNaN(height)) {
			// Not loaded yet: MTR draws nothing for it either, and it may turn out to be a catenary or a wall.
			return false;
		}
		final boolean tall = RailLod.isTall(height);
		if (tier == RailLod.Tier.SIMPLE) {
			if (tall) {
				return false;
			}
			strip(world, blockPos, x1, z1, x3, z3, y1, y2);
			return true;
		}
		final double interval = resource.getRepeatInterval();
		final int step = RailLod.farStep(interval, tall);
		if (step > 1) {
			final double fromStart = railStart == null ? 0 : Math.hypot(x1 - railStart.getX(), z1 - railStart.getZ());
			if (!RailLod.drawsFarPiece(RailLod.pieceIndex(fromStart, interval), step)) {
				return true;
			}
		}
		// This piece stands for the next `step` pieces: extend it along its own direction.
		final double endX = x1 + (x3 - x1) * step;
		final double endZ = z1 + (z3 - z1) * step;
		final double endY = y1 + (y2 - y1) * step;
		if (tall) {
			if (step == 1) {
				return false;
			}
			stretchedModel(world, resource, flip, renderType, blockPos, x1, z1, endX, endZ, y1, endY, step);
		} else {
			strip(world, blockPos, x1, z1, endX, endZ, y1, endY);
		}
		return true;
	}

	private static float height(RailResource resource) {
		final OptimizedModelWrapper wrapper = resource.getOptimizedModel();
		if (wrapper == null) {
			return Float.NaN;
		}
		final OptimizedModel model = ((OptimizedModelWrapperAccess) (Object) wrapper).atu$optimizedModel();
		final Float height = model == null ? null : HEIGHTS.get(model);
		return height == null ? Float.NaN : height;
	}

	/** Same quads as MTR's 2D rail ({@code RenderRails.renderRailStandard}), both faces. */
	private static void strip(ClientWorld world, BlockPos blockPos, double x1, double z1, double x3, double z3, double y1, double y2) {
		final double dx = x3 - x1;
		final double dz = z3 - z1;
		final double length = Math.hypot(dx, dz);
		if (length < 1E-6) {
			return;
		}
		final double px = -dz / length * HALF_WIDTH;
		final double pz = dx / length * HALF_WIDTH;
		final double ax = x1 - px, az = z1 - pz;
		final double bx = x1 + px, bz = z1 + pz;
		final double cx = x3 + px, cz = z3 + pz;
		final double ex = x3 - px, ez = z3 - pz;
		final double yStart = y1 + Y_OFFSET;
		final double yEnd = y2 + Y_OFFSET;
		final float textureOffset = (((int) (x1 + z1)) % 4) * 0.25F;
		final float v1 = 0.1875F + textureOffset;
		final float v2 = 0.3125F + textureOffset;
		final int light = light(world, blockPos);
		MainRenderer.scheduleRender(RAIL_TEXTURE, false, QueuedRenderLayer.EXTERIOR, (graphicsHolder, offset) -> {
			IDrawing.drawTexture(graphicsHolder, ax, yStart, az, bx, yStart + EPSILON, bz, cx, yEnd, cz, ex, yEnd + EPSILON, ez, offset, 0, v1, 1, v2, Direction.UP, -1, light);
			IDrawing.drawTexture(graphicsHolder, bx, yStart + EPSILON, bz, ax, yStart, az, ex, yEnd + EPSILON, ez, cx, yEnd, cz, offset, 0, v1, 1, v2, Direction.UP, -1, light);
		});
	}

	/** MTR's own piece transform ({@code lambda$renderRailStandard$16}) over the longer span, stretched along the track. */
	private static void stretchedModel(ClientWorld world, RailResource resource, boolean flip, boolean[] renderType, BlockPos blockPos, double x1, double z1, double x3, double z3, double y1, double y2, int step) {
		final double dx = x3 - x1;
		final double dz = z3 - z1;
		final double yaw = Math.atan2(dz, dx);
		final double pitch = Math.atan2(y2 - y1, Math.hypot(dx, dz));
		final StoredMatrixTransformations transformations = new StoredMatrixTransformations((x1 + x3) / 2, (y1 + y2) / 2 + resource.getModelYOffset(), (z1 + z3) / 2);
		transformations.add(graphicsHolder -> {
			graphicsHolder.rotateYRadians((float) (Math.PI / 2 - yaw + (flip ? Math.PI : 0)));
			graphicsHolder.rotateXRadians((float) (Math.PI - pitch * (flip ? -1 : 1)));
			graphicsHolder.rotateZDegrees((float) ((x1 * z1) % 10) / 100);
			graphicsHolder.scale(1, 1, step);
		});
		resource.render(transformations, light(world, blockPos));
		renderType[1] = true;
	}

	private static int light(ClientWorld world, BlockPos blockPos) {
		return LightmapTextureManager.pack(world.getLightLevel(LightType.getBlockMapped(), blockPos), world.getLightLevel(LightType.getSkyMapped(), blockPos));
	}
}
