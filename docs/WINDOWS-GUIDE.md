# GorillaCraft on Windows: the complete guide

This guide takes you from a plain Gorilla Tag and Minecraft install to climbing around your
Minecraft world as a gorilla. Each step says how to check that it worked before you move on.

> **Early release.** GorillaCraft 0.2.0 has been tested end to end with a real Minecraft client
> and a stand-in for Gorilla Tag, but not yet in a VR headset. Things like the size of blocks and
> how close your hand has to be may need tuning (see [Settings](#7-settings)). Please report what
> you find.

## Contents

1. [What you need](#1-what-you-need)
2. [Install BepInEx in Gorilla Tag](#2-install-bepinex-in-gorilla-tag)
3. [Install Prism Launcher](#3-install-prism-launcher)
4. [Install the GorillaCraft plugin](#4-install-the-gorillacraft-plugin)
5. [Play](#5-play)
6. [Controls](#6-controls)
7. [Settings](#7-settings)
8. [Playing with friends](#8-playing-with-friends)
9. [Performance tips](#9-performance-tips)
10. [Troubleshooting](#10-troubleshooting)
11. [Updating and uninstalling](#11-updating-and-uninstalling)
12. [Building from source](#12-building-from-source)
13. [Linux (Proton)](#13-linux-proton)

**Short version:** install BepInEx in Gorilla Tag, install Prism Launcher and sign in once, then put
`GorillaCraft.dll` in `BepInEx\plugins\GorillaCraft\`. When you start Gorilla Tag, GorillaCraft
sets up Minecraft 26.3 with Fabric and its own mod in Prism, starts it, opens a world and drops
you into it. You don't install anything in Minecraft yourself.

---

## 1. What you need

| | |
|---|---|
| **Gorilla Tag on PC VR** | The Steam version, or the Meta/Oculus PC version. Played with a PC headset or a Quest over Link, Air Link, Virtual Desktop or Steam Link. **The standalone Quest version can't be modded this way.** |
| **Minecraft: Java Edition** | Owned on your Microsoft account. GorillaCraft uses version 26.3, and Prism downloads it for you. |
| **[Prism Launcher](https://prismlauncher.org/download/windows/)** | Free Minecraft launcher. GorillaCraft uses it to install and start Minecraft for you. |
| **A reasonably strong PC** | Both games run at once. Plan for 16 GB of RAM and a GPU that already runs Gorilla Tag comfortably. |
| **From the [GorillaCraft release](https://github.com/easyax2/gorillacraft/releases/latest)** | `GorillaCraft.dll`, or `GorillaCraft-0.2.0-GorillaTag.zip` with the same dll already in its folders. The Minecraft mod is inside the dll; the separate `.jar` is only for servers and manual installs. |

**Utilla isn't needed.** GorillaCraft works:

- **alone**, before you join a room (you're the only one in your forest),
- in **private rooms** (join with a room code),
- in **modded lobbies**, if you also have Utilla installed.

It switches itself off in **public lobbies**, and teleports you back out of the Minecraft map if
you're in one. Don't try to get around that: using mods in public lobbies can get your account
banned.

## 2. Install BepInEx in Gorilla Tag

Skip this section if you already play Gorilla Tag with mods.

### 2.1 Find your Gorilla Tag folder

- **Steam:** in your library, right-click **Gorilla Tag → Manage → Browse local files**.
  Usually it's `C:\Program Files (x86)\Steam\steamapps\common\Gorilla Tag`.
- **Meta/Oculus PC app:** usually
  `C:\Program Files\Oculus\Software\Software\another-axiom-gorilla-tag`.

You should see `Gorilla Tag.exe` in it. This guide calls this the **game folder**.

### 2.2 BepInEx 5

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

## 3. Install Prism Launcher

1. Download Prism Launcher for Windows from
   [prismlauncher.org](https://prismlauncher.org/download/windows/) and install it. Use the
   normal installer, or a portable copy (see the `PrismPath` setting in [section 7](#7-settings)).
2. Start Prism. Go to **Settings → Accounts → Add Microsoft** and sign in with the account that
   owns Minecraft. Prism can't start Minecraft without an account.
3. Close Prism (or leave it open; either works).

You don't need to create an instance or download any mods. GorillaCraft does that.

## 4. Install the GorillaCraft plugin

1. In `BepInEx\plugins`, create a folder called `GorillaCraft`.
2. Put `GorillaCraft.dll` into it:
   `...\Gorilla Tag\BepInEx\plugins\GorillaCraft\GorillaCraft.dll`.
   With the zip from the release, you can extract it straight into the game folder instead.
   Don't leave a second copy loose in `BepInEx\plugins`.
3. If Windows marked the file as downloaded from the internet: right-click it →
   **Properties** → tick **Unblock** if it's there → **OK**.

**Check:** start Gorilla Tag, then open `BepInEx\LogOutput.log` in Notepad. You should see:

```
[Info   :   BepInEx] Loading [GorillaCraft 0.2.0]
[Info   :GorillaCraft] Ready (block colliders on layer 0)
[Info   :GorillaCraft] Prism instance in C:\Users\...\AppData\Roaming\PrismLauncher: created instance; installed gorillacraft-fabric-0.2.0+26.3.jar; installed fabric-api-0.161.0+26.3.jar
```

`BepInEx\config\dev.gorillacraft.cfg` now exists too. That's the settings file (see
[Settings](#7-settings)).

### What GorillaCraft sets up in Prism

About five seconds after Gorilla Tag starts, GorillaCraft:

1. creates (or updates) a Prism instance called **GorillaCraft** with Minecraft 26.3, Fabric
   Loader 0.19.5, Fabric API and the GorillaCraft mod,
2. starts that instance if Minecraft isn't already connected,
3. and Minecraft then opens a singleplayer world called **GorillaCraft** by itself. The first
   time, it creates it as a creative, peaceful world with cheats on.

Prism downloads Minecraft, Fabric and Java the first time, which takes a few minutes. After that,
Minecraft starts in well under a minute.

The instance also starts with settings suited to running in the background: 30 FPS, render
distance 6, music off, 3 GB of memory. You can change any of them in Prism or in Minecraft;
GorillaCraft only creates them once, and after that it only updates its own mod jar.

You can also use the instance like any other one: start it from Prism, play it normally, add
mods. Want a different world? Open it yourself from Minecraft's menu; GorillaCraft uses whatever
world or server Minecraft is in.

## 5. Play

1. Put on your headset and **start Gorilla Tag**.
2. Wait in the forest without joining a room. The first time, Prism downloads Minecraft, so
   this takes a few minutes. Your left wrist shows what's happening, e.g.
   "Starting Minecraft through Prism Launcher...". A Minecraft window opens on your desktop and
   loads the world.
3. As soon as the world is open, you're dropped into it, at the spot where your Minecraft player
   stands. For a moment you're held in place while the blocks around you load, then you're free.
4. Climb, punch and build. Minecraft keeps running in the background; it doesn't pause while
   Gorilla Tag is connected.

To play with others, join a **private room** (or a modded lobby with Utilla). In a public lobby
GorillaCraft turns itself off.

Your left wrist shows the GorillaCraft status (green dot = connected), your hotbar, and messages
from Minecraft such as "Containers can't be opened from Gorilla Tag yet".

To leave the Minecraft map, hold **B + Y** for a second. You're put back where you were in
Gorilla Tag. Hold B + Y again to go back in.

## 6. Controls

| Do this | Happens in Minecraft |
|---|---|
| Hand on a block, hold **trigger** | Mine it. Survival uses Minecraft's real break times, tools and drops; creative breaks with one hit. |
| Hand on a block, press **grip** | Right-click it with the held item: place a block on that face, open a door/trapdoor/gate, press a button, flip a lever, use a bucket, ... |
| **A** (right controller) | Next hotbar slot |
| **X** (left controller) | Previous hotbar slot |
| Hold **B + Y** for 1 second | Enter or leave the Minecraft map |

- A highlight box shows which block your hand is on: white when touching, orange while mining.
- You can touch blocks you can walk through, like torches, flowers, tall grass, buttons and levers.
  You can't climb on them.
- Block placement uses the direction you're looking, like in Minecraft (stairs, logs, furnaces
  face the way they would for a Minecraft player looking the same way).
- **Your inventory:** use the Minecraft window (Alt+Tab, press E) to move items into your hotbar.
  The same goes for chat and commands such as `/gamemode creative`.

## 7. Settings

### Gorilla Tag: `BepInEx\config\dev.gorillacraft.cfg`

Open it with Notepad while Gorilla Tag is closed.

| Setting | Default | What it does |
|---|---|---|
| `[Link] Host` | `127.0.0.1` | Where Minecraft runs. Leave it: the Minecraft mod only accepts connections from the same PC. |
| `[Link] Port` | `47123` | Must match `port` in Minecraft's settings. |
| `[Map] Anchor` | `0, 1500, 0` | Where Minecraft's spawn point is placed in Gorilla Tag's world. It's far above the Gorilla Tag maps so they don't overlap. |
| `[Map] BlockScale` | `1` | Size of one Minecraft block in meters. Lower it (e.g. `0.6`) to make blocks smaller compared to your gorilla, raise it to make them bigger. |
| `[Map] AutoEnter` | `true` | Put you into the Minecraft map as soon as Minecraft is ready. `false`: only when you hold B + Y. |
| `[Hands] TouchDistance` | `0.12` | How close (meters) your hand has to be to a block for mining and using. Raise it if hits don't register, lower it if you hit blocks by accident. |
| `[Performance] SectionsPerFrame` | `4` | How many 16×16×16 chunks of blocks are built per frame. Lower it if you get stutters while blocks load. |
| `[Rendering] Shader` | `Universal Render Pipeline/Particles/Unlit` | Shader for blocks. Only change it if blocks look wrong (see [Troubleshooting](#10-troubleshooting)). |
| `[Minecraft] AutoInstall` | `true` | Create/update the GorillaCraft instance in Prism when Gorilla Tag starts. |
| `[Minecraft] AutoLaunch` | `true` | Start that instance if Minecraft isn't already connected. Set `false` if you'd rather start Minecraft yourself. |
| `[Minecraft] PrismPath` | *(empty)* | Full path to `prismlauncher.exe`, if it's not in a usual place (portable copies, other drives). Empty: GorillaCraft looks in `%LOCALAPPDATA%\Programs\PrismLauncher`, `Program Files` and Scoop. |
| `[Minecraft] PrismDataDir` | *(empty)* | Prism's data folder (the one containing `instances`), if it's not `%APPDATA%\PrismLauncher`. Portable copies are found from `PrismPath`. |
| `[Minecraft] World` | `GorillaCraft` | World Minecraft opens by itself. Only used when the instance is first created; after that, it's the `-Dgorillacraft.autoWorld=` JVM argument in the instance's settings in Prism. |

### Minecraft: `config\gorillacraft.properties`

In the instance folder: in Prism, right-click **GorillaCraft → Folder**, then `minecraft\config`.

| Setting | Default | What it does |
|---|---|---|
| `port` | `47123` | Port Gorilla Tag connects to. |
| `radius` | `4` | How far around you blocks are sent, in chunks (16 blocks each). More looks further but costs more. |
| `verticalRadius` | `4` | How far up and down, in 16-block sections. |
| `sectionsPerTick` | `6` | How many sections are prepared per Minecraft tick. |

Minecraft only sends what it has loaded, so keep its own render distance at least `radius`.

## 8. Playing with friends

Everyone needs **their own** Minecraft (with GorillaCraft) and Gorilla Tag (with GorillaCraft).
Only the Minecraft world is shared, through Minecraft's normal multiplayer:

1. **One player hosts the Minecraft world:** in the Minecraft window, Esc → **Open to LAN**, or
   run a Fabric 26.3 server with `gorillacraft-fabric-0.2.0+26.3.jar` (from the release) and
   Fabric API in its `mods` folder.
2. **Everyone else joins that Minecraft world or server** in their own Minecraft (the
   GorillaCraft instance), from Minecraft's Multiplayer menu.
   (Over the internet you need a server, port forwarding, or a tunnelling mod.)
3. **Everyone joins the same private Gorilla Tag room** (same room code), or the same modded
   lobby if you all have Utilla.

The Minecraft world's spawn point anchors the map, so with the same `Anchor` and `BlockScale`
settings everyone stands on the same blocks in Gorilla Tag. Blocks one player breaks or places show
up for everyone.

Without GorillaCraft on a Minecraft server, gorillas still work, but the server may pull you back
when you climb on block edges.

## 9. Performance tips

Gorilla Tag in VR needs steady frame rates. Minecraft running in the background competes with it.

- The GorillaCraft instance starts at 30 FPS and render distance 6. Keep it low:
  Minecraft doesn't need to look good, Gorilla Tag draws the blocks. Keep render distance at
  least the `radius` setting (4).
- Minimise the Minecraft window once your world is open.
- If you stutter while blocks are loading, lower `SectionsPerFrame` (Gorilla Tag) and
  `sectionsPerTick` (Minecraft). If loading feels slow, raise them.
- The instance gets 3 GB of memory. If you're short on RAM you can lower it to 2 GB in Prism
  (**GorillaCraft → Edit → Settings → Java**). More doesn't help.

## 10. Troubleshooting

**Where the logs are**

- Gorilla Tag: `<game folder>\BepInEx\LogOutput.log`. Lines from the mod say `GorillaCraft`.
- Minecraft: `logs\latest.log` in the instance or `.minecraft` folder.

| Problem | Fix |
|---|---|
| Nothing about GorillaCraft in `LogOutput.log` | BepInEx isn't running (no `LogOutput.log` at all?), or the dll is in the wrong place. It must be in `BepInEx\plugins\GorillaCraft\`. Unblock it (section 4). |
| `Skipping [GorillaCraft ...] because a newer version exists` | There are two copies of `GorillaCraft.dll`. Keep only `BepInEx\plugins\GorillaCraft\GorillaCraft.dll`. |
| `Could not load [GorillaCraft 0.1.0] because it has missing dependencies: ...utilla` | That's the old 0.1.0, which needed Utilla. Install 0.2.0. |
| Wrist: "Prism Launcher not found" | Install Prism (section 3). If it's a portable copy or on another drive, set `PrismPath` (and maybe `PrismDataDir`). |
| Wrist: "prismlauncher.exe not found" | Set `PrismPath` to the full path of `prismlauncher.exe`. |
| Prism opens but Minecraft doesn't start | Add your Microsoft account in Prism (**Settings → Accounts**). Start the **GorillaCraft** instance from Prism once by hand to see the error. |
| Minecraft starts but stays on the title screen | The world couldn't be opened. Open any world yourself; GorillaCraft uses it. Check that the instance's JVM arguments (Prism: **Edit → Settings → Java**) still contain `-Dgorillacraft.autoWorld=GorillaCraft`. |
| Wrist dot stays red | Minecraft isn't running with the mod, or the ports differ. Minecraft's log should say `Waiting for Gorilla Tag on 127.0.0.1:47123`. If it says the port is in use, pick another port in **both** settings files. |
| Wrist says "Open a Minecraft world" | Minecraft is connected but on the title screen. Open a world. |
| "GorillaCraft is off in public lobbies" | You're in a public room. Leave it, or use a private room code. |
| You fall through the world after entering | Blocks didn't arrive in time. Hold B + Y twice to re-enter. Check Minecraft's log for errors. |
| Blocks are **pink/magenta** or invisible | The block shader isn't available in your Gorilla Tag version. In `dev.gorillacraft.cfg` set `Shader = Sprites/Default` and restart. Also check the log line `Block shader: ...`. |
| Blocks are white/untextured | The block texture hasn't arrived from Minecraft yet. Check Minecraft's log for `Block atlas exported`. If you change resource packs in Minecraft, restart Gorilla Tag so it picks up the new textures. |
| Leaves or glass look solid / water isn't see-through | Your Gorilla Tag build lacks that shader variant. Try `Shader = Sprites/Default`. |
| Mining/placing doesn't register | Raise `TouchDistance` to `0.18`. Make sure Minecraft isn't on a menu or the death screen. In survival, placing needs the block in your hotbar and selected. |
| Blocks get placed "inside" you or not at all next to your body | Your Minecraft player's body is where the gorilla is, and Minecraft won't place blocks into it. Reach a little further out. |
| A chest opens and closes immediately | Container screens aren't supported yet. Use the Minecraft window. |
| You keep getting pulled back when climbing on a server | Install GorillaCraft (and Fabric API) on the server too. |
| Version mismatch message on the wrist | The plugin and the Minecraft mod are from different releases. Restart Gorilla Tag; it updates the mod in the GorillaCraft instance. A server needs the matching jar from the same release. |

If you're stuck, open an [issue](https://github.com/easyax2/gorillacraft/issues) and attach both
log files.

## 11. Updating and uninstalling

**Updating:** replace `BepInEx\plugins\GorillaCraft\GorillaCraft.dll` with the one from the new
release. The next time Gorilla Tag starts, it updates the mod in the GorillaCraft Prism instance
too. Your settings and worlds are kept. Update servers by hand with the new jar.

**Uninstalling:**

- Gorilla Tag: delete `BepInEx\plugins\GorillaCraft\` and `BepInEx\config\dev.gorillacraft.cfg`.
  BepInEx can stay for other mods.
- Minecraft: in Prism, right-click the **GorillaCraft** instance → **Delete**. That deletes its
  worlds too; copy `minecraft\saves\GorillaCraft` out of the instance folder first to keep them.

GorillaCraft doesn't change your Minecraft worlds in any special way; anything you built is just
normal blocks in a normal world.

## 12. Building from source

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

## 13. Linux (Proton)

GorillaCraft also works with Gorilla Tag under Proton and a native Linux Prism Launcher. The
plugin finds Prism's data folder in your home folder (`~/.local/share/PrismLauncher`, or the
Flatpak's) and installs the GorillaCraft instance there, just like on Windows.

It **can't start** Prism, though: Proton runs Gorilla Tag in Steam's sandboxed runtime, which
can't run Linux programs. Either start the **GorillaCraft** instance in Prism yourself before
Gorilla Tag, or let Steam start it together with the game. In Steam: **Gorilla Tag → Properties
→ General → Launch Options**, and put this in front of whatever is there already:

```
prismlauncher --launch GorillaCraft & %command%
```

(If you already have options such as `PROTON_LOG=1 %command%`, it becomes
`prismlauncher --launch GorillaCraft & PROTON_LOG=1 %command%`.) For the Flatpak, use
`flatpak run org.prismlauncher.PrismLauncher --launch GorillaCraft &` instead. The instance only
exists after Gorilla Tag has started once with the plugin, so the very first time, start it from
Prism.

