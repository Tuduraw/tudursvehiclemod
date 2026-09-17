package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** Only used for VehicleModelProjectileEntity (weapons with a bullet_model/ bullet_texture configured. */
public class VehicleProjectileRenderer extends EntityRenderer<VehicleModelProjectileEntity, VehicleProjectileRenderState> {

	/** Logged at most once per distinct missing model, rather than every
	 * single render call, so a genuinely broken bullet_model reference is
	 * still noticeable without spamming the log every frame. */
	private static final java.util.Set<net.minecraft.util.Identifier> loggedMissingModels =
			java.util.concurrent.ConcurrentHashMap.newKeySet();

	public VehicleProjectileRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(VehicleModelProjectileEntity entity, net.minecraft.client.render.Frustum frustum,
			double x, double y, double z) {
		Boolean override = RenderDistanceHelper.tudursvehiclemod$overrideShouldRender(
				entity.getX(), entity.getY(), entity.getZ(), x, y, z);
		if (override != null) {
			return override;
		}
		return super.shouldRender(entity, frustum, x, y, z);
	}

	@Override
	public VehicleProjectileRenderState createRenderState() {
		return new VehicleProjectileRenderState();
	}

	@Override
	public void updateRenderState(VehicleModelProjectileEntity entity, VehicleProjectileRenderState state, float tickProgress) {
		super.updateRenderState(entity, state, tickProgress);
		state.model = entity.getBulletModel();
		state.texture = entity.getBulletTexture();
		state.scale = entity.getBulletScale();
		state.yaw = entity.getYaw(tickProgress);
		state.pitch = entity.getPitch(tickProgress);
		state.light = this.getLight(entity, tickProgress);
		// Per Readme_Weapon.txt's own BulletColor/BulletColorInWater doc.
		state.tintColor = entity.tudursvehiclemod$getBulletColor();
	}

	@Override
	public void render(VehicleProjectileRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
		if (state.model != null && state.texture != null) {
			matrices.push();
			// Same convention as VehicleEntityRenderer.
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-state.yaw));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.pitch));
			matrices.scale(state.scale, state.scale, state.scale);

			var modelOpt = ObjModelLoader.get(state.model);
			if (modelOpt.isEmpty() && loggedMissingModels.add(state.model)) {
				org.slf4j.LoggerFactory.getLogger("VehicleMod/ProjectileRender").warn(
						"Bullet model {} not found by ObjModelLoader at render time (scale={})",
						state.model, state.scale);
			}
			modelOpt.ifPresent(model -> {
				RenderLayer layer = com.example.tudursvehiclemod.client.render.DitherCutoutLayers.entityDitherCutout(state.texture);
				VehicleEntityRenderer.renderTriangles(queue, matrices, layer,
						model.getTrianglesExcluding(java.util.Set.of()), state.light, state.tintColor);
			});
			matrices.pop();
		}
		super.render(state, matrices, queue, cameraState);
	}
}
