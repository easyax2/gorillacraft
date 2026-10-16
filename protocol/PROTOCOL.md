# GorillaCraft link protocol (version 8)

Loopback TCP. Minecraft listens on `127.0.0.1:47123` (configurable), Gorilla Tag connects and
reconnects as needed. One Gorilla Tag at a time; a new connection replaces the old one.

TCP and not shared memory (which SkyCraft uses), because Gorilla Tag normally runs under Proton
while Minecraft runs natively, and loopback sockets are what both sides can see.

Every frame is `[u32 length][u8 type][payload]`, little-endian, where `length` counts the type
byte and the payload. Strings are `u16 byte length` + UTF-8. Mirrored in
`fabric/src/main/java/dev/gorillacraft/link/Proto.java` and `gorillatag/src/Proto.cs`.

Coordinates in messages are always Minecraft's (X east, Y up, Z south). Gorilla Tag converts:
`unity = anchor + scale * (x - originX, y - originY, -(z - originZ))`.

Faces use Minecraft's `Direction` 3D data value: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east.

## Gorilla Tag → Minecraft

| Type | Name | Payload |
|---|---|---|
| 0x01 | HELLO | `i32 protocolVersion, str pluginVersion, u8 wants` — bit 1: block meshes (and the atlas), bit 2: overlay frames, bit 4: per-eye frames (alternating left/right), bit 8: frames through shared memory. Collision and shapes are always sent. |
| 0x02 | POSE | `u8 inMap, i32 teleportAck, f64 x, f64 y, f64 z (eyes), f32 yaw, f32 pitch, f64 leftEye xyz, f64 rightEye xyz, f64 leftHand xyz, f64 rightHand xyz, u32 color (0xRRGGBB)` — ~50 Hz while in the map, and on every inMap change |
| 0x03 | MINE | `u8 active, i32 x, i32 y, i32 z, u8 face` — sent when the mined block/face changes or mining stops |
| 0x04 | USE | `i32 x, i32 y, i32 z, u8 face, f64 hitX, f64 hitY, f64 hitZ` — one right-click on that block |
| 0x05 | SLOT | `u8 slot (0-8)` |
| 0x06 | LIST_WORLDS | — (answered with WORLDS) |
| 0x07 | OPEN_WORLD | `str id` — open that singleplayer world, or create it (creative, cheats on) if there's none; leaves the current world first |
| 0x08 | JOIN_SERVER | `str address` — leave the current world, then connect |
| 0x09 | LEAVE_WORLD | — save and quit to the title screen |
| 0x0A | INVENTORY | `u8 open` — open the inventory, or (0) close whatever screen is open |
| 0x0B | POINTER | `f32 u, f32 v (0..1 across Minecraft's window, from the top left), u8 buttons (bit 0 left, bit 1 right)` |
| 0x0C | SCROLL | `f32 amount` |
| 0x0E | MODEL | Gorilla Tag's gorilla, read from the running game (sent once): `i32 n`, n × (`f32 pos xyz, normal xyz, uv`, `u8 bones[4]`, `f32 weights[4]`), `i32 parts`, parts × (`i32 count, i32 indices[count], u8 tinted, f32 rgb, f32 uv scale/offset[4], i32 len, PNG`), `i32 boneCount` |
| 0x0F | BONES | `f64 ref xyz, i32 n`, n × 12 `f32`: per bone, the 3x4 matrix from mesh space to Minecraft coordinates relative to ref (Gorilla Tag's live pose) |
| 0x0D | SHM | `u8 ok` — whether Gorilla Tag could map the shared frame memory offered with MC SHM |

## Minecraft → Gorilla Tag

| Type | Name | Payload |
|---|---|---|
| 0x81 | WELCOME | `i32 protocolVersion` |
| 0x82 | WORLD | `u8 hasWorld, str dimension, i32 originX, originY, originZ (world spawn), i32 minY, i32 maxY, str label (world name, or server address)` — drop every section |
| 0x83 | ATLAS | `i32 length, PNG bytes` — Minecraft's block atlas; section UVs point into it (v = 0 is the top row) |
| 0x84 | SECTION | see below |
| 0x85 | SECTION_REMOVE | `i32 sx, sy, sz` |
| 0x86 | TELEPORT | `i32 id, f64 x, f64 y, f64 z (eyes), f32 yaw` |
| 0x87 | HOTBAR | `u8 selected`, then 9 × `str name, u8 count` |
| 0x88 | STATUS | `str text` |
| 0x8A | FRAME | overlay picture, see below |
| 0x8B | GUI_FRAME | `i32 width, height`, then width × height × RGBA: Minecraft's whole window while a screen is open; 0 × 0 = closed |
| 0x8D | SHM | `str path, f64 size` — the shared frame memory file (Minecraft's own path) |
| 0x8C | HELD | `u8 kind (0 nothing, 1 item, 2 block), i32 length, PNG` — the held item's texture |
| 0x89 | WORLDS | `i32 n`, then n × `str id, str name` — singleplayer worlds, most recently played first (not the one that's open) |

### SECTION

```
i32 sx, sy, sz                      section coordinates (blocks = 16 × these)
3 × layer (solid, cutout, translucent):
    i32 quadCount
    quadCount × 4 × vertex:  f32 x, y, z (section-relative)  f32 u, v  u8 r, g, b, a
i32 collisionQuadCount
    collisionQuadCount × 4 × (f32 x, y, z)
i32 shapeCount
    shapeCount × (u16 index = y<<8 | z<<4 | x,  u8 boxCount (0xFF = full cube),  boxCount × f32 minX..maxZ block-local)
```

All quads are wound counter-clockwise seen from outside (Minecraft's convention). Vertex colour
already includes biome tint, ambient occlusion, face shading and light level. Collision faces
between two full cubes are left out, and so are shapes of full cubes buried on all six sides.

### FRAME

Sent after Minecraft renders a frame from the gorilla's head pose (only while Gorilla Tag is in
the map and asked for frames; one in flight at a time).

```
i32 frameId
u8 eye                         0 left, 1 right, 2 centre of the head
f64 camX, camY, camZ          camera position the frame was drawn from
i32 width, height
width × height × (u8 r, g, b, a)  the picture, rows top to bottom
i32 gridW, gridH
gridW × gridH × (f32 x, y, z)  where each grid point of the picture is, relative to the camera
                               (Minecraft axes), rows top to bottom; sky at 300 blocks
```

Grid points are ray casts from the camera through the picture into Minecraft's blocks (visual
shapes and fluids), so they match the picture except for entities. Gorilla Tag stretches the
picture over the grid.

### Shared frame memory

Like SkyCraft's overlay: when Gorilla Tag asks for it (HELLO bit 8) Minecraft creates a file in
`/dev/shm` (Linux) or the temp folder (Windows), offers it with MC SHM, and once Gorilla Tag
answers SHM ok, FRAME and GUI_FRAME pictures go there instead of the socket. Gorilla Tag under
Proton reaches a Linux path through the `Z:` drive. If mapping fails, everything stays on the
socket.

The file holds four lock-free triple buffers (left eye, right eye, centre, GUI panel). Minecraft
copies a finished GPU readback into its private slot with one memcpy (rows bottom-up, as the GPU
left them), then swaps it in with an atomic exchange; Gorilla Tag swaps the newest one out the
same way and hands it to Unity straight from the shared memory. Layout:
`fabric/src/main/java/dev/gorillacraft/link/SharedFrames.java` and `gorillatag/src/SharedFrames.cs`.

## Teleports

Gorilla Tag owns the player's position, except when Minecraft moves the player itself (entering
the map, `/tp`, respawning, server corrections). Then Minecraft sends `TELEPORT` with a new id
and ignores every `POSE` whose `teleportAck` is lower, until Gorilla Tag has jumped there and
echoes the id back.
