package dev.gorillacraft.link;

import dev.gorillacraft.GorillaCraft;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loopback TCP server that Gorilla Tag connects to. TCP rather than shared memory because
 * Gorilla Tag usually runs under Proton/Wine while Minecraft runs natively, and loopback sockets
 * are the one thing both sides see the same way. One Gorilla Tag at a time; a new connection
 * replaces the old one.
 *
 * Threads: an accept thread, plus a reader and a writer per connection. The client thread only
 * touches {@link #poll()} and {@link #send(byte[])}.
 */
public final class LinkServer {
	private final int port;
	private final ConcurrentLinkedQueue<Inbound> inbound = new ConcurrentLinkedQueue<>();
	private volatile Connection current;
	private int connectionCounter;

	public LinkServer(int port) {
		this.port = port;
	}

	public void start() {
		Thread t = new Thread(this::acceptLoop, "GorillaCraft link");
		t.setDaemon(true);
		t.start();
	}

	/** Next frame from Gorilla Tag, or null. */
	public Inbound poll() {
		return inbound.poll();
	}

	public boolean connected() {
		Connection c = current;
		return c != null && c.alive;
	}

	/** Id of the live connection (changes on every reconnect), or 0. */
	public int connectionId() {
		Connection c = current;
		return c != null && c.alive ? c.id : 0;
	}

	/** Bytes queued for Gorilla Tag but not yet written; used to throttle section streaming. */
	public long pendingBytes() {
		Connection c = current;
		return c != null ? c.pendingBytes.get() : 0;
	}

	public void send(byte[] frame) {
		Connection c = current;
		if (c != null && c.alive) {
			c.pendingBytes.addAndGet(frame.length);
			c.out.add(frame);
		}
	}

	private void acceptLoop() {
		while (true) {
			try (ServerSocket server = new ServerSocket()) {
				server.setReuseAddress(true);
				server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
				GorillaCraft.LOG.info("Waiting for Gorilla Tag on 127.0.0.1:{}", port);
				while (true) {
					Socket socket = server.accept();
					socket.setTcpNoDelay(true);
					Connection old = current;
					if (old != null) {
						old.close();
					}
					Connection c = new Connection(socket, ++connectionCounter);
					current = c;
					GorillaCraft.LOG.info("Gorilla Tag connected from {}", socket.getRemoteSocketAddress());
					c.start();
				}
			} catch (IOException e) {
				GorillaCraft.LOG.error("Link server on port {} failed, retrying in 5 s", port, e);
				try {
					Thread.sleep(5000);
				} catch (InterruptedException ignored) {
					return;
				}
			}
		}
	}

	private final class Connection {
		final Socket socket;
		final int id;
		final LinkedBlockingQueue<byte[]> out = new LinkedBlockingQueue<>();
		final AtomicLong pendingBytes = new AtomicLong();
		volatile boolean alive = true;

		Connection(Socket socket, int id) {
			this.socket = socket;
			this.id = id;
		}

		void start() {
			Thread r = new Thread(this::readLoop, "GorillaCraft link reader");
			r.setDaemon(true);
			r.start();
			Thread w = new Thread(this::writeLoop, "GorillaCraft link writer");
			w.setDaemon(true);
			w.start();
		}

		void close() {
			if (alive) {
				alive = false;
				GorillaCraft.LOG.info("Gorilla Tag disconnected");
			}
			try {
				socket.close();
			} catch (IOException ignored) {
			}
		}

		private void readLoop() {
			try (InputStream raw = socket.getInputStream()) {
				DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
				byte[] header = new byte[4];
				while (alive) {
					in.readFully(header);
					int len = (header[0] & 0xFF) | (header[1] & 0xFF) << 8 | (header[2] & 0xFF) << 16 | (header[3] & 0xFF) << 24;
					if (len < 1 || len > Proto.MAX_FRAME) {
						throw new IOException("bad frame length " + len);
					}
					int type = in.readUnsignedByte();
					byte[] payload = new byte[len - 1];
					in.readFully(payload);
					inbound.add(new Inbound(type, payload, id));
				}
			} catch (EOFException e) {
				// Gorilla Tag closed the connection.
			} catch (IOException e) {
				if (alive) {
					GorillaCraft.LOG.warn("Link read failed: {}", e.toString());
				}
			} finally {
				close();
			}
		}

		private void writeLoop() {
			try (OutputStream raw = socket.getOutputStream()) {
				BufferedOutputStream o = new BufferedOutputStream(raw, 1 << 16);
				while (alive) {
					byte[] frame = out.poll(250, TimeUnit.MILLISECONDS);
					if (frame == null) {
						continue;
					}
					o.write(frame);
					pendingBytes.addAndGet(-frame.length);
					if (out.isEmpty()) {
						o.flush();
					}
				}
			} catch (IOException | InterruptedException e) {
				if (alive) {
					GorillaCraft.LOG.warn("Link write failed: {}", e.toString());
				}
			} finally {
				close();
			}
		}
	}
}
