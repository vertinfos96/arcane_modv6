package arcane.net;

import arcane.ArcaneMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Клиент -> сервер: игрок нажал «+» у навыка. */
public record UpgradePayload(int skill) implements CustomPayload {
	public static final CustomPayload.Id<UpgradePayload> ID = new CustomPayload.Id<>(ArcaneMod.id("upgrade"));
	public static final PacketCodec<RegistryByteBuf, UpgradePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, UpgradePayload::skill,
			UpgradePayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
