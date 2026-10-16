package com.aureliatransit.utilities.client.perf;

import com.aureliatransit.utilities.perf.Perf;
import org.mtr.mapping.holder.BlockEntity;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.Vector3d;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockPSDAPGDoorBase;
import org.mtr.mod.block.PlatformHelper;
import org.mtr.mod.render.PositionAndRotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MTR 4.0.5's {@code RenderVehicleHelper.canOpenDoors}, which runs every frame for every doorway of every stopped car:
 * it looks up every block in a box around the doorway (often over a hundred) for a platform or an unlocked platform
 * screen door, and hands the door value to each such door it finds. In a test scene with one parked train that was a
 * fifth of the render thread.
 * <p>
 * Here the box is looked through once and remembered: which blocks in it are platform blocks, and where the platform
 * screen and APG doors are. The memory is exact while no client block changes ({@link BlockChanges}); after any change
 * the box is looked through again. The doors themselves are read every call (their unlocked state and block entity),
 * in MTR's order, so the result and the door values handed out are MTR's. Render thread.
 */
public final class DoorChecks {

	/** Cleared this often anyway (a change {@link BlockChanges} missed stays at most this long). */
	private static final long MAX_AGE_NANOS = 2_000_000_000L;
	private static final int MAX_ENTRIES = 8192;

	private static final Map<Key, Scan> SCANS = new HashMap<>();
	private static Object scansWorld;
	private static long scansVersion = -1;
	private static long scansSince;

	private static final int COMPARE_EVERY = 256;

	private static long hits;
	private static long misses;
	private static long compared;
	private static long mismatches;
	private static int calls;

	private DoorChecks() {
	}

	/** One doorway of one car at one position: its box (from MTR's own transform) is fixed by these. */
	private record Key(Box doorway, double x, double y, double z, double yaw, double pitch) {

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && key.doorway == doorway && Double.compare(key.x, x) == 0 && Double.compare(key.y, y) == 0 && Double.compare(key.z, z) == 0 && Double.compare(key.yaw, yaw) == 0 && Double.compare(key.pitch, pitch) == 0;
		}

		@Override
		public int hashCode() {
			int hash = System.identityHashCode(doorway);
			hash = hash * 31 + Double.hashCode(x);
			hash = hash * 31 + Double.hashCode(y);
			hash = hash * 31 + Double.hashCode(z);
			hash = hash * 31 + Double.hashCode(yaw);
			return hash * 31 + Double.hashCode(pitch);
		}
	}

	/** What the box holds: a platform block anywhere, and the door blocks in MTR's search order. */
	private record Scan(boolean platform, List<BlockPos> doors) {
	}

	public static boolean canOpenDoors(ClientWorld clientWorld, Box doorway, PositionAndRotation positionAndRotation, double doorValue) {
		final long now = System.nanoTime();
		final long version = BlockChanges.version();
		if (version != scansVersion || clientWorld.data != scansWorld || now - scansSince > MAX_AGE_NANOS || SCANS.size() > MAX_ENTRIES) {
			SCANS.clear();
			scansVersion = version;
			scansWorld = clientWorld.data;
			scansSince = now;
		}
		final Key key = new Key(doorway, positionAndRotation.position.x, positionAndRotation.position.y, positionAndRotation.position.z, positionAndRotation.yaw, positionAndRotation.pitch);
		Scan scan = SCANS.get(key);
		if (scan == null) {
			scan = scan(clientWorld, doorway, positionAndRotation);
			SCANS.put(key, scan);
			misses++;
		} else {
			hits++;
		}
		boolean canOpenDoors = scan.platform;
		for (final BlockPos checkPos : scan.doors) {
			final BlockState blockState = clientWorld.getBlockState(checkPos);
			if (blockState.getBlock().data instanceof BlockPSDAPGDoorBase && blockState.get(new Property<>(BlockPSDAPGDoorBase.UNLOCKED.data))) {
				canOpenDoors = true;
				final BlockEntity blockEntity = clientWorld.getBlockEntity(checkPos);
				if (blockEntity != null && blockEntity.data instanceof BlockPSDAPGDoorBase.BlockEntityBase) {
					((BlockPSDAPGDoorBase.BlockEntityBase) blockEntity.data).setDoorValue(doorValue);
				}
			}
		}
		return canOpenDoors;
	}

	/** Every so often the caller also runs MTR's own check and compares. */
	public static boolean shouldCompare() {
		if (++calls % COMPARE_EVERY != 0) {
			return false;
		}
		compared++;
		return true;
	}

	/** ATU's answer differed from MTR's: the caller stops using ATU's for this session. */
	public static void mismatch() {
		mismatches++;
		Perf.LOGGER.warn("A remembered door check differed from MTR's own; MTR's check runs for the rest of this session");
	}

	public static long compared() {
		return compared;
	}

	public static long mismatches() {
		return mismatches;
	}

	public static long hits() {
		return hits;
	}

	public static long misses() {
		return misses;
	}

	/** MTR's box and search, recording instead of deciding. */
	private static Scan scan(ClientWorld clientWorld, Box doorway, PositionAndRotation positionAndRotation) {
		final Vector3d doorwayPosition1 = transform(positionAndRotation, doorway.getMinXMapped(), doorway.getMaxYMapped(), doorway.getMinZMapped());
		final Vector3d doorwayPosition2 = transform(positionAndRotation, doorway.getMaxXMapped(), doorway.getMaxYMapped(), doorway.getMinZMapped());
		final Vector3d doorwayPosition3 = transform(positionAndRotation, doorway.getMaxXMapped(), doorway.getMaxYMapped(), doorway.getMaxZMapped());
		final Vector3d doorwayPosition4 = transform(positionAndRotation, doorway.getMinXMapped(), doorway.getMaxYMapped(), doorway.getMaxZMapped());
		final double minX = Math.min(Math.min(doorwayPosition1.getXMapped(), doorwayPosition2.getXMapped()), Math.min(doorwayPosition3.getXMapped(), doorwayPosition4.getXMapped()));
		final double maxX = Math.max(Math.max(doorwayPosition1.getXMapped(), doorwayPosition2.getXMapped()), Math.max(doorwayPosition3.getXMapped(), doorwayPosition4.getXMapped()));
		final double minY = Math.min(Math.min(doorwayPosition1.getYMapped(), doorwayPosition2.getYMapped()), Math.min(doorwayPosition3.getYMapped(), doorwayPosition4.getYMapped()));
		final double maxY = Math.max(Math.max(doorwayPosition1.getYMapped(), doorwayPosition2.getYMapped()), Math.max(doorwayPosition3.getYMapped(), doorwayPosition4.getYMapped()));
		final double minZ = Math.min(Math.min(doorwayPosition1.getZMapped(), doorwayPosition2.getZMapped()), Math.min(doorwayPosition3.getZMapped(), doorwayPosition4.getZMapped()));
		final double maxZ = Math.max(Math.max(doorwayPosition1.getZMapped(), doorwayPosition2.getZMapped()), Math.max(doorwayPosition3.getZMapped(), doorwayPosition4.getZMapped()));
		boolean platform = false;
		final List<BlockPos> doors = new ArrayList<>(0);
		// MTR's RenderVehicleHelper.CHECK_DOOR_RADIUS_XZ (1) and CHECK_DOOR_RADIUS_Y (2), same loops.
		for (double checkX = minX - 1; checkX <= maxX + 1; checkX++) {
			for (double checkY = minY - 2; checkY <= maxY + 2; checkY++) {
				for (double checkZ = minZ - 1; checkZ <= maxZ + 1; checkZ++) {
					final BlockPos checkPos = Init.newBlockPos(checkX, checkY, checkZ);
					final Object block = clientWorld.getBlockState(checkPos).getBlock().data;
					if (block instanceof PlatformHelper) {
						platform = true;
					} else if (block instanceof BlockPSDAPGDoorBase) {
						doors.add(checkPos);
					}
				}
			}
		}
		return new Scan(platform, doors);
	}

	private static Vector3d transform(PositionAndRotation positionAndRotation, double x, double y, double z) {
		return positionAndRotation.transformForwards(new Vector3d(x, y, z), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
	}

	/** Settings switch: off runs MTR's own search every frame. */
	public static boolean enabled() {
		return Perf.settings().fasterDoorChecks;
	}
}
