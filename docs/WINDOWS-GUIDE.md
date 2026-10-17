# GorillaCraft on Windows: the complete guide

This guide takes you from a plain Gorilla Tag and Minecraft install to playing Minecraft in VR
as a Gorilla Tag gorilla. Each step says how to check that it worked before you move on. Linux
players: follow the same steps, then read [section 14](#14-linux-proton-and-wivrn).

> **Early release.** GorillaCraft 0.10.14 changes how everything works (Minecraft is now the VR
> game). It has been tested against a real headset runtime and with a stand-in for Gorilla Tag,
> but not yet played with the headset on. Hand positions, the menu panel and the wrist hotbar
> may need tuning. Please report what you find.

## Contents

1. [How it works](#1-how-it-works)
2. [What you need](#2-what-you-need)
3. [Install BepInEx in Gorilla Tag](#3-install-bepinex-in-gorilla-tag)
4. [Install Prism Launcher](#4-install-prism-launcher)
5. [Install the GorillaCraft plugin](#5-install-the-gorillacraft-plugin)
6. [Play](#6-play)
7. [Controls](#7-controls)
8. [Settings](#8-settings)
9. [Multiplayer: playing with friends on a server](#9-multiplayer-playing-with-friends-on-a-server)
10. [Performance tips](#10-performance-tips)
11. [Troubleshooting](#11-troubleshooting)
12. [Updating and uninstalling](#12-updating-and-uninstalling)
13. [Building from source](#13-building-from-source)
14. [Linux (Proton and WiVRn)](#14-linux-proton-and-wivrn)

---

## 1. How it works

- **Minecraft is the VR game.** It talks to your headset directly through OpenXR (SteamVR,
  Oculus/Meta Link, Virtual Desktop, WiVRn...) and draws both eyes with all of Minecraft's own
  graphics. Minecraft's menus, inventory and chests float in front of you, and the hotbar sits
  on the back of your left hand.
- **Gorilla Tag is the gorilla.** When Minecraft starts, it starts Gorilla Tag in a small window
  **without VR**. Minecraft sends it your headset and controllers every frame; Gorilla Tag's own
  arm locomotion moves the gorilla around Minecraft's blocks and sends back where you are.
- **You see Gorilla Tag's real gorilla** in Minecraft: your arms and body, and other players as
  whole gorillas. The model comes from your own installed Gorilla Tag; nothing from the game is
  shipped.

**Short version:** install BepInEx in Gorilla Tag, install Prism Launcher and sign in, put
`GorillaCraft.dll` in `BepInEx\plugins\GorillaCraft\`, start Gorilla Tag once (it sets up
Minecraft in Prism), then from now on **start the GorillaCraft instance in Prism** with your
headset on. Minecraft opens in the headset and starts Gorilla Tag by itself.

## 2. What you need

| | |
|---|---|
| **A PC VR headset** | Anything with an OpenXR runtime on your PC: SteamVR, the Meta/Oculus app (Link, Air Link), Virtual Desktop, WiVRn... **The standalone Quest version of Gorilla Tag can't be used.** |
| **Gorilla Tag on PC** | The Steam version. It runs in a small window in the background. |
| **Minecraft: Java Edition** | Owned on your Microsoft account. GorillaCraft uses version 26.3, and Prism downloads it for you. |
| **[Prism Launcher](https://prismlauncher.org/download/windows/)** | Free Minecraft launcher. GorillaCraft sets up its own instance in it. |
| **A reasonably strong PC** | Minecraft draws two eyes at headset resolution while Gorilla Tag runs alongside. Plan for 16 GB of RAM and a GPU that runs VR games comfortably. |
| **From the [GorillaCraft release](https://github.com/easyax2/gorillacraft/releases)** | `GorillaCraft.dll`, or `GorillaCraft-0.10.14-GorillaTag.zip` with the same dll already in its folders. The Minecraft mod is inside the dll; the separate `.jar` is for servers and manual installs. |

**Utilla isn't needed.** GorillaCraft works alone (Gorilla Tag stays out of rooms while you play),
in private rooms, and in modded lobbies. It switches itself off in public lobbies.

## 3. Install BepInEx in Gorilla Tag

Skip this section if you already play Gorilla Tag with mods.

### 3.1 Find your Gorilla Tag folder

- **Steam:** in your library, right-click **Gorilla Tag → Manage → Browse local files**.
  Usually it's `C:\Program Files (x86)\Steam\steamapps\common\Gorilla Tag`.
- **Meta/Oculus PC app:** usually
  `C:\Program Files\Oculus\Software\Software\another-axiom-gorilla-tag`.

You should see `Gorilla Tag.exe` in it. This guide calls this the **game folder**.

### 3.2 BepInEx 5

1. Go to the [BepInEx releases](https://github.com/BepInEx/BepInEx/releases) and download the
   latest **5.4.x** build for **win_x64**, e.g. `BepInEx_win_x64_5.4.23.x.zip`. Use 5.x, not
   BepInEx 6.
2. Close Gorilla Tag. Extract the zip straight into the game folder, so that `winhttp.dll`,
   `doorstop_config.ini` and a `BepInEx` folder end up next to `Gorilla Tag.exe`.
3. Start Gorilla Tag once, wait until you're in the forest, then quit. BepInEx creates its
   folders on that first start.

**Check:** the game folder now has `BepInEx\plugins`, `BepInEx\config` and
`BepInEx\LogOutput.log`.

A Gorilla Tag mod manager that installs BepInEx for you works too.

## 4. Install Prism Launcher

1. Download Prism Launcher for Windows from
   [prismlauncher.org](https://prismlauncher.org/download/windows/) and install it. Use the
   normal installer, or a portable copy (see the `PrismPath` setting in [section 8](#8-settings)).
2. Start Prism. Go to **Settings → Accounts → Add Microsoft** and sign in with the account that
   owns Minecraft. Prism can't start Minecraft without an account.
3. Close Prism (or leave it open; either works).

You don't need to create an instance or download any mods. GorillaCraft does that.

## 5. Install the GorillaCraft plugin

1. In `BepInEx\plugins`, create a folder called `GorillaCraft`.
2. Put `GorillaCraft.dll` into it:
   `...\Gorilla Tag\BepInEx\plugins\GorillaCraft\GorillaCraft.dll`.
   With the zip from the release, you can extract it straight into the game folder instead.
   Don't leave a second copy loose in `BepInEx\plugins`.
3. If Windows marked the file as downloaded from the internet: right-click it →
   **Properties** → tick **Unblock** if it's there → **OK**.
4. **Start Gorilla Tag** from Steam. It opens in a window with VR off (with GorillaCraft it's
   your gorilla's body), sets up Minecraft in Prism and starts it. The first start of Minecraft
   downloads it, so give it a few minutes.

**Check:** open `BepInEx\LogOutput.log` in Notepad. You should see:

```
[Info   :   BepInEx] Loading [GorillaCraft 0.10.14]
[Info   :GorillaCraft] Started first: Minecraft will be started and take the headset
[Info   :GorillaCraft] Prism instance in C:\Users\...\AppData\Roaming\PrismLauncher: created instance; ...; installed gorillacraft-fabric-0.10.14+26.3.jar; installed fabric-api-0.161.0+26.3.jar
```

Prism now has an instance called **GorillaCraft**: Minecraft 26.3, Fabric Loader 0.19.5, Fabric
API and the GorillaCraft mod, set to open a survival world called **GorillaCraft** by itself.

**Windows:** don't put `prismlauncher` in Gorilla Tag's Steam launch options: Gorilla Tag starts
Minecraft by itself. (Linux is different, see section 14.)

## 6. Play

**Start Gorilla Tag first** (recommended, and the way that works with SteamVR):

1. **Start your VR runtime** (SteamVR, the Meta app with Link, Virtual Desktop...) and put on the
   headset.
2. **Start Gorilla Tag from Steam** as usual. With GorillaCraft installed it doesn't open in VR: it
   opens in a window with VR off (it's your gorilla's body now) and, a few seconds later, starts
   the **GorillaCraft** instance in Prism. The first time, Prism downloads Minecraft, Fabric and
   Java, which takes a few minutes.
3. Minecraft appears **in your headset** and opens the GorillaCraft world. As soon as it's
   connected to Gorilla Tag, you're a gorilla in the world: climb, punch and build. Leave
   Gorilla Tag's window running.

Why this order: when Steam starts a game while SteamVR is running, SteamVR hands the headset to
that game and closes whatever VR app had it. Started first, Gorilla Tag gets that hand-over
(and doesn't use it); Minecraft, started afterwards, keeps the headset.

**Or start Minecraft first:** start the GorillaCraft instance in Prism and Minecraft starts
Gorilla Tag itself. This works with WiVRn, Monado, Virtual Desktop and the Meta app, but SteamVR
may take the headset away from Minecraft when Gorilla Tag starts; then use the order above.

To play normal Gorilla Tag in VR again, set `PlayInMinecraft = false` in
`BepInEx\config\dev.gorillacraft.cfg` (or remove the plugin).

To start Minecraft without the menus, you can also make a desktop shortcut:

```
"%LOCALAPPDATA%\Programs\PrismLauncher\prismlauncher.exe" --launch GorillaCraft
```

Add `--server play.example.com` to join a server straight away.

Take the headset off and Minecraft carries on on your monitor like normal Minecraft. Put it back
on and you're in VR again.

## 7. Controls

| Do this | Happens |
|---|---|
| **Move your arms** | Gorilla Tag locomotion: push off blocks to move, climb, jump. |
| **Right stick** left/right | Snap turn (Gorilla Tag's). |
| **Swim** in water | Gorilla Tag swimming: you float, stroke with your arms to swim, and can jump out at the surface. |
| Point the **left** controller at a block, hold the **left trigger** | Break it (like holding left click). Tools and drops are Minecraft's. Pointing at a mob and pulling hits it. |
| Point the **right** controller at a block, pull the **right trigger** | Use it with the held item (like right click): place a block, open a door, press a button, eat, draw a bow... |
| **Punch** a block with your real hand (hard, straight into it) | Mine it: each punch keeps Minecraft mining for 0.4 s, so creative breaks in one punch and survival takes several. |
| **Punch** a mob | Hit it. |
| **A** / **X**, or **left stick** left/right | Next / previous hotbar slot. The hotbar is on the back of your left hand. |
| **Y** | Open / close the inventory. |
| **Menu** button (left controller) | Pause menu (Options, Save and Quit...). |

Minecraft outlines the block the controller you're using points at. Your held item is in your
right hand.

**Survival:** new GorillaCraft worlds are survival on Normal difficulty. Moving around costs a
little hunger (every 10 seconds, by how far you went: about 400 blocks per half drumstick),
hostile mobs come after you, and a held **spear** is always charged: rush at a mob and it hits
as hard as you're moving. Worlds made by older versions are creative and peaceful: open the
chat and type `/gamemode survival` and `/difficulty normal` to change that.

**Menus** (title screen, worlds, servers, options, inventory, chests, crafting tables) float about
a metre in front of you:

| Do this | |
|---|---|
| Point with your **right hand** | Moves Minecraft's mouse (a white dot shows where) |
| Right **trigger** | Click |
| Right **grip** | Right-click |
| Right **stick** up/down | Scroll |
| **B** | Close the screen (like Esc) |
| **Left stick click** | Bring the panel in front of you again |

While a menu is open your hands don't punch or place. For typing (world names, server
addresses, chat) use your real keyboard: click into the Minecraft window first.

- A punch is your real hand **entering** a block faster than 3 m/s. Your gorilla's hands stop at
  the surface when you climb, but your real hands go a little further in, so slow climbing
  never breaks blocks; fast slaps can, in creative.
- You can touch blocks you can walk through (torches, flowers, buttons, levers), but not climb on them.
- In a new creative world your hotbar starts empty: open the inventory (**Y**) and drag blocks
  into it.

## 8. Settings

### Minecraft: `config\gorillacraft.properties`

In the instance folder: in Prism, right-click **GorillaCraft → Folder**, then `minecraft\config`.

| Setting | Default | What it does |
|---|---|---|
| `vr` | `true` | Use the headset. `false`: plain Minecraft on the monitor. |
| `vrResolution` | `1.0` | Eye resolution compared to what your headset asks for. `0.7` is much lighter; `1.3` is sharper and heavier. |
| `launchGorillaTag` | `true` | Start Gorilla Tag through Steam when Minecraft starts. |
| `port` | `47123` | Port Gorilla Tag connects to (must match Gorilla Tag's `Port`). |
| `radius` | `4` | How far around you blocks become solid for the gorilla, in chunks (16 blocks each). |
| `verticalRadius` | `4` | How far up and down, in 16-block sections. |
| `sectionsPerTick` | `6` | How many sections are prepared per Minecraft tick. |

Minecraft's own **Graphics API** setting (Options → Video Settings) must stay on **OpenGL** (or
Default), not Vulkan: VR uses OpenGL.

### Gorilla Tag: `BepInEx\config\dev.gorillacraft.cfg`

Open it with Notepad while Gorilla Tag is closed.

| Setting | Default | What it does |
|---|---|---|
| `[Link] Host` / `Port` | `127.0.0.1` / `47123` | Where Minecraft listens. Leave them unless the port is taken. |
| `[Map] Anchor` | `0, 1500, 0` | Where Minecraft's world is placed in Gorilla Tag's world, far from Gorilla Tag's own maps. |
| `[Map] BlockScale` | `1` | Size of one Minecraft block in meters. Lower it (e.g. `0.6`) to make blocks smaller compared to your gorilla. |
| `[Hands] TouchDistance` | `0.12` | How close (meters) your hand has to be to a block for punching and using. |
| `[Hands] PunchSpeed` | `3.0` | How fast (m/s, into the block) a hand must hit to count as a punch. |
| `[Hands] PunchHold` | `0.4` | Seconds Minecraft keeps mining after each punch. |
| `[Hands] FistMode` | `false` | Only punches with grip held break blocks; the trigger places instead of the grip. |
| `[Performance] SectionsPerFrame` | `4` | How many 16×16×16 chunks of blocks become colliders per frame. |
| `[Minecraft] PlayInMinecraft` | `true` | Gorilla Tag is your gorilla for Minecraft whenever it starts (VR off in Gorilla Tag). `false`: only when Minecraft starts it; started yourself it's normal Gorilla Tag. |
| `[Minecraft] StartMinecraft` | `true` | When you start Gorilla Tag yourself, start the GorillaCraft instance in Prism (Windows). |
| `[Minecraft] AutoInstall` | `true` | Keep the GorillaCraft instance in Prism up to date when Gorilla Tag starts. |
| `[Minecraft] PrismPath` / `PrismDataDir` | *(empty)* | Where Prism is, if it's not in a usual place (portable copies, other drives). |
| `[Minecraft] World` | `GorillaCraft` | World Minecraft opens by itself (only used when the instance is first created). |

## 9. Multiplayer: playing with friends on a server

Everyone plays on a normal Minecraft server: blocks one player breaks or places show up for
everyone. Each player needs their **own** Gorilla Tag with GorillaCraft and their own
GorillaCraft instance. The server is where the Minecraft world lives.

### 9.1 Set up the server (one person)

You need [Java 25](https://adoptium.net/) on the server PC.

1. Make a new folder, e.g. `C:\GorillaCraftServer`.
2. Get the Fabric server launcher from [fabricmc.net/use/server](https://fabricmc.net/use/server/):
   Minecraft **26.3**, the newest loader, then **Executable Server (.jar)**. Save it into the folder.
3. Open a terminal in the folder (Shift + right-click → **Open in Terminal**) and run it once:
   ```
   java -Xmx2G -jar fabric-server-mc.26.3-loader.0.19.5-launcher.jar nogui
   ```
   (use the actual file name). It downloads Minecraft, creates its files and stops because of
   the EULA.
4. Open `eula.txt`, change `eula=false` to `eula=true`, save.
5. Put **two jars** into the new `mods` folder:
   - **Fabric API** for 26.3, from [Modrinth](https://modrinth.com/mod/fabric-api)
   - `gorillacraft-fabric-0.10.14+26.3.jar` from the [GorillaCraft release](https://github.com/easyax2/gorillacraft/releases)
6. Optional, in `server.properties`:
   - `gamemode=creative` (and `force-gamemode=true`) for building, or leave survival
   - `difficulty=peaceful` if you don't want mobs attacking gorillas
   - `spawn-protection=0`, so everyone can build near spawn
7. Start it again with the same command. It's ready when it prints `Done`.
8. If friends join over the internet, forward **TCP port 25565** on your router to this PC, and
   allow Java through Windows Firewall when it asks. On the same network, nobody needs that.

GorillaCraft on the server is what lets gorillas climb freely; without it the server pulls players
back when they hug block edges. The server doesn't need Gorilla Tag.

### 9.2 Join it (everyone)

In Minecraft (in VR): **Multiplayer → Add Server**, type the address with your keyboard, and
join, like any server:

- the server PC itself: `localhost`
- same home network: the server PC's local IP, e.g. `192.168.1.20`
- over the internet: the host's public IP or domain, e.g. `123.45.67.89` (add `:port` if it
  isn't 25565)

Or start straight on the server: `prismlauncher --launch GorillaCraft --server <address>`.

**Everyone is a gorilla.** Players with GorillaCraft see each other as Gorilla Tag's real
gorilla, moving exactly as it does in Gorilla Tag. Players without GorillaCraft see a gorilla
built from Minecraft blocks, in the Gorilla Tag colour. You don't need to be in the same Gorilla
Tag room: the Minecraft server is what connects you.

**Open to LAN** works too: one player opens their world (pause menu → **Open to LAN**), others
join `<that PC's IP>:<port shown in chat>` from Multiplayer.

## 10. Performance tips

- Minecraft draws **two eyes** every frame at your headset's resolution, so a world costs about
  twice what it does on a monitor. Keep **render distance** modest (6–10) and lower
  `vrResolution` (e.g. `0.8`) if frames drop.
- Minecraft's frame rate follows the headset. Its Max Framerate and VSync settings don't apply in VR.
- Gorilla Tag draws nothing and plays no sound while Minecraft drives it, so it costs little.
- If you stutter while blocks load, lower `SectionsPerFrame` (Gorilla Tag) and `sectionsPerTick`
  (Minecraft).
- Give the instance 3–4 GB of memory in Prism (**GorillaCraft → Edit → Settings → Java**).

## 11. Troubleshooting

**Where the logs are**

- Minecraft: `logs\latest.log` in the instance's `minecraft` folder. VR lines start with `VR` or `OpenXR`.
- Gorilla Tag: `<game folder>\BepInEx\LogOutput.log`. Lines from the mod say `GorillaCraft`.

| Problem | Fix |
|---|---|
| Minecraft opens on the monitor, not in the headset | Start your VR runtime first and check the log: `VR: No OpenXR runtime` means none is active (in SteamVR: Settings → OpenXR → Set SteamVR as OpenXR runtime; or in the Meta app: set it as the OpenXR runtime). `Headset not connected` means the runtime is up but sees no headset. Minecraft retries every few seconds. |
| Log says `VR needs Minecraft's OpenGL renderer` | Options → Video Settings → Graphics API: OpenGL (or Default), and restart. |
| The runtime can't take OpenGL frames | Your runtime doesn't support OpenGL apps. SteamVR, Oculus, WiVRn and Monado do. |
| Gorilla Tag doesn't start | Is Steam running? The Minecraft log says `Starting Gorilla Tag: ...`. Start it from Steam yourself: it still works, but only Gorilla Tag started by Minecraft drives the gorilla, so add `-gorillacraft` to its launch options for that. |
| Minecraft leaves VR or closes a moment after Gorilla Tag starts (log: `VR session ended`) | SteamVR switched to Gorilla Tag because it was started through Steam. Since 0.10.14 Minecraft starts `Gorilla Tag.exe` directly (log: `Starting Gorilla Tag: ...Gorilla Tag.exe`). If it says `steam.exe -applaunch` instead, it couldn't find the game in your Steam libraries: report your Steam library folders. |
| Gorilla Tag starts **in VR** | `PlayInMinecraft` is off in `dev.gorillacraft.cfg`, or the plugin isn't loaded (check `LogOutput.log`). |
| You stand still and arms don't move | Gorilla Tag isn't connected yet (its window says so), or it was started without `-gorillacraft`. Its log should say `Started by Minecraft: no VR here`. |
| Laggy or stuttering picture, but Minecraft's log says `VR: 85–90 fps` | It's the video stream to the headset, not the games. WiVRn's log (`~/.local/state/wivrn/wivrn-dashboard/server_logs_*.txt`) shows `IDR frame needed` each time the headset lost frames. Put the PC on Ethernet, use a 5 GHz (or 6 GHz) Wi-Fi network for the headset with nothing else busy on it, stay close to the router, and raise the bitrate/resolution in WiVRn's dashboard once it's stable. |
| Hands are offset from your controllers | Report it with your headset model; hand offsets may need tuning per controller. |
| Can't click menus | Point with the right hand; the white dot shows where. Left stick click brings the panel back in front of you. |
| Climbing breaks blocks in creative | Raise `PunchSpeed`, or turn on `FistMode`. |
| Punches don't break anything | Hit faster, or lower `PunchSpeed`. In survival, keep punching. |
| You fall through the world after joining | Blocks didn't arrive in time. Rejoin the world. Check both logs for errors. |
| "Protocol mismatch" in a log | Plugin and Minecraft mod are from different releases. Start Gorilla Tag normally once: it updates the mod in the instance. |
| You keep getting pulled back when climbing on a server | Install GorillaCraft (and Fabric API) on the server too. |
| "GorillaCraft is off in public lobbies" | Gorilla Tag joined a public room. Leave it. |

If you're stuck, open an [issue](https://github.com/easyax2/gorillacraft/issues) and attach both
log files.

## 12. Updating and uninstalling

**Updating:** replace `BepInEx\plugins\GorillaCraft\GorillaCraft.dll` with the one from the new
release, then start Gorilla Tag normally once: it updates the mod in the GorillaCraft Prism
instance. Your settings and worlds are kept. Update servers by hand with the new jar.

**Uninstalling:**

- Gorilla Tag: delete `BepInEx\plugins\GorillaCraft\` and `BepInEx\config\dev.gorillacraft.cfg`.
- Minecraft: in Prism, right-click the **GorillaCraft** instance → **Delete** (copy
  `minecraft\saves\GorillaCraft` out first to keep the world).

## 13. Building from source

You need [Git](https://git-scm.com/download/win), the
[.NET SDK 8 or newer](https://dotnet.microsoft.com/download) and a
[JDK 25](https://adoptium.net/) (`java -version` should say 25).

```powershell
git clone https://github.com/easyax2/gorillacraft
cd gorillacraft

# Minecraft mod → fabric\build\libs\gorillacraft-fabric-*.jar
cd fabric
.\gradlew.bat build
cd ..

# Gorilla Tag plugin → gorillatag\bin\Release\GorillaCraft.dll
# GamePath must point at your game folder (with BepInEx installed): it compiles against it.
cd gorillatag
dotnet build -c Release -p:GamePath="C:\Program Files (x86)\Steam\steamapps\common\Gorilla Tag"

# The plugin embeds the mod jar and Fabric API, so build the Minecraft mod first.
# Same, and copy the dll into BepInEx\plugins\GorillaCraft:
dotnet build -c Release -p:Deploy=true -p:GamePath="C:\Program Files (x86)\Steam\steamapps\common\Gorilla Tag"
```

The end-to-end test (`tools\linktest` with `gradlew runClientGameTest`) is described in the
[README](../README.md#testing).

## 14. Linux (Proton and WiVRn)

GorillaCraft works on Linux with Minecraft native (Prism) and Gorilla Tag under Proton. Minecraft
uses your OpenXR runtime directly: **WiVRn** (Quest) or **Monado**; SteamVR works too.

1. Install BepInEx in Gorilla Tag as in section 3. Under Proton, Gorilla Tag can't start Linux
   programs, so let Steam start Minecraft together with it. Gorilla Tag's Steam launch options:
   ```
   prismlauncher --launch GorillaCraft & WINEDLLOVERRIDES="winhttp=n,b" %command%
   ```
   (Flatpak: `flatpak run org.prismlauncher.PrismLauncher --launch GorillaCraft &` instead.)
2. Install Prism Launcher (native package or Flatpak) and sign in. Start Gorilla Tag once from
   Steam: the plugin finds Prism's folder in your home folder and creates the GorillaCraft
   instance, as on Windows.
3. Start WiVRn (or your runtime) and connect the headset. Then start Gorilla Tag from Steam:
   Minecraft starts with it and takes the headset. (Starting the GorillaCraft instance first
   works too: Minecraft then starts Gorilla Tag with `steam -applaunch 1533390 -gorillacraft`.)

Minecraft's window must use EGL for OpenXR on Linux: on Wayland it does already, on X11 GorillaCraft
asks SDL for EGL by itself. The system OpenXR loader (`libopenxr_loader.so.1`, package `openxr`)
is used if it's installed, so Minecraft finds the same runtime as other VR apps.
