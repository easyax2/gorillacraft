namespace GorillaCraft
{
    /// <summary>
    /// Wire protocol shared with the Minecraft mod (protocol/PROTOCOL.md, mirrored in
    /// fabric/.../link/Proto.java). Frames are [u32 length][u8 type][payload], little-endian.
    /// </summary>
    public static class Proto
    {
        public const int Version = 9;
        public const int MaxFrame = 64 << 20;

        // Gorilla Tag → Minecraft
        public const byte GtHello = 0x01;
        public const byte GtPose = 0x02;
        public const byte GtMine = 0x03;
        public const byte GtUse = 0x04;
        public const byte GtSlot = 0x05;
        public const byte GtModel = 0x0E;
        public const byte GtBones = 0x0F;

        // Minecraft → Gorilla Tag
        public const byte McWelcome = 0x81;
        public const byte McWorld = 0x82;
        public const byte McAtlas = 0x83;
        public const byte McSection = 0x84;
        public const byte McSectionRemove = 0x85;
        public const byte McTeleport = 0x86;
        public const byte McHotbar = 0x87;
        public const byte McStatus = 0x88;
        public const byte McTracking = 0x8E;

        /// <summary>HELLO flags: what Gorilla Tag wants from Minecraft.</summary>
        public const byte WantBlockMeshes = 1;

        public const byte ShapeFullCube = 0xFF;

        /// <summary>Minecraft's Direction.get3DDataValue order.</summary>
        public enum Face : byte { Down = 0, Up = 1, North = 2, South = 3, West = 4, East = 5 }
    }
}
