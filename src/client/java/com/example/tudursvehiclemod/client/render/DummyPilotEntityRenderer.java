package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.entity.DummyPilotEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;

/** Renders DummyPilotEntity with its own completely standalone model (DummyPilotModel) - see that class's own doc for why it shares nothing with PlayerEntityModel - and simply swaps in whichever skin that particular pilot is configured with (see getTexture()).
 *
 * No feature renderers are added at all (no armor layer, no held-item layer, no cape): this pilot has no equipment by design (see DummyPilotEntity's own getEquippedStack()), so every one of those would render nothing while still costing a pass. */
public class DummyPilotEntityRenderer extends LivingEntityRenderer<DummyPilotEntity, LivingEntityRenderState, DummyPilotModel> {

	public DummyPilotEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new DummyPilotModel(context.getPart(DummyPilotModelLayers.DUMMY_PILOT)), 0.5f);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public void updateRenderState(DummyPilotEntity entity, LivingEntityRenderState state, float tickDelta) {
		super.updateRenderState(entity, state, tickDelta);
		// Carried across so getTexture() below can pick the right skin - the render state is the only thing that method receives (it deliberately never sees the entity itself).
		this.currentSkinId = entity.tudursvehiclemod$getSkinId();
	}

	/** Set fresh in updateRenderState() immediately before getTexture() is called for that same pilot - see that method's own note for why the skin can't simply be read from the entity here. Renderers are per-entity-TYPE (one instance shared by every dummy pilot), so this deliberately holds only the value for whichever pilot is currently mid-render, never any longer. */
	private String currentSkinId;

	@Override
	public Identifier getTexture(LivingEntityRenderState state) {
		// Resolves through DummyPilotSkins so an addon-supplied skin gets registered on first use, and anything unknown/unreadable falls back to the built-in default - see that class's own doc.
		return DummyPilotSkins.tudursvehiclemod$getTexture(this.currentSkinId);
	}

	/** Vanilla's own default hasLabel() (confirmed via the decompiled EntityRenderer class - it references both hasCustomName() and the currently-hover-targeted entity) shows a custom-named entity's label whenever the player looks directly at it, REGARDLESS of setCustomNameVisible() - that flag only controls whether the label shows unconditionally (always-on) versus only-on-hover, never whether it can show at all. Overridden here so this screen's own toggle is a genuine, unconditional on/off switch: false means never, full stop, matching what a person setting it to "hidden" actually expects. */
	@Override
	protected boolean hasLabel(DummyPilotEntity entity, double distanceSquared) {
		return entity.isCustomNameVisible() && super.hasLabel(entity, distanceSquared);
	}
}
