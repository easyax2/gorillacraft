# GorillaCraft link protocol (version 1)

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
| 0x01 | HELLO | `i32 protocolVersion, str pluginVersion` |
| 0x02 | POSE | `u8 inMap, i32 teleportAck, f64 x, f64 y, f64 z (eyes), f32 yaw, f32 pitch` — ~50 Hz while in the map, and on every inMap change |
| 0x03 | MINE | `u8 active, i32 x, i32 y, i32 z, u8 face` — sent when the mined block/face changes or mining stops |
| 0x04 | USE | `i32 x, i32 y, i32 z, u8 face, f64 hitX, f64 hitY, f64 hitZ` — one right-click on that block |
| 0x05 | SLOT | `u8 slot (0-8)` |

## Minecraft → Gorilla Tag

| Type | Name | Payload |
|---|---|---|
| 0x81 | WELCOME | `i32 protocolVersion` |
| 0x82 | WORLD | `u8 hasWorld, str dimension, i32 originX, originY, originZ (world spawn), i32 minY, i32 maxY` — drop every section |
| 0x83 | ATLAS | `i32 length, PNG bytes` — Minecraft's block atlas; section UVs point into it (v = 0 is the top row) |
| 0x84 | SECTION | see below |
| 0x85 | SECTION_REMOVE | `i32 sx, sy, sz` |
| 0x86 | TELEPORT | `i32 id, f64 x, f64 y, f64 z (eyes), f32 yaw` |
| 0x87 | HOTBAR | `u8 selected`, then 9 × `str name, u8 count` |
| 0x88 | STATUS | `str text` |

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

## Teleports

Gorilla Tag owns the player's position, except when Minecraft moves the player itself (entering
the map, `/tp`, respawning, server corrections). Then Minecraft sends `TELEPORT` with a new id
and ignores every `POSE` whose `teleportAck` is lower, until Gorilla Tag has jumped there and
echoes the id back.
