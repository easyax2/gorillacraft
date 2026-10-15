using GorillaLocomotion;
using UnityEngine;
using UnityEngine.XR;

namespace GorillaCraft
{
    /// <summary>
    /// Minecraft's open screen (inventory, creative inventory, chest, crafting table, furnace...)
    /// on a panel in front of you. Point at it with your right hand: the trigger is the left mouse
    /// button, the grip the right one, the right thumbstick scrolls. It's Minecraft's own screen,
    /// driven through Minecraft's own mouse handling, so everything works as with a mouse.
    /// </summary>
    public sealed class InventoryPanel
    {
        private const float Width = 1.1f; // metres, at gorilla scale 1

        private readonly LinkClient link;
        private readonly GameObject panel;
        private readonly GameObject cursor;
        private readonly GameObject laser;
        private readonly Material panelMaterial;
        private Texture2D texture;
        private float aspect = 0.6f;
        private float nextPointer;
        private float nextScroll;
        private bool placed;

        public bool Open { get; private set; }

        public InventoryPanel(LinkClient link, Shader shader)
        {
            this.link = link;
            panel = GameObject.CreatePrimitive(PrimitiveType.Quad);
            panel.name = "GorillaCraft Minecraft screen";
            Object.Destroy(panel.GetComponent<Collider>());
            panelMaterial = MinecraftWorld.MakeMaterial(shader, 0, Texture2D.blackTexture);
            if (panelMaterial.HasProperty("_Cull"))
                panelMaterial.SetFloat("_Cull", 0f);
            panel.GetComponent<MeshRenderer>().sharedMaterial = panelMaterial;

            cursor = MakeBox("GorillaCraft cursor", shader, new Color(1f, 1f, 0.3f, 1f));
            laser = MakeBox("GorillaCraft laser", shader, new Color(1f, 1f, 1f, 0.5f));
            foreach (var go in new[] { panel, cursor, laser })
            {
                Object.DontDestroyOnLoad(go);
                GorillaOnlyView.Assign(go);
                go.SetActive(false);
            }
        }

        private static GameObject MakeBox(string name, Shader shader, Color color)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            Object.Destroy(go.GetComponent<Collider>());
            var m = MinecraftWorld.MakeMaterial(shader, color.a < 1f ? 2 : 0, Texture2D.whiteTexture);
            m.color = color;
            if (m.HasProperty("_BaseColor"))
                m.SetColor("_BaseColor", color);
            go.GetComponent<MeshRenderer>().sharedMaterial = m;
            return go;
        }

        /// <summary>Asks Minecraft to open the inventory, or to close whatever screen is open.</summary>
        public void Toggle()
        {
            link.Send(new FrameWriter(Proto.GtInventory).U8(Open ? 0 : 1).Finish());
        }

        public void Close()
        {
            if (Open)
                link.Send(new FrameWriter(Proto.GtInventory).U8(0).Finish());
            Hide();
        }

        public void Show(Messages.GuiFrame f)
        {
            if (f.Width <= 0)
            {
                Hide();
                return;
            }
            if (texture == null || texture.width != f.Width || texture.height != f.Height)
            {
                if (texture != null)
                    Object.Destroy(texture);
                texture = new Texture2D(f.Width, f.Height, TextureFormat.RGBA32, false) { filterMode = FilterMode.Bilinear, wrapMode = TextureWrapMode.Clamp };
                panelMaterial.mainTexture = texture;
                if (panelMaterial.HasProperty("_BaseMap"))
                    panelMaterial.SetTexture("_BaseMap", texture);
                // Rows come top first, Unity loads the first row at the bottom: flip the quad's V.
                panelMaterial.mainTextureScale = new Vector2(1f, -1f);
                panelMaterial.mainTextureOffset = new Vector2(0f, 1f);
                if (panelMaterial.HasProperty("_BaseMap_ST"))
                    panelMaterial.SetVector("_BaseMap_ST", new Vector4(1f, -1f, 0f, 1f));
                aspect = f.Height / (float)f.Width;
            }
            texture.LoadRawTextureData(f.Rgba);
            texture.Apply(false);
            if (!Open)
                PlaceInFront();
            Open = true;
            panel.SetActive(true);
        }

        private void Hide()
        {
            Open = false;
            placed = false;
            panel.SetActive(false);
            cursor.SetActive(false);
            laser.SetActive(false);
        }

        /// <summary>Puts the panel in front of the head, level, facing you; it stays there while open.</summary>
        private void PlaceInFront()
        {
            if (placed)
                return;
            placed = true;
            GTPlayer player = GTPlayer.Instance;
            Transform head = player.mainCamera != null ? player.mainCamera.transform : player.headCollider.transform;
            float scale = head.parent != null ? head.parent.lossyScale.x : 1f;
            Vector3 forward = Vector3.ProjectOnPlane(head.forward, Vector3.up).normalized;
            if (forward.sqrMagnitude < 0.01f)
                forward = Vector3.forward;
            panel.transform.position = head.position + forward * 0.65f * scale - Vector3.up * 0.1f * scale;
            panel.transform.rotation = Quaternion.LookRotation(forward, Vector3.up);
            panel.transform.localScale = new Vector3(Width, Width * aspect, 1f) * scale;
        }

        /// <summary>Laser pointer, clicks and scrolling. Call every frame while open.</summary>
        public void Update()
        {
            if (!Open)
                return;
            GTPlayer player = GTPlayer.Instance;
            Transform hand = player.GetControllerTransform(false);
            Vector3 origin = hand.position;
            Vector3 dir = hand.forward;

            var plane = new Plane(-panel.transform.forward, panel.transform.position);
            var ray = new Ray(origin, dir);
            bool hit = plane.Raycast(ray, out float dist) || new Plane(panel.transform.forward, panel.transform.position).Raycast(ray, out dist);
            Vector3 point = ray.GetPoint(hit ? dist : 0.5f);
            Vector3 local = panel.transform.InverseTransformPoint(point); // quad spans -0.5..0.5
            bool onPanel = hit && Mathf.Abs(local.x) <= 0.5f && Mathf.Abs(local.y) <= 0.5f;

            float scale = panel.transform.lossyScale.x / Width;
            float length = hit ? dist : 0.5f * scale;
            laser.SetActive(true);
            laser.transform.position = origin + dir * length * 0.5f;
            laser.transform.rotation = Quaternion.LookRotation(dir);
            laser.transform.localScale = new Vector3(0.004f * scale, 0.004f * scale, length);
            cursor.SetActive(onPanel);
            if (onPanel)
            {
                cursor.transform.position = point - panel.transform.forward * 0.003f * scale;
                cursor.transform.rotation = panel.transform.rotation;
                cursor.transform.localScale = new Vector3(0.012f, 0.012f, 0.002f) * scale;
            }

            int buttons = 0;
            if (ControllerInputPoller.TriggerFloat(XRNode.RightHand) > 0.5f)
                buttons |= 1;
            if (ControllerInputPoller.GripFloat(XRNode.RightHand) > 0.6f)
                buttons |= 2;
            if (onPanel && Time.time >= nextPointer)
            {
                nextPointer = Time.time + 1f / 60f;
                link.Send(new FrameWriter(Proto.GtPointer).F32(local.x + 0.5f).F32(0.5f - local.y).U8(buttons).Finish());
            }

            float stick = ControllerInputPoller.Primary2DAxis(XRNode.RightHand).y;
            if (Mathf.Abs(stick) > 0.5f && Time.time >= nextScroll)
            {
                nextScroll = Time.time + 0.12f;
                link.Send(new FrameWriter(Proto.GtScroll).F32(Mathf.Sign(stick)).Finish());
            }
        }
    }
}
