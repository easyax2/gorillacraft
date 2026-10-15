using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.RenderGraphModule;
using UnityEngine.Rendering.Universal;

namespace GorillaCraft
{
    /// <summary>
    /// Shows Minecraft's own picture of the world in Gorilla Tag, one per eye. Minecraft draws
    /// alternately from your left and right eye; each picture comes with a grid of 3D points
    /// (where each part of it is), and is stretched over a mesh of those points: a world-fixed 3D
    /// surface, so turning your head stays smooth between Minecraft's frames.
    ///
    /// Each eye only ever sees its own picture: a render pass draws the left surface while Unity
    /// renders the left eye and the right one for the right eye. That needs Gorilla Tag to render
    /// the eyes one after the other (multi-pass), which the plugin sets before VR starts. If the
    /// eyes are rendered together anyway (single-pass), or in the desktop view, one picture is
    /// used for both.
    ///
    /// Cells that span a real depth edge (a block in front of something far away, or against the
    /// sky) would otherwise be stretched into a slanted "curtain" that smears as soon as the eye
    /// moves. Instead such a cell gets its own corners, each slid back along its own camera ray
    /// to the far side's depth: from where Minecraft drew the picture it still looks exactly the
    /// same (a point moved along its ray lands on the same pixel), but from your eye it behaves
    /// like the background behind the edge. No holes, no curtains; only the outermost pixels of a
    /// silhouette ride on the background. Flat surfaces seen at a low angle and block corners have
    /// no real jump and are left alone.
    /// </summary>
    public sealed class OverlayView
    {
        private sealed class Surface
        {
            public Mesh Mesh;
            public Material Material;
            public Texture2D Texture;
            public Vector3[] Vertices = new Vector3[0];
            public Vector2[] Uvs = new Vector2[0];
            public readonly List<int> Triangles = new List<int>();
            public int GridW, GridH;
            public Matrix4x4 LocalToWorld = Matrix4x4.identity;
            public bool HasFrame;
            public bool BottomUp;
            public float[] Points = new float[0];
        }

        private readonly Surface[] surfaces = new Surface[3]; // left, right, centre
        private readonly EyePass pass;
        public bool Visible { get; set; }
        /// <summary>Show the left picture to the right eye and vice versa (if the views look inside out).</summary>
        public bool SwapEyes;

        /// <summary>Whether the last frame was drawn per eye (the render pass saw separate eye passes).</summary>
        public bool PerEyeSeen => pass.SawMultiPass;

        public OverlayView(Shader shader)
        {
            for (int i = 0; i < 3; i++)
            {
                var m = MinecraftWorld.MakeMaterial(shader, 0, Texture2D.blackTexture);
                if (m.HasProperty("_Cull"))
                    m.SetFloat("_Cull", 0f);
                var mesh = new Mesh { indexFormat = IndexFormat.UInt32 };
                mesh.MarkDynamic();
                surfaces[i] = new Surface { Mesh = mesh, Material = m };
            }
            pass = new EyePass(this) { renderPassEvent = RenderPassEvent.AfterRenderingOpaques };
            RenderPipelineManager.beginCameraRendering += OnBeginCamera;
        }

        private void OnBeginCamera(ScriptableRenderContext context, Camera camera)
        {
            if (!Visible || camera.cameraType != CameraType.Game)
                return;
            if (camera.targetTexture != null && !camera.stereoEnabled)
                return; // mirrors and screens in Gorilla Tag
            var data = camera.GetUniversalAdditionalCameraData();
            data?.scriptableRenderer?.EnqueuePass(pass);
        }

        /// <summary>A picture from the socket (rows top-down).</summary>
        public void Show(Messages.Frame f, Coords coords)
        {
            Surface s = Prepare(f.Eye, f.Width, f.Height, f.GridW, f.GridH, false);
            s.Texture.LoadRawTextureData(f.Rgba);
            s.Texture.Apply(false);
            Finish(s, f.Points, f.CamX, f.CamY, f.CamZ, coords);
        }

        /// <summary>A picture straight from shared memory (rows bottom-up, as Unity loads them).</summary>
        public void Show(SharedFrames.Slot f, Coords coords)
        {
            if (f.Width <= 0 || f.GridW < 2 || f.GridH < 2)
                return;
            Surface s = Prepare(f.Eye, f.Width, f.Height, f.GridW, f.GridH, f.BottomUp);
            s.Texture.LoadRawTextureData(f.Pixels, f.Width * f.Height * 4);
            s.Texture.Apply(false);
            int n = f.GridW * f.GridH * 3;
            if (s.Points.Length != n)
                s.Points = new float[n];
            System.Runtime.InteropServices.Marshal.Copy(f.Points, s.Points, 0, n);
            Finish(s, s.Points, f.CamX, f.CamY, f.CamZ, coords);
        }

        private Surface Prepare(int eyeIndex, int width, int height, int gridW, int gridH, bool bottomUp)
        {
            int eye = Mathf.Clamp(eyeIndex, 0, 2);
            Surface s = surfaces[eye];
            var f = (Width: width, Height: height, GridW: gridW, GridH: gridH);
            if (s.Texture == null || s.Texture.width != f.Width || s.Texture.height != f.Height)
            {
                if (s.Texture != null)
                    Object.Destroy(s.Texture);
                s.Texture = new Texture2D(f.Width, f.Height, TextureFormat.RGBA32, false) { filterMode = FilterMode.Bilinear, wrapMode = TextureWrapMode.Clamp };
                s.Material.mainTexture = s.Texture;
                if (s.Material.HasProperty("_BaseMap"))
                    s.Material.SetTexture("_BaseMap", s.Texture);
            }
            if (f.GridW != s.GridW || f.GridH != s.GridH || bottomUp != s.BottomUp)
            {
                s.BottomUp = bottomUp;
                int n = f.GridW * f.GridH;
                s.GridW = f.GridW;
                s.GridH = f.GridH;
                // The grid's points, then room for 4 extra corners per cell (edge cells).
                int cells = (f.GridW - 1) * (f.GridH - 1);
                s.Vertices = new Vector3[n + cells * 4];
                s.Uvs = new Vector2[n + cells * 4];
                var uvs = s.Uvs;
                // Grid rows go top to bottom. Unity puts the first pixel row at v = 0: that's the
                // top for socket frames, the bottom for shared-memory ones.
                for (int y = 0; y < s.GridH; y++)
                    for (int x = 0; x < s.GridW; x++)
                    {
                        float v = y / (s.GridH - 1f);
                        uvs[y * s.GridW + x] = new Vector2(x / (s.GridW - 1f), bottomUp ? 1f - v : v);
                    }
                s.Mesh.Clear();
            }
            return s;
        }

        /// <summary>Push edge cells back to the far depth (see the class comment). Off: plain stretched grid.</summary>
        public static bool CutEdges = true;

        private static void Finish(Surface s, float[] points, double camX, double camY, double camZ, Coords coords)
        {
            // Points are relative to the camera Minecraft drew from, so floats stay small.
            int n = s.GridW * s.GridH;
            for (int i = 0; i < n; i++)
                s.Vertices[i] = new Vector3(points[i * 3], points[i * 3 + 1], -points[i * 3 + 2]); // flip Z
            s.Triangles.Clear();
            int count = n;
            for (int y = 0; y < s.GridH - 1; y++)
            {
                for (int x = 0; x < s.GridW - 1; x++)
                {
                    int a = y * s.GridW + x, b = a + 1, c = a + s.GridW, d = c + 1;
                    if (CutEdges && !Continuous(s.Vertices, a, b, c, d))
                    {
                        // Its own corners, each slid back along its ray to the cell's farthest depth.
                        float far = Mathf.Max(Mathf.Max(s.Vertices[a].magnitude, s.Vertices[b].magnitude),
                            Mathf.Max(s.Vertices[c].magnitude, s.Vertices[d].magnitude));
                        int e = count;
                        PushBack(s, a, count++, far);
                        PushBack(s, b, count++, far);
                        PushBack(s, c, count++, far);
                        PushBack(s, d, count++, far);
                        a = e; b = e + 1; c = e + 2; d = e + 3;
                    }
                    s.Triangles.Add(a); s.Triangles.Add(b); s.Triangles.Add(c);
                    s.Triangles.Add(b); s.Triangles.Add(d); s.Triangles.Add(c);
                }
            }
            s.Mesh.Clear();
            s.Mesh.SetVertices(s.Vertices, 0, count);
            s.Mesh.SetUVs(0, s.Uvs, 0, count);
            s.Mesh.SetTriangles(s.Triangles, 0, false);
            s.Mesh.RecalculateBounds();
            s.LocalToWorld = Matrix4x4.TRS(coords.ToUnity(camX, camY, camZ), Quaternion.identity, Vector3.one * coords.Scale);
            s.HasFrame = true;
        }

        private static void PushBack(Surface s, int from, int to, float distance)
        {
            s.Vertices[to] = s.Vertices[from].normalized * distance;
            s.Uvs[to] = s.Uvs[from];
        }

        private const float Sky = 290f; // Minecraft puts sky points 300 blocks out

        /// <summary>
        /// Whether a cell's four corners are one surface. A real edge is a big jump in distance
        /// whose corners don't lie on one plane; a floor seen at a low angle has a big jump too,
        /// but stays flat.
        /// </summary>
        private static bool Continuous(Vector3[] v, int a, int b, int c, int d)
        {
            float da = v[a].magnitude, db = v[b].magnitude, dc = v[c].magnitude, dd = v[d].magnitude;
            bool sa = da > Sky, sb = db > Sky, sc = dc > Sky, sd = dd > Sky;
            if (sa || sb || sc || sd)
                return sa && sb && sc && sd; // all sky; a silhouette against the sky is an edge
            float min = Mathf.Min(Mathf.Min(da, db), Mathf.Min(dc, dd));
            float max = Mathf.Max(Mathf.Max(da, db), Mathf.Max(dc, dd));
            if (max <= min * 1.08f + 0.05f)
                return true;
            Vector3 normal = Vector3.Cross(v[b] - v[a], v[c] - v[a]);
            float len = normal.magnitude;
            if (len < 1e-8f)
                return false;
            float off = Mathf.Abs(Vector3.Dot(v[d] - v[a], normal / len));
            return off < 0.02f * min + 0.03f;
        }

        /// <summary>The surface for one eye (0 left, 1 right), or the best stand-in when that one has no picture yet.</summary>
        private Surface For(int eye)
        {
            if (eye >= 0 && eye < 2 && surfaces[eye].HasFrame)
                return surfaces[eye];
            if (surfaces[2].HasFrame)
                return surfaces[2];
            if (surfaces[0].HasFrame)
                return surfaces[0];
            return surfaces[1].HasFrame ? surfaces[1] : null;
        }

        /// <summary>Draws the right eye's picture for the eye Unity is rendering right now.</summary>
        private sealed class EyePass : ScriptableRenderPass
        {
            private readonly OverlayView view;
            public bool SawMultiPass;

            private sealed class PassData
            {
                public Mesh Mesh;
                public Material Material;
                public Matrix4x4 Matrix;
            }

            public EyePass(OverlayView view)
            {
                this.view = view;
            }

            public override void RecordRenderGraph(RenderGraph renderGraph, ContextContainer frameData)
            {
                var cameraData = frameData.Get<UniversalCameraData>();
                var resources = frameData.Get<UniversalResourceData>();
                int eye = -1;
                if (cameraData.xr != null && cameraData.xr.enabled && !cameraData.xr.singlePassEnabled)
                {
                    eye = cameraData.xr.multipassId;
                    if (view.SwapEyes)
                        eye = 1 - eye;
                    SawMultiPass = true;
                }
                Surface s = view.For(eye);
                if (s == null)
                    return;
                using (var builder = renderGraph.AddRasterRenderPass<PassData>("GorillaCraft Minecraft view", out var data))
                {
                    data.Mesh = s.Mesh;
                    data.Material = s.Material;
                    data.Matrix = s.LocalToWorld;
                    builder.SetRenderAttachment(resources.activeColorTexture, 0, AccessFlags.Write);
                    builder.SetRenderAttachmentDepth(resources.activeDepthTexture, AccessFlags.ReadWrite);
                    builder.AllowPassCulling(false);
                    builder.SetRenderFunc((PassData d, RasterGraphContext ctx) => ctx.cmd.DrawMesh(d.Mesh, d.Matrix, d.Material, 0, 0));
                }
            }
        }
    }
}
