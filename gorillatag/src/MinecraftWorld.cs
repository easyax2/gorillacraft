using System.Collections.Generic;
using UnityEngine;

namespace GorillaCraft
{
    /// <summary>
    /// Gorilla Tag's copy of the Minecraft world around the player: one GameObject per 16³
    /// section, with Minecraft's own baked block mesh (textured from Minecraft's block atlas) and
    /// a MeshCollider built from the blocks' collision shapes, on a layer gorilla locomotion
    /// climbs on.
    /// </summary>
    public sealed class MinecraftWorld
    {
        public readonly Coords Coords = new Coords();
        public bool HasWorld { get; private set; }
        public string Dimension { get; private set; } = "";

        private readonly Dictionary<(int, int, int), SectionView> sections = new Dictionary<(int, int, int), SectionView>();
        private readonly Material[] materials = new Material[3];
        private readonly GameObject root;
        private readonly int collisionLayer;
        private Texture2D atlas;

        private sealed class SectionView
        {
            public GameObject Go;
            public Mesh Render;
            public Mesh Collision;
            public Dictionary<int, Messages.Box[]> Shapes;
        }

        public MinecraftWorld(int collisionLayer, string shaderName)
        {
            this.collisionLayer = collisionLayer;
            root = new GameObject("GorillaCraft World");
            Object.DontDestroyOnLoad(root);
            root.SetActive(false);

            Shader shader = FindShader(shaderName);
            atlas = new Texture2D(2, 2, TextureFormat.RGBA32, true) { filterMode = FilterMode.Point, wrapMode = TextureWrapMode.Clamp, anisoLevel = 0 };
            for (int i = 0; i < 3; i++)
                materials[i] = MakeMaterial(shader, i, atlas);
        }

        public bool Visible
        {
            get => root.activeSelf;
            set => root.SetActive(value);
        }

        public int SectionCount => sections.Count;

        private static Shader FindShader(string preferred)
        {
            foreach (string name in new[] { preferred, "Universal Render Pipeline/Particles/Unlit", "Sprites/Default", "Unlit/Texture" })
            {
                if (string.IsNullOrEmpty(name))
                    continue;
                Shader s = Shader.Find(name);
                if (s != null)
                {
                    Plugin.Log.LogInfo($"Block shader: {name}");
                    return s;
                }
            }
            Plugin.Log.LogError("No usable shader found for blocks");
            return null;
        }

        /// <summary>Layer 0 solid, 1 cutout (leaves, glass), 2 translucent (water, stained glass).</summary>
        internal static Material MakeMaterial(Shader shader, int layer, Texture texture)
        {
            var m = new Material(shader) { name = "GorillaCraft layer " + layer };
            m.mainTexture = texture;
            if (m.HasProperty("_BaseMap"))
                m.SetTexture("_BaseMap", texture);
            if (m.HasProperty("_BaseColor"))
                m.SetColor("_BaseColor", Color.white);
            if (layer == 1)
            {
                m.SetFloat("_AlphaClip", 1f);
                m.SetFloat("_Cutoff", 0.5f);
                m.EnableKeyword("_ALPHATEST_ON");
                m.renderQueue = 2450;
            }
            else if (layer == 2)
            {
                SetTransparent(m);
            }
            return m;
        }

        internal static void SetTransparent(Material m)
        {
            m.SetFloat("_Surface", 1f);
            m.SetFloat("_Blend", 0f);
            m.SetFloat("_SrcBlend", (float)UnityEngine.Rendering.BlendMode.SrcAlpha);
            m.SetFloat("_DstBlend", (float)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
            m.SetFloat("_SrcBlendAlpha", (float)UnityEngine.Rendering.BlendMode.One);
            m.SetFloat("_DstBlendAlpha", (float)UnityEngine.Rendering.BlendMode.OneMinusSrcAlpha);
            m.SetFloat("_ZWrite", 0f);
            m.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            m.renderQueue = 3000;
        }

        public void SetWorld(Messages.World w)
        {
            Clear();
            HasWorld = w.HasWorld;
            Dimension = w.Dimension;
            Coords.OriginX = w.OriginX;
            Coords.OriginY = w.OriginY;
            Coords.OriginZ = w.OriginZ;
        }

        public void SetAtlas(byte[] png)
        {
            if (!atlas.LoadImage(png))
            {
                Plugin.Log.LogError("Couldn't decode Minecraft's block atlas");
                return;
            }
            atlas.filterMode = FilterMode.Point;
            atlas.wrapMode = TextureWrapMode.Clamp;
            Plugin.Log.LogInfo($"Block atlas {atlas.width}x{atlas.height}");
        }

        public void Clear()
        {
            foreach (var s in sections.Values)
                Destroy(s);
            sections.Clear();
        }

        public void RemoveSection(int x, int y, int z)
        {
            if (sections.TryGetValue((x, y, z), out var s))
            {
                Destroy(s);
                sections.Remove((x, y, z));
            }
        }

        public bool HasSectionAt(int bx, int by, int bz) => sections.ContainsKey((bx >> 4, by >> 4, bz >> 4));

        public void ApplySection(Messages.Section s)
        {
            RemoveSection(s.X, s.Y, s.Z);
            var view = new SectionView { Shapes = s.Shapes };
            sections[(s.X, s.Y, s.Z)] = view;
            if (s.IsEmpty)
                return; // still remembered, so "has this section arrived" works for air

            var go = new GameObject($"Section {s.X} {s.Y} {s.Z}");
            go.layer = collisionLayer;
            go.transform.SetParent(root.transform, false);
            go.transform.position = Coords.ToUnity(s.X * 16, s.Y * 16, s.Z * 16);
            go.transform.localScale = Vector3.one * Coords.Scale;
            view.Go = go;

            view.Render = BuildRenderMesh(s, out Material[] used);
            if (view.Render != null)
            {
                go.AddComponent<MeshFilter>().sharedMesh = view.Render;
                var mr = go.AddComponent<MeshRenderer>();
                mr.sharedMaterials = used;
                mr.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                mr.receiveShadows = false;
            }

            if (s.CollisionQuads > 0)
            {
                view.Collision = BuildCollisionMesh(s);
                var mc = go.AddComponent<MeshCollider>();
                mc.sharedMesh = view.Collision;
            }
            if (s.Water.Length > 0)
                MinecraftWater.Build(go, s.Water);
        }

        private Mesh BuildRenderMesh(Messages.Section s, out Material[] used)
        {
            int total = 0;
            int submeshes = 0;
            foreach (var l in s.Layers)
            {
                total += l.VertexCount;
                if (l.VertexCount > 0)
                    submeshes++;
            }
            used = new Material[submeshes];
            if (total == 0)
                return null;

            var verts = new Vector3[total];
            var uvs = new Vector2[total];
            var colors = new Color32[total];
            var mesh = new Mesh { indexFormat = total > 65000 ? UnityEngine.Rendering.IndexFormat.UInt32 : UnityEngine.Rendering.IndexFormat.UInt16 };
            var indices = new List<int[]>();
            int baseVertex = 0;
            int sub = 0;
            for (int l = 0; l < 3; l++)
            {
                var layer = s.Layers[l];
                if (layer.VertexCount == 0)
                    continue;
                for (int v = 0; v < layer.VertexCount; v++)
                {
                    int i = baseVertex + v;
                    // Minecraft → Unity: flip Z (see Coords).
                    verts[i] = new Vector3(layer.Positions[v * 3], layer.Positions[v * 3 + 1], -layer.Positions[v * 3 + 2]);
                    uvs[i] = new Vector2(layer.Uvs[v * 2], 1f - layer.Uvs[v * 2 + 1]);
                    colors[i] = new Color32(layer.Colors[v * 4], layer.Colors[v * 4 + 1], layer.Colors[v * 4 + 2], layer.Colors[v * 4 + 3]);
                }
                indices.Add(QuadTriangles(layer.VertexCount / 4, baseVertex));
                used[sub++] = materials[l];
                baseVertex += layer.VertexCount;
            }
            mesh.vertices = verts;
            mesh.uv = uvs;
            mesh.colors32 = colors;
            mesh.subMeshCount = indices.Count;
            for (int i = 0; i < indices.Count; i++)
                mesh.SetTriangles(indices[i], i, false);
            mesh.RecalculateBounds();
            return mesh;
        }

        private static Mesh BuildCollisionMesh(Messages.Section s)
        {
            int n = s.CollisionQuads * 4;
            var verts = new Vector3[n];
            for (int v = 0; v < n; v++)
                verts[v] = new Vector3(s.Collision[v * 3], s.Collision[v * 3 + 1], -s.Collision[v * 3 + 2]);
            var mesh = new Mesh { indexFormat = n > 65000 ? UnityEngine.Rendering.IndexFormat.UInt32 : UnityEngine.Rendering.IndexFormat.UInt16 };
            mesh.vertices = verts;
            mesh.SetTriangles(QuadTriangles(s.CollisionQuads, 0), 0, true);
            return mesh;
        }

        /// <summary>
        /// Minecraft quads are counter-clockwise seen from outside. The Z flip mirrors them, so
        /// (0,2,1)(0,3,2) is what keeps the outside the front face for Unity.
        /// </summary>
        private static int[] QuadTriangles(int quads, int baseVertex)
        {
            var t = new int[quads * 6];
            for (int q = 0; q < quads; q++)
            {
                int b = baseVertex + q * 4;
                int o = q * 6;
                t[o] = b;
                t[o + 1] = b + 2;
                t[o + 2] = b + 1;
                t[o + 3] = b;
                t[o + 4] = b + 3;
                t[o + 5] = b + 2;
            }
            return t;
        }

        private static void Destroy(SectionView s)
        {
            if (s.Go != null)
                Object.Destroy(s.Go);
            if (s.Render != null)
                Object.Destroy(s.Render);
            if (s.Collision != null)
                Object.Destroy(s.Collision);
        }

        /// <summary>Outline boxes (block-local) of a block a hand could touch, if it's loaded and exposed.</summary>
        public bool TryGetShape(int bx, int by, int bz, out Messages.Box[] boxes)
        {
            boxes = null;
            if (!sections.TryGetValue((bx >> 4, by >> 4, bz >> 4), out var s) || s.Shapes == null)
                return false;
            int key = (by & 15) << 8 | (bz & 15) << 4 | (bx & 15);
            return s.Shapes.TryGetValue(key, out boxes);
        }
    }
}
