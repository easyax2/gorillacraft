using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net.Sockets;
using System.Threading;

namespace GorillaCraft
{
    /// <summary>
    /// TCP connection to the Minecraft mod on 127.0.0.1. Keeps reconnecting in the background, so
    /// Minecraft and Gorilla Tag can be started in any order. Incoming frames are parsed on the
    /// reader thread (section meshes can be large) and handed to the main thread as messages.
    /// </summary>
    public sealed class LinkClient : IDisposable
    {
        private readonly string host;
        private readonly int port;
        private readonly Action<string> log;
        private readonly ConcurrentQueue<object> inbound = new ConcurrentQueue<object>();
        private readonly BlockingCollection<byte[]> outbound = new BlockingCollection<byte[]>(new ConcurrentQueue<byte[]>());
        private volatile bool running = true;
        private volatile TcpClient client;
        private int generation;

        public LinkClient(string host, int port, Action<string> log)
        {
            this.host = host;
            this.port = port;
            this.log = log;
        }

        public bool Connected => client != null;

        /// <summary>Changes every time a new connection is made.</summary>
        public int Generation => Volatile.Read(ref generation);

        public void Start()
        {
            new Thread(ConnectLoop) { IsBackground = true, Name = "GorillaCraft link" }.Start();
        }

        public bool TryReceive(out object message) => inbound.TryDequeue(out message);

        public void Send(byte[] frame)
        {
            if (client != null)
                outbound.Add(frame);
        }

        private void ConnectLoop()
        {
            bool loggedWaiting = false;
            while (running)
            {
                TcpClient c = null;
                try
                {
                    c = new TcpClient { NoDelay = true, ReceiveBufferSize = 1 << 20 };
                    c.Connect(host, port);
                }
                catch (Exception)
                {
                    c?.Close();
                    if (!loggedWaiting)
                    {
                        log($"Waiting for Minecraft on {host}:{port}");
                        loggedWaiting = true;
                    }
                    Thread.Sleep(2000);
                    continue;
                }

                loggedWaiting = false;
                log($"Connected to Minecraft on {host}:{port}");
                while (outbound.TryTake(out _)) { }
                client = c;
                Interlocked.Increment(ref generation);
                inbound.Enqueue(new ConnectedEvent());
                Send(new FrameWriter(Proto.GtHello).I32(Proto.Version).Str(Plugin.Version).Finish());

                var writer = new Thread(() => WriteLoop(c)) { IsBackground = true, Name = "GorillaCraft link writer" };
                writer.Start();
                try
                {
                    ReadLoop(c.GetStream());
                }
                catch (Exception e) when (e is IOException || e is SocketException || e is ObjectDisposedException)
                {
                }
                catch (Exception e)
                {
                    log("Link error: " + e);
                }
                client = null;
                c.Close();
                writer.Join(1000);
                inbound.Enqueue(new DisconnectedEvent());
                log("Disconnected from Minecraft");
                Thread.Sleep(1000);
            }
        }

        private void ReadLoop(NetworkStream s)
        {
            var header = new byte[4];
            while (running)
            {
                ReadExactly(s, header, 4);
                int len = BitConverter.ToInt32(header, 0);
                if (len < 1 || len > Proto.MaxFrame)
                    throw new IOException("bad frame length " + len);
                var frame = new byte[len];
                ReadExactly(s, frame, len);
                byte type = frame[0];
                var payload = new byte[len - 1];
                Buffer.BlockCopy(frame, 1, payload, 0, len - 1);
                object msg = Messages.Parse(type, new FrameReader(payload));
                if (msg != null)
                    inbound.Enqueue(msg);
            }
        }

        private static void ReadExactly(Stream s, byte[] buf, int n)
        {
            int got = 0;
            while (got < n)
            {
                int r = s.Read(buf, got, n - got);
                if (r <= 0)
                    throw new IOException("connection closed");
                got += r;
            }
        }

        private void WriteLoop(TcpClient c)
        {
            try
            {
                var s = c.GetStream();
                while (running && client == c)
                {
                    if (!outbound.TryTake(out byte[] frame, 250))
                        continue;
                    s.Write(frame, 0, frame.Length);
                }
            }
            catch (Exception)
            {
                c.Close(); // makes the reader fail too
            }
        }

        public void Dispose()
        {
            running = false;
            client?.Close();
        }

        public sealed class ConnectedEvent { }

        public sealed class DisconnectedEvent { }
    }
}
