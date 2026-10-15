using GorillaLocomotion;
using UnityEngine;
using UnityEngine.XR;

namespace GorillaCraft
{
    /// <summary>
    /// Turns gorilla hands into Minecraft clicks:
    ///   trigger held with a hand on a block  → mine it (Minecraft's own break speed, tools, drops)
    ///   grip pressed with a hand on a block  → use the held item on it (place a block, open a
    ///                                          door, press a button, flip a lever, ...)
    ///   A / X                                → next / previous hotbar slot
    /// Minecraft has one block being mined at a time, so if both triggers are down the hand that
    /// started first keeps mining.
    /// </summary>
    public sealed class HandInteraction
    {
        private readonly MinecraftWorld world;
        private readonly LinkClient link;
        private readonly float reach;
        private readonly Highlight highlight;

        private bool mining;
        private bool miningLeft;
        private BlockTarget mineTarget;
        private readonly bool[] gripWasDown = new bool[2];
        private readonly bool[] primaryWasDown = new bool[2];
        public int SelectedSlot;

        public HandInteraction(MinecraftWorld world, LinkClient link, float reach, Shader shader)
        {
            this.world = world;
            this.link = link;
            this.reach = reach;
            highlight = new Highlight(shader);
        }

        public void Update()
        {
            GTPlayer player = GTPlayer.Instance;
            if (player == null)
                return;

            bool hasLeft = BlockTargeting.Find(world, player.GetHandFollower(true).position, reach, out BlockTarget left);
            bool hasRight = BlockTargeting.Find(world, player.GetHandFollower(false).position, reach, out BlockTarget right);
            bool trigLeft = ControllerInputPoller.TriggerFloat(XRNode.LeftHand) > 0.5f;
            bool trigRight = ControllerInputPoller.TriggerFloat(XRNode.RightHand) > 0.5f;

            // Mining.
            bool wantMine;
            bool useLeft;
            if (mining && (miningLeft ? trigLeft : trigRight))
            {
                useLeft = miningLeft;
                wantMine = miningLeft ? hasLeft : hasRight;
            }
            else
            {
                useLeft = trigLeft && hasLeft && !(trigRight && hasRight);
                wantMine = useLeft || (trigRight && hasRight);
            }
            BlockTarget t = useLeft ? left : right;
            if (wantMine && (!mining || !t.SameBlock(mineTarget) || t.Face != mineTarget.Face))
            {
                mining = true;
                miningLeft = useLeft;
                mineTarget = t;
                SendMine(true, t);
            }
            else if (!wantMine && mining)
            {
                mining = false;
                SendMine(false, mineTarget);
            }

            // Using / placing, on the grip's press.
            for (int h = 0; h < 2; h++)
            {
                bool isLeft = h == 0;
                bool grip = ControllerInputPoller.GripFloat(isLeft ? XRNode.LeftHand : XRNode.RightHand) > 0.6f;
                if (grip && !gripWasDown[h] && (isLeft ? hasLeft : hasRight))
                    SendUse(isLeft ? left : right);
                gripWasDown[h] = grip;
            }

            // Hotbar.
            bool x = ControllerInputPoller.PrimaryButtonPress(XRNode.LeftHand);
            bool a = ControllerInputPoller.PrimaryButtonPress(XRNode.RightHand);
            if (a && !primaryWasDown[1])
                SelectSlot((SelectedSlot + 1) % 9);
            if (x && !primaryWasDown[0])
                SelectSlot((SelectedSlot + 8) % 9);
            primaryWasDown[0] = x;
            primaryWasDown[1] = a;

            // Show the block the mining hand (or else the right hand) is on.
            if (mining)
                highlight.Show(world, mineTarget, true);
            else if (hasRight)
                highlight.Show(world, right, false);
            else if (hasLeft)
                highlight.Show(world, left, false);
            else
                highlight.Hide();
        }

        public void Stop()
        {
            if (mining)
            {
                mining = false;
                SendMine(false, mineTarget);
            }
            highlight.Hide();
        }

        private void SelectSlot(int slot)
        {
            SelectedSlot = slot;
            link.Send(new FrameWriter(Proto.GtSlot).U8(slot).Finish());
        }

        private void SendMine(bool active, BlockTarget t)
        {
            link.Send(new FrameWriter(Proto.GtMine).U8(active ? 1 : 0).I32(t.X).I32(t.Y).I32(t.Z).U8((int)t.Face).Finish());
        }

        private void SendUse(BlockTarget t)
        {
            link.Send(new FrameWriter(Proto.GtUse).I32(t.X).I32(t.Y).I32(t.Z).U8((int)t.Face)
                .F64(t.HitX).F64(t.HitY).F64(t.HitZ).Finish());
        }

        /// <summary>A see-through box over the targeted block's outline.</summary>
        private sealed class Highlight
        {
            private readonly GameObject go;
            private readonly Material material;

            public Highlight(Shader shader)
            {
                go = GameObject.CreatePrimitive(PrimitiveType.Cube);
                go.name = "GorillaCraft Highlight";
                Object.Destroy(go.GetComponent<Collider>());
                Object.DontDestroyOnLoad(go);
                material = MinecraftWorld.MakeMaterial(shader, 2, Texture2D.whiteTexture);
                go.GetComponent<MeshRenderer>().sharedMaterial = material;
                go.GetComponent<MeshRenderer>().shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                go.SetActive(false);
            }

            public void Show(MinecraftWorld world, BlockTarget t, bool mining)
            {
                var b = t.Box;
                const float grow = 0.01f;
                Vector3 min = world.Coords.ToUnity(t.X + b.MinX - grow, t.Y + b.MinY - grow, t.Z + b.MinZ - grow);
                Vector3 max = world.Coords.ToUnity(t.X + b.MaxX + grow, t.Y + b.MaxY + grow, t.Z + b.MaxZ + grow);
                go.transform.position = (min + max) * 0.5f;
                go.transform.localScale = new Vector3(Mathf.Abs(max.x - min.x), Mathf.Abs(max.y - min.y), Mathf.Abs(max.z - min.z));
                Color c = mining ? new Color(1f, 0.55f, 0.2f, 0.35f) : new Color(1f, 1f, 1f, 0.18f);
                material.color = c;
                if (material.HasProperty("_BaseColor"))
                    material.SetColor("_BaseColor", c);
                go.SetActive(true);
            }

            public void Hide() => go.SetActive(false);
        }
    }
}
