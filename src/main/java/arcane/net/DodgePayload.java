package arcane.net;

import arcane.ArcaneMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Клиент -> сервер: перекат в заданном направлении (координаты x100). */
public record DodgePayload(int dx, int dz) implements CustomPayload {
	public static final CustomPayload.Id<DodgePayload> ID = new CustomPayload.Id<>(ArcaneMod.id("dodge"));
	public static final PacketCodec<RegistryByteBuf, DodgePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, DodgePayload::dx,
			PacketCodecs.INTEGER, DodgePayload::dz,
			DodgePayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
