using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using GorillaCraft;

namespace GorillaCraft
{
    // LinkClient sends Plugin.Version in its hello.
    static class Plugin { public const string Version = "linktest"; }
}

static class Program
{
    static LinkClient link;
    static readonly Dictionary<(int, int, int), Messages.Section> sections = new();
    static Messages.World world;
    static Messages.Hotbar hotbar;
    static Messages.Teleport teleport;
    static byte[] atlas;
    static int sectionUpdates;
    static readonly List<string> statuses = new();
    static int failures;

    static int Main(string[] args)
    {
        string dir = Environment.GetEnvironmentVariable("GORILLACRAFT_TEST_DIR") ?? Path.GetTempPath();
        int port = args.Length > 0 ? int.Parse(args[0]) : 47123;
        Console.WriteLine($"linktest: waiting for {Path.Combine(dir, "ready")}");
        WaitFor(() => File.Exists(Path.Combine(dir, "ready")), 600, "world ready");

        link = new LinkClient("127.0.0.1", port, s => Console.WriteLine("link: " + s));
        link.Start();

        WaitFor(() => world != null && world.HasWorld, 30, "WORLD");
        Console.WriteLine($"world {world.Dimension} origin {world.OriginX} {world.OriginY} {world.OriginZ} y {world.MinY}..{world.MaxY}");
        Check(world.Dimension == "minecraft:overworld", "dimension is the overworld");

        WaitFor(() => atlas != null, 60, "ATLAS");
        CheckAtlas(atlas);

        // Section with the test blocks: x 0..15, y -64..-49, z 0..15.
        WaitFor(() => sections.ContainsKey((0, -4, 0)), 60, "section 0 -4 0");
        var s = sections[(0, -4, 0)];
        Console.WriteLine($"section 0 -4 0: solid {s.Layers[0].VertexCount / 4} quads, cutout {s.Layers[1].VertexCount / 4}, translucent {s.Layers[2].VertexCount / 4}, collision {s.CollisionQuads}, shapes {s.Shapes.Count}");
        CheckSection(s);
        WaitFor(() => sections.Count >= 20, 60, "20+ sections");
        Console.WriteLine($"{sections.Count} sections received, {sections.Values.Count(x => !x.IsEmpty)} non-empty");

        // Enter the map: Minecraft answers with a teleport to its player.
        SendPose(true, 0, 0.5, -58.3, 0.5);
        WaitFor(() => teleport != null, 10, "TELEPORT on entering");
        Console.WriteLine($"teleport {teleport.Id} to {teleport.X:F2} {teleport.Y:F2} {teleport.Z:F2}");
        int ack = teleport.Id;
        for (int i = 0; i < 10; i++) { SendPose(true, ack, 0.5, -58.3, 0.5); Pump(50); }

        // Hotbar.
        WaitFor(() => hotbar != null, 10, "HOTBAR");
        Console.WriteLine($"hotbar: selected {hotbar.Selected}, slot 1 '{hotbar.Names[0]}' x{hotbar.Counts[0]}, slot 2 '{hotbar.Names[1]}'");
        Check(hotbar.Names[0].Contains("Plank"), "slot 1 holds planks");
        link.Send(new FrameWriter(Proto.GtSlot).U8(1).Finish());
        WaitFor(() => hotbar.Selected == 1, 5, "slot 2 selected");
        link.Send(new FrameWriter(Proto.GtSlot).U8(0).Finish());
        WaitFor(() => hotbar.Selected == 0, 5, "slot 1 selected");

        // Mine the stone (creative: one hit).
        int before = sectionUpdates;
        link.Send(new FrameWriter(Proto.GtMine).U8(1).I32(2).I32(-60).I32(0).U8((int)Proto.Face.Up).Finish());
        WaitFor(() => sectionUpdates > before && !HasShape(2, -60, 0), 10, "stone mined");
        link.Send(new FrameWriter(Proto.GtMine).U8(0).I32(2).I32(-60).I32(0).U8((int)Proto.Face.Up).Finish());
        Console.WriteLine("mined stone at 2 -60 0");

        // Place planks on top of the grass at 1 -61 0.
        Use(1, -61, 0, Proto.Face.Up, 1.5, -60.0, 0.5);
        WaitFor(() => HasShape(1, -60, 0), 10, "planks placed");
        Console.WriteLine("placed planks at 1 -60 0");

        // Flip the lever: its model changes, so the section's mesh must come back different.
        Pump(500);
        float[] leverMeshBefore = sections[(0, -4, 0)].Layers[0].Positions;
        Use(4, -60, 0, Proto.Face.Up, 4.5, -59.9, 0.5);
        WaitFor(() => !sections[(0, -4, 0)].Layers[0].Positions.SequenceEqual(leverMeshBefore), 10, "section re-meshed after the lever flipped");
        Console.WriteLine("flipped the lever");

        // Minecraft moves the player itself (/tp): we must be told, and poses ignored until we ack.
        int oldId = teleport.Id;
        File.WriteAllText(Path.Combine(dir, "phase-tp"), "");
        WaitFor(() => teleport.Id > oldId, 15, "TELEPORT after /tp");
        Console.WriteLine($"teleport {teleport.Id} to {teleport.X:F2} {teleport.Y:F2} {teleport.Z:F2}");
        Check(Math.Abs(teleport.X - 10.5) < 0.01 && Math.Abs(teleport.Z - 10.5) < 0.01, "teleport target is the /tp destination");
        SendPose(true, oldId, 0.5, -58.3, 0.5); // stale: must be ignored
        Pump(200);
        for (int i = 0; i < 10; i++) { SendPose(true, teleport.Id, 10.5, -58.3, 10.5); Pump(50); }

        Console.WriteLine("statuses: " + string.Join(" | ", statuses));
        File.WriteAllText(Path.Combine(dir, "done"), "");
        Pump(500);
        link.Dispose();
        Console.WriteLine(failures == 0 ? "LINKTEST PASSED" : $"LINKTEST FAILED ({failures})");
        return failures == 0 ? 0 : 1;
    }

    static void SendPose(bool inMap, int ack, double x, double y, double z) =>
        link.Send(new FrameWriter(Proto.GtPose).U8(inMap ? 1 : 0).I32(ack).F64(x).F64(y).F64(z).F32(0).F32(30).Finish());

    static void Use(int x, int y, int z, Proto.Face f, double hx, double hy, double hz) =>
        link.Send(new FrameWriter(Proto.GtUse).I32(x).I32(y).I32(z).U8((int)f).F64(hx).F64(hy).F64(hz).Finish());

    static bool HasShape(int x, int y, int z) =>
        sections.TryGetValue((x >> 4, y >> 4, z >> 4), out var s) && s.Shapes.ContainsKey((y & 15) << 8 | (z & 15) << 4 | (x & 15));

    static void Pump(int ms)
    {
        var until = DateTime.UtcNow.AddMilliseconds(ms);
        do
        {
            while (link != null && link.TryReceive(out object m))
            {
                switch (m)
                {
                    case Messages.World w: world = w; sections.Clear(); break;
                    case Messages.Atlas a: atlas = a.Png; break;
                    case Messages.Section s: sections[(s.X, s.Y, s.Z)] = s; sectionUpdates++; break;
                    case Messages.SectionRemove r: sections.Remove((r.X, r.Y, r.Z)); break;
                    case Messages.Teleport t: teleport = t; break;
                    case Messages.Hotbar h: hotbar = h; break;
                    case Messages.Status st: statuses.Add(st.Text); break;
                }
            }
            Thread.Sleep(5);
        } while (DateTime.UtcNow < until);
    }

    static void WaitFor(Func<bool> cond, int seconds, string what)
    {
        var until = DateTime.UtcNow.AddSeconds(seconds);
        while (!cond())
        {
            if (DateTime.UtcNow > until)
            {
                Console.WriteLine($"LINKTEST FAILED: timed out waiting for {what}");
                Environment.Exit(2);
            }
            Pump(20);
        }
    }

    static void Check(bool ok, string what)
    {
        Console.WriteLine((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) failures++;
    }

    static void CheckAtlas(byte[] png)
    {
        bool sig = png.Length > 24 && png[0] == 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G';
        int w = sig ? png[16] << 24 | png[17] << 16 | png[18] << 8 | png[19] : 0;
        int h = sig ? png[20] << 24 | png[21] << 16 | png[22] << 8 | png[23] : 0;
        Console.WriteLine($"atlas {png.Length / 1024} KiB, {w}x{h}");
        Check(sig && w >= 256 && h >= 256, "atlas is a PNG of plausible size");
    }

    static void CheckSection(Messages.Section s)
    {
        Check(Box(s, 2, -60, 0) is { Length: 1 } b && b[0].MaxX == 1 && b[0].MaxY == 1, "stone has a full-cube shape");
        Check(Box(s, 2, -59, 0) is { Length: 2 }, "stairs have two outline boxes");
        Check(Box(s, 4, -60, 0) is { Length: 1 } l && l[0].MaxY < 0.7f, "lever has a small outline");
        Check(!HasShapeIn(s, 8, -61, 8), "water has no outline");
        Check(s.Layers[2].VertexCount > 0, "water is in the translucent layer");
        Check(s.Layers[1].VertexCount > 0, "leaves are in the cutout layer");
        Check(!HasShapeIn(s, 8, -63, 8), "buried dirt is left out of the shapes");

        bool inRange = true, uvOk = true;
        foreach (var layer in s.Layers)
        {
            for (int i = 0; i < layer.VertexCount * 3; i++)
                inRange &= layer.Positions[i] > -0.6f && layer.Positions[i] < 16.6f;
            for (int i = 0; i < layer.VertexCount * 2; i++)
                uvOk &= layer.Uvs[i] >= 0f && layer.Uvs[i] <= 1f;
        }
        Check(inRange, "render vertices are section-relative");
        Check(uvOk, "UVs are inside the atlas");

        // Grass top at 6 -61 0 (local y 3 → 4): which way does each quad face?
        Check(TopFaceNormalY(s.Layers[0].Positions, s.Layers[0].VertexCount / 4, 6, 4, 0, 3) > 0, "grass top render quad is counter-clockwise from above");
        Check(TopFaceNormalY(s.Collision, s.CollisionQuads, 6, 4, 0, 3) > 0, "grass top collision quad is counter-clockwise from above");
        Check(s.CollisionQuads < 2000, $"buried faces are culled from collision ({s.CollisionQuads} quads)");
    }

    static Messages.Box[] Box(Messages.Section s, int x, int y, int z) =>
        s.Shapes.TryGetValue((y & 15) << 8 | (z & 15) << 4 | (x & 15), out var b) ? b : null;

    static bool HasShapeIn(Messages.Section s, int x, int y, int z) => Box(s, x, y, z) != null;

    /// <summary>Y of the normal (v1-v0)×(v2-v0) of the horizontal quad at height y over block (x, z).</summary>
    static float TopFaceNormalY(float[] p, int quads, int x, int y, int z, int stride)
    {
        for (int q = 0; q < quads; q++)
        {
            int o = q * 4 * stride;
            bool flat = true, over = true;
            for (int v = 0; v < 4; v++)
            {
                float vx = p[o + v * stride], vy = p[o + v * stride + 1], vz = p[o + v * stride + 2];
                flat &= Math.Abs(vy - y) < 1e-4;
                over &= vx >= x - 1e-4 && vx <= x + 1 + 1e-4 && vz >= z - 1e-4 && vz <= z + 1 + 1e-4;
            }
            if (!flat || !over)
                continue;
            float ax = p[o + stride] - p[o], ay = p[o + stride + 1] - p[o + 1], az = p[o + stride + 2] - p[o + 2];
            float bx = p[o + 2 * stride] - p[o], by = p[o + 2 * stride + 1] - p[o + 1], bz = p[o + 2 * stride + 2] - p[o + 2];
            return az * bx - ax * bz;
        }
        Console.WriteLine($"  (no horizontal quad at y={y} over {x},{z})");
        return 0;
    }
}
