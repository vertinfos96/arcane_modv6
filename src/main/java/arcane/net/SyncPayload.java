package arcane.net;

import arcane.ArcaneMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/** Сервер -> клиент: опыт по классам и ранги навыков игрока. */
public record SyncPayload(List<Integer> xps, List<Integer> ranks) implements CustomPayload {
	public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(ArcaneMod.id("sync"));
	public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER.collect(PacketCodecs.toList()), SyncPayload::xps,
			PacketCodecs.INTEGER.collect(PacketCodecs.toList()), SyncPayload::ranks,
			SyncPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
