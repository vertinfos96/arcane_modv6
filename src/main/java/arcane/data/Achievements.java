package arcane.data;

import arcane.ArcaneMod;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Выдача достижений мода (описаны в data/arcane/advancement/*.json). */
public final class Achievements {
	private Achievements() {
	}

	public static void grant(ServerPlayerEntity player, String name) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		AdvancementEntry entry = server.getAdvancementLoader().get(ArcaneMod.id(name));
		if (entry == null) {
			return;
		}
		player.getAdvancementTracker().grantCriterion(entry, "done");
	}
}
