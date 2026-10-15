package dev.gorillacraft.client;

import dev.gorillacraft.GorillaCraft;
import dev.gorillacraft.GorillaPlayers;
import dev.gorillacraft.GorillaPresencePayload;
import dev.gorillacraft.link.FrameWriter;
import dev.gorillacraft.link.Inbound;
import dev.gorillacraft.link.LinkServer;
import dev.gorillacraft.link.Proto;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the Minecraft client does for Gorilla Tag, run on the client thread.
 *
 * Authority is split the opposite way from SkyCraft: Gorilla Tag owns the player's movement
 * (gorilla locomotion), Minecraft owns the world. Gorilla Tag reports where the gorilla's head
 * is; we put the Minecraft player there, so chunks load, mobs react and the server sees us. Hand
 * interactions arrive as block targets and go through MultiPlayerGameMode exactly like a mouse
 * click would, so survival mining speed, tools, block placement rules and doors/buttons/levers
 * are all vanilla.
 */
public final class GorillaSession {
	private final LinkServer link;
	private final ClientConfig cfg;
	private final SectionStreamer streamer = new SectionStreamer();
	private final AtlasExporter atlas = new AtlasExporter();
	private final MenuActions menu;
	private final FrameCapture frames;
	/** What Gorilla Tag asked for in its hello (Proto.WANT_*). */
	private int wants = Proto.WANT_BLOCK_MESHES;
	private boolean overlayWasActive;
	private boolean savedHideGui;
	private boolean savedBobView;

	private int connection;
	private ClientLevel worldSentFor;
	private boolean noWorldSent;
	private boolean atlasSent;

	// Latest pose from Gorilla Tag (Minecraft coordinates of the gorilla's eyes).
	private boolean inMap;
	private boolean havePose;
	private int poseAck;
	private double poseX, poseY, poseZ;
	/** Eye positions (Minecraft coordinates), for per-eye frames. */
	private final double[][] eyes = new double[2][3];
	/** Which eye the next overlay frame is drawn for. */
	private int nextEye = Proto.EYE_LEFT;
	private float poseYaw, posePitch;

	/** Bumped each time Minecraft moves the player itself; Gorilla Tag must ack it. */
	private int teleportId;
	private Vec3 lastApplied;
	private boolean presence;

	// Mining: the block Gorilla Tag's hand is digging into, if any.
	private BlockPos mineTarget;
	private Direction mineFace;
	private boolean mining;

	private String lastHotbar = "";
	private int respawnCooldown;

	GorillaSession(LinkServer link, ClientConfig cfg) {
		this.link = link;
		this.cfg = cfg;
		this.menu = new MenuActions(link, this::status);
		this.frames = new FrameCapture(link, cfg);
	}

	/** True while Gorilla Tag is moving the Minecraft player. */
	public boolean driving() {
		Minecraft mc = Minecraft.getInstance();
		return connection != 0 && inMap && mc.level != null && mc.level == worldSentFor && mc.player != null;
	}

	void tick(Minecraft mc) {
		int cid = link.connectionId();
		if (cid != connection) {
			onConnectionChanged(cid);
		}
		if (cid == 0) {
			updatePresence(mc);
			return;
		}

		Inbound msg;
		while ((msg = link.poll()) != null) {
			if (msg.connection == cid) {
				handle(mc, msg);
			}
		}

		menu.tick(mc);

		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) {
			if (!noWorldSent) {
				link.send(new FrameWriter(Proto.MC_WORLD).u8(0).str("").i32(0).i32(0).i32(0).i32(0).i32(0).str("").finish());
				status("Open a Minecraft world (or join a server) to load the map");
				noWorldSent = true;
				worldSentFor = null;
				streamer.reset();
			}
			stopMining(mc);
			updatePresence(mc);
			return;
		}
		noWorldSent = false;

		if (level != worldSentFor) {
			worldSentFor = level;
			streamer.reset();
			BlockPos origin = level.getRespawnData().pos();
			link.send(new FrameWriter(Proto.MC_WORLD)
					.u8(1).str(level.dimension().identifier().toString())
					.i32(origin.getX()).i32(origin.getY()).i32(origin.getZ())
					.i32(level.getMinY()).i32(level.getMaxY()).str(worldLabel(mc)).finish());
			status("Connected to " + (mc.isLocalServer() ? "singleplayer world" : "server"));
			if (inMap) {
				sendTeleport(player);
			}
		}

		// The Minecraft window is hidden behind Gorilla Tag; losing focus must not pause the game.
		mc.options.pauseOnLostFocus = false;

		if ((wants & Proto.WANT_BLOCK_MESHES) != 0 && !atlasSent && !atlas.pending()) {
			atlas.request();
		}
		if (atlas.pending()) {
			byte[] png = atlas.poll();
			if (png != null) {
				link.send(new FrameWriter(Proto.MC_ATLAS, png.length + 16).i32(png.length).bytes(png).finish());
				atlasSent = true;
			}
		}

		updatePresence(mc);
		updateOverlay(mc);
		tickScreens(mc, player);
		tickMining(mc, player);
		tickHotbar(player);
		streamer.tick(level, player, link, cfg, (wants & Proto.WANT_BLOCK_MESHES) != 0);
	}

	private void onConnectionChanged(int cid) {
		connection = cid;
		wants = Proto.WANT_BLOCK_MESHES;
		worldSentFor = null;
		noWorldSent = false;
		atlasSent = false;
		inMap = false;
		havePose = false;
		lastApplied = null;
		mineTarget = null;
		lastHotbar = "";
		streamer.reset();
		if (cid != 0) {
			link.send(new FrameWriter(Proto.MC_WELCOME).i32(Proto.VERSION).finish());
		}
	}

	private void handle(Minecraft mc, Inbound m) {
		switch (m.type) {
			case Proto.GT_HELLO -> {
				int version = m.i32();
				String gt = m.str();
				wants = version >= 3 ? m.u8() : Proto.WANT_BLOCK_MESHES;
				frames.reset();
				streamer.reset(); // resend sections in the form it wants
				GorillaCraft.LOG.info("Gorilla Tag plugin {} (protocol {}) connected", gt, version);
				if (version != Proto.VERSION) {
					status("Protocol mismatch: Gorilla Tag plugin speaks " + version + ", Minecraft mod speaks " + Proto.VERSION + ". Update both halves.");
				}
			}
			case Proto.GT_POSE -> {
				boolean nowInMap = m.u8() != 0;
				poseAck = m.i32();
				poseX = m.f64();
				poseY = m.f64();
				poseZ = m.f64();
				poseYaw = m.f32();
				posePitch = m.f32();
				for (double[] eye : eyes) {
					eye[0] = m.f64();
					eye[1] = m.f64();
					eye[2] = m.f64();
				}
				havePose = true;
				if (nowInMap && !inMap) {
					inMap = true;
					lastApplied = null;
					if (mc.player != null && worldSentFor != null) {
						// Entering: Gorilla Tag jumps to wherever the Minecraft player is.
						sendTeleport(mc.player);
					}
				} else if (!nowInMap && inMap) {
					inMap = false;
					stopMining(mc);
				}
			}
			case Proto.GT_MINE -> {
				boolean active = m.u8() != 0;
				BlockPos pos = new BlockPos(m.i32(), m.i32(), m.i32());
				Direction face = Direction.from3DDataValue(m.u8());
				if (active) {
					mineTarget = pos;
					mineFace = face;
				} else {
					mineTarget = null;
				}
			}
			case Proto.GT_USE -> {
				BlockPos pos = new BlockPos(m.i32(), m.i32(), m.i32());
				Direction face = Direction.from3DDataValue(m.u8());
				Vec3 hit = new Vec3(m.f64(), m.f64(), m.f64());
				use(mc, pos, face, hit);
			}
			case Proto.GT_SLOT -> {
				int slot = m.u8();
				if (mc.player != null && Inventory.isHotbarSlot(slot)) {
					mc.player.getInventory().setSelectedSlot(slot);
				}
			}
			case Proto.GT_LIST_WORLDS -> menu.listWorlds(mc);
			case Proto.GT_OPEN_WORLD -> menu.openWorld(mc, m.str());
			case Proto.GT_JOIN_SERVER -> menu.joinServer(mc, m.str());
			case Proto.GT_LEAVE_WORLD -> menu.leave(mc);
			default -> GorillaCraft.LOG.warn("Unknown message 0x{} from Gorilla Tag", Integer.toHexString(m.type));
		}
	}

	/** True while Minecraft draws the gorilla's view for Gorilla Tag's overlay. */
	public boolean overlayActive() {
		return (wants & Proto.WANT_FRAMES) != 0 && driving() && havePose && poseAck >= teleportId;
	}

	private boolean stereo() {
		return (wants & Proto.WANT_STEREO) != 0;
	}

	/** Which eye the camera draws for this frame: left and right take turns, or the head's centre. */
	public int cameraEye() {
		return stereo() ? nextEye : Proto.EYE_CENTER;
	}

	public double cameraX() {
		return cameraEye() == Proto.EYE_CENTER ? poseX : eyes[cameraEye()][0];
	}

	public double cameraY() {
		return cameraEye() == Proto.EYE_CENTER ? poseY : eyes[cameraEye()][1];
	}

	public double cameraZ() {
		return cameraEye() == Proto.EYE_CENTER ? poseZ : eyes[cameraEye()][2];
	}

	public float poseYaw() {
		return poseYaw;
	}

	public float posePitch() {
		return posePitch;
	}

	public float overlayFov() {
		return cfg.overlayFov;
	}

	/** Called after every rendered frame. */
	public void onFrameRendered() {
		if (overlayActive() && frames.capture(Minecraft.getInstance(), cameraEye()) && stereo()) {
			nextEye = 1 - nextEye; // that eye is on its way: draw the other one next
		}
	}

	/**
	 * While overlaying, Minecraft's picture must be just the world: no HUD, no hand, no view
	 * bobbing. The hand is skipped in GameRendererMixin. Restored afterwards.
	 */
	private void updateOverlay(Minecraft mc) {
		boolean active = overlayActive();
		if (active == overlayWasActive) {
			return;
		}
		overlayWasActive = active;
		if (active) {
			savedHideGui = mc.gui.hud.isHidden();
			savedBobView = mc.options.bobView().get();
			if (!savedHideGui) {
				mc.gui.hud.toggle();
			}
			mc.options.bobView().set(false);
		} else {
			if (mc.gui.hud.isHidden() != savedHideGui) {
				mc.gui.hud.toggle();
			}
			mc.options.bobView().set(savedBobView);
		}
	}

	/** "World name" in singleplayer, the server's address in multiplayer. */
	private static String worldLabel(Minecraft mc) {
		if (mc.getSingleplayerServer() != null) {
			return mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		var server = mc.getCurrentServer();
		return server != null ? server.ip : "server";
	}

	/** Called from LocalPlayer.tick (HEAD), before the player's movement is sent to the server. */
	public void applyPose(LocalPlayer player) {
		if (!driving() || !havePose) {
			return;
		}
		// The server (or a command, or a respawn) moved us: Gorilla Tag has to follow.
		if (lastApplied != null && player.position().distanceToSqr(lastApplied) > 0.25) {
			sendTeleport(player);
			return;
		}
		if (poseAck < teleportId) {
			return; // Gorilla Tag hasn't done the last teleport yet; its poses are stale.
		}
		player.setPos(poseX, poseY - player.getEyeHeight(), poseZ);
		player.setDeltaMovement(Vec3.ZERO);
		player.setYRot(poseYaw);
		player.setXRot(posePitch);
		player.setYHeadRot(poseYaw);
		player.setYBodyRot(poseYaw);
		player.setOnGround(true);
		player.resetFallDistance();
		lastApplied = player.position();
	}

	private void sendTeleport(LocalPlayer player) {
		teleportId++;
		lastApplied = null;
		Vec3 eye = player.getEyePosition();
		link.send(new FrameWriter(Proto.MC_TELEPORT).i32(teleportId)
				.f64(eye.x).f64(eye.y).f64(eye.z).f32(player.getYRot()).finish());
	}

	private void updatePresence(Minecraft mc) {
		boolean want = driving();
		if (want == presence) {
			return;
		}
		presence = want;
		if (mc.player != null) {
			GorillaPlayers.set(mc.player.getUUID(), want);
		}
		if (mc.getConnection() != null && ClientPlayNetworking.canSend(GorillaPresencePayload.TYPE)) {
			ClientPlayNetworking.send(new GorillaPresencePayload(want));
		} else if (want) {
			status("This server doesn't have GorillaCraft; it may pull you back when you climb on block edges");
		}
	}

	private void tickMining(Minecraft mc, LocalPlayer player) {
		MultiPlayerGameMode gameMode = mc.gameMode;
		if (gameMode == null) {
			return;
		}
		if (mineTarget != null && driving()) {
			// Same calls as holding the attack button: start once, then continue every tick.
			// continueDestroyBlock handles creative's break delay and switching to a new block.
			if (!mining) {
				gameMode.startDestroyBlock(mineTarget, mineFace);
				mining = true;
			} else {
				gameMode.continueDestroyBlock(mineTarget, mineFace);
			}
			if (player.tickCount % 5 == 0) {
				player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
			}
		} else {
			stopMining(mc);
		}
	}

	private void stopMining(Minecraft mc) {
		if (mining) {
			if (mc.gameMode != null) {
				mc.gameMode.stopDestroyBlock();
			}
			mining = false;
		}
		mineTarget = null;
	}

	private void use(Minecraft mc, BlockPos pos, Direction face, Vec3 hit) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || !driving()) {
			return;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			var result = mc.gameMode.useItemOn(player, hand, new BlockHitResult(hit, face, pos, false));
			if (result.consumesAction()) {
				player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
				return;
			}
			result = mc.gameMode.useItem(player, hand);
			if (result.consumesAction()) {
				player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
				return;
			}
		}
	}

	/**
	 * Minecraft's window is hidden behind Gorilla Tag, so nobody can click its screens. While
	 * driving: respawn straight away after dying, and close container screens (chests, furnaces)
	 * that a hand interaction opened, since Gorilla Tag can't show them yet.
	 */
	private void tickScreens(Minecraft mc, LocalPlayer player) {
		if (!driving()) {
			return;
		}
		if (player.isDeadOrDying()) {
			if (respawnCooldown-- <= 0) {
				player.respawn();
				respawnCooldown = 20;
				status("You died in Minecraft and respawned");
			}
			return;
		}
		respawnCooldown = 0;
		if (mc.gui.screen() instanceof AbstractContainerScreen<?>) {
			player.closeContainer();
			status("Containers can't be opened from Gorilla Tag yet");
		}
	}

	private void tickHotbar(LocalPlayer player) {
		Inventory inv = player.getInventory();
		StringBuilder key = new StringBuilder().append(inv.getSelectedSlot());
		FrameWriter w = new FrameWriter(Proto.MC_HOTBAR).u8(inv.getSelectedSlot());
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			ItemStack stack = inv.getItem(i);
			String name = stack.isEmpty() ? "" : stack.getHoverName().getString();
			int count = stack.isEmpty() ? 0 : Math.min(stack.getCount(), 255);
			key.append('|').append(name).append('*').append(count);
			w.str(name).u8(count);
		}
		String k = key.toString();
		if (!k.equals(lastHotbar)) {
			lastHotbar = k;
			link.send(w.finish());
		}
	}

	private void status(String text) {
		GorillaCraft.LOG.info("Status for Gorilla Tag: {}", text);
		link.send(new FrameWriter(Proto.MC_STATUS).str(text).finish());
	}
}
