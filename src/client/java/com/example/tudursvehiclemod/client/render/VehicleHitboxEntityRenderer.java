package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.entity.VehicleHitboxEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;

/** Minimal no-op renderer for VehicleHitboxEntity - Fabric/Minecraft requires
 * every registered entity type to have SOME renderer, but this entity is
 * always invisible (see that class's own doc: it's purely a hit-detection
 * helper, one small piece of a vehicle's own multi-box attack hitbox), so
 * this one never actually draws anything at all. */
public class VehicleHitboxEntityRenderer extends EntityRenderer<VehicleHitboxEntity, EntityRenderState> {

	public VehicleHitboxEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void render(EntityRenderState state, net.minecraft.client.util.math.MatrixStack matrices,
			net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
			net.minecraft.client.render.state.CameraRenderState cameraState) {
		// Deliberately empty - this entity is always invisible.
	}
}
