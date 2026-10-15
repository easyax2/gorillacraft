using System.Collections.Generic;
using BepInEx;
using BepInEx.Configuration;
using BepInEx.Logging;
using GorillaLocomotion;
using UnityEngine;
using UnityEngine.XR;

namespace GorillaCraft
{
    /// <summary>
    /// Gorilla Tag half of GorillaCraft. Minecraft runs alongside (with the GorillaCraft Fabric
    /// mod) and owns the world; Gorilla Tag owns the gorilla. This plugin draws the Minecraft
    /// blocks around you as a Gorilla Tag map you can climb, and turns your hands into Minecraft
    /// mining and clicking. Only active where mods are fair: alone (not in a room), in private
    /// rooms, and in modded lobbies (Utilla's, if it's installed). Never in public lobbies.
    /// </summary>
    [BepInPlugin(Guid, Name, Version)]
    public sealed class Plugin : BaseUnityPlugin, MainMenu.IHost
    {
        public const string Guid = "dev.gorillacraft";
        public const string Name = "GorillaCraft";
        public const string Version = "0.7.1";

        internal static ManualLogSource Log;

        private ConfigEntry<string> host;
        private ConfigEntry<int> port;
        private ConfigEntry<Vector3> anchor;
        private ConfigEntry<float> blockScale;
        private ConfigEntry<float> touchDistance;
        private ConfigEntry<float> punchSpeed;
        private ConfigEntry<float> punchHold;
        private ConfigEntry<bool> fistMode;
        private ConfigEntry<bool> autoEnter;
        private ConfigEntry<int> sectionsPerFrame;
        private ConfigEntry<string> shaderName;
        private ConfigEntry<string> renderMode;
        private ConfigEntry<bool> perEye;
        private bool perEyeLogged;
        private ConfigEntry<bool> autoInstall;
        private ConfigEntry<bool> autoLaunch;
        private ConfigEntry<string> prismPath;
        private ConfigEntry<string> prismDataDir;
        private ConfigEntry<string> worldName;
        private ConfigEntry<string> servers;
        private ConfigEntry<bool> menuAtStart;
        private MainMenu menu;
        private string lastStatus = "";
        private string worldLabel = "";

        private LinkClient link;
        private MinecraftWorld world;
        private HandInteraction hands;
        private OverlayView overlay;
        private readonly GorillaOnlyView gorillaOnly = new GorillaOnlyView();
        private InventoryPanel inventory;
        private SharedFrames shared;
        private HeldItemView held;
        private ConfigEntry<bool> swapEyes;
        private ConfigEntry<bool> cutEdges;
        private float yDownAt = -1f;
        private bool yDown;
        private readonly Messages.Frame[] latestFrames = new Messages.Frame[3]; // newest per eye
        private WristDisplay wrist;

        private readonly Dictionary<(int, int, int), Messages.Section> pendingSections = new Dictionary<(int, int, int), Messages.Section>();
        private readonly List<(int, int, int)> applyOrder = new List<(int, int, int)>();

        private bool wantEnter;
        private float launchCheckAt;
        private bool launchChecked;
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
            touchDistance = Config.Bind("Hands", "TouchDistance", 0.12f, "How close (in Gorilla Tag units) a hand must be to a block to punch or use it.");
            punchSpeed = Config.Bind("Hands", "PunchSpeed", 3.0f, "How fast (metres per second, straight into the block) a hand must hit it to count as a punch. Higher: you have to hit harder, and climbing never breaks blocks.");
            punchHold = Config.Bind("Hands", "PunchHold", 0.4f, "Seconds Minecraft keeps mining after each punch. Survival blocks that take longer need more punches.");
            fistMode = Config.Bind("Hands", "FistMode", false, "Only punches with a fist (grip held) break blocks, so climbing never does. The trigger then uses/places instead of the grip.");
            autoEnter = Config.Bind("Map", "AutoEnter", true, "Enter the Minecraft map as soon as Minecraft is ready (alone, in a private room or in a modded lobby). Hold B+Y for a second to enter or leave by hand.");
            sectionsPerFrame = Config.Bind("Performance", "SectionsPerFrame", 4, "How many 16x16x16 block sections to build per frame.");
            shaderName = Config.Bind("Rendering", "Shader", "Universal Render Pipeline/Particles/Unlit", "Shader used for blocks.");
            renderMode = Config.Bind("Rendering", "Mode", "Overlay", new ConfigDescription(
                "Overlay: Minecraft draws the world (its real graphics: mobs, sky, lighting) and Gorilla Tag shows that picture in 3D. " +
                "Blocks: Gorilla Tag draws Minecraft's block meshes itself (older mode, lighter on the GPU). Restart after changing.",
                new AcceptableValueList<string>("Overlay", "Blocks")));
            perEye = Config.Bind("Rendering", "PerEye", true,
                "Overlay: Minecraft draws each eye's view separately (left and right take turns), and Gorilla Tag renders the eyes " +
                "one after the other so each sees its own picture. Off: one picture from the middle of your head for both eyes (lighter). Restart after changing.");
            cutEdges = Config.Bind("Rendering", "CutEdges", true, "Overlay: cut the picture at depth edges and fill the gap from the other eye, instead of stretching it (which smears when you move).");
            swapEyes = Config.Bind("Rendering", "SwapEyes", false, "Show each eye the other eye's Minecraft picture. Only if the 3D looks inside out.");
            if (OverlayMode && perEye.Value)
                ForceMultiPass();
            autoInstall = Config.Bind("Minecraft", "AutoInstall", true, "Create/update the GorillaCraft instance in Prism Launcher (Minecraft 26.3, Fabric, Fabric API, this mod) when Gorilla Tag starts.");
            autoLaunch = Config.Bind("Minecraft", "AutoLaunch", true, "Start that instance through Prism Launcher if Minecraft isn't running (Windows only; on Linux use the Steam launch option from the guide).");
            prismPath = Config.Bind("Minecraft", "PrismPath", "", "Full path to prismlauncher.exe. Empty: look in the usual install folders.");
            prismDataDir = Config.Bind("Minecraft", "PrismDataDir", "", "Prism's data folder (the one with instances in it). Empty: the default one.");
            worldName = Config.Bind("Minecraft", "World", "GorillaCraft", "Singleplayer world Minecraft opens by itself (created the first time). Only used when the instance is first created.");
            servers = Config.Bind("Menu", "Servers", "", "Servers you joined from the menu, newest first, separated by ;");
            menuAtStart = Config.Bind("Menu", "OpenAtStart", true, "Show the GorillaCraft menu on your monitor when Gorilla Tag starts. F1 opens/closes it.");
            wantEnter = autoEnter.Value;
            menu = new MainMenu(this, MenuSettings(), () => new List<string>(servers.Value.Split(new[] { ';' }, System.StringSplitOptions.RemoveEmptyEntries)),
                list => servers.Value = string.Join(";", list), menuAtStart.Value);
            launchCheckAt = Time.realtimeSinceStartup + 5f;

            link = new LinkClient(host.Value, port.Value, s => Log.LogInfo(s),
                () => OverlayMode ? (byte)(Proto.WantFrames | Proto.WantShm | (perEye.Value ? Proto.WantStereo : 0)) : (byte)(Proto.WantBlockMeshes | Proto.WantShm));
            link.Start();
        }

        private List<MainMenu.Setting> MenuSettings()
        {
            string OnOff(bool b) => b ? "on" : "off";
            return new List<MainMenu.Setting>
            {
                new MainMenu.Setting { Name = "Enter the map automatically", Value = () => OnOff(autoEnter.Value), Change = d => autoEnter.Value = !autoEnter.Value },
                new MainMenu.Setting { Name = "Fist mode (punch with grip, use with trigger)", Value = () => OnOff(fistMode.Value), Change = d => fistMode.Value = !fistMode.Value },
                new MainMenu.Setting { Name = "Punch speed", Value = () => punchSpeed.Value.ToString("0.0") + " m/s", Change = d => punchSpeed.Value = Mathf.Clamp(punchSpeed.Value + 0.2f * d, 0.2f, 6f) },
                new MainMenu.Setting { Name = "Mining per punch", Value = () => punchHold.Value.ToString("0.00") + " s", Change = d => punchHold.Value = Mathf.Clamp(punchHold.Value + 0.05f * d, 0.05f, 2f) },
                new MainMenu.Setting { Name = "Cut picture at edges (less smearing)", Value = () => OnOff(cutEdges.Value), Change = d => cutEdges.Value = !cutEdges.Value },
                new MainMenu.Setting { Name = "Swap eyes (if 3D looks inside out)", Value = () => OnOff(swapEyes.Value), Change = d => swapEyes.Value = !swapEyes.Value },
                new MainMenu.Setting { Name = "Hand reach", Value = () => touchDistance.Value.ToString("0.00") + " m", Change = d => touchDistance.Value = Mathf.Clamp(touchDistance.Value + 0.02f * d, 0.02f, 0.5f) },
                new MainMenu.Setting { Name = "Block size (after restart)", Value = () => blockScale.Value.ToString("0.0#") + " m", Change = d => blockScale.Value = Mathf.Clamp(blockScale.Value + 0.1f * d, 0.2f, 3f) },
                new MainMenu.Setting { Name = "Start Minecraft with Gorilla Tag", Value = () => OnOff(autoLaunch.Value), Change = d => autoLaunch.Value = !autoLaunch.Value },
                new MainMenu.Setting { Name = "Open this menu at start", Value = () => OnOff(menuAtStart.Value), Change = d => menuAtStart.Value = !menuAtStart.Value },
            };
        }

        private void OnGUI()
        {
            menu?.OnGUI();
        }

        // MainMenu.IHost
        bool MainMenu.IHost.Connected => link.Connected;
        bool MainMenu.IHost.HasWorld => world != null && world.HasWorld;
        string MainMenu.IHost.WorldLabel => worldLabel;
        bool MainMenu.IHost.InMap => inMap;
        bool MainMenu.IHost.ModsAllowed => ModsAllowedHere();
        string MainMenu.IHost.LastStatus => lastStatus;

        void MainMenu.IHost.ToggleMap()
        {
            if (inMap)
            {
                wantEnter = false;
                ExitMap(null);
            }
            else
                wantEnter = true;
        }

        void MainMenu.IHost.RequestWorlds() => link.Send(new FrameWriter(Proto.GtListWorlds).Finish());
        void MainMenu.IHost.OpenWorld(string id) { wantEnter = true; link.Send(new FrameWriter(Proto.GtOpenWorld).Str(id).Finish()); }
        void MainMenu.IHost.JoinServer(string address) { wantEnter = true; link.Send(new FrameWriter(Proto.GtJoinServer).Str(address).Finish()); }
        void MainMenu.IHost.LeaveWorld() => link.Send(new FrameWriter(Proto.GtLeaveWorld).Finish());

        private bool OverlayMode => renderMode.Value != "Blocks";

        /// <summary>
        /// Per-eye pictures need Gorilla Tag to render the two eyes in separate passes. VR starts
        /// after BepInEx loads plugins, so setting the XR plugins' stereo mode here takes effect.
        /// </summary>
        private static void ForceMultiPass()
        {
            try
            {
                var openVr = Unity.XR.OpenVR.OpenVRSettings.GetSettings(false);
                if (openVr != null)
                {
                    openVr.StereoRenderingMode = Unity.XR.OpenVR.OpenVRSettings.StereoRenderingModes.MultiPass;
                    Log.LogInfo("OpenVR set to multi-pass stereo for per-eye Minecraft pictures");
                }
            }
            catch (System.Exception e)
            {
                Log.LogWarning("Couldn't set OpenVR stereo mode: " + e.Message);
            }
            try
            {
                var openXr = UnityEngine.XR.OpenXR.OpenXRSettings.Instance;
                if (openXr != null)
                    openXr.renderMode = UnityEngine.XR.OpenXR.OpenXRSettings.RenderMode.MultiPass;
            }
            catch (System.Exception e)
            {
                Log.LogWarning("Couldn't set OpenXR stereo mode: " + e.Message);
            }
        }

        private static Camera headCamera;
        private static string loggedHead;

        /// <summary>
        /// The camera that is your headset: the one rendering in stereo. Not GTPlayer.mainCamera
        /// (that's Camera.main, which can be Gorilla Tag's desktop/third-person camera, above and
        /// behind the gorilla), not the shoulder camera.
        /// </summary>
        internal static Camera HeadCamera()
        {
            if (headCamera == null || !headCamera.isActiveAndEnabled || !headCamera.stereoEnabled)
            {
                headCamera = null;
                foreach (Camera c in Camera.allCameras)
                {
                    if (c.stereoEnabled && c.targetTexture == null)
                    {
                        headCamera = c;
                        break;
                    }
                }
                if (headCamera == null && GorillaTagger.Instance != null && GorillaTagger.Instance.mainCamera != null)
                    headCamera = GorillaTagger.Instance.mainCamera.GetComponent<Camera>();
                if (headCamera == null && GTPlayer.Instance != null)
                    headCamera = GTPlayer.Instance.mainCamera;
                string name = headCamera != null ? headCamera.name + (headCamera.stereoEnabled ? " (stereo)" : " (not stereo)") : "head collider";
                if (name != loggedHead)
                {
                    loggedHead = name;
                    Log.LogInfo("Minecraft's camera follows " + name);
                }
            }
            return headCamera;
        }

        /// <summary>Where each eye is, in Unity world space: from the XR eye poses, or head ± half the eye distance.</summary>
        private static void EyePositions(Camera cam, out Vector3 left, out Vector3 right)
        {
            if (cam != null && cam.stereoEnabled)
            {
                left = cam.GetStereoViewMatrix(Camera.StereoscopicEye.Left).inverse.MultiplyPoint(Vector3.zero);
                right = cam.GetStereoViewMatrix(Camera.StereoscopicEye.Right).inverse.MultiplyPoint(Vector3.zero);
                float d = Vector3.Distance(left, right);
                if (d > 0.01f && d < 1f && Vector3.Distance((left + right) * 0.5f, cam.transform.position) < 0.2f)
                    return;
            }
            Transform t = cam != null ? cam.transform : GTPlayer.Instance.headCollider.transform;
            float half = 0.032f * (t.parent != null ? t.parent.lossyScale.x : 1f);
            left = t.position - t.right * half;
            right = t.position + t.right * half;
        }

        private void OnDestroy()
        {
            shared?.Dispose();
            gorillaOnly.Disable();
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

        /// <summary>
        /// A few seconds after start, if Minecraft hasn't connected: set up the Prism instance and
        /// start it. Runs once.
        /// </summary>
        private void CheckMinecraft()
        {
            if (launchChecked || Time.realtimeSinceStartup < launchCheckAt)
                return;
            launchChecked = true;
            try
            {
                bool wine = PrismSetup.IsWine();
                string exe = wine ? null : PrismSetup.FindExecutable(prismPath.Value);
                string data = PrismSetup.FindDataDir(prismDataDir.Value, exe);
                if (autoInstall.Value)
                {
                    if (data == null)
                    {
                        SetStatus("Prism Launcher not found. Install it and add your Microsoft account, then restart Gorilla Tag.");
                        return;
                    }
                    Log.LogInfo($"Prism instance in {data}: {PrismSetup.Install(data, worldName.Value)}");
                }
                if (link.Connected || !autoLaunch.Value)
                    return;
                if (wine)
                {
                    SetStatus("Start the GorillaCraft instance in Prism (Linux: see the Steam launch option in the guide)");
                    return;
                }
                if (exe == null)
                {
                    SetStatus("prismlauncher.exe not found. Set PrismPath in dev.gorillacraft.cfg.");
                    return;
                }
                PrismSetup.Launch(exe, prismDataDir.Value);
                SetStatus("Starting Minecraft through Prism Launcher...");
            }
            catch (System.Exception e)
            {
                Log.LogError("Setting up Minecraft failed: " + e);
                SetStatus("Couldn't set up Minecraft: " + e.Message);
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
            if (OverlayMode)
                overlay = new OverlayView(highlightShader);
            inventory = new InventoryPanel(link, highlightShader);
            held = new HeldItemView(highlightShader);
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
            {
                if (msg is Messages.Frame f)
                    latestFrames[Mathf.Clamp(f.Eye, 0, 2)] = f; // only the newest picture per eye matters
                else
                    Handle(msg);
            }
            ApplySections();
            gorillaOnly.Update();
            if (shared != null)
            {
                // SkyCraft-style: the newest picture per stream, straight from shared memory.
                for (int b = 0; b < 3; b++)
                    if (shared.TryTake(b, out SharedFrames.Slot slot) && overlay != null && inMap)
                        overlay.Show(slot, world.Coords);
                if (shared.TryTake(SharedFrames.Gui, out SharedFrames.Slot gui) && (inMap || gui.Width == 0))
                    inventory.Show(gui);
            }
            for (int e = 0; e < latestFrames.Length; e++)
            {
                Messages.Frame frame = latestFrames[e];
                if (frame == null)
                    continue;
                latestFrames[e] = null;
                if (overlay == null || !inMap)
                    continue;
                overlay.Show(frame, world.Coords);
                if (!perEyeLogged && frame.Eye != Proto.EyeCenter && Time.frameCount % 90 == 0)
                {
                    perEyeLogged = true;
                    Log.LogInfo(overlay.PerEyeSeen ? "Per-eye Minecraft pictures: each eye sees its own" :
                        "Per-eye pictures arrive, but Gorilla Tag renders both eyes together (single-pass): both eyes see one picture");
                }
            }

            CheckMinecraft();
            UpdateToggle();
            bool allowed = ModsAllowedHere();
            if (inMap && !allowed)
                ExitMap("GorillaCraft is off in public lobbies");
            if (!inMap && wantEnter && allowed && link.Connected && world.HasWorld)
                EnterMap();

            hands.reach = touchDistance.Value;
            hands.punchSpeed = punchSpeed.Value;
            hands.punchHold = punchHold.Value;
            hands.fistMode = fistMode.Value;
            if (overlay != null)
                overlay.SwapEyes = swapEyes.Value;
            OverlayView.CutEdges = cutEdges.Value;
            if (inMap)
            {
                UpdateHold();
                UpdateInventoryButton();
                if (inventory.Open)
                {
                    hands.Stop(); // pointing at the panel, not punching
                    inventory.Update();
                }
                else if (!holding)
                    hands.Update();
            }
            held.Visible = inMap && !inventory.Open;
            held.Update();
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
                    shared?.Dispose();
                    shared = null;
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
                    worldLabel = w.Label ?? "";
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
                case Messages.Shm shm:
                    shared?.Dispose();
                    shared = SharedFrames.Open(shm.Path, shm.Size, s => Log.LogWarning(s));
                    if (shared != null)
                        Log.LogInfo("Minecraft's frames come through shared memory: " + shm.Path);
                    link.Send(new FrameWriter(Proto.GtShm).U8(shared != null ? 1 : 0).Finish());
                    break;
                case Messages.GuiFrame gf:
                    if (inMap || gf.Width == 0)
                        inventory.Show(gf);
                    break;
                case Messages.Held h:
                    held.Set(h);
                    break;
                case Messages.Worlds ws:
                    menu.SetWorlds(ws);
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
            wrist?.SetStatus(text);
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

        /// <summary>A quick tap on Y (without B) opens or closes Minecraft's inventory.</summary>
        private void UpdateInventoryButton()
        {
            bool y = ControllerInputPoller.SecondaryButtonPress(XRNode.LeftHand);
            bool b = ControllerInputPoller.SecondaryButtonPress(XRNode.RightHand);
            if (y && !yDown)
                yDownAt = b ? -1f : Time.time;
            if (b)
                yDownAt = -1f; // B+Y is the map toggle, not the inventory
            if (!y && yDown && yDownAt >= 0f && Time.time - yDownAt < 0.4f)
                inventory.Toggle();
            yDown = y;
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
                else if (!ModsAllowedHere())
                    SetStatus("GorillaCraft is off in public lobbies: play alone, in a private room or a modded lobby");
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
            returnPosition = (HeadCamera() != null ? HeadCamera().transform : player.headCollider.transform).position;
            inMap = true;
            world.Visible = true;
            if (overlay != null)
            {
                overlay.Visible = true;
                gorillaOnly.Enable(); // Gorilla Tag draws only gorillas; Minecraft draws everything else
            }
            wrist.Visible = true;
            SetStatus("Entering Minecraft...");
            // Minecraft answers the inMap pose with a teleport to where its player is.
        }

        private void ExitMap(string reason)
        {
            inMap = false;
            holding = false;
            hands.Stop();
            inventory.Close();
            world.Visible = false;
            if (overlay != null)
                overlay.Visible = false;
            gorillaOnly.Disable();
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
            nextPoseTime = Time.time + (overlay != null ? 0f : 0.02f); // overlay: Minecraft's camera follows every frame
            sentInMap = inMap;

            GTPlayer player = GTPlayer.Instance;
            Camera headCam = HeadCamera();
            Transform cam = headCam != null ? headCam.transform : player.headCollider.transform;
            Vector3 head = holding ? holdPosition : cam.position;
            world.Coords.ToMinecraft(head, out double x, out double y, out double z);
            Coords.LookAngles(cam.forward, out float yaw, out float pitch);
            EyePositions(headCam, out Vector3 leftEye, out Vector3 rightEye);
            if (holding)
            {
                Vector3 offset = holdPosition - cam.position;
                leftEye += offset;
                rightEye += offset;
            }
            world.Coords.ToMinecraft(leftEye, out double lx, out double ly, out double lz);
            world.Coords.ToMinecraft(rightEye, out double rx, out double ry, out double rz);
            link.Send(new FrameWriter(Proto.GtPose).U8(inMap ? 1 : 0).I32(teleportAck)
                .F64(x).F64(y).F64(z).F32(yaw).F32(pitch)
                .F64(lx).F64(ly).F64(lz).F64(rx).F64(ry).F64(rz).Finish());
        }
    }
}
