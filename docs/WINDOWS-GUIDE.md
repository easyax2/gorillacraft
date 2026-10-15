# GorillaCraft on Windows: the complete guide

This guide takes you from a plain Gorilla Tag and Minecraft install to climbing around your
Minecraft world as a gorilla. Each step says how to check that it worked before you move on.

> **Early release.** GorillaCraft 0.1.0 has been tested end to end with a real Minecraft client
> and a stand-in for Gorilla Tag, but not yet in a VR headset. Things like the size of blocks and
> how close your hand has to be may need tuning (see [Settings](#7-settings)). Please report what
> you find.

## Contents

1. [What you need](#1-what-you-need)
2. [Install BepInEx and Utilla in Gorilla Tag](#2-install-bepinex-and-utilla-in-gorilla-tag)
3. [Install the GorillaCraft plugin](#3-install-the-gorillacraft-plugin)
4. [Set up Minecraft 26.3 with Fabric](#4-set-up-minecraft-263-with-fabric)
5. [Play](#5-play)
6. [Controls](#6-controls)
7. [Settings](#7-settings)
8. [Playing with friends](#8-playing-with-friends)
9. [Performance tips](#9-performance-tips)
10. [Troubleshooting](#10-troubleshooting)
11. [Updating and uninstalling](#11-updating-and-uninstalling)
12. [Building from source](#12-building-from-source)

---

## 1. What you need

| | |
|---|---|
| **Gorilla Tag on PC VR** | The Steam version, or the Meta/Oculus PC version. Played with a PC headset or a Quest over Link, Air Link, Virtual Desktop or Steam Link. **The standalone Quest version can't be modded this way.** |
| **Minecraft: Java Edition** | Owned on your Microsoft account. Version **26.3**. |
| **A reasonably strong PC** | Both games run at once. Plan for 16 GB of RAM and a GPU that already runs Gorilla Tag comfortably. |
| **From the [GorillaCraft release](https://github.com/easyax2/gorillacraft/releases/latest)** | `GorillaCraft.dll` (Gorilla Tag plugin) and `gorillacraft-fabric-0.1.0+26.3.jar` (Minecraft mod). `GorillaCraft-0.1.0-GorillaTag.zip` contains the same dll already in the right folders. |

GorillaCraft only works in **modded lobbies**. It never activates in public, unmodded rooms, the
same as every other movement mod. Don't use mods in normal lobbies; it can get your account banned.

## 2. Install BepInEx and Utilla in Gorilla Tag

Skip this section if you already play with Gorilla Tag mods that need Utilla.

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

### 2.3 Utilla

Utilla adds modded lobbies, and GorillaCraft needs it.

1. Download the latest Utilla that supports the current Gorilla Tag version, from the
   [Gorilla Tag Thunderstore community](https://thunderstore.io/c/gorilla-tag/) (search "Utilla")
   or its GitHub releases.
2. Put `Utilla.dll` into `BepInEx\plugins`.

You can also use a Gorilla Tag mod manager that installs BepInEx and Utilla for you. That's fine
too.

**Check:** start Gorilla Tag. At the computer in the treehouse, the game mode list now has
**Modded** modes. `BepInEx\LogOutput.log` contains `Loading [Utilla ...]`.

## 3. Install the GorillaCraft plugin

1. In `BepInEx\plugins`, create a folder called `GorillaCraft`.
2. Put `GorillaCraft.dll` into it:
   `...\Gorilla Tag\BepInEx\plugins\GorillaCraft\GorillaCraft.dll`.
   With the zip from the release, you can extract it straight into the game folder instead.
3. If Windows marked the file as downloaded from the internet: right-click it →
   **Properties** → tick **Unblock** if it's there → **OK**.

**Check:** start Gorilla Tag, then open `BepInEx\LogOutput.log` in Notepad. You should see:

```
[Info   :   BepInEx] Loading [GorillaCraft 0.1.0]
[Info   :GorillaCraft] Waiting for Minecraft on 127.0.0.1:47123
```

`BepInEx\config\dev.gorillacraft.cfg` now exists too. That's the settings file (see
[Settings](#7-settings)).

## 4. Set up Minecraft 26.3 with Fabric

You need Minecraft 26.3 with Fabric Loader 0.19.5 or newer, Fabric API, and the GorillaCraft mod.
Pick **one** of the two ways below. A separate instance (option B) keeps your normal Minecraft
untouched and is easier to manage.

### Option A: the official Minecraft Launcher

1. Start the Minecraft Launcher once, select version 26.3 and play it once so it's downloaded.
   Then close it.
2. Download the **Fabric Installer** for Windows from
   [fabricmc.net/use/installer](https://fabricmc.net/use/installer/) and run it.
   - Tab **Client**, Minecraft version **26.3**, the newest loader version.
   - Leave **Create profile** ticked, then click **Install**.
3. Download **Fabric API** for 26.3 from [Modrinth](https://modrinth.com/mod/fabric-api)
   (Versions tab, pick a file whose game version is 26.3).
4. Press **Win + R**, type `%APPDATA%\.minecraft` and press Enter. Create a folder called `mods`
   if there isn't one.
5. Put **both** jars into `mods`:
   - `fabric-api-....jar`
   - `gorillacraft-fabric-0.1.0+26.3.jar`
6. In the Minecraft Launcher, pick the **fabric-loader-26.3** profile and press **Play**.

### Option B: Prism Launcher (separate instance)

1. Install [Prism Launcher](https://prismlauncher.org/download/windows/) and sign in with your
   Microsoft account (**Settings → Accounts**).
2. **Add Instance** → Minecraft **26.3** → Mod loader **Fabric** (newest) → **OK**.
3. Select the instance → **Edit** → **Mods** → **Download mods** → search **Fabric API** →
   select it → **Review and confirm**.
4. Still in **Mods**: **Add file** → pick `gorillacraft-fabric-0.1.0+26.3.jar`.
5. Close the editor and **Launch**.

Minecraft 26.x needs **Java 25**. The official launcher brings its own. Prism downloads it when it
asks you, or set it under **Settings → Java**.

### Check

On the title screen, **Mods** lists **GorillaCraft** and **Fabric API**. Minecraft's log
(`logs\latest.log` in the instance or `.minecraft` folder) contains:

```
[GorillaCraft] Waiting for Gorilla Tag on 127.0.0.1:47123
```

If Windows Firewall asks about Java: GorillaCraft only listens on your own PC (127.0.0.1), so
**Private networks** is enough, or cancel. It doesn't need internet access for this.

## 5. Play

1. **Start Minecraft first** (it can also be second; the two find each other within a couple of
   seconds) and open a world:
   - **Creative** is the easiest to start with: blocks break with one hit and you have
     everything. Use **Open to LAN** if friends will join (see [section 8](#8-playing-with-friends)).
   - Or join a server that has GorillaCraft installed.
2. Put on your headset and **start Gorilla Tag**.
3. At the treehouse computer, pick a **Modded** game mode, then join a room. A private room code
   works too.
4. You're dropped into the Minecraft world, at the spot where your Minecraft player stands. For
   a moment you're held in place while the blocks around you load, then you're free.
5. Climb, punch and build. Minecraft can sit in the background; it doesn't pause while Gorilla
   Tag is connected.

Your left wrist shows the GorillaCraft status (green dot = connected), your hotbar, and messages
from Minecraft such as "Containers can't be opened from Gorilla Tag yet".

To leave the Minecraft map, hold **B + Y** for a second, or leave the lobby. You're put back where
you were in Gorilla Tag. Hold B + Y again to go back in.

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
| `[Map] AutoEnter` | `true` | Put you into the Minecraft map as soon as you join a modded lobby. `false`: only when you hold B + Y. |
| `[Hands] TouchDistance` | `0.12` | How close (meters) your hand has to be to a block for mining and using. Raise it if hits don't register, lower it if you hit blocks by accident. |
| `[Performance] SectionsPerFrame` | `4` | How many 16×16×16 chunks of blocks are built per frame. Lower it if you get stutters while blocks load. |
| `[Rendering] Shader` | `Universal Render Pipeline/Particles/Unlit` | Shader for blocks. Only change it if blocks look wrong (see [Troubleshooting](#10-troubleshooting)). |

### Minecraft: `config\gorillacraft.properties`

In the instance folder (Prism: right-click the instance → **Folder**) or in `%APPDATA%\.minecraft\config`.

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

1. **One player hosts the Minecraft world:** Esc → **Open to LAN**, or run a Fabric server with
   GorillaCraft and Fabric API in its `mods` folder.
2. **Everyone else joins that Minecraft world or server** in their own Minecraft.
   (Over the internet you need a server, port forwarding, or a tunnelling mod.)
3. **Everyone joins the same modded Gorilla Tag lobby**, using a private room code.

The Minecraft world's spawn point anchors the map, so with the same `Anchor` and `BlockScale`
settings everyone stands on the same blocks in Gorilla Tag. Blocks one player breaks or places show
up for everyone.

Without GorillaCraft on a Minecraft server, gorillas still work, but the server may pull you back
when you climb on block edges.

## 9. Performance tips

Gorilla Tag in VR needs steady frame rates. Minecraft running in the background competes with it.

- In Minecraft: **Options → Video Settings** → set **Max Framerate** to 30, and
  **Render Distance** to 6–8. Minecraft doesn't need to look good; Gorilla Tag draws the
  blocks.
- Minimise the Minecraft window once your world is open.
- If you stutter while blocks are loading, lower `SectionsPerFrame` (Gorilla Tag) and
  `sectionsPerTick` (Minecraft). If loading feels slow, raise them.
- If you're running low on memory, give Minecraft 3–4 GB (Prism: **Settings → Java → Maximum
  memory**; official launcher: **Installations → Edit → More options → JVM arguments**,
  `-Xmx4G`). More doesn't help.

## 10. Troubleshooting

**Where the logs are**

- Gorilla Tag: `<game folder>\BepInEx\LogOutput.log`. Lines from the mod say `GorillaCraft`.
- Minecraft: `logs\latest.log` in the instance or `.minecraft` folder.

| Problem | Fix |
|---|---|
| Nothing about GorillaCraft in `LogOutput.log` | BepInEx isn't running (no `LogOutput.log` at all?), or the dll is in the wrong place. It must be in `BepInEx\plugins\GorillaCraft\`. Unblock it (section 3). |
| The log says GorillaCraft was skipped because of a missing dependency | Install Utilla (section 2.3). |
| Wrist dot stays red / log keeps saying `Waiting for Minecraft` | Minecraft isn't running with the mod, or the ports differ. Check Minecraft's log for `Waiting for Gorilla Tag on 127.0.0.1:47123`. If it says the port is in use, pick another port in **both** settings files. |
| Wrist says "Open a world in Minecraft" | Minecraft is connected but on the title screen. Open a world. |
| Joined a lobby but nothing happens | It must be a **modded** game mode. Or `AutoEnter` is off: hold B + Y. |
| "Join a modded lobby to play in Minecraft" | You held B + Y outside a modded lobby. |
| You fall through the world after entering | Blocks didn't arrive in time. Wait at the edge of a loaded area and try B + Y twice. Check Minecraft's log for errors. |
| Blocks are **pink/magenta** or invisible | The block shader isn't available in your Gorilla Tag version. In `dev.gorillacraft.cfg` set `Shader = Sprites/Default` and restart. Also check the log line `Block shader: ...`. |
| Blocks are white/untextured | The block texture hasn't arrived from Minecraft yet. Check Minecraft's log for `Block atlas exported`. If you change resource packs in Minecraft, restart Gorilla Tag so it picks up the new textures. |
| Leaves or glass look solid / water isn't see-through | Your Gorilla Tag build lacks that shader variant. Try `Shader = Sprites/Default`. |
| Mining/placing doesn't register | Raise `TouchDistance` to `0.18`. Make sure Minecraft isn't on a menu or the death screen. In survival, placing needs the block in your hotbar and selected. |
| Blocks get placed "inside" you or not at all next to your body | Your Minecraft player's body is where the gorilla is, and Minecraft won't place blocks into it. Reach a little further out. |
| A chest opens and closes immediately | Container screens aren't supported yet. Use the Minecraft window. |
| You keep getting pulled back when climbing on a server | Install GorillaCraft (and Fabric API) on the server too. |
| Minecraft pauses when you Alt+Tab | That only happens while Gorilla Tag is **not** connected. Once it is, GorillaCraft turns off "pause on lost focus". |
| Version mismatch message on the wrist | The plugin and the Minecraft mod are from different releases. Install both from the same release. |

If you're stuck, open an [issue](https://github.com/easyax2/gorillacraft/issues) and attach both
log files.

## 11. Updating and uninstalling

**Updating:** replace `BepInEx\plugins\GorillaCraft\GorillaCraft.dll` and the jar in your Minecraft
`mods` folder with the ones from the new release. Always update both together. Your settings files
are kept.

**Uninstalling:** delete `BepInEx\plugins\GorillaCraft\` and
`BepInEx\config\dev.gorillacraft.cfg`, and remove the jar (and `config\gorillacraft.properties`)
from Minecraft. BepInEx and Utilla can stay for other mods.

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
# GamePath must point at your game folder (BepInEx and Utilla installed): it compiles against them.
cd gorillatag
dotnet build -c Release -p:GamePath="C:\Program Files (x86)\Steam\steamapps\common\Gorilla Tag"

# Same, and copy the dll into BepInEx\plugins\GorillaCraft:
dotnet build -c Release -p:Deploy=true -p:GamePath="C:\Program Files (x86)\Steam\steamapps\common\Gorilla Tag"
```

The end-to-end test (`tools\linktest` with `gradlew runClientGameTest`) is described in the
[README](../README.md#testing).
