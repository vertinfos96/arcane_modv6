package arcane.net;

import arcane.data.ArcaneLogic;
import arcane.data.ClassAbilities;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ArcaneNetworking {
	private ArcaneNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HudPayload.ID, HudPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DodgePayload.ID, DodgePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(UpgradePayload.ID, UpgradePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AbilityPayload.ID, AbilityPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(UpgradePayload.ID, (payload, context) ->
				ArcaneLogic.upgrade(context.player(), payload.skill()));
		ServerPlayNetworking.registerGlobalReceiver(DodgePayload.ID, (payload, context) ->
				ClassAbilities.dodge(context.player(), payload.dx(), payload.dz()));
		ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.ID, (payload, context) ->
				ClassAbilities.handleKey(context.player(), payload.key()));
	}
}
