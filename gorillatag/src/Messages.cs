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

            /// <summary>Water columns (section-local): x, z, bottom y, top y (inclusive), surface height of the top block in 1/16.</summary>
            public WaterRun[] Water = new WaterRun[0];

            public bool IsEmpty => CollisionQuads == 0 && Shapes.Count == 0 && Water.Length == 0
                && Layers[0].VertexCount == 0 && Layers[1].VertexCount == 0 && Layers[2].VertexCount == 0;
        }

        public struct WaterRun
        {
            public byte X, Z, Bottom, Top, Height;
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

        /// <summary>
        /// The headset and controllers from Minecraft's OpenXR session: tracking space, OpenXR axes
        /// (right-handed, -Z forward), metres. Poses are x y z, then quaternion x y z w. Hands are
        /// already in SteamVR's controller pose (what Gorilla Tag is tuned for).
        /// </summary>
        public sealed class Tracking
        {
            public int Flags;
            public float[] Head = new float[7], Left = new float[7], Right = new float[7];
            public float[] Trigger = new float[2], Grip = new float[2], StickX = new float[2], StickY = new float[2];
            public int Buttons;
            public bool LeftValid => (Flags & 2) != 0;
            public bool RightValid => (Flags & 4) != 0;
            public bool ScreenOpen => (Flags & 8) != 0;
            public bool Button(int bit) => (Buttons & (1 << bit)) != 0;
        }

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
                case Proto.McTracking:
                    var t = new Tracking { Flags = r.U8() };
                    for (int i = 0; i < 7; i++) t.Head[i] = r.F32();
                    for (int i = 0; i < 7; i++) t.Left[i] = r.F32();
                    for (int i = 0; i < 7; i++) t.Right[i] = r.F32();
                    for (int hand = 0; hand < 2; hand++)
                    {
                        t.Trigger[hand] = r.F32();
                        t.Grip[hand] = r.F32();
                        t.StickX[hand] = r.F32();
                        t.StickY[hand] = r.F32();
                    }
                    t.Buttons = r.U16();
                    return t;
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
            if (r.Remaining >= 4)
            {
                s.Water = new WaterRun[r.I32()];
                for (int i = 0; i < s.Water.Length; i++)
                    s.Water[i] = new WaterRun { X = r.U8(), Z = r.U8(), Bottom = r.U8(), Top = r.U8(), Height = r.U8() };
            }
            return s;
        }
    }
}
