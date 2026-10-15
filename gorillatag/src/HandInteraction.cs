using GorillaLocomotion;
using UnityEngine;
using UnityEngine.XR;

namespace GorillaCraft
{
    /// <summary>
    /// Turns gorilla hands into Minecraft clicks:
    ///   punch a block                        → mine it. Each punch keeps Minecraft mining it for
    ///                                          a moment, so creative breaks in one hit and survival
    ///                                          takes as many punches as Minecraft's break time
    ///                                          (tools in your hotbar still count)
    ///   grip pressed with a hand on a block  → use the held item on it (place a block, open a
    ///                                          door, press a button, flip a lever, ...)
    ///   A / X                                → next / previous hotbar slot
    /// A punch is a hand arriving at a block faster than <see cref="punchSpeed"/>. Gorilla
    /// climbing slaps surfaces too, so slow climbing never counts; with <see cref="fistMode"/> only
    /// a fist (grip held) punches, and the trigger becomes "use".
    /// </summary>
    public sealed class HandInteraction
    {
        private readonly MinecraftWorld world;
        private readonly LinkClient link;
        // Settings, updated live from the config.
        public float reach;
        public float punchSpeed;
        public float punchHold;
        public bool fistMode;
        private readonly Highlight highlight;

        private bool mining;
        private BlockTarget mineTarget;
        private float mineUntil;
        private readonly bool[] wasTouching = new bool[2];
        private readonly Vector3[] lastHand = new Vector3[2];
        private readonly float[] speed = new float[2];
        private readonly bool[] gripWasDown = new bool[2];
        private readonly bool[] primaryWasDown = new bool[2];
        public int SelectedSlot;

        public HandInteraction(MinecraftWorld world, LinkClient link, float reach, float punchSpeed, float punchHold, bool fistMode, Shader shader)
        {
            this.world = world;
            this.link = link;
            this.reach = reach;
            this.punchSpeed = punchSpeed;
            this.punchHold = punchHold;
            this.fistMode = fistMode;
            highlight = new Highlight(shader);
        }

        public void Update()
        {
            GTPlayer player = GTPlayer.Instance;
            if (player == null)
                return;

            var found = new bool[2];
            var targets = new BlockTarget[2];
            for (int h = 0; h < 2; h++)
            {
                bool isLeft = h == 0;
                // The real controller keeps moving into a surface after the gorilla hand stops at it,
                // so its speed is the punch speed.
                Vector3 controller = player.GetControllerTransform(isLeft).position;
                if (Time.deltaTime > 0f)
                    speed[h] = Mathf.Lerp(speed[h], (controller - lastHand[h]).magnitude / Time.deltaTime, 0.5f);
                lastHand[h] = controller;

                found[h] = BlockTargeting.Find(world, player.GetHandFollower(isLeft).position, reach, out targets[h]);
                bool fist = !fistMode || ControllerInputPoller.GripFloat(isLeft ? XRNode.LeftHand : XRNode.RightHand) > 0.6f;
                bool punched = fist && found[h] && !wasTouching[h] && speed[h] >= punchSpeed;
                wasTouching[h] = found[h];
                if (punched)
                    Punch(targets[h]);
            }

            // Mining runs on for a moment after each punch; stop when it runs out or the block is gone.
            if (mining && (Time.time > mineUntil || !world.TryGetShape(mineTarget.X, mineTarget.Y, mineTarget.Z, out _)))
            {
                mining = false;
                SendMine(false, mineTarget);
            }

            // Using / placing, on the grip's press (the trigger's, in fist mode).
            for (int h = 0; h < 2; h++)
            {
                XRNode node = h == 0 ? XRNode.LeftHand : XRNode.RightHand;
                bool down = fistMode ? ControllerInputPoller.TriggerFloat(node) > 0.5f : ControllerInputPoller.GripFloat(node) > 0.6f;
                if (down && !gripWasDown[h] && found[h])
                    SendUse(targets[h]);
                gripWasDown[h] = down;
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

            // Show the block being mined, or else the one a hand is on.
            if (mining)
                highlight.Show(world, mineTarget, true);
            else if (found[1])
                highlight.Show(world, targets[1], false);
            else if (found[0])
                highlight.Show(world, targets[0], false);
            else
                highlight.Hide();
        }

        private void Punch(BlockTarget t)
        {
            mineUntil = Time.time + punchHold;
            if (mining && t.SameBlock(mineTarget))
                return; // same block: just keeps going
            mining = true;
            mineTarget = t;
            SendMine(true, t);
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
