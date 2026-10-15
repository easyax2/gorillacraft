namespace GorillaCraft
{
    /// <summary>
    /// Wire protocol shared with the Minecraft mod (protocol/PROTOCOL.md, mirrored in
    /// fabric/.../link/Proto.java). Frames are [u32 length][u8 type][payload], little-endian.
    /// </summary>
    public static class Proto
    {
        public const int Version = 5;
        public const int MaxFrame = 64 << 20;

        // Gorilla Tag → Minecraft
        public const byte GtHello = 0x01;
        public const byte GtPose = 0x02;
        public const byte GtMine = 0x03;
        public const byte GtUse = 0x04;
        public const byte GtSlot = 0x05;
        public const byte GtListWorlds = 0x06;
        public const byte GtOpenWorld = 0x07;
        public const byte GtJoinServer = 0x08;
        public const byte GtLeaveWorld = 0x09;
        public const byte GtInventory = 0x0A;
        public const byte GtPointer = 0x0B;
        public const byte GtScroll = 0x0C;

        // Minecraft → Gorilla Tag
        public const byte McWelcome = 0x81;
        public const byte McWorld = 0x82;
        public const byte McAtlas = 0x83;
        public const byte McSection = 0x84;
        public const byte McSectionRemove = 0x85;
        public const byte McTeleport = 0x86;
        public const byte McHotbar = 0x87;
        public const byte McStatus = 0x88;
        public const byte McWorlds = 0x89;
        public const byte McFrame = 0x8A;
        public const byte McGuiFrame = 0x8B;
        public const byte McHeld = 0x8C;

        /// <summary>HELLO flags: what Gorilla Tag wants from Minecraft.</summary>
        public const byte WantBlockMeshes = 1;
        public const byte WantFrames = 2;
        public const byte WantStereo = 4;

        public const byte EyeLeft = 0, EyeRight = 1, EyeCenter = 2;

        public const byte ShapeFullCube = 0xFF;

        /// <summary>Minecraft's Direction.get3DDataValue order.</summary>
        public enum Face : byte { Down = 0, Up = 1, North = 2, South = 3, West = 4, East = 5 }
    }
}
