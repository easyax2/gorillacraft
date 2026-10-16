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
	/** What Gorilla Tag asked for in its hello (Proto.WANT_*). */
	private int wants = Proto.WANT_BLOCK_MESHES;

	private int connection;
	private ClientLevel worldSentFor;
	private boolean noWorldSent;
	private boolean atlasSent;

	// Latest pose from Gorilla Tag (Minecraft coordinates of the gorilla's eyes).
	private boolean inMap;
	private boolean havePose;
	private int poseAck;
	private double poseX, poseY, poseZ;
	/** Hand positions (Minecraft coordinates) and the gorilla's colour, for the avatar others see. */
	private Vec3 leftHand = Vec3.ZERO, rightHand = Vec3.ZERO;
	private int gorillaColor = 0x202020;
	/** Gorilla Tag's bone pose (GT_BONES), relative to the head, for the real gorilla model. */
	private float[] bones = new float[0];
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
	}

	private int poseCount;

	/** Poses received since the last call (for the VR frame-rate log). */
	public int takePoseCount() {
		int n = poseCount;
		poseCount = 0;
		return n;
	}

	public boolean connected() {
		return connection != 0;
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

		// You're in the headset, not at Minecraft's window: losing focus must not pause the game.
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
		sendAvatarPose(mc);
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
		setRig(null);
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
				streamer.reset(); // resend sections in the form it wants
				GorillaCraft.LOG.info("Gorilla Tag plugin {} (protocol {}) connected", gt, version);
				if (version != Proto.VERSION) {
					status("Protocol mismatch: Gorilla Tag plugin speaks " + version + ", Minecraft mod speaks " + Proto.VERSION + ". Update both halves.");
				}
			}
			case Proto.GT_POSE -> {
				poseCount++;
				boolean nowInMap = m.u8() != 0;
				poseAck = m.i32();
				poseX = m.f64();
				poseY = m.f64();
				poseZ = m.f64();
				poseYaw = m.f32();
				posePitch = m.f32();
				leftHand = new Vec3(m.f64(), m.f64(), m.f64());
				rightHand = new Vec3(m.f64(), m.f64(), m.f64());
				gorillaColor = m.i32();
				if (m.u8() != 0) {
					double[] rig = new double[12];
					for (int i = 0; i < 12; i++) {
						rig[i] = m.f64();
					}
					setRig(nowInMap && poseAck >= teleportId ? rig : null);
				} else {
					setRig(null);
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
			case Proto.GT_MODEL -> GorillaMeshes.readModel(m);
			case Proto.GT_BONES -> {
				Vec3 ref = new Vec3(m.f64(), m.f64(), m.f64());
				float[] b = new float[m.i32() * 12];
				for (int i = 0; i < b.length; i++) {
					b[i] = m.f32();
				}
				bones = b;
				if (mc.player != null) {
					GorillaMeshes.setPose(mc.player.getUUID(), ref, gorillaColor, b);
				}
			}
			default -> GorillaCraft.LOG.warn("Unknown message 0x{} from Gorilla Tag", Integer.toHexString(m.type));
		}
	}

	/** Tells the server where this gorilla's head and hands are, so everyone sees it (GorillaAvatars). */
	private void sendAvatarPose(Minecraft mc) {
		if (!driving() || !havePose || poseAck < teleportId || mc.getConnection() == null
				|| !ClientPlayNetworking.canSend(dev.gorillacraft.GorillaPosePayload.TYPE)) {
			return;
		}
		ClientPlayNetworking.send(new dev.gorillacraft.GorillaPosePayload(new Vec3(poseX, poseY, poseZ), poseYaw, posePitch, leftHand, rightHand,
				gorillaColor, bones));
	}

	/** Inverse of xrizer's Touch grip offset: rotate -20.6° about X, then back off the grip point. */
	private static final org.joml.Quaternionf GRIP_TO_RAW_ROT = new org.joml.Quaternionf().rotationX((float) Math.toRadians(-20.6));
	private static final org.joml.Vector3f[] GRIP_TO_RAW_POS = {
			GRIP_TO_RAW_ROT.transform(new org.joml.Vector3f(-0.007f, 0.00182941f, -0.1019482f)),
			GRIP_TO_RAW_ROT.transform(new org.joml.Vector3f(0.007f, 0.00182941f, -0.1019482f))};

	/** Gorilla Tag's rig (tracking space → Minecraft), for the headset's camera; null when it isn't driving. */
	private void setRig(double[] rig) {
		var vr = dev.gorillacraft.client.vr.VrRenderer.get();
		if (vr != null) {
			vr.setGorillaRig(rig);
		}
	}

	/**
	 * The headset and controllers for Gorilla Tag (which runs without VR of its own): tracking
	 * space, OpenXR axes, sent every frame. While a screen is open the triggers and grips belong
	 * to the panel, so Gorilla Tag gets them released.
	 */
	public void sendTracking(org.joml.Vector3f head, org.joml.Quaternionf headRot, dev.gorillacraft.client.vr.XrInput in, boolean screen) {
		if (connection == 0) {
			return;
		}
		int flags = 1 | (in.gripValid[0] ? 2 : 0) | (in.gripValid[1] ? 4 : 0) | (screen ? 8 : 0);
		FrameWriter w = new FrameWriter(Proto.MC_TRACKING, 160).u8(flags);
		w.f32(head.x).f32(head.y).f32(head.z).f32(headRot.x).f32(headRot.y).f32(headRot.z).f32(headRot.w);
		for (int h = 0; h < 2; h++) {
			// SteamVR's controller pose (Gorilla Tag's hand offsets are tuned for it), from the grip
			// pose the way xrizer does it for Touch controllers.
			org.joml.Quaternionf q = new org.joml.Quaternionf(in.gripRot[h]).mul(GRIP_TO_RAW_ROT);
			org.joml.Vector3f p = in.gripRot[h].transform(new org.joml.Vector3f(GRIP_TO_RAW_POS[h])).add(in.gripPos[h]);
			w.f32(p.x).f32(p.y).f32(p.z).f32(q.x).f32(q.y).f32(q.z).f32(q.w);
		}
		for (int h = 0; h < 2; h++) {
			w.f32(screen ? 0 : in.trigger_[h]).f32(screen ? 0 : in.squeeze_[h]).f32(in.stickX[h]).f32(in.stickY[h]);
		}
		int buttons = (in.primary_[0] ? 1 : 0) | (in.primary_[1] ? 2 : 0) | (in.secondary_[0] ? 4 : 0) | (in.secondary_[1] ? 8 : 0)
				| (in.stickClick_[0] ? 16 : 0) | (in.stickClick_[1] ? 32 : 0) | (in.menu_ ? 64 : 0);
		link.send(w.u16(buttons).finish());
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

	/** While driving: respawn straight away after dying (the gorilla keeps going). */
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
