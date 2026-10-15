package dev.gorillacraft.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.Projection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The projection the camera renders with, for turning depth back into positions. */
@Mixin(Camera.class)
public interface CameraAccess {
	@Accessor("projection")
	Projection gorillacraft$projection();
}
