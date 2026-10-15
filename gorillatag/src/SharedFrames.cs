using System;
using System.IO;
using System.IO.MemoryMappedFiles;
using System.Threading;

namespace GorillaCraft
{
    /// <summary>
    /// Reading side of the shared-memory frame triple buffers (fabric/.../link/SharedFrames.java
    /// has the layout), the way Skyrim reads SkyCraft's overlay: no copies, no socket. The newest
    /// finished picture is swapped in with one atomic exchange and handed to Unity straight from
    /// the shared memory.
    /// </summary>
    public sealed unsafe class SharedFrames : IDisposable
    {
        public const int Magic = 0x42464347;
        public const int Version = 1;
        public const int Gui = 3;
        private const int Dirty = 4;

        private readonly MemoryMappedFile file;
        private readonly MemoryMappedViewAccessor view;
        private readonly byte* basePtr;
        private readonly long slotBytes, dataOffset, pixelBytes;
        private readonly int buffers;
        private readonly int[] front;

        /// <summary>One picture as it sits in shared memory. Valid until the next TryTake on the same buffer.</summary>
        public struct Slot
        {
            public int Width, Height, GridW, GridH, Flags, Eye;
            public long FrameId;
            public double CamX, CamY, CamZ;
            public IntPtr Pixels;
            public IntPtr Points;
            public bool BottomUp => (Flags & 1) != 0;
        }

        private SharedFrames(MemoryMappedFile file, MemoryMappedViewAccessor view, byte* basePtr)
        {
            this.file = file;
            this.view = view;
            this.basePtr = basePtr;
            buffers = *(int*)(basePtr + 8);
            slotBytes = *(long*)(basePtr + 16);
            dataOffset = *(long*)(basePtr + 24);
            pixelBytes = 2560L * 1600 * 4;
            front = new int[buffers];
            for (int b = 0; b < buffers; b++)
                front[b] = 2; // writer starts with slot 0, the middle is slot 1
        }

        /// <summary>
        /// Maps Minecraft's frame file. Minecraft gives its own path; when it's a Linux path and
        /// Gorilla Tag runs under Wine/Proton, it's reached through the Z: drive.
        /// </summary>
        public static SharedFrames Open(string minecraftPath, long size, Action<string> log)
        {
            string path = minecraftPath;
            bool windows = System.Runtime.InteropServices.RuntimeInformation.IsOSPlatform(System.Runtime.InteropServices.OSPlatform.Windows);
            if (path.StartsWith("/") && windows)
            {
                if (!PrismSetup.IsWine())
                {
                    log("Minecraft's frames are in " + path + ", which Windows can't reach");
                    return null;
                }
                path = PrismSetup.UnixToWine(path);
            }
            MemoryMappedFile file = null;
            MemoryMappedViewAccessor view = null;
            try
            {
                var stream = new FileStream(path, FileMode.Open, FileAccess.ReadWrite, FileShare.ReadWrite);
                file = MemoryMappedFile.CreateFromFile(stream, null, size, MemoryMappedFileAccess.ReadWrite, HandleInheritability.None, false);
                view = file.CreateViewAccessor(0, size, MemoryMappedFileAccess.ReadWrite);
                byte* ptr = null;
                view.SafeMemoryMappedViewHandle.AcquirePointer(ref ptr);
                ptr += view.PointerOffset;
                if (*(int*)ptr != Magic || *(int*)(ptr + 4) != Version)
                {
                    log("Shared frames have the wrong format (update both halves)");
                    view.SafeMemoryMappedViewHandle.ReleasePointer();
                    view.Dispose();
                    file.Dispose();
                    return null;
                }
                return new SharedFrames(file, view, ptr);
            }
            catch (Exception e)
            {
                log("Can't map Minecraft's shared frames (" + path + "): " + e.Message);
                view?.Dispose();
                file?.Dispose();
                return null;
            }
        }

        private int* State(int b) => (int*)(basePtr + 0x100 + b * 0x100);

        /// <summary>If buffer b has a new picture, swaps it in and describes it.</summary>
        public bool TryTake(int b, out Slot slot)
        {
            slot = default;
            if (b >= buffers || (Volatile.Read(ref *State(b)) & Dirty) == 0)
                return false;
            int old = Interlocked.Exchange(ref *State(b), front[b]);
            int s = front[b] = old & 3;
            byte* hdr = basePtr + 0x100 + b * 0x100 + 0x40 + s * 0x40;
            slot.Width = *(int*)hdr;
            slot.Height = *(int*)(hdr + 4);
            slot.GridW = *(int*)(hdr + 8);
            slot.GridH = *(int*)(hdr + 12);
            slot.Flags = *(int*)(hdr + 16);
            slot.Eye = *(int*)(hdr + 20);
            slot.FrameId = *(long*)(hdr + 24);
            slot.CamX = *(double*)(hdr + 32);
            slot.CamY = *(double*)(hdr + 40);
            slot.CamZ = *(double*)(hdr + 48);
            byte* data = basePtr + dataOffset + (b * 3 + s) * slotBytes;
            slot.Pixels = (IntPtr)data;
            slot.Points = (IntPtr)(data + pixelBytes);
            return true;
        }

        public void Dispose()
        {
            view.SafeMemoryMappedViewHandle.ReleasePointer();
            view.Dispose();
            file.Dispose();
        }
    }
}
