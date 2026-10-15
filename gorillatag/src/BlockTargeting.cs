using UnityEngine;

namespace GorillaCraft
{
    /// <summary>A block a hand is touching, in Minecraft terms.</summary>
    public struct BlockTarget
    {
        public int X, Y, Z;
        public Proto.Face Face;
        /// <summary>Touch point on the block's outline, Minecraft coordinates.</summary>
        public double HitX, HitY, HitZ;
        /// <summary>The touched outline box, block-local.</summary>
        public Messages.Box Box;

        public bool SameBlock(BlockTarget o) => X == o.X && Y == o.Y && Z == o.Z;
    }

    /// <summary>
    /// Finds the block a hand is touching by testing the hand point against Minecraft's outline
    /// shapes. Outline rather than collision shapes, so buttons, torches, flowers and tall grass
    /// (which a gorilla hand passes through) can still be hit.
    /// </summary>
    public static class BlockTargeting
    {
        public static bool Find(MinecraftWorld world, Vector3 handUnity, float reachUnity, out BlockTarget target)
        {
            target = default;
            world.Coords.ToMinecraft(handUnity, out double hx, out double hy, out double hz);
            double reach = reachUnity / world.Coords.Scale;
            int cx = Mathf.FloorToInt((float)hx), cy = Mathf.FloorToInt((float)hy), cz = Mathf.FloorToInt((float)hz);
            double best = double.MaxValue;
            bool found = false;

            for (int x = cx - 1; x <= cx + 1; x++)
            for (int y = cy - 1; y <= cy + 1; y++)
            for (int z = cz - 1; z <= cz + 1; z++)
            {
                if (!world.TryGetShape(x, y, z, out var boxes))
                    continue;
                foreach (var b in boxes)
                {
                    double minX = x + b.MinX, minY = y + b.MinY, minZ = z + b.MinZ;
                    double maxX = x + b.MaxX, maxY = y + b.MaxY, maxZ = z + b.MaxZ;
                    double px = Clamp(hx, minX, maxX), py = Clamp(hy, minY, maxY), pz = Clamp(hz, minZ, maxZ);
                    double dx = hx - px, dy = hy - py, dz = hz - pz;
                    double dist = System.Math.Sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > reach || dist >= best)
                        continue;
                    best = dist;
                    found = true;
                    target = new BlockTarget { X = x, Y = y, Z = z, Box = b };
                    if (dist > 1e-6)
                    {
                        target.Face = DominantFace(dx, dy, dz);
                        target.HitX = px;
                        target.HitY = py;
                        target.HitZ = pz;
                    }
                    else
                    {
                        // Hand is inside the outline (thin blocks, or pushed in): leave by the nearest side.
                        InsideExit(hx, hy, hz, minX, minY, minZ, maxX, maxY, maxZ, ref target);
                    }
                }
            }
            return found;
        }

        private static double Clamp(double v, double lo, double hi) => v < lo ? lo : v > hi ? hi : v;

        private static Proto.Face DominantFace(double dx, double dy, double dz)
        {
            double ax = System.Math.Abs(dx), ay = System.Math.Abs(dy), az = System.Math.Abs(dz);
            if (ay >= ax && ay >= az)
                return dy > 0 ? Proto.Face.Up : Proto.Face.Down;
            if (ax >= az)
                return dx > 0 ? Proto.Face.East : Proto.Face.West;
            return dz > 0 ? Proto.Face.South : Proto.Face.North;
        }

        private static void InsideExit(double hx, double hy, double hz, double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ, ref BlockTarget t)
        {
            t.HitX = hx;
            t.HitY = hy;
            t.HitZ = hz;
            double best = maxY - hy;
            t.Face = Proto.Face.Up;
            t.HitY = maxY;
            void Try(double d, Proto.Face f, ref BlockTarget tt, double x, double y, double z)
            {
                if (d < best)
                {
                    best = d;
                    tt.Face = f;
                    tt.HitX = x;
                    tt.HitY = y;
                    tt.HitZ = z;
                }
            }
            Try(hy - minY, Proto.Face.Down, ref t, hx, minY, hz);
            Try(maxX - hx, Proto.Face.East, ref t, maxX, hy, hz);
            Try(hx - minX, Proto.Face.West, ref t, minX, hy, hz);
            Try(maxZ - hz, Proto.Face.South, ref t, hx, hy, maxZ);
            Try(hz - minZ, Proto.Face.North, ref t, hx, hy, minZ);
        }
    }
}
