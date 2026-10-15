using System.Collections.Generic;

namespace GorillaCraft
{
    /// <summary>Parsed Minecraft → Gorilla Tag messages. Nothing here touches Unity.</summary>
    public static class Messages
    {
        public sealed class Welcome { public int Version; }

        public sealed class World
        {
            public bool HasWorld;
            public string Dimension;
            public int OriginX, OriginY, OriginZ;
            public int MinY, MaxY;
            /// <summary>World name in singleplayer, server address in multiplayer.</summary>
            public string Label;
        }

        public sealed class Worlds
        {
            public string[] Ids;
            public string[] Names;
        }

        public sealed class Atlas { public byte[] Png; }

        public sealed class Layer
        {
            public int VertexCount;
            /// <summary>xyz per vertex, section-relative Minecraft coordinates.</summary>
            public float[] Positions;
            public float[] Uvs;
            /// <summary>rgba per vertex.</summary>
            public byte[] Colors;
        }

        public struct Box
        {
            public float MinX, MinY, MinZ, MaxX, MaxY, MaxZ;
        }

        public sealed class Section
        {
            public int X, Y, Z;
            /// <summary>Solid, cutout, translucent.</summary>
            public Layer[] Layers;
            /// <summary>Collision quads: 4 vertices × xyz each, counter-clockwise seen from outside (Minecraft axes).</summary>
            public float[] Collision;
            public int CollisionQuads;
            /// <summary>Outline boxes (block-local 0..1) of blocks a hand can reach, keyed by y&lt;&lt;8 | z&lt;&lt;4 | x.</summary>
            public Dictionary<int, Box[]> Shapes;

            public bool IsEmpty => CollisionQuads == 0 && Shapes.Count == 0
                && Layers[0].VertexCount == 0 && Layers[1].VertexCount == 0 && Layers[2].VertexCount == 0;
        }

        public sealed class SectionRemove { public int X, Y, Z; }

        public sealed class Teleport
        {
            public int Id;
            public double X, Y, Z;
            public float Yaw;
        }

        public sealed class Hotbar
        {
            public int Selected;
            public string[] Names = new string[9];
            public int[] Counts = new int[9];
        }

        public sealed class Status { public string Text; }

        public static readonly Box FullCube = new Box { MaxX = 1, MaxY = 1, MaxZ = 1 };

        public static object Parse(byte type, FrameReader r)
        {
            switch (type)
            {
                case Proto.McWelcome:
                    return new Welcome { Version = r.I32() };
                case Proto.McWorld:
                    return new World
                    {
                        HasWorld = r.U8() != 0, Dimension = r.Str(),
                        OriginX = r.I32(), OriginY = r.I32(), OriginZ = r.I32(),
                        MinY = r.I32(), MaxY = r.I32(), Label = r.Str(),
                    };
                case Proto.McWorlds:
                    int n = r.I32();
                    var ws = new Worlds { Ids = new string[n], Names = new string[n] };
                    for (int i = 0; i < n; i++)
                    {
                        ws.Ids[i] = r.Str();
                        ws.Names[i] = r.Str();
                    }
                    return ws;
                case Proto.McAtlas:
                    return new Atlas { Png = r.Bytes(r.I32()) };
                case Proto.McSection:
                    return ParseSection(r);
                case Proto.McSectionRemove:
                    return new SectionRemove { X = r.I32(), Y = r.I32(), Z = r.I32() };
                case Proto.McTeleport:
                    return new Teleport { Id = r.I32(), X = r.F64(), Y = r.F64(), Z = r.F64(), Yaw = r.F32() };
                case Proto.McHotbar:
                    var h = new Hotbar { Selected = r.U8() };
                    for (int i = 0; i < 9; i++)
                    {
                        h.Names[i] = r.Str();
                        h.Counts[i] = r.U8();
                    }
                    return h;
                case Proto.McStatus:
                    return new Status { Text = r.Str() };
                default:
                    return null;
            }
        }

        private static Section ParseSection(FrameReader r)
        {
            var s = new Section { X = r.I32(), Y = r.I32(), Z = r.I32(), Layers = new Layer[3] };
            for (int l = 0; l < 3; l++)
            {
                int quads = r.I32();
                int n = quads * 4;
                var layer = new Layer
                {
                    VertexCount = n, Positions = new float[n * 3], Uvs = new float[n * 2], Colors = new byte[n * 4],
                };
                for (int v = 0; v < n; v++)
                {
                    layer.Positions[v * 3] = r.F32();
                    layer.Positions[v * 3 + 1] = r.F32();
                    layer.Positions[v * 3 + 2] = r.F32();
                    layer.Uvs[v * 2] = r.F32();
                    layer.Uvs[v * 2 + 1] = r.F32();
                    layer.Colors[v * 4] = r.U8();
                    layer.Colors[v * 4 + 1] = r.U8();
                    layer.Colors[v * 4 + 2] = r.U8();
                    layer.Colors[v * 4 + 3] = r.U8();
                }
                s.Layers[l] = layer;
            }
            s.CollisionQuads = r.I32();
            s.Collision = r.Floats(s.CollisionQuads * 12);
            int shapes = r.I32();
            s.Shapes = new Dictionary<int, Box[]>(shapes);
            for (int i = 0; i < shapes; i++)
            {
                int key = r.U16();
                int n = r.U8();
                if (n == Proto.ShapeFullCube)
                {
                    s.Shapes[key] = new[] { FullCube };
                    continue;
                }
                var boxes = new Box[n];
                for (int b = 0; b < n; b++)
                    boxes[b] = new Box { MinX = r.F32(), MinY = r.F32(), MinZ = r.F32(), MaxX = r.F32(), MaxY = r.F32(), MaxZ = r.F32() };
                s.Shapes[key] = boxes;
            }
            return s;
        }
    }
}
