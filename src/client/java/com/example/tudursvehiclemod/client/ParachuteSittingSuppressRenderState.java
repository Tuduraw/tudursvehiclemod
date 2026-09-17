package com.example.tudursvehiclemod.client;

/** Implemented by LivingEntityRenderStateMixin (added to every LivingEntityRenderState instance, covering players and any other living entity) - mixin.LivingEntityRendererMixin's own updateRenderState() injection sets this flag from the entity's own getVehicle() check (the only place in the whole pipeline that still has a reference to the actual entity, not just its render state); mixin.BipedEntityModelMixin then reads it at the very start of setAngles() - the actual method that consumes BipedEntityRenderState's own hasVehicle field to decide whether to bend the legs into a seated pose - and forces hasVehicle back to false there if this flag is set, immediately before that same method's own vanilla logic runs. Deliberately a single mechanism (replacing the previous session's own duplicated, TAIL-injected overrides scattered across LivingEntityRendererMixin/BipedEntityRendererMixin, which turned out not to actually work) rather than several independent attempts at the same fix. */
public interface ParachuteSittingSuppressRenderState {
	boolean tudursvehiclemod$isSittingPoseSuppressed();

	void tudursvehiclemod$setSittingPoseSuppressed(boolean value);
}
