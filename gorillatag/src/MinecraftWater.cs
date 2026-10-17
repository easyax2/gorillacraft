using System.Collections.Generic;
using GorillaLocomotion;
using GorillaLocomotion.Swimming;
using HarmonyLib;
using UnityEngine;

namespace GorillaCraft
{
    /// <summary>
    /// Minecraft's water as Gorilla Tag water, so you swim in it the Gorilla Tag way: Gorilla
    /// Tag's own WaterVolume (floating, swim strokes, surface jumps, currents off). One volume per
    /// water surface height in a section, made of trigger boxes on Gorilla Tag's water layer with
    /// a flat surface plane on top. Splashes, ripples and drips are off: nothing here is drawn.
    /// </summary>
    internal static class MinecraftWater
    {
        private static WaterParameters parameters;
        private static int waterLayer = -1;
        private static bool warned;

        /// <summary>
        /// Gorilla Tag's water plays ripples (and splashes, drips) whatever its parameters say, from
        /// effect prefabs ours doesn't have: that throws, and the exception stops the water from
        /// handling the rest of the gorilla that frame (no swimming). Skip effects without a prefab.
        /// </summary>
        public static void PatchEffects(Harmony harmony)
        {
            var t = typeof(WaterOverlappingCollider);
            foreach (string name in new[] { "PlayRippleEffect", "PlaySplashEffect", "PlayDripEffect" })
            {
                var m = AccessTools.Method(t, name);
                if (m != null)
                    harmony.Patch(m, prefix: new HarmonyMethod(typeof(MinecraftWater), nameof(OnlyWithPrefab)));
            }
        }

        private static bool OnlyWithPrefab(object[] __args)
        {
            if (__args.Length == 0)
                return true;
            var prefab = __args[0] as GameObject;
            return prefab != null; // null (or destroyed): skip the effect
        }

        private static int WaterLayer()
        {
            if (waterLayer >= 0)
                return waterLayer;
            GTPlayer p = GTPlayer.Instance;
            int mask = p != null ? p.waterLayer.value : 0;
            for (int i = 0; i < 32; i++)
                if ((mask & (1 << i)) != 0)
                    return waterLayer = i;
            int named = LayerMask.NameToLayer("Water");
            return named >= 0 ? waterLayer = named : -1;
        }

        private static WaterParameters Parameters()
        {
            if (parameters != null)
                return parameters;
            parameters = ScriptableObject.CreateInstance<WaterParameters>();
            parameters.playSplashEffect = false;
            parameters.playRippleEffect = false;
            parameters.playDripEffect = false;
            parameters.sendSplashEffectRPCs = false;
            Object.DontDestroyOnLoad(parameters);
            return parameters;
        }

        /// <summary>Adds the section's water under its GameObject (section-local Minecraft units, Z flipped).</summary>
        public static void Build(GameObject section, Messages.WaterRun[] runs)
        {
            int layer = WaterLayer();
            if (layer < 0)
            {
                if (!warned)
                {
                    warned = true;
                    Plugin.Log.LogWarning("Gorilla Tag's water layer not found: no swimming in Minecraft's water");
                }
                return;
            }
            // One volume per surface height (Gorilla Tag water has one flat surface per volume).
            var bySurface = new Dictionary<int, List<Messages.WaterRun>>();
            foreach (var r in runs)
            {
                int surface = r.Top * 16 + r.Height; // in 1/16 block
                if (!bySurface.TryGetValue(surface, out var list))
                    bySurface[surface] = list = new List<Messages.WaterRun>();
                list.Add(r);
            }
            foreach (var kv in bySurface)
            {
                float surfaceY = kv.Key / 16f;
                var go = new GameObject("Minecraft water");
                go.SetActive(false); // the WaterVolume wakes up once everything is set
                go.layer = layer;
                go.transform.SetParent(section.transform, false);
                var colliders = new List<Collider>();
                foreach (var r in Merge(kv.Value))
                {
                    var box = go.AddComponent<BoxCollider>();
                    box.isTrigger = true;
                    float y0 = r.y0, y1 = surfaceY;
                    box.center = new Vector3(r.x0 + r.x1 * 0.5f, (y0 + y1) * 0.5f, -(r.z + 0.5f));
                    box.size = new Vector3(r.x1, y1 - y0, 1f);
                    colliders.Add(box);
                }
                var plane = new GameObject("Surface").transform;
                plane.SetParent(go.transform, false);
                plane.localPosition = new Vector3(8f, surfaceY, -8f);
                var volume = go.AddComponent<WaterVolume>();
                volume.surfacePlane = plane;
                volume.volumeColliders = colliders;
                Traverse.Create(volume).Field("waterParams").SetValue(Parameters());
                go.SetActive(true);
                built++;
            }
            if (!logged)
            {
                logged = true;
                Plugin.Log.LogInfo($"Minecraft water is Gorilla Tag water (layer {LayerMask.LayerToName(layer)}): first volume in {section.name}");
            }
        }

        private static int built;
        private static bool logged;

        public static int Built => built;

        private struct Box
        {
            public int x0, x1, z, y0; // x1 = width
        }

        /// <summary>Runs side by side along X with the same bottom become one box.</summary>
        private static IEnumerable<Box> Merge(List<Messages.WaterRun> runs)
        {
            runs.Sort((a, b) => a.Z != b.Z ? a.Z.CompareTo(b.Z) : a.Bottom != b.Bottom ? a.Bottom.CompareTo(b.Bottom) : a.X.CompareTo(b.X));
            Box? cur = null;
            foreach (var r in runs)
            {
                if (cur is Box c && c.z == r.Z && c.y0 == r.Bottom && c.x0 + c.x1 == r.X)
                {
                    c.x1++;
                    cur = c;
                    continue;
                }
                if (cur is Box done)
                    yield return done;
                cur = new Box { x0 = r.X, x1 = 1, z = r.Z, y0 = r.Bottom };
            }
            if (cur is Box last)
                yield return last;
        }
    }
}
