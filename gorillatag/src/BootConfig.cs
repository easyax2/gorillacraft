using System.IO;
using System.Linq;
using UnityEngine;

namespace GorillaCraft
{
    /// <summary>
    /// Gorilla Tag's boot.config has "xrsdk-pre-init-library=XRSDKOpenVR": Unity starts OpenVR
    /// natively at launch, before any plugin runs. With real SteamVR (Windows) that makes Gorilla
    /// Tag SteamVR's VR app, and SteamVR closes Minecraft (or Gorilla Tag) as soon as the other
    /// one takes the headset. Without the line VR still works (it's only an early start), so
    /// GorillaCraft takes it out while Gorilla Tag is your gorilla, and puts it back (from the
    /// backup) when PlayInMinecraft is turned off. Takes effect from the next start.
    /// </summary>
    internal static class BootConfig
    {
        private const string Key = "xrsdk-pre-init-library=";
        private const string BackupName = "boot.config.gorillacraft";

        public static void Apply(bool gorillaCraft)
        {
            try
            {
                string path = Path.Combine(Application.dataPath, "boot.config");
                string backup = Path.Combine(Application.dataPath, BackupName);
                if (!File.Exists(path))
                    return;
                var lines = File.ReadAllLines(path).ToList();
                int i = lines.FindIndex(l => l.StartsWith(Key));
                if (gorillaCraft && i >= 0)
                {
                    File.WriteAllText(backup, lines[i]);
                    lines.RemoveAt(i);
                    File.WriteAllLines(path, lines);
                    Plugin.Log.LogInfo("Took OpenVR's early start out of boot.config (from the next start, Gorilla Tag doesn't touch the headset at all)");
                }
                else if (!gorillaCraft && i < 0 && File.Exists(backup))
                {
                    lines.Add(File.ReadAllText(backup).Trim());
                    File.WriteAllLines(path, lines);
                    File.Delete(backup);
                    Plugin.Log.LogInfo("Put OpenVR's early start back in boot.config (normal Gorilla Tag VR)");
                }
            }
            catch (System.Exception e)
            {
                Plugin.Log.LogWarning("Couldn't update boot.config: " + e.Message);
            }
        }
    }
}
