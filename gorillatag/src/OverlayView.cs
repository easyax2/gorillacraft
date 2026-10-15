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
    /// Every cell of the grid is drawn: seen from where Minecraft drew it, the surface shows
    /// exactly Minecraft's pixels.
    /// </summary>
    public sealed class OverlayView
    {
        private sealed class Surface
        {
            public Mesh Mesh;
            public Material Material;
            public Texture2D Texture;
            public Vector3[] Vertices = new Vector3[0];
            public readonly List<int> Triangles = new List<int>();
            public int GridW, GridH;
            public Matrix4x4 LocalToWorld = Matrix4x4.identity;
            public bool HasFrame;
        }

        private readonly Surface[] surfaces = new Surface[3]; // left, right, centre
        private readonly EyePass pass;
        public bool Visible { get; set; }

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

        public void Show(Messages.Frame f, Coords coords)
        {
            int eye = Mathf.Clamp(f.Eye, 0, 2);
            Surface s = surfaces[eye];
            if (s.Texture == null || s.Texture.width != f.Width || s.Texture.height != f.Height)
            {
                if (s.Texture != null)
                    Object.Destroy(s.Texture);
                s.Texture = new Texture2D(f.Width, f.Height, TextureFormat.RGB24, false) { filterMode = FilterMode.Bilinear, wrapMode = TextureWrapMode.Clamp };
                s.Material.mainTexture = s.Texture;
                if (s.Material.HasProperty("_BaseMap"))
                    s.Material.SetTexture("_BaseMap", s.Texture);
            }
            s.Texture.LoadRawTextureData(f.Rgb);
            s.Texture.Apply(false);

            int n = f.GridW * f.GridH;
            if (f.GridW != s.GridW || f.GridH != s.GridH)
            {
                s.GridW = f.GridW;
                s.GridH = f.GridH;
                s.Vertices = new Vector3[n];
                var uvs = new Vector2[n];
                // The picture's first row is the top; Unity loads the first row at v = 0.
                for (int y = 0; y < s.GridH; y++)
                    for (int x = 0; x < s.GridW; x++)
                        uvs[y * s.GridW + x] = new Vector2(x / (s.GridW - 1f), y / (s.GridH - 1f));
                s.Triangles.Clear();
                for (int y = 0; y < s.GridH - 1; y++)
                {
                    for (int x = 0; x < s.GridW - 1; x++)
                    {
                        int a = y * s.GridW + x, b = a + 1, c = a + s.GridW, d = c + 1;
                        s.Triangles.Add(a); s.Triangles.Add(b); s.Triangles.Add(c);
                        s.Triangles.Add(b); s.Triangles.Add(d); s.Triangles.Add(c);
                    }
                }
                s.Mesh.Clear();
                s.Mesh.vertices = s.Vertices;
                s.Mesh.uv = uvs;
                s.Mesh.SetTriangles(s.Triangles, 0, false);
            }

            // Points are relative to the camera Minecraft drew from, so floats stay small.
            for (int i = 0; i < n; i++)
                s.Vertices[i] = new Vector3(f.Points[i * 3], f.Points[i * 3 + 1], -f.Points[i * 3 + 2]); // flip Z
            s.Mesh.vertices = s.Vertices;
            s.Mesh.RecalculateBounds();
            s.LocalToWorld = Matrix4x4.TRS(coords.ToUnity(f.CamX, f.CamY, f.CamZ), Quaternion.identity, Vector3.one * coords.Scale);
            s.HasFrame = true;
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
