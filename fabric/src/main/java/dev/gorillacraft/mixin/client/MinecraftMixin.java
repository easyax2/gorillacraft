package dev.gorillacraft.mixin.client;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.gorillacraft.client.vr.VrRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The VR frame: begins with the runtime's frame timing, renders both eyes and the GUI, mirrors an eye on the monitor. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void gorillacraft$vrBegin(boolean advanceGameTime, CallbackInfo ci) {
		VrRenderer vr = VrRenderer.get();
		if (vr != null) {
			vr.beginFrame((Minecraft) (Object) this);
		}
	}

	/** Quitting: end the VR session while Minecraft's OpenGL is still there. */
	@Inject(method = "close", at = @At("HEAD"))
	private void gorillacraft$vrClose(CallbackInfo ci) {
		VrRenderer vr = VrRenderer.get();
		if (vr != null) {
			vr.xr().close();
		}
	}

	@Redirect(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render()V"))
	private void gorillacraft$vrRender(GameRenderer gameRenderer) {
		VrRenderer vr = VrRenderer.get();
		if (vr != null && vr.inFrame()) {
			vr.render((Minecraft) (Object) this, gameRenderer);
		} else {
			gameRenderer.render();
		}
	}

	@ModifyArg(method = "renderFrame", at = @At(value = "INVOKE",
			target = "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"),
			index = 1)
	private GpuTextureView gorillacraft$mirror(GpuTextureView texture) {
		VrRenderer vr = VrRenderer.get();
		return vr != null ? vr.mirror(texture) : texture;
	}
}
