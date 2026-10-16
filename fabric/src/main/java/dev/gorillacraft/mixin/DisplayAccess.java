package dev.gorillacraft.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Display entities' setters are private; gorilla avatars are built from display entities. */
@Mixin(Display.class)
public interface DisplayAccess {
	@Invoker("setTransformation")
	void gorillacraft$setTransformation(Transformation transformation);

	@Invoker("setTransformationInterpolationDuration")
	void gorillacraft$setTransformationInterpolationDuration(int ticks);

	@Invoker("setTransformationInterpolationDelay")
	void gorillacraft$setTransformationInterpolationDelay(int ticks);

	@Invoker("setPosRotInterpolationDuration")
	void gorillacraft$setPosRotInterpolationDuration(int ticks);

	@Invoker("setViewRange")
	void gorillacraft$setViewRange(float range);

	@Invoker("setWidth")
	void gorillacraft$setWidth(float width);

	@Invoker("setHeight")
	void gorillacraft$setHeight(float height);
}
