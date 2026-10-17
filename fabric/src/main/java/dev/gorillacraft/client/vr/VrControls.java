package dev.gorillacraft.client.vr;

import dev.gorillacraft.client.GorillaCraftClient;
import dev.gorillacraft.client.GorillaSession;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Controllers → Minecraft, and → Gorilla Tag. Gorilla Tag gets the head, both hands and every
 * button each frame (it moves the gorilla, mines and places with them). Minecraft's own screens
 * float in front of you as a panel you point at with the right hand:
 *
 * - right trigger: click, right grip: right click, right stick up/down: scroll
 * - B: close the screen; Y: inventory; left menu button: pause menu; left stick click: re-centre the panel
 * - left stick left/right: hotbar slot
 */
public final class VrControls {
	private static final float PANEL_WIDTH = 1.1f, PANEL_DISTANCE = 1.0f;

	private final VrRenderer vr;
	private final XrRuntime xr;

	private boolean panelPlaced;
	private final Vector3f panelPos = new Vector3f();
	private final Quaternionf panelRot = new Quaternionf();
	private boolean pointing;
	private final Vector3f cursorPos = new Vector3f();
	private int mouseButtons;
	private long nextScroll;
	private boolean wasScreen;

	// Last frame's buttons, for presses.
	private boolean prevSecondaryL, prevSecondaryR, prevMenu, prevStickClickL, prevPrimaryL, prevPrimaryR;
	private float prevStickXL;

	VrControls(VrRenderer vr, XrRuntime xr) {
		this.vr = vr;
		this.xr = xr;
	}

	/** True while a screen (or the title menu) takes the controllers. */
	public static boolean screenOpen(Minecraft mc) {
		return mc.gui.screen() != null || mc.level == null || mc.player == null;
	}

	void update(Minecraft mc) {
		XrInput in = xr.input();
		if (in == null) {
			return;
		}
		boolean screen = screenOpen(mc);
		if (screen && (!wasScreen || !panelPlaced) || pressed(in.stickClick_[0], prevStickClickL)) {
			placePanel();
		}
		wasScreen = screen;

		pointing = false;
		if (screen) {
			pointer(mc, in);
		} else if (mouseButtons != 0) {
			mouseButtons = 0;
		}

		Screen s = mc.gui.screen();
		if (pressed(in.secondary_[1], prevSecondaryR) && s != null) {
			s.onClose();
		}
		if (pressed(in.secondary_[0], prevSecondaryL) && mc.player != null) {
			if (s == null) {
				mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
			} else if (s instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
				s.onClose();
			}
		}
		if (pressed(in.menu_, prevMenu)) {
			if (s == null && mc.level != null) {
				mc.pauseGame(false);
			} else if (s != null) {
				s.onClose();
			}
		}
		if (!screen && mc.player != null) {
			// A / X: next / previous hotbar slot.
			int step = (pressed(in.primary_[1], prevPrimaryR) ? 1 : 0) - (pressed(in.primary_[0], prevPrimaryL) ? 1 : 0);
			if (step != 0) {
				Inventory inv = mc.player.getInventory();
				inv.setSelectedSlot(Math.floorMod(inv.getSelectedSlot() + step, Inventory.getSelectionSize()));
			}
			float x = in.stickX[0];
			if (Math.abs(x) > 0.7f && Math.abs(prevStickXL) <= 0.7f) {
				Inventory inv = mc.player.getInventory();
				int n = Inventory.getSelectionSize();
				inv.setSelectedSlot(Math.floorMod(inv.getSelectedSlot() + (x > 0 ? 1 : -1), n));
			}
		}
		VrHands.update(mc, in, screen);
		prevPrimaryL = in.primary_[0];
		prevPrimaryR = in.primary_[1];
		prevSecondaryL = in.secondary_[0];
		prevSecondaryR = in.secondary_[1];
		prevMenu = in.menu_;
		prevStickClickL = in.stickClick_[0];
		prevStickXL = in.stickX[0];

		GorillaSession session = GorillaCraftClient.session();
		if (session != null) {
			session.sendTracking(vr.headTracking(new Vector3f()), xr.view(0).rot, in, screen);
			if (mc.player != null && !session.driving()) {
				// No gorilla yet: the Minecraft player looks where you look.
				Vector3f f = vr.headWorldRotation().transform(new Vector3f(0, 0, -1));
				mc.player.setYRot((float) Math.toDegrees(Math.atan2(-f.x, f.z)));
				mc.player.setXRot((float) Math.toDegrees(-Math.asin(Math.clamp(f.y, -1f, 1f))));
			}
		}
	}

	private static boolean pressed(boolean now, boolean before) {
		return now && !before;
	}

	/** Puts the panel a metre in front of your head, upright, facing you. */
	private void placePanel() {
		Vector3f head = vr.headTracking(new Vector3f());
		Vector3f fwd = xr.view(0).rot.transform(new Vector3f(0, 0, -1));
		fwd.y = 0;
		if (fwd.lengthSquared() < 1e-4f) {
			fwd.set(0, 0, -1);
		}
		fwd.normalize();
		panelPos.set(head).fma(PANEL_DISTANCE, fwd).add(0, -0.15f, 0);
		panelRot.rotationY((float) Math.atan2(-fwd.x, -fwd.z));
		panelPlaced = true;
	}

	/** The right hand's laser on the panel: moves Minecraft's mouse and clicks. */
	private void pointer(Minecraft mc, XrInput in) {
		var window = mc.getWindow();
		float aspect = (float) window.getHeight() / Math.max(1, window.getWidth());
		float height = PANEL_WIDTH * aspect;
		if (!in.aimValid[1]) {
			return;
		}
		Vector3f origin = in.aimPos[1];
		Vector3f dir = in.aimRot[1].transform(new Vector3f(0, 0, -1));
		Vector3f normal = panelRot.transform(new Vector3f(0, 0, 1));
		float denom = dir.dot(normal);
		if (denom > -1e-4f) {
			return; // pointing away from (or along) the panel
		}
		float t = new Vector3f(panelPos).sub(origin).dot(normal) / denom;
		if (t <= 0 || t > 10) {
			return;
		}
		Vector3f hit = new Vector3f(origin).fma(t, dir);
		Vector3f local = new Quaternionf(panelRot).conjugate().transform(new Vector3f(hit).sub(panelPos));
		float u = local.x / PANEL_WIDTH + 0.5f, v = 0.5f - local.y / height;
		if (u < 0 || u > 1 || v < 0 || v > 1) {
			return;
		}
		pointing = true;
		cursorPos.set(hit).fma(0.003f, normal);
		double x = u * window.getScreenWidth(), y = v * window.getScreenHeight();
		mc.mouseHandler.onMove(window.handle(), x, y, 0, 0);
		int buttons = (in.trigger_[1] > 0.6f ? 1 : 0) | (in.squeeze_[1] > 0.6f ? 2 : 0);
		for (int b = 0; b < 2; b++) {
			boolean now = (buttons & 1 << b) != 0, was = (mouseButtons & 1 << b) != 0;
			if (now != was) {
				mc.mouseHandler.onButton(window.handle(), new MouseButtonInfo(b, 0), now ? 1 : 0);
			}
		}
		mouseButtons = buttons;
		float sy = in.stickY[1];
		long now = System.nanoTime();
		if (Math.abs(sy) > 0.5f && now >= nextScroll) {
			mc.mouseHandler.onScroll(window.handle(), 0, Math.signum(sy));
			nextScroll = now + 120_000_000L;
		}
	}

	/** The open screen as a panel, plus the pointer's dot when it's on it. */
	List<XrRuntime.Quad> panelQuads(XrRuntime.Swapchain gui, float aspect) {
		List<XrRuntime.Quad> quads = new ArrayList<>();
		quads.add(new XrRuntime.Quad(gui, false, new Vector3f(panelPos), new Quaternionf(panelRot), PANEL_WIDTH, PANEL_WIDTH * aspect));
		if (pointing) {
			quads.add(xr.cursorQuad(new Vector3f(cursorPos), new Quaternionf(panelRot), 0.012f));
		}
		return quads;
	}

	/**
	 * The hotbar on the back of your left hand. The left grip's +X points out of the back of the
	 * hand, so the panel faces that way, a few centimetres out; it reads left to right across the
	 * knuckles (towards the thumb, grip -Z) with its top towards the fingertips.
	 */
	XrRuntime.Quad wristQuad(XrRuntime.Swapchain hud) {
		float width = 0.14f;
		return new XrRuntime.Quad(hud, true, new Vector3f(0.055f, -0.01f, 0f), new Quaternionf().rotationY((float) Math.toRadians(90)), width,
				width * hud.height / hud.width);
	}
}
