# GorillaCraft

Minecraft inside Gorilla Tag. You stay a gorilla (Gorilla Tag's arm locomotion, in VR) and see
Minecraft's own graphics around you: Minecraft draws the world from your gorilla head, and Gorilla
Tag shows that picture in 3D around you. You climb Minecraft's blocks, punch them out with
Minecraft's mining rules, and place blocks, open doors and flip levers with your hands.

Modelled on [SkyCraft](https://github.com/RyanCraighead/SkyCraft) (Minecraft in Skyrim) and its
rule that **neither game is rewritten**: Minecraft runs its own game in the background and does the
world (blocks, meshing, lighting, mining, placement rules, saving), Gorilla Tag does the gorilla.
Two small mods translate between them. Authority is split the other way round from SkyCraft:
there, Minecraft owns the player and Skyrim the world; here, Gorilla Tag owns the player and
Minecraft the world.

Tag features aren't in yet. This is interactions and maps only.

## What works

- **Minecraft's real graphics (overlay), per eye:** Minecraft renders the view from your left
  and right eye in turn (world, mobs, items, sky, day and night, weather, particles, lighting, resource
  packs, shaders it supports). Gorilla Tag shows that picture on a 3D surface built from where
  each part of it is, so it has real depth in VR, stays fixed in the world when you turn your
  head, and gorillas and hands sort against it. Gorilla Tag draws nothing of its own while you're
  in the map except gorillas: no Gorilla Tag sky or scenery, black outside Minecraft's view.
- **Maps:** any Minecraft world, singleplayer or a server. Its collision shapes are real (but
  invisible) Gorilla Tag colliders you can climb, streamed in around you (4 chunks by default).
  The older mode, where Gorilla Tag draws Minecraft's block meshes itself, is still there as
  `Rendering.Mode = Blocks`.
- **Mining:** punch a block (hard, straight into it). Each punch keeps Minecraft mining it for a moment, so creative
  breaks in one hit and survival takes as many punches as Minecraft's break time. Tools and drops
  are Minecraft's. Optional fist mode: only punches with grip held count, so climbing never
  breaks blocks.
- **Using:** press **grip** with a hand on a block to right-click it with your held item (the
  trigger, in fist mode). That places blocks, opens doors and trapdoors, presses buttons and
  flips levers.
- **Main menu** on your monitor (F1), with mouse and keyboard: pick or create a singleplayer
  world, join a server, enter or leave the map, change settings.
- **Hotbar:** **A** = next slot, **X** = previous slot. The hotbar and status are on your left
  wrist, and the held item is in your gorilla's hand.
- **Inventory:** tap **Y**: Minecraft's own inventory (and chests, crafting tables, furnaces)
  on a panel you point at and click with your right hand.
- **Live changes:** anything that changes in Minecraft (you, other players, redstone, water,
  explosions) updates in Gorilla Tag.
- **Gorillas in Minecraft:** every GorillaCraft player is a gorilla in the Minecraft world, posed
  from their real head and hands, in their Gorilla Tag colour, holding their held item. It's made
  of Minecraft display entities, so **everyone** on the server sees it, even players without the
  mod; the player's normal model is hidden. In VR you see other gorillas in Minecraft's picture,
  while your own hands stay Gorilla Tag's (no delay).

  ![A gorilla in Minecraft](docs/gorilla-avatar.png)
- **Multiplayer:** run a Fabric 26.3 server with GorillaCraft and Fabric API, and everyone joins
  it from the menu. Gorillas on the same server share the world, and they line up in Gorilla Tag
  too, because the map is anchored at the Minecraft world's spawn point.

**Windows players: follow the [step-by-step Windows guide](docs/WINDOWS-GUIDE.md).** Downloads are on the [releases page](https://github.com/easyax2/gorillacraft/releases).

## Requirements

- Gorilla Tag (Steam/PC VR) with **BepInEx 5**. Utilla is optional. GorillaCraft is active alone
  (before joining a room), in private rooms and in Utilla's modded lobbies, and switches itself
  off in public lobbies.
- **Prism Launcher** with your Microsoft account (owning Minecraft: Java Edition). The plugin
  carries the Minecraft mod and Fabric API: it creates a **GorillaCraft** instance (Minecraft
  26.3 + Fabric) in Prism, starts it (on Windows), and Minecraft opens its world by itself.
- On a Minecraft server, install `gorillacraft-fabric-<version>+26.3.jar` and Fabric API too.
  Without it, the server may pull you back when you hug block edges.

## Playing

1. Start Gorilla Tag. After a few seconds GorillaCraft sets up and starts Minecraft through
   Prism (the first time, Prism downloads Minecraft). Minecraft opens a creative world called
   GorillaCraft. It can sit in the background; it doesn't pause while Gorilla Tag is connected.
   On Linux/Proton, start the instance yourself or with a Steam launch option (see the guide).
2. You're dropped into the Minecraft world where your Minecraft player stands. Hold **B + Y**
   for a second to leave the map or go back in. Play with others in a private room.

Tip: lower Minecraft's max framerate (or minimise it) so your VR game gets the GPU.

## Configuration

Gorilla Tag: `BepInEx/config/dev.gorillacraft.cfg`

| Setting | Default | |
|---|---|---|
| `Link.Host` / `Link.Port` | `127.0.0.1` / `47123` | Where Minecraft listens |
| `Map.Anchor` | `0, 1500, 0` | Where Minecraft's spawn point sits in Gorilla Tag's world, far from Gorilla Tag's own maps |
| `Map.BlockScale` | `1` | Size of a block in Gorilla Tag units (meters) |
| `Map.AutoEnter` | `true` | Enter the map as soon as Minecraft is ready |
| `Minecraft.AutoInstall` / `AutoLaunch` | `true` / `true` | Set up / start the GorillaCraft Prism instance |
| `Minecraft.PrismPath` / `PrismDataDir` / `World` | | Where Prism is, and which world opens |
| `Hands.TouchDistance` | `0.12` | How close a hand must be to a block to punch or use it |
| `Hands.PunchSpeed` / `PunchHold` / `FistMode` | `2.0` / `0.4` / `false` | What counts as a punch, how long each punch mines, fist mode |
| `Menu.OpenAtStart` / `Servers` | `true` / | Show the menu at start; servers joined from it |
| `Performance.SectionsPerFrame` | `4` | Mesh/collider builds per frame |
| `Rendering.PerEye` | `true` | Each eye gets its own Minecraft picture (Gorilla Tag switches to multi-pass rendering) |
| `Rendering.Mode` | `Overlay` | `Overlay`: Minecraft's picture in 3D. `Blocks`: Gorilla Tag draws block meshes itself |
| `Rendering.Shader` | `Universal Render Pipeline/Particles/Unlit` | Block shader (falls back to `Sprites/Default`) |

Minecraft: `config/gorillacraft.properties` (`port`, `radius` in chunks, `verticalRadius` in
sections, `sectionsPerTick`, and for the overlay `overlayWidth`, `overlayGrid`, `overlayFov`).

## Known limitations

- **Not tested in a headset yet.** Both halves are tested together over the real link (see
  below), but the Unity side (meshes, colliders, hands, wrist display) has only been compiled
  against Gorilla Tag. Expect tuning: `BlockScale`, `TouchDistance` and the wrist panel position.
- Container screens (chests, furnaces, crafting tables) can't be shown in Gorilla Tag yet. They
  close straight away. Use the Minecraft window for your inventory.
- Frames go from Minecraft to Gorilla Tag through shared memory, the way SkyCraft hands its
  overlay to Skyrim (about 115 frames/s at 1920×1200 in testing; real worlds cost Minecraft more
  to draw).
- The overlay updates at Minecraft's frame rate, shared between the eyes (each eye gets every
  other frame: raise Minecraft's Max Framerate to 60+ if you can). The 3D surface keeps head
  turning smooth in between. Fast movement shows edges where Minecraft hasn't drawn
  yet, and things right next to edges can look smeared until the next frame.
- Mobs, players and gorillas get depth from their hitboxes, so they stand in front of the wall
  behind them, but as boxes: an outstretched arm is drawn at the body's depth.
- The overlay needs Minecraft's window open (not minimised), and its window size is the overlay's
  resolution.
- If you die in Minecraft (survival), you respawn straight away.

## Building

```bash
cd fabric && ./gradlew build                              # → fabric/build/libs/gorillacraft-fabric-*.jar
cd gorillatag && dotnet build -c Release                  # → gorillatag/bin/Release/GorillaCraft.dll (embeds the jar above + Fabric API)
cd gorillatag && dotnet build -c Release -p:Deploy=true   # also copies it to BepInEx/plugins/GorillaCraft
```

`GamePath` defaults to the usual Steam path on Linux; pass `-p:GamePath=...` otherwise.

## Testing

`tools/linktest` is a stand-in for Gorilla Tag, built from the plugin's own link and parsing
code (`gorillatag/src/{Proto,Wire,LinkClient,Messages}.cs`). It drives a real Minecraft client
running a Fabric client gametest:

```bash
export GORILLACRAFT_TEST_DIR=/tmp/gorillacraft-test && rm -rf $GORILLACRAFT_TEST_DIR
(cd fabric && ./gradlew runClientGameTest) &
(cd tools/linktest && dotnet run -c Release)
```

It checks the atlas, meshes, shapes, face winding and collision culling. It also enters the map,
switches hotbar slots, mines, places, flips a lever and follows a `/tp`. The gametest then checks
the results in the server's world.

## Layout

| Folder | |
|---|---|
| `fabric/` | Minecraft Fabric mod (Java 25, MC 26.3) |
| `gorillatag/` | Gorilla Tag BepInEx plugin (C#, netstandard2.1) |
| `protocol/` | The link protocol between them |
| `tools/linktest/` | Gorilla Tag stand-in for end-to-end tests |

## License

[MIT](LICENSE). Design after SkyCraft (MIT, © 2026 chasmlol); no code is shared with it.
