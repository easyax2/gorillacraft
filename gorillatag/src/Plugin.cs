using System.Collections.Generic;
using BepInEx;
using BepInEx.Configuration;
using BepInEx.Logging;
using GorillaLocomotion;
using UnityEngine;
using UnityEngine.XR;
using Utilla;

namespace GorillaCraft
{
    /// <summary>
    /// Gorilla Tag half of GorillaCraft. Minecraft runs alongside (with the GorillaCraft Fabric
    /// mod) and owns the world; Gorilla Tag owns the gorilla. This plugin draws the Minecraft
    /// blocks around you as a Gorilla Tag map you can climb, and turns your hands into Minecraft
    /// mining and clicking. Only active in modded lobbies.
    /// </summary>
    [BepInPlugin(Guid, Name, Version)]
    [BepInDependency("org.legoandmars.gorillatag.utilla")]
    [ModdedGamemode]
    public sealed class Plugin : BaseUnityPlugin
    {
        public const string Guid = "dev.gorillacraft";
        public const string Name = "GorillaCraft";
        public const string Version = "0.1.0";

        internal static ManualLogSource Log;

        private ConfigEntry<string> host;
        private ConfigEntry<int> port;
        private ConfigEntry<Vector3> anchor;
        private ConfigEntry<float> blockScale;
        private ConfigEntry<float> touchDistance;
        private ConfigEntry<bool> autoEnter;
        private ConfigEntry<int> sectionsPerFrame;
        private ConfigEntry<string> shaderName;

        private LinkClient link;
        private MinecraftWorld world;
        private HandInteraction hands;
        private WristDisplay wrist;

        private readonly Dictionary<(int, int, int), Messages.Section> pendingSections = new Dictionary<(int, int, int), Messages.Section>();
        private readonly List<(int, int, int)> applyOrder = new List<(int, int, int)>();

        private bool inModdedRoom;
        private bool wantEnter;
        private bool inMap;
        private bool sentInMap;
        private Vector3 returnPosition;
        private int teleportAck;
        private float nextPoseTime;
        private float toggleHeld;

        // After a teleport, hold the gorilla in place until the blocks under it have arrived.
        private bool holding;
        private Vector3 holdPosition;
        private float holdUntil;

        private void Awake()
        {
            Log = Logger;
            host = Config.Bind("Link", "Host", "127.0.0.1", "Where Minecraft (with the GorillaCraft Fabric mod) is listening.");
            port = Config.Bind("Link", "Port", 47123, "Must match port in Minecraft's config/gorillacraft.properties.");
            anchor = Config.Bind("Map", "Anchor", new Vector3(0f, 1500f, 0f),
                "Where the Minecraft world's spawn point is placed in Gorilla Tag's world, far away from Gorilla Tag's own maps.");
            blockScale = Config.Bind("Map", "BlockScale", 1f, "Size of one Minecraft block in Gorilla Tag units (meters).");
            touchDistance = Config.Bind("Hands", "TouchDistance", 0.12f, "How close (in Gorilla Tag units) a hand must be to a block to mine or use it.");
            autoEnter = Config.Bind("Map", "AutoEnter", true, "Enter the Minecraft map as soon as you join a modded lobby. Hold B+Y for a second to enter or leave by hand.");
            sectionsPerFrame = Config.Bind("Performance", "SectionsPerFrame", 4, "How many 16x16x16 block sections to build per frame.");
            shaderName = Config.Bind("Rendering", "Shader", "Universal Render Pipeline/Particles/Unlit", "Shader used for blocks.");

            link = new LinkClient(host.Value, port.Value, s => Log.LogInfo(s));
            link.Start();
        }

        private void OnDestroy()
        {
            link?.Dispose();
        }

        [ModdedGamemodeJoin]
        private void OnModdedJoin(string gamemode)
        {
            inModdedRoom = true;
            wantEnter = autoEnter.Value;
        }

        [ModdedGamemodeLeave]
        private void OnModdedLeave(string gamemode)
        {
            inModdedRoom = false;
            wantEnter = false;
            if (inMap)
                ExitMap(null);
        }

        private bool EnsureSetUp()
        {
            if (world != null)
                return true;
            GTPlayer player = GTPlayer.Instance;
            if (player == null)
                return false;
            int layer = PickCollisionLayer(player.locomotionEnabledLayers);
            world = new MinecraftWorld(layer, shaderName.Value);
            world.Coords.Anchor = anchor.Value;
            world.Coords.Scale = Mathf.Max(0.05f, blockScale.Value);
            Shader highlightShader = Shader.Find(shaderName.Value) ?? Shader.Find("Sprites/Default");
            hands = new HandInteraction(world, link, touchDistance.Value, highlightShader);
            wrist = new WristDisplay(player.GetControllerTransform(true));
            wrist.SetConnected(link.Connected);
            Log.LogInfo($"Ready (block colliders on layer {layer})");
            return true;
        }

        /// <summary>A layer gorilla hands and body collide with: Default if it's in the mask.</summary>
        private static int PickCollisionLayer(LayerMask mask)
        {
            if ((mask.value & 1) != 0)
                return 0;
            if ((mask.value & (1 << 9)) != 0)
                return 9; // GorillaObject
            for (int i = 0; i < 32; i++)
                if ((mask.value & (1 << i)) != 0)
                    return i;
            return 0;
        }

        private void Update()
        {
            if (!EnsureSetUp())
                return;

            while (link.TryReceive(out object msg))
                Handle(msg);
            ApplySections();

            UpdateToggle();
            if (!inMap && wantEnter && inModdedRoom && link.Connected && world.HasWorld)
                EnterMap();

            if (inMap)
            {
                UpdateHold();
                if (!holding)
                    hands.Update();
            }
            SendPose();
        }

        private void Handle(object msg)
        {
            switch (msg)
            {
                case LinkClient.ConnectedEvent _:
                    wrist.SetConnected(true);
                    sentInMap = !inMap; // force a pose message with the current state
                    break;
                case LinkClient.DisconnectedEvent _:
                    wrist.SetConnected(false);
                    if (inMap)
                        ExitMap("Lost connection to Minecraft");
                    world.SetWorld(new Messages.World { HasWorld = false, Dimension = "" });
                    pendingSections.Clear();
                    teleportAck = 0;
                    break;
                case Messages.Welcome w:
                    if (w.Version != Proto.Version)
                        SetStatus($"Protocol mismatch: Minecraft mod {w.Version}, Gorilla Tag plugin {Proto.Version}. Update both.");
                    break;
                case Messages.World w:
                    pendingSections.Clear();
                    world.SetWorld(w);
                    Log.LogInfo(w.HasWorld ? $"Minecraft world: {w.Dimension}, spawn {w.OriginX} {w.OriginY} {w.OriginZ}" : "Minecraft has no world open");
                    if (!w.HasWorld && inMap)
                        ExitMap("Minecraft closed its world");
                    break;
                case Messages.Atlas a:
                    world.SetAtlas(a.Png);
                    break;
                case Messages.Section s:
                    pendingSections[(s.X, s.Y, s.Z)] = s;
                    break;
                case Messages.SectionRemove r:
                    pendingSections.Remove((r.X, r.Y, r.Z));
                    world.RemoveSection(r.X, r.Y, r.Z);
                    break;
                case Messages.Teleport t:
                    teleportAck = t.Id;
                    if (inMap)
                        TeleportInto(world.Coords.ToUnity(t.X, t.Y, t.Z));
                    break;
                case Messages.Hotbar h:
                    hands.SelectedSlot = h.Selected;
                    wrist.SetHotbar(h);
                    break;
                case Messages.Status st:
                    SetStatus(st.Text);
                    break;
            }
        }

        private void SetStatus(string text)
        {
            Log.LogInfo("Minecraft: " + text);
            wrist.SetStatus(text);
        }

        /// <summary>Builds a few sections per frame, nearest to the gorilla first.</summary>
        private void ApplySections()
        {
            if (pendingSections.Count == 0)
                return;
            Vector3 head = GTPlayer.Instance.headCollider.transform.position;
            world.Coords.ToMinecraft(head, out double hx, out double hy, out double hz);
            int cx = Mathf.FloorToInt((float)hx) >> 4, cy = Mathf.FloorToInt((float)hy) >> 4, cz = Mathf.FloorToInt((float)hz) >> 4;
            applyOrder.Clear();
            applyOrder.AddRange(pendingSections.Keys);
            applyOrder.Sort((a, b) => Dist2(a, cx, cy, cz).CompareTo(Dist2(b, cx, cy, cz)));
            int budget = Mathf.Max(1, sectionsPerFrame.Value);
            for (int i = 0; i < applyOrder.Count && i < budget; i++)
            {
                world.ApplySection(pendingSections[applyOrder[i]]);
                pendingSections.Remove(applyOrder[i]);
            }
        }

        private static int Dist2((int, int, int) s, int cx, int cy, int cz)
        {
            int dx = s.Item1 - cx, dy = s.Item2 - cy, dz = s.Item3 - cz;
            return dx * dx + dy * dy + dz * dz;
        }

        /// <summary>Hold B and Y together for a second to enter or leave the Minecraft map.</summary>
        private void UpdateToggle()
        {
            bool both = ControllerInputPoller.SecondaryButtonPress(XRNode.LeftHand) && ControllerInputPoller.SecondaryButtonPress(XRNode.RightHand);
            if (!both)
            {
                toggleHeld = 0f;
                return;
            }
            float before = toggleHeld;
            toggleHeld += Time.deltaTime;
            if (before < 1f && toggleHeld >= 1f)
            {
                if (inMap)
                {
                    wantEnter = false;
                    ExitMap(null);
                }
                else if (!inModdedRoom)
                    SetStatus("Join a modded lobby to play in Minecraft");
                else if (!link.Connected)
                    SetStatus("Start Minecraft with the GorillaCraft mod first");
                else if (!world.HasWorld)
                    SetStatus("Open a world in Minecraft first");
                else
                    wantEnter = true;
            }
        }

        private void EnterMap()
        {
            GTPlayer player = GTPlayer.Instance;
            returnPosition = player.mainCamera.transform.position;
            inMap = true;
            world.Visible = true;
            wrist.Visible = true;
            SetStatus("Entering Minecraft...");
            // Minecraft answers the inMap pose with a teleport to where its player is.
        }

        private void ExitMap(string reason)
        {
            inMap = false;
            holding = false;
            hands.Stop();
            world.Visible = false;
            wrist.Visible = false;
            GTPlayer.Instance.TeleportTo(returnPosition, GTPlayer.Instance.transform.rotation, false, true);
            if (reason != null)
                SetStatus(reason);
        }

        private void TeleportInto(Vector3 head)
        {
            holding = true;
            holdPosition = head;
            holdUntil = Time.time + 15f;
            GTPlayer.Instance.TeleportTo(head, GTPlayer.Instance.transform.rotation, false, true);
        }

        private void UpdateHold()
        {
            if (!holding)
                return;
            world.Coords.ToMinecraft(holdPosition, out double x, out double y, out double z);
            int bx = Mathf.FloorToInt((float)x), by = Mathf.FloorToInt((float)y), bz = Mathf.FloorToInt((float)z);
            bool ready = world.HasSectionAt(bx, by, bz) && world.HasSectionAt(bx, by - 16, bz);
            if (ready || Time.time > holdUntil)
            {
                holding = false;
                SetStatus(ready ? "" : "Blocks are still loading");
                return;
            }
            GTPlayer.Instance.TeleportTo(holdPosition, GTPlayer.Instance.transform.rotation, false, true);
        }

        private void SendPose()
        {
            if (!link.Connected)
                return;
            bool changed = inMap != sentInMap;
            if (!changed && (!inMap || Time.time < nextPoseTime))
                return;
            nextPoseTime = Time.time + 0.02f;
            sentInMap = inMap;

            GTPlayer player = GTPlayer.Instance;
            Transform cam = player.mainCamera != null ? player.mainCamera.transform : player.headCollider.transform;
            Vector3 head = holding ? holdPosition : cam.position;
            world.Coords.ToMinecraft(head, out double x, out double y, out double z);
            Coords.LookAngles(cam.forward, out float yaw, out float pitch);
            link.Send(new FrameWriter(Proto.GtPose).U8(inMap ? 1 : 0).I32(teleportAck)
                .F64(x).F64(y).F64(z).F32(yaw).F32(pitch).Finish());
        }
    }
}
