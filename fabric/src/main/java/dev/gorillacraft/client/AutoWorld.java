package dev.gorillacraft.client;

import dev.gorillacraft.GorillaCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * When Gorilla Tag starts Minecraft (it passes -Dgorillacraft.autoWorld=NAME), skip the menus:
 * open that singleplayer world from the title screen, creating it the first time as a creative
 * world with cheats on, so Gorilla Tag has a map straight away.
 */
final class AutoWorld {
	private static final String WORLD = System.getProperty("gorillacraft.autoWorld", "").trim();
	private boolean done = WORLD.isEmpty();

	void tick(Minecraft mc) {
		if (done || mc.level != null) {
			return;
		}
		Screen screen = mc.gui.screen();
		if (!(screen instanceof TitleScreen)) {
			return;
		}
		done = true;
		WorldOpenFlows flows = mc.createWorldOpenFlows();
		if (mc.getLevelSource().levelExists(WORLD)) {
			GorillaCraft.LOG.info("Opening world {} for Gorilla Tag", WORLD);
			flows.openWorld(WORLD, () -> mc.gui.setScreen(screen));
		} else {
			GorillaCraft.LOG.info("Creating world {} for Gorilla Tag", WORLD);
			LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
					new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
			flows.createFreshLevel(WORLD, settings, WorldOptions.defaultWithRandomSeed(), WorldPresets::createNormalWorldDimensions, screen);
		}
	}
}
