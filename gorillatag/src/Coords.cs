using UnityEngine;

namespace GorillaCraft
{
    /// <summary>
    /// Minecraft ↔ Unity coordinates. Minecraft is right-handed (X east, Y up, Z south), Unity is
    /// left-handed, so Z flips: Unity +Z is Minecraft north. The Minecraft world's spawn point sits
    /// at <see cref="Anchor"/>, far from Gorilla Tag's own maps, and one block is
    /// <see cref="Scale"/> Unity units. Everyone with the same config sees the same blocks in the
    /// same place, so gorillas in a modded lobby who share a Minecraft server line up.
    /// </summary>
    public sealed class Coords
    {
        public Vector3 Anchor;
        public float Scale = 1f;
        public int OriginX, OriginY, OriginZ;

        public Vector3 ToUnity(double x, double y, double z)
        {
            return Anchor + Scale * new Vector3((float)(x - OriginX), (float)(y - OriginY), (float)-(z - OriginZ));
        }

        public void ToMinecraft(Vector3 u, out double x, out double y, out double z)
        {
            Vector3 d = (u - Anchor) / Scale;
            x = d.x + OriginX;
            y = d.y + OriginY;
            z = -d.z + OriginZ;
        }

        public static Vector3 DirectionToMinecraft(Vector3 d) => new Vector3(d.x, d.y, -d.z);

        /// <summary>Minecraft yaw/pitch (degrees) looking along a Unity direction.</summary>
        public static void LookAngles(Vector3 unityForward, out float yaw, out float pitch)
        {
            Vector3 d = DirectionToMinecraft(unityForward).normalized;
            yaw = Mathf.Atan2(-d.x, d.z) * Mathf.Rad2Deg;
            pitch = -Mathf.Asin(Mathf.Clamp(d.y, -1f, 1f)) * Mathf.Rad2Deg;
        }
    }
}
