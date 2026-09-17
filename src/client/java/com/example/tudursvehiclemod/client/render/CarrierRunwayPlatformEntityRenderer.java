package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;

/** CarrierRunwayPlatformEntity's own EntityType (see ModEntityTypes's own doc) was registered server-side but never had a corresponding client-side renderer registered - every entity type needs one, even one that's always invisible and genuinely has nothing to draw, or the client crashes the instant it tries to render one at all. This renderer does nothing at all (render() is a no-op) - matches this entity's own always-invisible nature (setInvisible(true) is set every time one is created, from AbstractVehicleEntity's own tudursvehiclemod$updateCarrierRunwayPlatform()) exactly; there's genuinely no visual to draw, ever. Method signature matches this project's own VehicleEntityRenderer exactly (MatrixStack/OrderedRenderCommandQueue/CameraRenderState) - an earlier draft of this file was mistakenly written against an older Matrix4f/VertexConsumerProvider/int-light EntityRenderer API that doesn't match what this Minecraft version actually uses. */
public final class CarrierRunwayPlatformEntityRenderer extends EntityRenderer<CarrierRunwayPlatformEntity, EntityRenderState> {

	public CarrierRunwayPlatformEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void render(EntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
		// Per this class's own doc: intentionally empty - this entity is always invisible, with nothing to ever actually draw.
	}
}
