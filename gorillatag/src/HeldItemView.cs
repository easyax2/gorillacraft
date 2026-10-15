using GorillaLocomotion;
using UnityEngine;

namespace GorillaCraft
{
    /// <summary>
    /// The item you're holding in Minecraft, in the gorilla's right hand: a little cube with the
    /// block's texture for blocks, a flat picture for items (tools, food...). The texture is
    /// Minecraft's own, from the resource pack it's using.
    /// </summary>
    public sealed class HeldItemView
    {
        private readonly GameObject cube;
        private readonly GameObject flat;
        private readonly Material cubeMaterial;
        private readonly Material flatMaterial;
        private Texture2D texture;
        private int kind;

        public bool Visible { get; set; }

        public HeldItemView(Shader shader)
        {
            cube = GameObject.CreatePrimitive(PrimitiveType.Cube);
            cube.name = "GorillaCraft held block";
            Object.Destroy(cube.GetComponent<Collider>());
            cubeMaterial = MinecraftWorld.MakeMaterial(shader, 1, Texture2D.whiteTexture);
            cube.GetComponent<MeshRenderer>().sharedMaterial = cubeMaterial;

            flat = GameObject.CreatePrimitive(PrimitiveType.Quad);
            flat.name = "GorillaCraft held item";
            Object.Destroy(flat.GetComponent<Collider>());
            flatMaterial = MinecraftWorld.MakeMaterial(shader, 1, Texture2D.whiteTexture);
            if (flatMaterial.HasProperty("_Cull"))
                flatMaterial.SetFloat("_Cull", 0f);
            flat.GetComponent<MeshRenderer>().sharedMaterial = flatMaterial;

            foreach (var go in new[] { cube, flat })
            {
                Object.DontDestroyOnLoad(go);
                GorillaOnlyView.Assign(go);
                go.SetActive(false);
            }
        }

        public void Set(Messages.Held held)
        {
            kind = held.Png == null ? 0 : held.Kind;
            if (kind == 0)
                return;
            if (texture == null)
                texture = new Texture2D(2, 2, TextureFormat.RGBA32, false);
            if (!texture.LoadImage(held.Png))
            {
                kind = 0;
                return;
            }
            texture.filterMode = FilterMode.Point;
            texture.wrapMode = TextureWrapMode.Clamp;
            // Animated textures are a strip of square frames: show the first (the top one).
            float frame = texture.height > texture.width ? texture.width / (float)texture.height : 1f;
            foreach (var m in new[] { cubeMaterial, flatMaterial })
            {
                m.mainTexture = texture;
                m.mainTextureScale = new Vector2(1f, frame);
                m.mainTextureOffset = new Vector2(0f, 1f - frame);
                if (m.HasProperty("_BaseMap"))
                {
                    m.SetTexture("_BaseMap", texture);
                    m.SetVector("_BaseMap_ST", new Vector4(1f, frame, 0f, 1f - frame));
                }
            }
        }

        /// <summary>Follows the gorilla's right hand. Call every frame.</summary>
        public void Update()
        {
            bool show = Visible && kind != 0 && GTPlayer.Instance != null;
            cube.SetActive(show && kind == 2);
            flat.SetActive(show && kind == 1);
            if (!show)
                return;
            Transform hand = GTPlayer.Instance.GetHandFollower(false);
            Transform controller = GTPlayer.Instance.GetControllerTransform(false);
            float scale = controller.parent != null ? controller.parent.lossyScale.x : 1f;
            Quaternion rot = controller.rotation;
            Vector3 pos = hand.position + rot * new Vector3(0f, 0.02f, 0.07f) * scale;
            if (kind == 2)
            {
                cube.transform.SetPositionAndRotation(pos, rot);
                cube.transform.localScale = Vector3.one * 0.07f * scale;
            }
            else
            {
                flat.transform.SetPositionAndRotation(pos + rot * new Vector3(0f, 0.03f, 0f) * scale, rot * Quaternion.Euler(0f, 90f, 0f));
                flat.transform.localScale = Vector3.one * 0.11f * scale;
            }
        }
    }
}
