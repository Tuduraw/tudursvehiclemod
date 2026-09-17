package com.example.tudursvehiclemod.client.render;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;

/** A completely standalone entity model, built entirely from its own ModelPartBuilder calls below - it shares no geometry, no ModelData, and no construction code with PlayerEntityModel or vanilla's own player rendering whatsoever, so nothing this class does can ever affect an actual player or interact with any other mod's own player-model changes.
 *
 * Proportions match Steve's own well-established body part dimensions exactly (so an ordinary player skin still maps onto it correctly), but the legs' own rest pose is built seated from the start (see SEATED_LEG_PITCH's own doc) - there is no "standing" variant of this model at all, matching a dummy pilot's own always-seated nature (see DummyPilotEntity's own tick(), which discards itself the instant it isn't riding a vehicle).
 *
 * Base body parts (head, body, arms, legs) each carry their own 1.8+ overlay layer (hat/jacket/sleeves/pants) as a direct child, supporting Minecraft 1.8+ player-skin layers - see getTexturedModelData()'s own doc for the exact UV/dilation values. Still no cape or ears at all - a dummy pilot is a fixed-shape passenger, not a full player-feature surface. */
public class DummyPilotModel extends EntityModel<LivingEntityRenderState> {

	/** Forward bend (radians) for each leg's own hip pivot, baked into its rest pose from construction - roughly 90 degrees, a normal seated angle. */
	private static final float SEATED_LEG_PITCH = (float) (-Math.PI / 2.0);

	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;

	public DummyPilotModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		// Legs are never separately posed at runtime (see this class's own doc) - no field needed for them beyond their own baked-in construction below.
	}

	/** Builds this model's own geometry from scratch - see this class's own doc for why nothing here is derived from or shared with PlayerEntityModel. Dimensions and pivots match vanilla's own long-standing Steve body proportions exactly, so an ordinary 64x64 player skin still maps onto every part correctly; only the leg pivots' own rotation differs from a standing biped. Each base part also gets its own 1.8+ overlay layer (hat/jacket/sleeves/pants) as a direct child - see this class's own doc for why nesting (rather than a separate root sibling) is what actually makes these render at all with no further code needed. */
	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();
		ModelPartData headData = root.addChild("head", ModelPartBuilder.create().uv(0, 0)
				.cuboid(-4.0f, -8.0f, -4.0f, 8, 8, 8, Dilation.NONE), ModelTransform.origin(0.0f, 0.0f, 0.0f));
		headData.addChild("hat", ModelPartBuilder.create().uv(32, 0)
				.cuboid(-4.0f, -8.0f, -4.0f, 8, 8, 8, new Dilation(0.5f)), ModelTransform.origin(0.0f, 0.0f, 0.0f));

		ModelPartData bodyData = root.addChild("body", ModelPartBuilder.create().uv(16, 16)
				.cuboid(-4.0f, 0.0f, -2.0f, 8, 12, 4, Dilation.NONE), ModelTransform.origin(0.0f, 0.0f, 0.0f));
		bodyData.addChild("jacket", ModelPartBuilder.create().uv(16, 32)
				.cuboid(-4.0f, 0.0f, -2.0f, 8, 12, 4, new Dilation(0.25f)), ModelTransform.origin(0.0f, 0.0f, 0.0f));

		ModelPartData rightArmData = root.addChild("right_arm", ModelPartBuilder.create().uv(40, 16)
				.cuboid(-3.0f, -2.0f, -2.0f, 4, 12, 4, Dilation.NONE), ModelTransform.origin(-5.0f, 2.0f, 0.0f));
		rightArmData.addChild("right_sleeve", ModelPartBuilder.create().uv(40, 32)
				.cuboid(-3.0f, -2.0f, -2.0f, 4, 12, 4, new Dilation(0.25f)), ModelTransform.origin(0.0f, 0.0f, 0.0f));

		ModelPartData leftArmData = root.addChild("left_arm", ModelPartBuilder.create().uv(40, 16).mirrored()
				.cuboid(-1.0f, -2.0f, -2.0f, 4, 12, 4, Dilation.NONE), ModelTransform.origin(5.0f, 2.0f, 0.0f));
		leftArmData.addChild("left_sleeve", ModelPartBuilder.create().uv(40, 32).mirrored()
				.cuboid(-1.0f, -2.0f, -2.0f, 4, 12, 4, new Dilation(0.25f)), ModelTransform.origin(0.0f, 0.0f, 0.0f));

		// Per this class's own doc: the ONLY parts whose own rest transform includes rotation - baked seated from construction, never touched at runtime.
		ModelPartData rightLegData = root.addChild("right_leg", ModelPartBuilder.create().uv(0, 16)
						.cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, Dilation.NONE),
				ModelTransform.of(-1.9f, 12.0f, 0.0f, SEATED_LEG_PITCH, 0.0f, 0.0f));
		rightLegData.addChild("right_pants", ModelPartBuilder.create().uv(0, 32)
				.cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, new Dilation(0.25f)), ModelTransform.origin(0.0f, 0.0f, 0.0f));

		ModelPartData leftLegData = root.addChild("left_leg", ModelPartBuilder.create().uv(0, 16).mirrored()
						.cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, Dilation.NONE),
				ModelTransform.of(1.9f, 12.0f, 0.0f, SEATED_LEG_PITCH, 0.0f, 0.0f));
		leftLegData.addChild("left_pants", ModelPartBuilder.create().uv(0, 32).mirrored()
				.cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, new Dilation(0.25f)), ModelTransform.origin(0.0f, 0.0f, 0.0f));
		return TexturedModelData.of(modelData, 64, 64);
	}

	/** Only the head tracks the render state at all (a natural "looking around" cue) - arms/body/legs stay exactly at their own construction-time rest pose, since this pilot never walks, swings its arms, or otherwise animates beyond that. */
	@Override
	public void setAngles(LivingEntityRenderState state) {
		this.head.yaw = state.relativeHeadYaw * ((float) Math.PI / 180f);
		this.head.pitch = state.pitch * ((float) Math.PI / 180f);
	}
}
