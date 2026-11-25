package arcane.data;

import arcane.net.HudPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Мана (заклинания) и выносливость (бег, прыжки, перекат, умения мечника и лучника).
 * Значения живут на сервере и каждые несколько тиков отправляются клиенту для полосок на экране.
 */
public final class Resources {
	private Resources() {
	}

	private static final class State {
		float mana;
		float stamina;
		boolean exhausted;
		int lastJumps = -1;
		long lastManaUse;
		long lastStaminaUse;
		int[] sent = new int[0];
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	public static int maxMana(ArcaneData d) {
		return 60 + 8 * d.level(0);
	}

	public static int maxStamina(ArcaneData d) {
		return 80 + 3 * d.totalLevels();
	}

	private static State state(ServerPlayerEntity p) {
		return STATES.computeIfAbsent(p.getUuid(), id -> {
			State s = new State();
			ArcaneData d = ArcaneData.get(p);
			s.mana = maxMana(d);
			s.stamina = maxStamina(d);
			return s;
		});
	}

	/** Заново начинает отсчёт (вход в мир, возрождение): ресурсы полные, усталость снята. */
	public static void reset(ServerPlayerEntity p) {
		STATES.remove(p.getUuid());
		applyExhaustion(p, false);
	}

	private static boolean free(ServerPlayerEntity p) {
		return p.isCreative() || p.isSpectator();
	}

	public static boolean spendMana(ServerPlayerEntity p, int cost) {
		if (free(p)) {
			return true;
		}
		State s = state(p);
		if (s.mana + 0.001f < cost) {
			p.sendMessage(Text.translatable("message.arcane.no_mana"), true);
			return false;
		}
		s.mana -= cost;
		s.lastManaUse = p.getWorld().getTime();
		return true;
	}

	public static void refundMana(ServerPlayerEntity p, int amount) {
		if (free(p)) {
			return;
		}
		State s = state(p);
		s.mana = Math.min(maxMana(ArcaneData.get(p)), s.mana + amount);
	}

	/** Хватает ли выносливости (не списывает). Если нет — показывает сообщение. */
	public static boolean canStamina(ServerPlayerEntity p, int cost) {
		if (free(p)) {
			return true;
		}
		State s = state(p);
		if (s.exhausted || s.stamina < cost) {
			p.sendMessage(Text.translatable("message.arcane.no_stamina"), true);
			return false;
		}
		return true;
	}

	public static void spendStamina(ServerPlayerEntity p, int cost) {
		if (free(p)) {
			return;
		}
		State s = state(p);
		s.stamina = Math.max(0.0f, s.stamina - cost);
		s.lastStaminaUse = p.getWorld().getTime();
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			update(p);
		}
	}

	private static void update(ServerPlayerEntity p) {
		ArcaneData d = ArcaneData.get(p);
		State s = state(p);
		long now = p.getWorld().getTime();
		float maxM = maxMana(d);
		float maxS = maxStamina(d);

		if (free(p)) {
			s.mana = maxM;
			s.stamina = maxS;
			if (s.exhausted) {
				s.exhausted = false;
				applyExhaustion(p, false);
			}
		} else {
			// Мана восстанавливается через полторы секунды после последнего заклинания.
			if (now - s.lastManaUse > 30) {
				s.mana += (0.15f + 0.012f * d.level(0)) * ArcaneGear.manaRegenFactor(p);
			}
			s.mana = Math.min(s.mana, maxM);

			// Прыжки тратят выносливость (считаем по статистике «прыжков»).
			int jumps = p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP));
			if (s.lastJumps < 0) {
				s.lastJumps = jumps;
			}
			if (jumps > s.lastJumps) {
				s.stamina = Math.max(0.0f, s.stamina - 8.0f * (jumps - s.lastJumps));
				s.lastStaminaUse = now;
				s.lastJumps = jumps;
			}
			// Бег тратит выносливость, иначе она восстанавливается.
			if (p.isSprinting() && !s.exhausted) {
				s.stamina = Math.max(0.0f, s.stamina - 0.3f);
				s.lastStaminaUse = now;
			} else if (now - s.lastStaminaUse > 20) {
				s.stamina += 0.75f;
			}
			s.stamina = Math.min(s.stamina, maxS);

			if (!s.exhausted && s.stamina <= 0.0f) {
				s.exhausted = true;
				applyExhaustion(p, true);
				p.sendMessage(Text.translatable("message.arcane.exhausted"), true);
				p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.8f, 0.6f);
			} else if (s.exhausted && s.stamina >= 30.0f) {
				s.exhausted = false;
				applyExhaustion(p, false);
			}
		}

		// Отправляем клиенту (только при изменениях).
		int[] now6 = {(int) s.mana, (int) maxM, (int) s.stamina, (int) maxS, d.selected(), packedCooldowns(p)};
		if (!Arrays.equals(now6, s.sent)) {
			s.sent = now6;
			ServerPlayNetworking.send(p, new HudPayload(now6[0], now6[1], now6[2], now6[3], now6[4], now6[5]));
		}
	}

	/** Усталость: нельзя прыгнуть и медленнее ходьба. */
	private static void applyExhaustion(ServerPlayerEntity p, boolean on) {
		ClassAbilities.setModifier(p, EntityAttributes.JUMP_STRENGTH, "exhausted_jump",
				on ? -1.0 : 0.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ClassAbilities.setModifier(p, EntityAttributes.MOVEMENT_SPEED, "exhausted_speed",
				on ? -0.3 : 0.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Перезарядки умений R и Z для оружия в руке (секунды): R * 100 + Z. */
	private static int packedCooldowns(ServerPlayerEntity p) {
		ItemStack held = p.getMainHandStack();
		int r = 0;
		int z = 0;
		if (held.isIn(ItemTags.SWORDS)) {
			r = ClassAbilities.cooldownSeconds(p, Skills.WHIRL);
			z = ClassAbilities.cooldownSeconds(p, Skills.DASH);
		} else if (held.isOf(Items.BOW)) {
			r = ClassAbilities.cooldownSeconds(p, Skills.HAIL);
			z = ClassAbilities.cooldownSeconds(p, Skills.RAIN);
		}
		return Math.min(99, r) * 100 + Math.min(99, z);
	}
}
