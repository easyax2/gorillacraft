using System;
using System.IO;
using System.Text;

namespace GorillaCraft
{
    /// <summary>Builds one outgoing frame.</summary>
    public sealed class FrameWriter
    {
        private readonly MemoryStream ms = new MemoryStream(64);
        private readonly BinaryWriter w;

        public FrameWriter(byte type)
        {
            w = new BinaryWriter(ms, Encoding.UTF8); // BinaryWriter is little-endian
            w.Write(0);
            w.Write(type);
        }

        public FrameWriter U8(int v) { w.Write((byte)v); return this; }
        public FrameWriter I32(int v) { w.Write(v); return this; }
        public FrameWriter F32(float v) { w.Write(v); return this; }
        public FrameWriter F64(double v) { w.Write(v); return this; }
        public FrameWriter Bytes(byte[] b) { w.Write(b); return this; }

        public FrameWriter Str(string s)
        {
            byte[] b = Encoding.UTF8.GetBytes(s ?? "");
            int len = Math.Min(b.Length, 0xFFFF);
            w.Write((ushort)len);
            w.Write(b, 0, len);
            return this;
        }

        public byte[] Finish()
        {
            w.Flush();
            byte[] data = ms.ToArray();
            BitConverter.GetBytes(data.Length - 4).CopyTo(data, 0);
            return data;
        }
    }

    /// <summary>Reads one incoming frame's payload.</summary>
    public sealed class FrameReader
    {
        private readonly byte[] b;
        private int p;

        public FrameReader(byte[] payload) { b = payload; }

        public int Remaining => b.Length - p;

        public byte U8() => b[p++];
        public ushort U16() { ushort v = BitConverter.ToUInt16(b, p); p += 2; return v; }
        public int I32() { int v = BitConverter.ToInt32(b, p); p += 4; return v; }
        public float F32() { float v = BitConverter.ToSingle(b, p); p += 4; return v; }
        public double F64() { double v = BitConverter.ToDouble(b, p); p += 8; return v; }

        public string Str()
        {
            int len = U16();
            string s = Encoding.UTF8.GetString(b, p, len);
            p += len;
            return s;
        }

        public byte[] Bytes(int n)
        {
            var r = new byte[n];
            Buffer.BlockCopy(b, p, r, 0, n);
            p += n;
            return r;
        }

        public float[] Floats(int n)
        {
            var r = new float[n];
            Buffer.BlockCopy(b, p, r, 0, n * 4);
            p += n * 4;
            return r;
        }
    }
}
