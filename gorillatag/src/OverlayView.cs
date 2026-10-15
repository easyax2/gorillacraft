using System.Collections.Generic;
using UnityEngine;

namespace GorillaCraft
{
    /// <summary>
    /// Shows Minecraft's own picture of the world in Gorilla Tag. Each frame comes with a grid of
    /// 3D points (where each part of the picture is), so the picture is stretched over a mesh of
    /// those points: a world-fixed 3D surface rather than a flat screen. Both eyes see it with
    /// correct depth, turning your head is smooth even though Minecraft's frames arrive a little
    /// later, and gorillas and hands sort against it properly.
    ///
    /// Every cell of the grid is drawn, edges included: seen from where Minecraft drew the frame,
    /// the surface shows exactly Minecraft's pixels, whatever its depth. Leaving cells out made
    /// holes where Gorilla Tag's own sky showed through.
    /// </summary>
    public sealed class OverlayView
    {
        private readonly GameObject go;
        private readonly Mesh mesh;
        private readonly Material material;
        private Texture2D texture;
        private Vector3[] vertices = new Vector3[0];
        private Vector2[] uvs = new Vector2[0];
        private readonly List<int> triangles = new List<int>();
        private int gridW, gridH;

        public OverlayView(Shader shader)
        {
            go = new GameObject("GorillaCraft Minecraft view");
            Object.DontDestroyOnLoad(go);
            mesh = new Mesh { indexFormat = UnityEngine.Rendering.IndexFormat.UInt32 };
            mesh.MarkDynamic();
            go.AddComponent<MeshFilter>().sharedMesh = mesh;
            var mr = go.AddComponent<MeshRenderer>();
            material = MinecraftWorld.MakeMaterial(shader, 0, Texture2D.blackTexture);
            if (material.HasProperty("_Cull"))
                material.SetFloat("_Cull", 0f);
            mr.sharedMaterial = material;
            mr.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            mr.receiveShadows = false;
            GorillaOnlyView.Assign(go);
            go.SetActive(false);
        }

        public bool Visible
        {
            get => go.activeSelf;
            set => go.SetActive(value);
        }

        public void Show(Messages.Frame f, Coords coords)
        {
            if (texture == null || texture.width != f.Width || texture.height != f.Height)
            {
                if (texture != null)
                    Object.Destroy(texture);
                texture = new Texture2D(f.Width, f.Height, TextureFormat.RGB24, false) { filterMode = FilterMode.Bilinear, wrapMode = TextureWrapMode.Clamp };
                material.mainTexture = texture;
                if (material.HasProperty("_BaseMap"))
                    material.SetTexture("_BaseMap", texture);
            }
            texture.LoadRawTextureData(f.Rgb);
            texture.Apply(false);

            int n = f.GridW * f.GridH;
            if (f.GridW != gridW || f.GridH != gridH)
            {
                gridW = f.GridW;
                gridH = f.GridH;
                vertices = new Vector3[n];
                uvs = new Vector2[n];
                // The picture's first row is the top; Unity loads the first row at v = 0.
                for (int y = 0; y < gridH; y++)
                    for (int x = 0; x < gridW; x++)
                        uvs[y * gridW + x] = new Vector2(x / (gridW - 1f), y / (gridH - 1f));
                mesh.Clear();
            }

            // Points are relative to the camera, so the mesh sits at the camera and floats stay small.
            go.transform.position = coords.ToUnity(f.CamX, f.CamY, f.CamZ);
            go.transform.localScale = Vector3.one * coords.Scale;
            for (int i = 0; i < n; i++)
            {
                float x = f.Points[i * 3], y = f.Points[i * 3 + 1], z = f.Points[i * 3 + 2];
                vertices[i] = new Vector3(x, y, -z); // Minecraft → Unity: flip Z
            }

            if (triangles.Count != (gridW - 1) * (gridH - 1) * 6)
            {
                triangles.Clear();
                for (int y = 0; y < gridH - 1; y++)
                {
                    for (int x = 0; x < gridW - 1; x++)
                    {
                        int a = y * gridW + x, b = a + 1, c = a + gridW, d = c + 1;
                        triangles.Add(a); triangles.Add(b); triangles.Add(c);
                        triangles.Add(b); triangles.Add(d); triangles.Add(c);
                    }
                }
                mesh.SetVertices(vertices);
                mesh.SetUVs(0, uvs);
                mesh.SetTriangles(triangles, 0, false);
            }
            mesh.SetVertices(vertices);
            mesh.RecalculateBounds();
        }
    }
}
