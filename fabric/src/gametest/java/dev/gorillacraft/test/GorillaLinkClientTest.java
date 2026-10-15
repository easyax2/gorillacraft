package dev.gorillacraft.test;

import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Opens a world and keeps it running while tools/linktest (a stand-in for Gorilla Tag built from
 * the plugin's own protocol code) connects over the link and plays it. Phases are coordinated
 * through files in $GORILLACRAFT_TEST_DIR. Afterwards the world is checked server-side for what
 * the stand-in did with its "hands".
 */
public class GorillaLinkClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Path dir = Path.of(System.getenv().getOrDefault("GORILLACRAFT_TEST_DIR", System.getProperty("java.io.tmpdir")));
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			TestServerContext server = sp.getServer();
			server.runCommand("gamemode creative @a");
			server.runCommand("setblock 2 -60 0 stone");
			server.runCommand("setblock 2 -59 0 oak_stairs");
			server.runCommand("setblock 8 -61 8 water"); // in a hole, so it can't flow
			server.runCommand("setblock 4 -60 0 lever[face=floor]");
			server.runCommand("setblock 5 -60 0 oak_leaves[persistent=true]");
			server.runCommand("item replace entity @a hotbar.0 with oak_planks 64");
			server.runCommand("item replace entity @a hotbar.1 with diamond_pickaxe");
			context.waitTicks(20);
			touch(dir.resolve("ready"));
			System.out.println("GORILLACRAFT TEST world ready");

			context.waitFor(mc -> Files.exists(dir.resolve("phase-tp")), 20 * 180);
			server.runCommand("tp @a 10.5 -60 10.5");
			context.waitFor(mc -> Files.exists(dir.resolve("done")), 20 * 180);
			context.waitTicks(10);
			context.takeScreenshot("gorillacraft-linktest");

			BlockState mined = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(2, -60, 0)));
			BlockState placed = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(1, -60, 0)));
			BlockState lever = server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(4, -60, 0)));
			double[] pos = context.computeOnClient(mc -> new double[] {mc.player.getX(), mc.player.getEyeY(), mc.player.getZ()});
			System.out.printf("GORILLACRAFT TEST mined=%s placed=%s lever=%s eye=%.2f %.2f %.2f%n", mined, placed, lever, pos[0], pos[1], pos[2]);
			if (!mined.isAir()) {
				throw new AssertionError("stone at 2 -60 0 wasn't mined: " + mined);
			}
			if (!placed.is(Blocks.OAK_PLANKS)) {
				throw new AssertionError("no planks placed at 1 -60 0: " + placed);
			}
			if (!lever.is(Blocks.LEVER) || !lever.getValue(LeverBlock.POWERED)) {
				throw new AssertionError("lever wasn't flipped: " + lever);
			}
			if (Math.abs(pos[0] - 10.5) > 0.05 || Math.abs(pos[1] - (-58.3)) > 0.05 || Math.abs(pos[2] - 10.5) > 0.05) {
				throw new AssertionError("player isn't where the gorilla's head is");
			}
		}
	}

	private static void touch(Path p) {
		try {
			Files.createDirectories(p.getParent());
			Files.writeString(p, "");
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}
}
