using System.Collections;
using System.Collections.Generic;
using System.Linq;
using GorillaLocomotion;
using HarmonyLib;
using UnityEngine;

namespace GorillaCraft
{
    /// <summary>
    /// Gorilla Tag started by Minecraft (with -gorillacraft): Minecraft is the VR game and owns the
    /// headset. Gorilla Tag runs without VR, in a small window, and only moves the gorilla:
    /// Minecraft sends the headset and controllers every frame, they're put on Gorilla Tag's head
    /// camera and hands, and Gorilla Tag's own locomotion does the rest. What comes back is where
    /// Gorilla Tag's rig ended up (Plugin.SendPose) and the gorilla's bones.
    /// </summary>
    internal static class MinecraftVr
    {
        public static bool Active { get; private set; }

        private static Messages.Tracking tracking;
        private static bool loggedRig;

        /// <summary>Whether Minecraft started this Gorilla Tag.</summary>
        public static bool StartedByMinecraft()
        {
            return System.Environment.GetCommandLineArgs().Any(a => a.Equals("-gorillacraft", System.StringComparison.OrdinalIgnoreCase));
        }

        public static void Enable(Harmony harmony)
        {
            Active = true;
            KeepXrOff(harmony);
            harmony.Patch(AccessTools.Method(typeof(ControllerInputPoller), "LateUpdate"),
                prefix: new HarmonyMethod(typeof(MinecraftVr), nameof(PollerPrefix)));
            harmony.Patch(AccessTools.Method(typeof(GTPlayer), "LateUpdate"),
                prefix: new HarmonyMethod(typeof(MinecraftVr), nameof(ApplyPrefix)));
            QualitySettings.vSyncCount = 0;
            Application.targetFrameRate = 144;
            AudioListener.volume = 0f; // Minecraft makes the sound
            Plugin.Log.LogInfo("Started by Minecraft: no VR here, Minecraft's headset and controllers move the gorilla");
        }

        /// <summary>Unity's XR (OpenVR → the headset) must not start: Minecraft has the headset.</summary>
        private static void KeepXrOff(Harmony harmony)
        {
            try
            {
                var general = UnityEngine.XR.Management.XRGeneralSettings.Instance;
                if (general != null)
                {
                    Traverse.Create(general).Field("m_InitManagerOnStart").SetValue(false);
                    var manager = general.Manager;
                    if (manager != null && manager.isInitializationComplete)
                    {
                        manager.StopSubsystems();
                        manager.DeinitializeLoader();
                    }
                }
                var mgr = typeof(UnityEngine.XR.Management.XRManagerSettings);
                harmony.Patch(AccessTools.Method(mgr, "InitializeLoaderSync"), prefix: new HarmonyMethod(typeof(MinecraftVr), nameof(Skip)));
                harmony.Patch(AccessTools.Method(mgr, "InitializeLoader"), prefix: new HarmonyMethod(typeof(MinecraftVr), nameof(SkipCoroutine)));
                harmony.Patch(AccessTools.Method(mgr, "StartSubsystems"), prefix: new HarmonyMethod(typeof(MinecraftVr), nameof(Skip)));
            }
            catch (System.Exception e)
            {
                Plugin.Log.LogWarning("Couldn't keep Unity XR off: " + e);
            }
            // SteamVR's own plugin can start OpenVR by itself.
            foreach (string name in new[] { "Valve.VR.SteamVR:Initialize", "Valve.VR.SteamVR_Behaviour:Initialize", "Valve.VR.SteamVR_Input:Initialize" })
            {
                try
                {
                    var m = AccessTools.Method(name);
                    if (m != null)
                        harmony.Patch(m, prefix: new HarmonyMethod(typeof(MinecraftVr), nameof(Skip)));
                }
                catch (System.Exception e)
                {
                    Plugin.Log.LogWarning($"Couldn't stop {name}: {e.Message}");
                }
            }
        }

        private static bool Skip() => false;

        private static bool SkipCoroutine(ref IEnumerator __result)
        {
            __result = Enumerable.Empty<object>().GetEnumerator();
            return false;
        }

        public static void SetTracking(Messages.Tracking t) => tracking = t;

        public static Messages.Tracking Tracking => tracking;

        // OpenXR (right-handed, -Z forward) → Unity (left-handed, +Z forward).
        private static Vector3 Pos(float[] p) => new Vector3(p[0], p[1], -p[2]);
        private static Quaternion Rot(float[] p) => new Quaternion(-p[3], -p[4], p[5], p[6]);

        /// <summary>The head camera: what Gorilla Tag's locomotion follows.</summary>
        public static Transform Head()
        {
            GTPlayer p = GTPlayer.Instance;
            if (p == null)
                return null;
            return p.mainCamera != null ? p.mainCamera.transform : p.headCollider.transform;
        }

        /// <summary>The tracking space (the head camera's parent), which Gorilla Tag's physics moves around.</summary>
        public static Transform Rig()
        {
            Transform head = Head();
            return head != null ? head.parent : null;
        }

        /// <summary>Before Gorilla Tag's locomotion: head and hands where Minecraft says they are.</summary>
        private static void ApplyPrefix()
        {
            Messages.Tracking t = tracking;
            GTPlayer p = GTPlayer.Instance;
            Transform head = Head(), rig = Rig();
            if (t == null || p == null || head == null || rig == null)
                return;
            if (!loggedRig)
            {
                loggedRig = true;
                Plugin.Log.LogInfo($"Head {Path(head)}, rig {Path(rig)}, hands {Path(p.GetControllerTransform(true))} / {Path(p.GetControllerTransform(false))}");
            }
            head.localPosition = Pos(t.Head);
            head.localRotation = Rot(t.Head);
            if (t.LeftValid)
                Place(p.GetControllerTransform(true), rig, t.Left);
            if (t.RightValid)
                Place(p.GetControllerTransform(false), rig, t.Right);
        }

        private static void Place(Transform hand, Transform rig, float[] pose)
        {
            if (hand == null)
                return;
            hand.SetPositionAndRotation(rig.TransformPoint(Pos(pose)), rig.rotation * Rot(pose));
        }

        private static string Path(Transform t)
        {
            var names = new List<string>();
            for (; t != null; t = t.parent)
                names.Add(t.name);
            names.Reverse();
            return string.Join("/", names);
        }

        private static readonly System.Reflection.MethodInfo updatePressFlags = AccessTools.Method(typeof(ControllerInputPoller), "_UpdatePressFlags");
        private static readonly AccessTools.FieldRef<ControllerInputPoller, List<System.Action>> onUpdate =
            AccessTools.FieldRefAccess<ControllerInputPoller, List<System.Action>>("onUpdate");
        private static readonly AccessTools.FieldRef<ControllerInputPoller, List<System.Action>> onUpdateNext =
            AccessTools.FieldRefAccess<ControllerInputPoller, List<System.Action>>("onUpdateNext");
        private static readonly AccessTools.FieldRef<ControllerInputPoller, bool> didModifyOnUpdate =
            AccessTools.FieldRefAccess<ControllerInputPoller, bool>("didModifyOnUpdate");

        /// <summary>Gorilla Tag's controller input, from Minecraft's controllers instead of SteamVR.</summary>
        private static bool PollerPrefix(ControllerInputPoller __instance)
        {
            Messages.Tracking t = tracking ?? new Messages.Tracking();
            var c = __instance;
            c.leftControllerIsValid = t.LeftValid;
            c.rightControllerIsValid = t.RightValid;
            c.leftControllerPrimaryButton = t.Button(0);
            c.rightControllerPrimaryButton = t.Button(1);
            c.leftControllerSecondaryButton = t.Button(2);
            c.rightControllerSecondaryButton = t.Button(3);
            c.leftControllerPrimaryButtonTouch = c.leftControllerPrimaryButton;
            c.rightControllerPrimaryButtonTouch = c.rightControllerPrimaryButton;
            c.leftControllerSecondaryButtonTouch = c.leftControllerSecondaryButton;
            c.rightControllerSecondaryButtonTouch = c.rightControllerSecondaryButton;
            c.leftControllerIndexFloat = t.Trigger[0];
            c.rightControllerIndexFloat = t.Trigger[1];
            c.leftControllerGripFloat = t.Grip[0];
            c.rightControllerGripFloat = t.Grip[1];
            c.leftControllerIndexTouch = t.Trigger[0] > 0.05f ? 1f : 0f;
            c.rightControllerIndexTouch = t.Trigger[1] > 0.05f ? 1f : 0f;
            c.leftControllerTriggerButton = t.Trigger[0] > 0.5f;
            c.rightControllerTriggerButton = t.Trigger[1] > 0.5f;
            c.leftControllerPrimary2DAxis = new Vector2(t.StickX[0], t.StickY[0]);
            c.rightControllerPrimary2DAxis = new Vector2(t.StickX[1], t.StickY[1]);
            c.rightStickLRFloat = t.StickX[1];
            c.RecalculateGrabState();
            try
            {
                updatePressFlags?.Invoke(c, null);
            }
            catch (System.Exception)
            {
            }
            if (didModifyOnUpdate(c))
            {
                var next = onUpdateNext(c);
                onUpdateNext(c) = onUpdate(c);
                onUpdate(c) = next;
                didModifyOnUpdate(c) = false;
            }
            foreach (System.Action a in onUpdate(c))
                a();
            return false;
        }

        /// <summary>
        /// Gorilla Tag's rig as a 3x4 matrix, row-major: OpenXR tracking space → Minecraft. Minecraft
        /// puts its camera (and so your eyes) through this, so where you are is where Gorilla Tag's
        /// physics put the gorilla.
        /// </summary>
        public static double[] RigToMinecraft(Coords coords)
        {
            Transform rig = Rig();
            if (rig == null)
                return null;
            float inv = 1f / coords.Scale;
            // Unity world → Minecraft: x' = (x-Ax)/s + Ox, y' = (y-Ay)/s + Oy, z' = -(z-Az)/s + Oz (in doubles for the far-away origin).
            Matrix4x4 m = Matrix4x4.Scale(new Vector3(inv, inv, -inv)) * Matrix4x4.Translate(-coords.Anchor)
                * rig.localToWorldMatrix * Matrix4x4.Scale(new Vector3(1, 1, -1));
            return new double[]
            {
                m.m00, m.m01, m.m02, m.m03 + coords.OriginX,
                m.m10, m.m11, m.m12, m.m13 + coords.OriginY,
                m.m20, m.m21, m.m22, m.m23 + coords.OriginZ,
            };
        }
    }
}
