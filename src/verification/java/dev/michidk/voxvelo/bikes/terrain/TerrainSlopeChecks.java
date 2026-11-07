package dev.michidk.voxvelo.bikes.terrain;

import java.lang.reflect.Proxy;
import java.util.OptionalDouble;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;

/** Regression checks for roads near buildings, overhead structures and missing surfaces. */
public final class TerrainSlopeChecks {
	public static void main(String[] args) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BlockGetter level = (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(), new Class<?>[] {BlockGetter.class}, (proxy, method, arguments) -> {
			if (method.getName().equals("getBlockState")) {
				BlockPos pos = (BlockPos) arguments[0];
				return pos.getY() == 63 || (pos.getX() > 0 && pos.getY() == 66)
					? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
			}
			throw new UnsupportedOperationException(method.getName());
		});
		LevelGround ground = new LevelGround(level);
		require(ground.height(1, 0, 64).orElseThrow() == 64, "road is selected instead of overhead structure");
		require(Math.abs(TerrainSlope.measure(ground, 0, 0, 1, 0, 64, 10).orElseThrow()) < 1.0e-9, "flat road beneath asymmetric overhead structure reads zero grade");
		BlockGetter solidRamp = (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(), new Class<?>[] {BlockGetter.class}, (proxy, method, arguments) -> {
			if (method.getName().equals("getBlockState")) {
				BlockPos pos = (BlockPos) arguments[0];
				return pos.getY() < 64 + Math.floorDiv(pos.getX(), 5) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
			}
			throw new UnsupportedOperationException(method.getName());
		});
		require(new LevelGround(solidRamp).height(5, 0, 64).orElseThrow() == 65, "solid stair selects exposed top rather than buried block face");
		require(TerrainSlope.measure(new LevelGround(solidRamp), 0, 0, 1, 0, 64, 10).orElseThrow() > 0.15, "solid hills retain a nonzero incline");
		TerrainSlope.Ground wall = (x, z, y) -> OptionalDouble.of(x >= 5 ? 67 : 64);
		require(Math.abs(TerrainSlope.measure(wall, 0, 0, 1, 0, 64, 10).orElseThrow()) < 1.0e-9, "nearby wall does not become a climb");
		TerrainSlope.Ground gap = (x, z, y) -> x == 4 ? OptionalDouble.empty() : OptionalDouble.of(x > 4 ? 65 : 64);
		require(Math.abs(TerrainSlope.measure(gap, 0, 0, 1, 0, 64, 10).orElseThrow()) < 1.0e-9, "measurement does not jump a gap to unrelated ground");
		TerrainSlope.Ground ramp = (x, z, y) -> OptionalDouble.of(64 + x * 0.1);
		require(Math.abs(TerrainSlope.measure(ramp, 0, 0, 1, 0, 64, 10).orElseThrow() - 0.1) < 1.0e-9, "real continuous climb keeps its grade");
		System.out.println("Terrain grade checks passed for flat roads, overhead structures, walls, gaps and real climbs.");
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
