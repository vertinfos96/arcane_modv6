package arcane.net;

import arcane.ArcaneMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Клиент -> сервер: игрок нажал клавишу способности (1 = R, 2 = Z). */
public record AbilityPayload(int key) implements CustomPayload {
	public static final CustomPayload.Id<AbilityPayload> ID = new CustomPayload.Id<>(ArcaneMod.id("ability"));
	public static final PacketCodec<RegistryByteBuf, AbilityPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, AbilityPayload::key,
			AbilityPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
