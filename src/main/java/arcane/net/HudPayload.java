package arcane.net;

import arcane.ArcaneMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Сервер -> клиент: мана, выносливость, выбранное заклинание и перезарядки (для полосок на экране). */
public record HudPayload(int mana, int maxMana, int stamina, int maxStamina, int spell, int cooldowns)
		implements CustomPayload {
	public static final CustomPayload.Id<HudPayload> ID = new CustomPayload.Id<>(ArcaneMod.id("hud"));
	public static final PacketCodec<RegistryByteBuf, HudPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, HudPayload::mana,
			PacketCodecs.INTEGER, HudPayload::maxMana,
			PacketCodecs.INTEGER, HudPayload::stamina,
			PacketCodecs.INTEGER, HudPayload::maxStamina,
			PacketCodecs.INTEGER, HudPayload::spell,
			PacketCodecs.INTEGER, HudPayload::cooldowns,
			HudPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
