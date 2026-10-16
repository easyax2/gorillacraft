package dev.gorillacraft.client.vr;

import static dev.gorillacraft.client.vr.XrRuntime.check;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.stackPush;

import dev.gorillacraft.GorillaCraft;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.XrAction;
import org.lwjgl.openxr.XrActionCreateInfo;
import org.lwjgl.openxr.XrActionSet;
import org.lwjgl.openxr.XrActionSetCreateInfo;
import org.lwjgl.openxr.XrActionSpaceCreateInfo;
import org.lwjgl.openxr.XrActionStateBoolean;
import org.lwjgl.openxr.XrActionStateFloat;
import org.lwjgl.openxr.XrActionStateGetInfo;
import org.lwjgl.openxr.XrActionStateVector2f;
import org.lwjgl.openxr.XrActionSuggestedBinding;
import org.lwjgl.openxr.XrActionsSyncInfo;
import org.lwjgl.openxr.XrActiveActionSet;
import org.lwjgl.openxr.XrInteractionProfileSuggestedBinding;
import org.lwjgl.openxr.XrPosef;
import org.lwjgl.openxr.XrSessionActionSetsAttachInfo;
import org.lwjgl.openxr.XrSpace;
import org.lwjgl.openxr.XrSpaceLocation;
import org.lwjgl.system.MemoryStack;

/**
 * The controllers: hand poses (grip for the gorilla's hands, aim for the laser pointer) and
 * every button, read once per frame at the time the frame will be shown.
 */
public final class XrInput {
	private final XrRuntime xr;
	private XrActionSet set;
	private XrAction gripPose, aimPose, trigger, squeeze, stick, stickClick, primary, secondary, menu;
	private final long[] hands = new long[2];
	private final XrSpace[] gripSpaces = new XrSpace[2], aimSpaces = new XrSpace[2];

	/** Tracking-space poses (metres, OpenXR axes). */
	public final Vector3f[] gripPos = {new Vector3f(), new Vector3f()}, aimPos = {new Vector3f(), new Vector3f()};
	public final Quaternionf[] gripRot = {new Quaternionf(), new Quaternionf()}, aimRot = {new Quaternionf(), new Quaternionf()};
	public final boolean[] gripValid = new boolean[2], aimValid = new boolean[2];
	public final float[] trigger_ = new float[2], squeeze_ = new float[2], stickX = new float[2], stickY = new float[2];
	public final boolean[] stickClick_ = new boolean[2], primary_ = new boolean[2], secondary_ = new boolean[2];
	public boolean menu_;

	XrInput(XrRuntime xr) {
		this.xr = xr;
	}

	XrSpace gripSpace(int hand) {
		return gripSpaces[hand];
	}

	void create() {
		try (MemoryStack stack = stackPush()) {
			hands[0] = path(stack, "/user/hand/left");
			hands[1] = path(stack, "/user/hand/right");
			XrActionSetCreateInfo si = XrActionSetCreateInfo.calloc(stack).type$Default().actionSetName(stack.UTF8("gorillacraft"))
					.localizedActionSetName(stack.UTF8("GorillaCraft")).priority(0);
			PointerBuffer pp = stack.mallocPointer(1);
			check(xrCreateActionSet(xr.instance(), si, pp), "xrCreateActionSet");
			set = new XrActionSet(pp.get(0), xr.instance());

			gripPose = action(stack, "grip_pose", "Hand", XR_ACTION_TYPE_POSE_INPUT);
			aimPose = action(stack, "aim_pose", "Pointer", XR_ACTION_TYPE_POSE_INPUT);
			trigger = action(stack, "trigger", "Trigger", XR_ACTION_TYPE_FLOAT_INPUT);
			squeeze = action(stack, "squeeze", "Grip", XR_ACTION_TYPE_FLOAT_INPUT);
			stick = action(stack, "stick", "Thumbstick", XR_ACTION_TYPE_VECTOR2F_INPUT);
			stickClick = action(stack, "stick_click", "Thumbstick click", XR_ACTION_TYPE_BOOLEAN_INPUT);
			primary = action(stack, "primary", "A / X", XR_ACTION_TYPE_BOOLEAN_INPUT);
			secondary = action(stack, "secondary", "B / Y", XR_ACTION_TYPE_BOOLEAN_INPUT);
			menu = action(stack, "menu", "Menu", XR_ACTION_TYPE_BOOLEAN_INPUT);

			String[] lr = {"left", "right"};
			List<Object[]> touch = new ArrayList<>();
			for (String h : lr) {
				String p = "/user/hand/" + h + "/input/";
				touch.add(new Object[] {gripPose, p + "grip/pose"});
				touch.add(new Object[] {aimPose, p + "aim/pose"});
				touch.add(new Object[] {trigger, p + "trigger/value"});
				touch.add(new Object[] {squeeze, p + "squeeze/value"});
				touch.add(new Object[] {stick, p + "thumbstick"});
				touch.add(new Object[] {stickClick, p + "thumbstick/click"});
				touch.add(new Object[] {primary, p + (h.equals("left") ? "x/click" : "a/click")});
				touch.add(new Object[] {secondary, p + (h.equals("left") ? "y/click" : "b/click")});
			}
			touch.add(new Object[] {menu, "/user/hand/left/input/menu/click"});
			suggest(stack, "/interaction_profiles/oculus/touch_controller", touch);

			List<Object[]> index = new ArrayList<>();
			for (String h : lr) {
				String p = "/user/hand/" + h + "/input/";
				index.add(new Object[] {gripPose, p + "grip/pose"});
				index.add(new Object[] {aimPose, p + "aim/pose"});
				index.add(new Object[] {trigger, p + "trigger/value"});
				index.add(new Object[] {squeeze, p + "squeeze/value"});
				index.add(new Object[] {stick, p + "thumbstick"});
				index.add(new Object[] {stickClick, p + "thumbstick/click"});
				index.add(new Object[] {primary, p + "a/click"});
				index.add(new Object[] {secondary, p + "b/click"});
			}
			suggest(stack, "/interaction_profiles/valve/index_controller", index);

			List<Object[]> vive = new ArrayList<>();
			for (String h : lr) {
				String p = "/user/hand/" + h + "/input/";
				vive.add(new Object[] {gripPose, p + "grip/pose"});
				vive.add(new Object[] {aimPose, p + "aim/pose"});
				vive.add(new Object[] {trigger, p + "trigger/value"});
				vive.add(new Object[] {squeeze, p + "squeeze/click"});
				vive.add(new Object[] {stick, p + "trackpad"});
				vive.add(new Object[] {stickClick, p + "trackpad/click"});
			}
			vive.add(new Object[] {menu, "/user/hand/left/input/menu/click"});
			suggest(stack, "/interaction_profiles/htc/vive_controller", vive);

			List<Object[]> simple = new ArrayList<>();
			for (String h : lr) {
				String p = "/user/hand/" + h + "/input/";
				simple.add(new Object[] {gripPose, p + "grip/pose"});
				simple.add(new Object[] {aimPose, p + "aim/pose"});
				simple.add(new Object[] {trigger, p + "select/click"});
			}
			simple.add(new Object[] {menu, "/user/hand/left/input/menu/click"});
			suggest(stack, "/interaction_profiles/khr/simple_controller", simple);

			XrSessionActionSetsAttachInfo ai = XrSessionActionSetsAttachInfo.calloc(stack).type$Default()
					.actionSets(stack.pointers(set.address()));
			check(xrAttachSessionActionSets(xr.session(), ai), "xrAttachSessionActionSets");

			for (int h = 0; h < 2; h++) {
				gripSpaces[h] = actionSpace(stack, gripPose, hands[h]);
				aimSpaces[h] = actionSpace(stack, aimPose, hands[h]);
			}
		}
	}

	private long path(MemoryStack stack, String p) {
		LongBuffer out = stack.mallocLong(1);
		check(xrStringToPath(xr.instance(), p, out), "xrStringToPath " + p);
		return out.get(0);
	}

	private XrAction action(MemoryStack stack, String name, String label, int type) {
		XrActionCreateInfo ci = XrActionCreateInfo.calloc(stack).type$Default().actionName(stack.UTF8(name))
				.localizedActionName(stack.UTF8(label)).actionType(type).subactionPaths(stack.longs(hands[0], hands[1]));
		PointerBuffer pp = stack.mallocPointer(1);
		check(xrCreateAction(set, ci, pp), "xrCreateAction " + name);
		return new XrAction(pp.get(0), set);
	}

	/** Suggests bindings for one controller type; a runtime without that type just says no. */
	private void suggest(MemoryStack stack, String profile, List<Object[]> bindings) {
		XrActionSuggestedBinding.Buffer b = XrActionSuggestedBinding.calloc(bindings.size(), stack);
		for (int i = 0; i < bindings.size(); i++) {
			b.get(i).action((XrAction) bindings.get(i)[0]).binding(path(stack, (String) bindings.get(i)[1]));
		}
		XrInteractionProfileSuggestedBinding s = XrInteractionProfileSuggestedBinding.calloc(stack).type$Default()
				.interactionProfile(path(stack, profile)).suggestedBindings(b);
		int r = xrSuggestInteractionProfileBindings(xr.instance(), s);
		if (r < 0) {
			GorillaCraft.LOG.info("VR controller bindings for {} not taken ({})", profile, r);
		}
	}

	private XrSpace actionSpace(MemoryStack stack, XrAction action, long hand) {
		XrActionSpaceCreateInfo ci = XrActionSpaceCreateInfo.calloc(stack).type$Default().action(action).subactionPath(hand);
		ci.poseInActionSpace().orientation().w(1f);
		PointerBuffer pp = stack.mallocPointer(1);
		check(xrCreateActionSpace(xr.session(), ci, pp), "xrCreateActionSpace");
		return new XrSpace(pp.get(0), xr.session());
	}

	/** Reads every button and hand pose for this frame. Unfocused (the runtime's menu is up): all released. */
	void sync(long time, boolean focused) {
		if (!focused) {
			java.util.Arrays.fill(trigger_, 0);
			java.util.Arrays.fill(squeeze_, 0);
			java.util.Arrays.fill(stickX, 0);
			java.util.Arrays.fill(stickY, 0);
			java.util.Arrays.fill(stickClick_, false);
			java.util.Arrays.fill(primary_, false);
			java.util.Arrays.fill(secondary_, false);
			menu_ = false;
		}
		try (MemoryStack stack = stackPush()) {
			if (focused) {
				XrActiveActionSet.Buffer active = XrActiveActionSet.calloc(1, stack);
				active.get(0).actionSet(set).subactionPath(XR_NULL_PATH);
				check(xrSyncActions(xr.session(), XrActionsSyncInfo.calloc(stack).type$Default().activeActionSets(active)), "xrSyncActions");
				for (int h = 0; h < 2; h++) {
					trigger_[h] = floatState(stack, trigger, hands[h]);
					squeeze_[h] = floatState(stack, squeeze, hands[h]);
					XrActionStateVector2f v = XrActionStateVector2f.calloc(stack).type$Default();
					xrGetActionStateVector2f(xr.session(), getInfo(stack, stick, hands[h]), v);
					stickX[h] = v.isActive() ? v.currentState().x() : 0;
					stickY[h] = v.isActive() ? v.currentState().y() : 0;
					stickClick_[h] = boolState(stack, stickClick, hands[h]);
					primary_[h] = boolState(stack, primary, hands[h]);
					secondary_[h] = boolState(stack, secondary, hands[h]);
				}
				menu_ = boolState(stack, menu, hands[0]);
			}
			for (int h = 0; h < 2; h++) {
				gripValid[h] = locate(stack, gripSpaces[h], time, gripPos[h], gripRot[h]);
				aimValid[h] = locate(stack, aimSpaces[h], time, aimPos[h], aimRot[h]);
			}
		}
	}

	private XrActionStateGetInfo getInfo(MemoryStack stack, XrAction a, long hand) {
		return XrActionStateGetInfo.calloc(stack).type$Default().action(a).subactionPath(hand);
	}

	private float floatState(MemoryStack stack, XrAction a, long hand) {
		XrActionStateFloat s = XrActionStateFloat.calloc(stack).type$Default();
		xrGetActionStateFloat(xr.session(), getInfo(stack, a, hand), s);
		return s.isActive() ? s.currentState() : 0f;
	}

	private boolean boolState(MemoryStack stack, XrAction a, long hand) {
		XrActionStateBoolean s = XrActionStateBoolean.calloc(stack).type$Default();
		xrGetActionStateBoolean(xr.session(), getInfo(stack, a, hand), s);
		return s.isActive() && s.currentState();
	}

	private boolean locate(MemoryStack stack, XrSpace s, long time, Vector3f pos, Quaternionf rot) {
		XrSpaceLocation loc = XrSpaceLocation.calloc(stack).type$Default();
		if (xrLocateSpace(s, xr.space(), time, loc) < 0) {
			return false;
		}
		long need = XR_SPACE_LOCATION_ORIENTATION_VALID_BIT | XR_SPACE_LOCATION_POSITION_VALID_BIT;
		if ((loc.locationFlags() & need) != need) {
			return false;
		}
		XrPosef p = loc.pose();
		pos.set(p.position$().x(), p.position$().y(), p.position$().z());
		rot.set(p.orientation().x(), p.orientation().y(), p.orientation().z(), p.orientation().w());
		return true;
	}

	void destroy() {
		for (int h = 0; h < 2; h++) {
			if (gripSpaces[h] != null) {
				xrDestroySpace(gripSpaces[h]);
			}
			if (aimSpaces[h] != null) {
				xrDestroySpace(aimSpaces[h]);
			}
		}
		if (set != null) {
			xrDestroyActionSet(set); // destroys its actions too
			set = null;
		}
	}
}
