package dev.gorillacraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.gorillacraft.GorillaCraft;
import dev.gorillacraft.link.FrameWriter;
import dev.gorillacraft.link.LinkServer;
import dev.gorillacraft.link.Proto;
import net.minecraft.client.Minecraft;

/**
 * While a Minecraft screen is open (inventory, chest, crafting table, furnace...), the whole
 * finished frame, GUI included, goes to Gorilla Tag, which shows it on a panel in front of the
 * gorilla. A frame with no size tells Gorilla Tag to hide the panel.
 */
final class GuiCapture {
	private static final int MAX_WIDTH = 1600;

	private final LinkServer link;
	private GpuBuffer buffer;
	private int width, height;
	private boolean busy;
	private boolean showing;

	GuiCapture(LinkServer link) {
		this.link = link;
	}

	/** Called after every finished frame. */
	void capture(Minecraft mc, boolean screenOpen) {
		if (!screenOpen) {
			if (showing) {
				showing = false;
				link.send(new FrameWriter(Proto.MC_GUI_FRAME).i32(0).i32(0).finish());
			}
			return;
		}
		if (busy || link.pendingBytes() > 24L << 20) {
			return;
		}
		GpuTexture color = mc.gameRenderer.mainRenderTarget().getColorTexture();
		if (color == null) {
			return;
		}
		int w = color.getWidth(0), h = color.getHeight(0);
		GpuDevice device = RenderSystem.getDevice();
		if (buffer == null || w != width || h != height) {
			if (buffer != null) {
				buffer.close();
			}
			buffer = device.createBuffer(() -> "GorillaCraft GUI frame", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, (long) w * h * 4);
			width = w;
			height = h;
		}
		busy = true;
		try {
			device.createCommandEncoder().copyTextureToBuffer(color, buffer, 0, () -> mc.execute(this::finish), 0);
		} catch (RuntimeException e) {
			GorillaCraft.LOG.error("Can't read Minecraft's screen back", e);
			busy = false;
		}
	}

	private void finish() {
		try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
			int step = Math.max(1, (width + MAX_WIDTH - 1) / MAX_WIDTH);
			int iw = width / step, ih = height / step;
			FrameWriter out = new FrameWriter(Proto.MC_GUI_FRAME, iw * ih * 4 + 16);
			out.i32(iw).i32(ih);
			FrameCapture.writeRows(out, view.data(), width, height, step);
			link.send(out.finish());
			showing = true;
		} catch (RuntimeException e) {
			GorillaCraft.LOG.error("Reading Minecraft's screen back failed", e);
		} finally {
			busy = false;
		}
	}
}
