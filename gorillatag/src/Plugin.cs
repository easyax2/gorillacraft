using System.Collections.Generic;
using BepInEx;
using BepInEx.Configuration;
using BepInEx.Logging;
using GorillaLocomotion;
using HarmonyLib;
using UnityEngine;

namespace GorillaCraft
{
    /// <summary>
    /// Gorilla Tag half of GorillaCraft. You play in Minecraft, in VR (Minecraft's own OpenXR);
    /// Minecraft starts Gorilla Tag in the background with -gorillacraft. Then Gorilla Tag is the
    /// gorilla's body: Minecraft's blocks become colliders here, Minecraft's headset and
    /// controllers drive the gorilla (MinecraftVr), Gorilla Tag's locomotion moves it, and the
    /// result (where the gorilla is, its bones, hand punches) goes back to Minecraft. Gorilla Tag
    /// shows nothing and plays no sound. Only where mods are fair: alone, in private rooms or
    /// modded lobbies.
    ///
    /// Started normally (not by Minecraft) it only keeps the Prism instance up to date.
    /// </summary>
    [BepInPlugin(Guid, Name, Version)]
    public sealed class Plugin : BaseUnityPlugin
    {
        public const string Guid = "dev.gorillacraft";
        public const string Name = "GorillaCraft";
        public const string Version = "0.10.9";

        internal static ManualLogSource Log;
        private static Plugin instance;

        private ConfigEntry<string> host;
        private ConfigEntry<int> port;
        private ConfigEntry<Vector3> anchor;
        private ConfigEntry<float> blockScale;
        private ConfigEntry<float> touchDistance;
        private ConfigEntry<float> punchSpeed;
        private ConfigEntry<float> punchHold;
        private ConfigEntry<bool> fistMode;
        private ConfigEntry<int> sectionsPerFrame;
        private ConfigEntry<string> shaderName;
        private ConfigEntry<bool> autoInstall;
        private ConfigEntry<string> prismPath;
        private ConfigEntry<string> prismDataDir;
        private ConfigEntry<string> worldName;
        private string lastStatus = "";

        private LinkClient link;
        private MinecraftWorld world;
        private HandInteraction hands;
        private bool modelSent;
        private float nextModelTry;
        private bool installChecked;

        private readonly Dictionary<(int, int, int), Messages.Section> pendingSections = new Dictionary<(int, int, int), Messages.Section>();
        private readonly List<(int, int, int)> applyOrder = new List<(int, int, int)>();

        private float nextDiagnostics;
        private bool inMap;
        private bool sentInMap;
        private int teleportAck;

        // After a teleport, hold the gorilla in place until the blocks under it have arrived.
        private bool holding;
        private Vector3 holdPosition;
        private float holdUntil;

        private void Awake()
        {
            Log = Logger;
            instance = this;
            host = Config.Bind("Link", "Host", "127.0.0.1", "Where Minecraft (with the GorillaCraft Fabric mod) is listening.");
            port = Config.Bind("Link", "Port", 47123, "Must match port in Minecraft's config/gorillacraft.properties.");
            anchor = Config.Bind("Map", "Anchor", new Vector3(0f, 1500f, 0f),
                "Where the Minecraft world's spawn point is placed in Gorilla Tag's world, far away from Gorilla Tag's own maps.");
            blockScale = Config.Bind("Map", "BlockScale", 1f, "Size of one Minecraft block in Gorilla Tag units (meters).");
            touchDistance = Config.Bind("Hands", "TouchDistance", 0.12f, "How close (in Gorilla Tag units) a hand must be to a block to punch or use it.");
            punchSpeed = Config.Bind("Hands", "PunchSpeed", 3.0f, "How fast (metres per second, straight into the block) a hand must hit it to count as a punch. Higher: you have to hit harder, and climbing never breaks blocks.");
            punchHold = Config.Bind("Hands", "PunchHold", 0.4f, "Seconds Minecraft keeps mining after each punch. Survival blocks that take longer need more punches.");
            fistMode = Config.Bind("Hands", "FistMode", false, "Only punches with a fist (grip held) break blocks, so climbing never does.");
            sectionsPerFrame = Config.Bind("Performance", "SectionsPerFrame", 4, "How many 16x16x16 block sections to build per frame.");
            shaderName = Config.Bind("Rendering", "Shader", "Universal Render Pipeline/Particles/Unlit", "Shader for Minecraft's blocks (they're colliders; nothing is shown).");
            autoInstall = Config.Bind("Minecraft", "AutoInstall", true, "Create/update the GorillaCraft instance in Prism Launcher (Minecraft 26.3, Fabric, Fabric API, this mod) when Gorilla Tag starts.");
            prismPath = Config.Bind("Minecraft", "PrismPath", "", "Full path to prismlauncher.exe. Empty: look in the usual install folders.");
            prismDataDir = Config.Bind("Minecraft", "PrismDataDir", "", "Prism's data folder (the one with instances in it). Empty: the default one.");
            worldName = Config.Bind("Minecraft", "World", "GorillaCraft", "Singleplayer world Minecraft opens by itself (created the first time). Only used when the instance is first created.");

            if (!MinecraftVr.StartedByMinecraft())
            {
                Log.LogInfo("Gorilla Tag started normally: GorillaCraft stays out of the way. To play, start the GorillaCraft instance in Prism; it starts Gorilla Tag itself.");
                return;
            }
            MinecraftVr.Enable(new Harmony(Guid));
            // Colliders only: nothing here is drawn.
            link = new LinkClient(host.Value, port.Value, s => Log.LogInfo(s), () => (byte)0);
            link.Start();
            var harmony = new Harmony(Guid + ".pose");
            harmony.Patch(AccessTools.Method(typeof(GTPlayer), "LateUpdate"), postfix: new HarmonyMethod(typeof(Plugin), nameof(AfterLocomotion)));
        }

        /// <summary>Right after Gorilla Tag's locomotion: the freshest gorilla for Minecraft.</summary>
        private static void AfterLocomotion()
        {
            instance?.SendPose();
        }

        private void OnGUI()
        {
            if (!MinecraftVr.Active)
                return;
            GUI.Label(new Rect(10, 10, Screen.width - 20, 60),
                "GorillaCraft: this Gorilla Tag moves your gorilla for Minecraft. Play in Minecraft (in VR).\n" +
                (link != null && link.Connected ? (inMap ? "In the Minecraft world." : "Connected to Minecraft.") : "Waiting for Minecraft...") +
                (lastStatus.Length > 0 ? "  " + lastStatus : ""));
        }

        /// <summary>Your gorilla's colour as 0xRRGGBB.</summary>
        private static int GorillaColor()
        {
            VRRig rig = GorillaTagger.Instance != null ? GorillaTagger.Instance.offlineVRRig : null;
            if (rig == null)
                return 0x202020;
            Color c = rig.playerColor;
            return (int)(Mathf.Clamp01(c.r) * 255) << 16 | (int)(Mathf.Clamp01(c.g) * 255) << 8 | (int)(Mathf.Clamp01(c.b) * 255);
        }

        private void OnDestroy()
        {
            link?.Dispose();
        }

        /// <summary>Alone, in a private room, or in a modded lobby. Never in public lobbies.</summary>
        private static bool ModsAllowedHere()
        {
            NetworkSystem net = NetworkSystem.Instance;
            if (net == null || !net.InRoom)
                return true;
            if (net.SessionIsPrivate)
                return true;
            return (net.GameModeString ?? "").IndexOf("MODDED", System.StringComparison.OrdinalIgnoreCase) >= 0;
        }

        /// <summary>Once, a few seconds in: keep the Prism instance's mod up to date with this plugin.</summary>
        private void CheckInstall()
        {
            if (installChecked || Time.realtimeSinceStartup < 5f)
                return;
            installChecked = true;
            if (!autoInstall.Value)
                return;
            try
            {
                string exe = PrismSetup.IsWine() ? null : PrismSetup.FindExecutable(prismPath.Value);
                string data = PrismSetup.FindDataDir(prismDataDir.Value, exe);
                if (data == null)
                    Log.LogInfo("Prism Launcher not found; not installing the Minecraft mod");
                else
                    Log.LogInfo($"Prism instance in {data}: {PrismSetup.Install(data, worldName.Value)}");
            }
            catch (System.Exception e)
            {
                Log.LogError("Setting up Minecraft failed: " + e);
            }
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
            hands = new HandInteraction(world, link, touchDistance.Value, punchSpeed.Value, punchHold.Value, fistMode.Value, highlightShader);
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
            CheckInstall();
            if (!MinecraftVr.Active || !EnsureSetUp())
                return;
            HideEverything();

            while (link.TryReceive(out object msg))
                Handle(msg);
            MinecraftVr.Apply();
            ApplySections();
            if (Time.unscaledTime >= nextDiagnostics)
            {
                nextDiagnostics = Time.unscaledTime + 5f;
                Log.LogInfo($"Gorilla: {Mathf.RoundToInt(1f / Mathf.Max(0.0001f, Time.smoothDeltaTime))} fps, {MinecraftVr.Diagnostics()}, inMap {inMap} holding {holding}");
            }

            bool allowed = ModsAllowedHere();
            if (inMap && !allowed)
                ExitMap("GorillaCraft is off in public lobbies");
            if (!inMap && allowed && link.Connected && world.HasWorld)
                EnterMap();

            hands.reach = touchDistance.Value;
            hands.punchSpeed = punchSpeed.Value;
            hands.punchHold = punchHold.Value;
            hands.fistMode = fistMode.Value;
            if (inMap)
            {
                UpdateHold();
                Messages.Tracking t = MinecraftVr.Tracking;
                // Punching, placing and the hotbar are Minecraft's now (VrHands): its triggers, and
                // punches from your real hands.
                hands.Stop();
            }
            if (!inMap && sentInMap)
                SendPose(); // tell Minecraft we left
        }

        /// <summary>Nobody looks at Gorilla Tag's window: don't spend the GPU drawing it.</summary>
        private static void HideEverything()
        {
            foreach (Camera c in Camera.allCameras)
            {
                if (c.cullingMask != 0)
                {
                    c.cullingMask = 0;
                    c.clearFlags = CameraClearFlags.SolidColor;
                    c.backgroundColor = Color.black;
                }
            }
        }

        private void Handle(object msg)
        {
            switch (msg)
            {
                case LinkClient.ConnectedEvent _:
                    sentInMap = !inMap; // force a pose message with the current state
                    break;
                case LinkClient.DisconnectedEvent _:
                    modelSent = false;
                    MinecraftVr.SetTracking(null);
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
                    break;
                case Messages.Tracking tr:
                    MinecraftVr.SetTracking(tr);
                    break;
                case Messages.Status st:
                    SetStatus(st.Text);
                    break;
            }
        }

        private void SetStatus(string text)
        {
            Log.LogInfo("Minecraft: " + text);
            lastStatus = text;
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

        private void EnterMap()
        {
            inMap = true;
            world.Visible = true;
            SetStatus("Entering Minecraft...");
            // Minecraft answers the inMap pose with a teleport to where its player is.
        }

        private void ExitMap(string reason)
        {
            inMap = false;
            holding = false;
            hands.Stop();
            world.Visible = false;
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

        /// <summary>
        /// Gorilla Tag's model for Minecraft once (read from this game), then every frame its bones
        /// and the pose: head, hands, colour and the rig (tracking → Minecraft) that puts Minecraft's
        /// camera where the gorilla's head is.
        /// </summary>
        private void SendGorilla(double headX, double headY, double headZ)
        {
            if (!modelSent && Time.time >= nextModelTry)
            {
                nextModelTry = Time.time + 5f;
                try
                {
                    byte[] model = GorillaModel.Export(s => Log.LogInfo(s));
                    if (model != null)
                    {
                        link.Send(model);
                        modelSent = true;
                    }
                }
                catch (System.Exception e)
                {
                    Log.LogWarning("Couldn't read the gorilla model: " + e);
                }
            }
            if (!modelSent || !inMap)
                return;
            byte[] pose = GorillaModel.Pose(world.Coords, headX, headY, headZ);
            if (pose != null)
                link.Send(pose);
        }

        private void SendPose()
        {
            if (link == null || !link.Connected || world == null)
                return;
            bool changed = inMap != sentInMap;
            if (!changed && !inMap)
                return;
            sentInMap = inMap;

            GTPlayer player = GTPlayer.Instance;
            Transform cam = MinecraftVr.Head() ?? player.headCollider.transform;
            Vector3 head = holding ? holdPosition : cam.position;
            world.Coords.ToMinecraft(head, out double x, out double y, out double z);
            Coords.LookAngles(cam.forward, out float yaw, out float pitch);
            world.Coords.ToMinecraft(player.GetHandFollower(true).position, out double hlx, out double hly, out double hlz);
            world.Coords.ToMinecraft(player.GetHandFollower(false).position, out double hrx, out double hry, out double hrz);
            SendGorilla(x, y, z);
            double[] rig = holding ? null : MinecraftVr.RigToMinecraft(world.Coords);
            var w = new FrameWriter(Proto.GtPose).U8(inMap ? 1 : 0).I32(teleportAck)
                .F64(x).F64(y).F64(z).F32(yaw).F32(pitch)
                .F64(hlx).F64(hly).F64(hlz).F64(hrx).F64(hry).F64(hrz).I32(GorillaColor())
                .U8(rig != null ? 1 : 0);
            if (rig != null)
                foreach (double v in rig)
                    w.F64(v);
            link.Send(w.Finish());
        }
    }
}
