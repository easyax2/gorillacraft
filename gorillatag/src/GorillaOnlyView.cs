using System.Collections.Generic;
using UnityEngine;

namespace GorillaCraft
{
    /// <summary>
    /// While you're in the Minecraft map, Gorilla Tag draws nothing of its own except gorillas
    /// (bodies, cosmetics, name tags): its cameras only see the layers gorilla rigs use, plus
    /// GorillaCraft's layer (the Minecraft picture, the block highlight, the wrist display), on
    /// black instead of Gorilla Tag's sky. Restored when you leave.
    /// </summary>
    public sealed class GorillaOnlyView
    {
        /// <summary>Unnamed in Gorilla Tag, so nothing of its own is on it.</summary>
        public const int Layer = 3;

        private readonly Dictionary<Camera, (int mask, CameraClearFlags clear, Color color, float far)> saved = new Dictionary<Camera, (int, CameraClearFlags, Color, float)>();
        private int rigMask;
        private int loggedMask;
        private float nextRefresh;
        public bool Active { get; private set; }

        public void Enable()
        {
            Active = true;
            nextRefresh = 0f;
        }

        public void Disable()
        {
            Active = false;
            foreach (var kv in saved)
            {
                if (kv.Key == null)
                    continue;
                kv.Key.cullingMask = kv.Value.mask;
                kv.Key.clearFlags = kv.Value.clear;
                kv.Key.backgroundColor = kv.Value.color;
                kv.Key.farClipPlane = kv.Value.far;
            }
            saved.Clear();
        }

        /// <summary>Call every frame. Rigs come and go (and change cosmetics), so this refreshes now and then.</summary>
        public void Update()
        {
            if (!Active)
                return;
            if (Time.unscaledTime >= nextRefresh)
            {
                nextRefresh = Time.unscaledTime + 2f;
                rigMask = 0;
                foreach (VRRig rig in Object.FindObjectsByType<VRRig>(FindObjectsInactive.Exclude, FindObjectsSortMode.None))
                    foreach (Renderer r in rig.GetComponentsInChildren<Renderer>(true))
                        rigMask |= 1 << r.gameObject.layer;
                rigMask |= 1 << Layer;
                if (rigMask != loggedMask)
                {
                    loggedMask = rigMask;
                    var names = new List<string>();
                    for (int i = 0; i < 32; i++)
                        if ((rigMask & 1 << i) != 0)
                            names.Add(i + ":" + LayerMask.LayerToName(i));
                    Plugin.Log.LogInfo("Gorilla-only view keeps layers " + string.Join(", ", names));
                }
            }
            foreach (Camera cam in Camera.allCameras)
            {
                if (cam.targetTexture != null && !cam.stereoEnabled && cam != Camera.main)
                    continue; // mirrors, monitors and the like: leave them
                if (!saved.ContainsKey(cam))
                    saved[cam] = (cam.cullingMask, cam.clearFlags, cam.backgroundColor, cam.farClipPlane);
                int mask = saved[cam].mask & rigMask | 1 << Layer;
                if (cam.cullingMask != mask)
                    cam.cullingMask = mask;
                cam.clearFlags = CameraClearFlags.SolidColor;
                cam.backgroundColor = Color.black;
                // Minecraft's sky sits 300 blocks out; Gorilla Tag's far plane would clip it into a black disc.
                if (cam.farClipPlane < 2000f)
                    cam.farClipPlane = 2000f;
            }
        }

        /// <summary>Puts a GorillaCraft object (and its children) on the layer the cameras keep.</summary>
        public static void Assign(GameObject go)
        {
            foreach (Transform t in go.GetComponentsInChildren<Transform>(true))
                t.gameObject.layer = Layer;
        }
    }
}
