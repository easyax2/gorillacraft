package dev.gorillacraft.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.gorillacraft.client.vr.VrGameRenderer;
import dev.gorillacraft.client.vr.VrRenderer;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.gui.render.GuiRenderer;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * VR rendering hooks: the eye passes draw into eye-sized targets with no GUI, no first-person
 * hand and no view bobbing; the GUI is drawn on its own for the panel.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin implements VrGameRenderer {
	@Shadow @Final @Mutable private RenderTarget mainRenderTarget;
	@Shadow @Final @Mutable private RenderTarget hud3DTarget;
	@Shadow private boolean useUiLightmap;
	@Shadow @Final private GuiRenderer guiRenderer;
	@Shadow @Final private GameRenderState gameRenderState;
	@Shadow @Final private GlobalSettingsUniform globalSettingsUniform;
	@Shadow @Final private FogRenderer fogRenderer;
	@Shadow @Final private RenderBuffers renderBuffers;
	@Shadow @Final private CrossFrameResourcePool resourcePool;

	@Shadow public abstract Lighting lighting();

	@Shadow public abstract void resize(int width, int height);

	@Override
	public void gorillacraft$setTargets(RenderTarget main, RenderTarget hud3d) {
		mainRenderTarget = main;
		hud3DTarget = hud3d;
	}

	@Override
	public RenderTarget gorillacraft$hud3DTarget() {
		return hud3DTarget;
	}

	@Override
	public void gorillacraft$renderGuiOnly(boolean endFrame) {
		var window = gameRenderState.windowRenderState;
		if (window.width != mainRenderTarget.width || window.height != mainRenderTarget.height) {
			resize(window.width, window.height);
		}
		RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(mainRenderTarget.getColorTexture(),
				gameRenderState.guiRenderState.clearColorOverride, mainRenderTarget.getDepthTexture(), 0.0);
		var level = gameRenderState.levelRenderState;
		var options = gameRenderState.optionsRenderState;
		globalSettingsUniform.update(window.width, window.height, options.glintStrength,
				gameRenderState.shouldRenderLevel ? level.gameTime : 0L, gameRenderState.shouldRenderLevel ? level.worldPartialTicks : 0f,
				options.menuBackgroundBlurriness, level.cameraRenderState.pos, options.textureFiltering == TextureFilteringMethod.RGSS);
		if (endFrame) {
			fogRenderer.endFrame();
		}
		lighting().setupFor(Lighting.Entry.ITEMS_3D);
		useUiLightmap = true;
		guiRenderer.render();
		guiRenderer.endFrame();
		useUiLightmap = false;
		if (endFrame) {
			renderBuffers.endFrame();
			resourcePool.endFrame();
		}
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
	private void gorillacraft$guiNotInEyes(GuiRenderer gui) {
		VrRenderer vr = VrRenderer.get();
		if (vr == null || !vr.eyePass()) {
			gui.render();
		}
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V"))
	private void gorillacraft$guiEndNotInEyes(GuiRenderer gui) {
		VrRenderer vr = VrRenderer.get();
		if (vr == null || !vr.eyePass()) {
			gui.endFrame();
		}
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void gorillacraft$noHand(CallbackInfo ci) {
		VrRenderer vr = VrRenderer.get();
		if (vr != null && vr.active()) {
			ci.cancel(); // your hands are the gorilla's
		}
	}

	@Inject(method = {"bobView", "bobHurt"}, at = @At("HEAD"), cancellable = true)
	private void gorillacraft$noBobbing(CallbackInfo ci) {
		VrRenderer vr = VrRenderer.get();
		if (vr != null && vr.active()) {
			ci.cancel(); // moving the picture you didn't move yourself makes people sick
		}
	}
}
