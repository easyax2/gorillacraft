using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;

namespace GorillaCraft
{
    /// <summary>
    /// Sets up and starts the Minecraft half through Prism Launcher: a "GorillaCraft" instance with
    /// Minecraft 26.3, Fabric, Fabric API and the GorillaCraft mod (the jars ship inside this dll),
    /// which opens its world by itself. Nothing here touches Unity.
    /// </summary>
    public static class PrismSetup
    {
        public const string InstanceId = "GorillaCraft";
        public const string MinecraftVersion = "26.3";
        public const string FabricLoaderVersion = "0.19.5";

        /// <summary>True under Wine/Proton, where Prism (a Linux program) can't be started from here.</summary>
        public static bool IsWine()
        {
            try
            {
                IntPtr ntdll = GetModuleHandle("ntdll.dll");
                return ntdll != IntPtr.Zero && GetProcAddress(ntdll, "wine_get_version") != IntPtr.Zero;
            }
            catch (Exception)
            {
                return false;
            }
        }

        [DllImport("kernel32", CharSet = CharSet.Ansi)]
        private static extern IntPtr GetModuleHandle(string name);

        [DllImport("kernel32", CharSet = CharSet.Ansi)]
        private static extern IntPtr GetProcAddress(IntPtr module, string name);

        /// <summary>Prism's data folder (the one holding instances/), or null.</summary>
        public static string FindDataDir(string configured, string executable)
        {
            foreach (string dir in DataDirCandidates(configured, executable))
            {
                if (!string.IsNullOrEmpty(dir) && (Directory.Exists(Path.Combine(dir, "instances")) || File.Exists(Path.Combine(dir, "prismlauncher.cfg"))))
                    return dir;
            }
            return null;
        }

        private static IEnumerable<string> DataDirCandidates(string configured, string executable)
        {
            yield return configured;
            if (!string.IsNullOrEmpty(executable) && File.Exists(Path.Combine(Path.GetDirectoryName(executable) ?? "", "portable.txt")))
                yield return Path.GetDirectoryName(executable);
            if (IsWine())
            {
                // Linux home folders, seen through Wine's Z: drive.
                var homes = new List<string>();
                string home = Environment.GetEnvironmentVariable("HOME");
                if (!string.IsNullOrEmpty(home) && home.StartsWith("/"))
                    homes.Add(home);
                if (Directory.Exists(@"Z:\home"))
                    homes.AddRange(Directory.GetDirectories(@"Z:\home").Select(d => "/home/" + Path.GetFileName(d)));
                string xdg = Environment.GetEnvironmentVariable("XDG_DATA_HOME");
                if (!string.IsNullOrEmpty(xdg) && xdg.StartsWith("/"))
                    yield return UnixToWine(xdg + "/PrismLauncher");
                foreach (string h in homes.Distinct())
                {
                    yield return UnixToWine(h + "/.local/share/PrismLauncher");
                    yield return UnixToWine(h + "/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher");
                }
            }
            else
            {
                yield return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "PrismLauncher");
                string scoop = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "scoop", "persist", "prismlauncher");
                yield return scoop;
            }
        }

        public static string UnixToWine(string unixPath) => "Z:" + unixPath.Replace('/', '\\');

        /// <summary>prismlauncher.exe on Windows, or null.</summary>
        public static string FindExecutable(string configured)
        {
            if (!string.IsNullOrEmpty(configured))
                return File.Exists(configured) ? configured : null;
            string local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string user = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            var candidates = new[]
            {
                Path.Combine(local, "Programs", "PrismLauncher", "prismlauncher.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "PrismLauncher", "prismlauncher.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "PrismLauncher", "prismlauncher.exe"),
                Path.Combine(user, "scoop", "apps", "prismlauncher", "current", "prismlauncher.exe"),
            };
            return candidates.FirstOrDefault(File.Exists);
        }

        /// <summary>
        /// Creates the GorillaCraft instance if it's missing, and puts this release's mod jar (and
        /// Fabric API, if the instance has none) into it. Leaves everything else the player changed.
        /// </summary>
        /// <returns>What it did, for the log.</returns>
        public static string Install(string dataDir, string worldName)
        {
            var log = new StringBuilder();
            string instance = Path.Combine(dataDir, "instances", InstanceId);
            Directory.CreateDirectory(instance);

            string cfg = Path.Combine(instance, "instance.cfg");
            if (!File.Exists(cfg))
            {
                File.WriteAllText(cfg, string.Join("\n", new[]
                {
                    "[General]",
                    "ConfigVersion=1.3",
                    "InstanceType=OneSix",
                    "iconKey=default",
                    "name=GorillaCraft",
                    "OverrideJavaArgs=true",
                    "JvmArgs=-Dgorillacraft.autoWorld=" + worldName,
                    "OverrideMemory=true",
                    "MinMemAlloc=512",
                    "MaxMemAlloc=3072",
                    "",
                }));
                log.Append("created instance; ");
            }

            string pack = Path.Combine(instance, "mmc-pack.json");
            if (!File.Exists(pack))
            {
                File.WriteAllText(pack,
                    "{\n    \"components\": [\n" +
                    "        { \"important\": true, \"uid\": \"net.minecraft\", \"version\": \"" + MinecraftVersion + "\" },\n" +
                    "        { \"dependencyOnly\": true, \"uid\": \"net.fabricmc.intermediary\", \"version\": \"" + MinecraftVersion + "\" },\n" +
                    "        { \"uid\": \"net.fabricmc.fabric-loader\", \"version\": \"" + FabricLoaderVersion + "\" }\n" +
                    "    ],\n    \"formatVersion\": 1\n}\n");
            }

            // Prism uses .minecraft for older instances and minecraft for new ones.
            string game = Directory.Exists(Path.Combine(instance, ".minecraft")) ? Path.Combine(instance, ".minecraft") : Path.Combine(instance, "minecraft");
            string mods = Path.Combine(game, "mods");
            Directory.CreateDirectory(mods);

            string options = Path.Combine(game, "options.txt");
            if (!File.Exists(options))
            {
                // Minecraft runs in the background: keep it light and quiet, and skip the
                // first-start accessibility screen so the world can open by itself.
                File.WriteAllText(options, "onboardAccessibility:false\nmaxFps:30\nrenderDistance:6\nsimulationDistance:6\n" +
                    "pauseOnLostFocus:false\nsoundCategory_music:0.0\nskipMultiplayerWarning:true\n");
            }

            Assembly asm = typeof(PrismSetup).Assembly;
            foreach (string res in asm.GetManifestResourceNames().Where(n => n.StartsWith("jar/")))
            {
                string name = res.Substring(4);
                bool isMod = name.StartsWith("gorillacraft-fabric-");
                string prefix = isMod ? "gorillacraft-fabric-" : "fabric-api-";
                string[] existing = Directory.GetFiles(mods, prefix + "*.jar");
                if (!isMod && existing.Length > 0)
                    continue; // the player's own Fabric API is fine (maybe newer)
                string target = Path.Combine(mods, name);
                byte[] bytes;
                using (Stream s = asm.GetManifestResourceStream(res))
                using (var ms = new MemoryStream())
                {
                    s.CopyTo(ms);
                    bytes = ms.ToArray();
                }
                if (File.Exists(target) && new FileInfo(target).Length == bytes.Length && File.ReadAllBytes(target).SequenceEqual(bytes))
                    continue;
                foreach (string old in existing)
                {
                    if (!string.Equals(Path.GetFileName(old), name, StringComparison.OrdinalIgnoreCase))
                        File.Delete(old);
                }
                File.WriteAllBytes(target, bytes);
                log.Append("installed ").Append(name).Append("; ");
            }
            return log.Length > 0 ? log.ToString().TrimEnd(' ', ';') : "up to date";
        }

        /// <summary>Starts the GorillaCraft instance (Windows). Prism forwards to an already-running Prism.</summary>
        public static void Launch(string executable, string configuredDataDir)
        {
            string args = "--launch " + InstanceId;
            if (!string.IsNullOrEmpty(configuredDataDir))
                args = "--dir \"" + configuredDataDir + "\" " + args;
            Process.Start(new ProcessStartInfo(executable, args)
            {
                UseShellExecute = false,
                WorkingDirectory = Path.GetDirectoryName(executable) ?? "",
            });
        }
    }
}
