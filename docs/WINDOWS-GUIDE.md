# GorillaCraft on Windows: the complete guide

This guide takes you from a plain Gorilla Tag and Minecraft install to climbing around your
Minecraft world as a gorilla. Each step says how to check that it worked before you move on.

> **Early release.** GorillaCraft 0.7.2 has been tested end to end with a real Minecraft client
> and a stand-in for Gorilla Tag, but not yet in a VR headset. Things like the size of blocks and
> how close your hand has to be may need tuning (see [Settings](#7-settings)). Please report what
> you find.

## Contents

1. [What you need](#1-what-you-need)
2. [Install BepInEx in Gorilla Tag](#2-install-bepinex-in-gorilla-tag)
3. [Install Prism Launcher](#3-install-prism-launcher)
4. [Install the GorillaCraft plugin](#4-install-the-gorillacraft-plugin)
5. [Play](#5-play)
6. [Controls and the main menu](#6-controls-and-the-main-menu)
7. [Settings](#7-settings)
8. [Multiplayer: playing with friends on a server](#8-multiplayer-playing-with-friends-on-a-server)
9. [Performance tips](#9-performance-tips)
10. [Troubleshooting](#10-troubleshooting)
11. [Updating and uninstalling](#11-updating-and-uninstalling)
12. [Building from source](#12-building-from-source)
13. [Linux (Proton)](#13-linux-proton)

**How it looks:** you're a gorilla in Gorilla Tag, and around you is Minecraft's real picture
of its world: Minecraft draws the view from your gorilla head, and Gorilla Tag shows it in 3D.
So you get Minecraft's graphics (mobs, sky, day and night, weather, lighting) with Gorilla Tag's
movement. While you're in the map, Gorilla Tag draws nothing of its own except gorillas (yours
and other players', with cosmetics). Minecraft runs in a window on your desktop while you play.

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
| **From the [GorillaCraft release](https://github.com/easyax2/gorillacraft/releases)** | `GorillaCraft.dll`, or `GorillaCraft-0.7.2-GorillaTag.zip` with the same dll already in its folders. The Minecraft mod is inside the dll; the separate `.jar` is only for servers and manual installs. |

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
[Info   :   BepInEx] Loading [GorillaCraft 0.7.2]
[Info   :GorillaCraft] Ready (block colliders on layer 0)
[Info   :GorillaCraft] Prism instance in C:\Users\...\AppData\Roaming\PrismLauncher: created instance; set window size for the overlay; installed gorillacraft-fabric-0.7.2+26.3.jar; installed fabric-api-0.161.0+26.3.jar
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

The instance also starts with settings suited to this: a 1920×1200 window (that's the
resolution of what you see in VR), 30 FPS, render distance 6, music off, 3 GB of memory. You can change any of them in Prism or in Minecraft;
GorillaCraft only creates them once, and after that it only updates its own mod jar.

**Starting it yourself:** on Windows you don't need a launch command; Gorilla Tag starts the
instance. To start it by hand (a shortcut, a script, or with `AutoLaunch` off), run:

```
"%LOCALAPPDATA%\Programs\PrismLauncher\prismlauncher.exe" --launch GorillaCraft
```

(adjust the path if Prism is installed elsewhere, e.g. `C:\Program Files\PrismLauncher\`). Add
`--server play.example.com` to join a server straight away instead of the world.

You can also use the instance like any other one: start it from Prism, play it normally, add
mods. Want a different world? Open it yourself from Minecraft's menu; GorillaCraft uses whatever
world or server Minecraft is in.

## 5. Play

1. **Start Gorilla Tag.** The GorillaCraft menu appears on your monitor (see
   [section 6](#6-controls-and-the-main-menu)); you can use it now or close it with F1/Esc and
   put on your headset.
2. Wait in the forest without joining a room. The first time, Prism downloads Minecraft, so
   this takes a few minutes. Your left wrist shows what's happening, e.g.
   "Starting Minecraft through Prism Launcher...". A Minecraft window opens on your desktop and
   loads the world.
3. As soon as the world is open, you're dropped into it, at the spot where your Minecraft player
   stands. For a moment you're held in place while the blocks around you load, then you're free.
4. Climb, punch and build. Minecraft keeps running in its window; it doesn't pause while
   Gorilla Tag is connected. **Don't minimise the Minecraft window**: it's drawing what you see.
   It can be behind other windows. While you're in the map, that window shows a wide view without
   HUD or hand; that's the picture going into VR.

To play with others, join a **private room** (or a modded lobby with Utilla). In a public lobby
GorillaCraft turns itself off.

Your left wrist shows the GorillaCraft status (green dot = connected), your hotbar, and messages
from Minecraft such as "Containers can't be opened from Gorilla Tag yet".

To leave the Minecraft map, hold **B + Y** for a second. You're put back where you were in
Gorilla Tag. Hold B + Y again to go back in.

## 6. Controls and the main menu

### In VR

| Do this | Happens in Minecraft |
|---|---|
| **Punch** a block (hit it hard, straight into it) | Mine it. Each punch keeps Minecraft mining for a moment (0.4 s by default): creative breaks with one punch, survival takes as many punches as the block's break time. Tools in your hotbar speed it up, and drops are Minecraft's. |
| Hand on a block, press **grip** | Right-click it with the held item: place a block on that face, open a door/trapdoor/gate, press a button, flip a lever, use a bucket, ... |
| Tap **Y** | Open / close Minecraft's inventory (creative or survival). Chests, crafting tables and furnaces you open with grip show up the same way. |
| **A** (right controller) | Next hotbar slot |
| **X** (left controller) | Previous hotbar slot |
| Hold **B + Y** for 1 second | Enter or leave the Minecraft map |

- A punch is your hand **arriving** at a block moving **into it** faster than 3 m/s (the
  `Punch speed` setting). Resting a hand on a block, sliding along it, or climbing doesn't
  break it.
- Fast climbing slaps a surface just like a punch does, so in creative they can break the block
  you're climbing on. If that happens too often, raise `Punch speed`, or turn on **fist mode**:
  then only punches with **grip held** (a fist) break blocks, and the **trigger** does the
  right-click instead of the grip.
- A highlight box shows which block your hand is on: white when touching, orange while mining.
- You can touch blocks you can walk through, like torches, flowers, tall grass, buttons and levers.
  You can't climb on them.
- Block placement uses the direction you're looking, like in Minecraft (stairs, logs, furnaces
  face the way they would for a Minecraft player looking the same way).
- **Your inventory:** tap **Y**. Minecraft's inventory appears on a panel in front of you. Point
  at it with your right hand: **trigger** = left click, **grip** = right click, **right
  thumbstick** = scroll. Everything works as with a mouse (creative tabs, dragging stacks,
  crafting). Tap Y again to close it. Chests and other blocks with screens open on the same panel.
  While it's open, punching is paused. (In a new creative world your hotbar starts empty: open
  the inventory and drag blocks into it to have something to place.)
- **Held item:** whatever you hold in Minecraft shows in your gorilla's right hand (blocks as a
  small cube, items as a picture).
- Chat and commands (such as `/gamemode creative`) still need the Minecraft window.

### The main menu (keyboard and mouse)

The menu is drawn on your **monitor**, over the Gorilla Tag window. It opens when Gorilla Tag
starts (turn that off in Settings). Click into the Gorilla Tag window first so it has keyboard
focus.

| Key | |
|---|---|
| **F1** | Open / close the menu |
| **Mouse** | Hover to highlight, click to choose |
| **↑ / ↓** (or Tab) | Move |
| **Enter** | Choose |
| **← / →** | Change the highlighted setting |
| **Esc** | Back (closes the menu on the first page) |
| **Delete** | Forget the highlighted server (Multiplayer page) |
| Typing | Goes into the highlighted text box (world name, server address) |

The top of the menu shows whether Minecraft is connected, which world or server it's in, whether
you're in the map, and Minecraft's latest message.

- **Enter / Leave the Minecraft map:** same as holding B + Y.
- **Singleplayer worlds:** your worlds in the GorillaCraft instance, most recent first. Choose one
  to switch to it. Type a name under **New world** and press Enter to create a new creative world
  and play in it. (The world that's open right now isn't listed.)
- **Multiplayer:** type a server address (`play.example.com`, `123.45.67.89:25565`, ...) and press
  Enter, or choose one you joined before. See [section 8](#8-multiplayer-playing-with-friends-on-a-server).
- **Leave the current world:** saves and goes back to Minecraft's title screen.
- **Settings:** enter the map automatically, fist mode, punch speed, mining per punch, hand
  reach, block size (after a restart), starting Minecraft with Gorilla Tag, and opening the menu
  at start. Changes apply straight away and are saved.

Switching worlds or servers takes you out of the map while Minecraft loads, then back in.

## 7. Settings

### Gorilla Tag: `BepInEx\config\dev.gorillacraft.cfg`

Most of these can be changed in the menu's **Settings** page. For the rest, open the file with
Notepad while Gorilla Tag is closed.

| Setting | Default | What it does |
|---|---|---|
| `[Link] Host` | `127.0.0.1` | Where Minecraft runs. Leave it: the Minecraft mod only accepts connections from the same PC. |
| `[Link] Port` | `47123` | Must match `port` in Minecraft's settings. |
| `[Map] Anchor` | `0, 1500, 0` | Where Minecraft's spawn point is placed in Gorilla Tag's world. It's far above the Gorilla Tag maps so they don't overlap. |
| `[Map] BlockScale` | `1` | Size of one Minecraft block in meters. Lower it (e.g. `0.6`) to make blocks smaller compared to your gorilla, raise it to make them bigger. |
| `[Map] AutoEnter` | `true` | Put you into the Minecraft map as soon as Minecraft is ready. `false`: only when you hold B + Y. |
| `[Hands] TouchDistance` | `0.12` | How close (meters) your hand has to be to a block for punching and using. Raise it if hits don't register, lower it if you hit blocks by accident. |
| `[Hands] PunchSpeed` | `3.0` | How fast (m/s, straight into the block) a hand must hit it to count as a punch. Lower: lighter hits break blocks. Higher: you have to hit harder. |
| `[Hands] PunchHold` | `0.4` | Seconds Minecraft keeps mining after each punch. Higher: fewer punches for survival blocks. |
| `[Hands] FistMode` | `false` | Only punches with grip held break blocks; the trigger right-clicks instead of the grip. |
| `[Menu] OpenAtStart` | `true` | Show the menu when Gorilla Tag starts. F1 opens it any time. |
| `[Menu] Servers` | *(empty)* | Servers you joined from the menu, separated by `;`. |
| `[Performance] SectionsPerFrame` | `4` | How many 16×16×16 chunks of blocks are built per frame. Lower it if you get stutters while blocks load. |
| `[Rendering] Mode` | `Overlay` | `Overlay`: Minecraft draws the world and Gorilla Tag shows that picture in 3D (Minecraft's real graphics). `Blocks`: Gorilla Tag draws Minecraft's block meshes itself: lighter on the PC, but no mobs, sky or Minecraft lighting. Restart after changing. |
| `[Rendering] CutEdges` | `true` | Overlay: at depth edges, the picture sits on the background behind the edge instead of being stretched across the gap (which smears when you move). No holes. Also in the menu's Settings. |
| `[Rendering] SwapEyes` | `false` | Show each eye the other eye's picture. Only if the 3D looks inside out or depth feels wrong. Also in the menu's Settings. |
| `[Rendering] PerEye` | `true` | Overlay: each eye gets its own Minecraft picture, drawn from that eye. Gorilla Tag is switched to rendering the eyes one after another (multi-pass), which costs it some performance. `false`: one picture from the middle of your head for both eyes. Restart after changing. |
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
| `overlayWidth` | `2048` | The picture sent to Gorilla Tag is scaled down to at most this width. The Minecraft window size is the real limit. |
| `overlayGrid` | `160` | How detailed the 3D surface is (columns). Lower it (e.g. `96`) if the overlay updates slowly; higher gives sharper edges. |
| `overlayFov` | `100` | Vertical field of view Minecraft draws with. It must cover both eyes plus some head turning; lower makes the picture sharper but shows edges sooner. |

Minecraft only sends what it has loaded, so keep its own render distance at least `radius`.

## 8. Multiplayer: playing with friends on a server

**Yes, you can run a normal Minecraft server for this.** Everyone plays on that server, and
blocks one player breaks or places show up for everyone, in Minecraft and in Gorilla Tag.

Each player still needs their **own** Gorilla Tag with GorillaCraft and their **own** Minecraft
(the GorillaCraft instance that the plugin sets up). The server is just where the Minecraft world
lives.

### 8.1 Set up the server (one person)

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
   - `gorillacraft-fabric-0.7.2+26.3.jar` from the [GorillaCraft release](https://github.com/easyax2/gorillacraft/releases)
6. Optional, in `server.properties`:
   - `gamemode=creative` (and `force-gamemode=true`) for building, or leave survival
   - `difficulty=peaceful` if you don't want mobs attacking gorillas
   - `spawn-protection=0`, so everyone can build near spawn
7. Start it again with the same command. It's ready when it prints `Done`.
8. If friends join over the internet, forward **TCP port 25565** on your router to this PC, and
   allow Java through Windows Firewall when it asks. On the same network, nobody needs that.

GorillaCraft on the server is what lets gorillas climb freely; without it the server pulls players
back when they hug block edges. The server doesn't need Gorilla Tag.

### 8.2 Join it (everyone)

1. Start Gorilla Tag. The GorillaCraft menu is on your monitor (F1 if it's closed).
2. **Multiplayer** → type the server's address → **Enter**:
   - the server PC itself: `localhost`
   - same home network: the server PC's local IP, e.g. `192.168.1.20`
   - over the internet: the host's public IP or domain, e.g. `123.45.67.89`
     (add `:port` if it isn't 25565)
3. Minecraft leaves its singleplayer world and joins the server, and you're dropped into the
   server's world in Gorilla Tag.
4. **Everyone joins the same private Gorilla Tag room** (same room code) so you can see each other
   as gorillas. Or join the same modded lobby if you all have Utilla.

The server remembers each player's Minecraft position, so you come back where you left off.
Everyone needs the same `Anchor` and `BlockScale` settings (the defaults are fine) so the gorillas
line up on the same blocks.

### Quick alternative: Open to LAN

For a quick game on one home network, one player can open their singleplayer world in the
Minecraft window: Esc → **Open to LAN** → **Start LAN World**. Others join with
`<that PC's IP>:<the port Minecraft shows in chat>` from the GorillaCraft menu.

## 9. Performance tips

Gorilla Tag in VR needs steady frame rates. Minecraft running in the background competes with it.

- In overlay mode, Minecraft's frame rate is how often your view of Minecraft updates, and with
  per-eye pictures the eyes take turns, so each eye gets half. The instance starts at 30 FPS;
  raise **Max Framerate** (Minecraft: Options → Video Settings) to 60 or more if your PC has room,
  and keep render distance modest (6–8). If it's too heavy, `PerEye = false` halves the work.
- While you're in the map, Minecraft runs uncapped (no FPS limit, no vsync, no slowing down
  when its window is idle); your Max Framerate setting comes back afterwards.
- The overlay's sharpness is the Minecraft window's size (1920×1200 by default; in Prism:
  **GorillaCraft → Edit → Settings → Window size**). Bigger is sharper and heavier.
- If the overlay updates slowly, lower `overlayGrid` in Minecraft's `gorillacraft.properties`.
- On a weaker PC, `Rendering.Mode = Blocks` is much lighter: Gorilla Tag draws the blocks
  itself and Minecraft doesn't have to render anything for you.
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
| `Could not load [GorillaCraft 0.1.0] because it has missing dependencies: ...utilla` | That's the old 0.1.0, which needed Utilla. Install the newest release. |
| Wrist: "Prism Launcher not found" | Install Prism (section 3). If it's a portable copy or on another drive, set `PrismPath` (and maybe `PrismDataDir`). |
| Wrist: "prismlauncher.exe not found" | Set `PrismPath` to the full path of `prismlauncher.exe`. |
| Prism opens but Minecraft doesn't start | Add your Microsoft account in Prism (**Settings → Accounts**). Start the **GorillaCraft** instance from Prism once by hand to see the error. |
| Minecraft starts but stays on the title screen | The world couldn't be opened. Open any world yourself; GorillaCraft uses it. Check that the instance's JVM arguments (Prism: **Edit → Settings → Java**) still contain `-Dgorillacraft.autoWorld=GorillaCraft`. |
| Menu doesn't react to keys | Click into the Gorilla Tag window on your monitor first, so it has keyboard focus. F1 toggles the menu. |
| Climbing breaks blocks in creative | Raise `Punch speed` in the menu, or turn on fist mode. |
| Punches don't break anything | Hit faster, or lower `Punch speed`. In survival, keep punching: each punch mines for `Mining per punch` seconds. Check the white highlight: your hand has to be on the block. |
| "Couldn't join" / "Connection Lost" on the wrist or menu | Wrong address, the server is off, or the port isn't forwarded. The Minecraft window shows the full reason. The server must run Minecraft 26.3 with Fabric. |
| Wrist dot stays red | Minecraft isn't running with the mod, or the ports differ. Minecraft's log should say `Waiting for Gorilla Tag on 127.0.0.1:47123`. If it says the port is in use, pick another port in **both** settings files. |
| Wrist says "Open a Minecraft world" | Minecraft is connected but on the title screen. Open a world. |
| "GorillaCraft is off in public lobbies" | You're in a public room. Leave it, or use a private room code. |
| You fall through the world after entering | Blocks didn't arrive in time. Hold B + Y twice to re-enter. Check Minecraft's log for errors. |
| Some Gorilla Tag scenery still shows in the map | BepInEx's log has a line `Gorilla-only view keeps layers ...`; please report it in an issue with that line. |
| Each eye seems to show the same picture | BepInEx's log says whether per-eye works: "each eye sees its own", or "renders both eyes together (single-pass)". In the second case Gorilla Tag couldn't be switched to multi-pass; please report it. |
| You see nothing / only black in the map (overlay) | The Minecraft window must be open and not minimised, and in the world. Check Minecraft's log for `Overlay:` lines and errors. Try `Rendering.Mode = Blocks` to rule out the overlay. |
| The overlay is blurry | Make the Minecraft window bigger (Prism: instance settings → Window size), or lower `overlayFov` a little. |
| A black circle in the sky | Fixed in 0.6.0 (Gorilla Tag's far clip plane cut the sky off). |
| Depth or perspective feels wrong | Try **Swap eyes** in the menu's Settings (or `SwapEyes = true`). |
| Can't place blocks | Your hotbar may be empty (new creative world): tap Y and drag blocks into the hotbar. The held item shows in your hand. |
| Overlay is slow / log says frames go "through the socket" | Frames normally go through shared memory: BepInEx's log says `Minecraft's frames come through shared memory`. If it says it can't map them instead, report the message. |
| The overlay lags or smears when you move fast | Raise Minecraft's Max Framerate, lower `overlayGrid`. A bit of smearing at edges right after fast moves is expected. |
| Blocks are **pink/magenta** or invisible | The block shader isn't available in your Gorilla Tag version. In `dev.gorillacraft.cfg` set `Shader = Sprites/Default` and restart. Also check the log line `Block shader: ...`. |
| Blocks are white/untextured | The block texture hasn't arrived from Minecraft yet. Check Minecraft's log for `Block atlas exported`. If you change resource packs in Minecraft, restart Gorilla Tag so it picks up the new textures. |
| Leaves or glass look solid / water isn't see-through | Your Gorilla Tag build lacks that shader variant. Try `Shader = Sprites/Default`. |
| Placing/using doesn't register | Raise `TouchDistance` to `0.18`. Make sure Minecraft isn't on a menu or the death screen. In survival, placing needs the block in your hotbar and selected. |
| Blocks get placed "inside" you or not at all next to your body | Your Minecraft player's body is where the gorilla is, and Minecraft won't place blocks into it. Reach a little further out. |
| A chest opens and closes immediately | Container screens aren't supported yet. Use the Minecraft window. |
| You keep getting pulled back when climbing on a server | Install GorillaCraft (and Fabric API) on the server too. |
| Version mismatch message on the wrist | The plugin and the Minecraft mod are from different releases. Restart Gorilla Tag; it updates the mod in the GorillaCraft instance. A server needs the matching jar from the same release. |

If you're stuck, open an [issue](https://github.com/easyax2/gorillacraft/issues) and attach both
log files.

## 11. Updating and uninstalling

**Updating:** replace `BepInEx\plugins\GorillaCraft\GorillaCraft.dll` with the one from the new
release. The next time Gorilla Tag starts, it updates the mod in the GorillaCraft Prism instance
too. Your settings and worlds are kept. Update servers by hand with the new jar from the release (and restart them).

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

