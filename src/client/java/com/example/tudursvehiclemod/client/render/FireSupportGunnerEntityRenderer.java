package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.entity.FireSupportGunnerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;

/** Draws nothing: a Fire Support gunner is never seen (see FireSupportGunnerEntity's own doc). Every entity type needs a renderer registered all the same, as CarrierRunwayPlatformEntityRenderer's doc explains. */
public final class FireSupportGunnerEntityRenderer extends EntityRenderer<FireSupportGunnerEntity, EntityRenderState> {

	public FireSupportGunnerEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void render(EntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
	}
}
