package arcane.data;

import arcane.ArcaneMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Данные прокачки игрока (сохраняются вместе с игроком).
 * У каждого класса (0 = Маг, 1 = Мечник, 2 = Лучник) свой опыт, уровень и очки навыков.
 * xps — опыт по классам, ranks — ранги навыков (номера навыков см. в {@link Skills}).
 * Заклинания: 0 = Огненный луч, 1 = Молния, 2 = Телепорт, 3 = Исцеление, 4 = Ледяная волна, 5 = Метеор.
 */
public record ArcaneData(List<Integer> xps, List<Integer> ranks, int selected) {

	public static final int MAX_LEVEL = 15;

	public static final ArcaneData EMPTY = new ArcaneData(List.of(), List.of(), 0);

	public static final Codec<ArcaneData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.listOf().optionalFieldOf("xps", List.of()).forGetter(ArcaneData::xps),
			Codec.INT.listOf().optionalFieldOf("ranks", List.of()).forGetter(ArcaneData::ranks),
			Codec.INT.optionalFieldOf("selected", 0).forGetter(ArcaneData::selected)
	).apply(i, ArcaneData::new));

	public static final AttachmentType<ArcaneData> TYPE = AttachmentRegistry.create(
			ArcaneMod.id("rpg_data_v4"),
			builder -> builder
					.initializer(() -> EMPTY)
					.persistent(CODEC)
					.copyOnDeath()
	);

	public static ArcaneData get(PlayerEntity player) {
		return player.getAttachedOrElse(TYPE, EMPTY);
	}

	public static void set(PlayerEntity player, ArcaneData data) {
		player.setAttached(TYPE, data);
	}

	/** Опыт, нужный для достижения уровня (уровень 1 = 0 опыта). */
	public static int xpForLevel(int level) {
		int l = level - 1;
		return 25 * l * l;
	}

	/** Опыт класса. */
	public int xp(int cls) {
		return cls >= 0 && cls < xps.size() ? xps.get(cls) : 0;
	}

	/** Уровень класса. */
	public int level(int cls) {
		int lvl = (int) Math.floor(Math.sqrt(xp(cls) / 25.0)) + 1;
		return Math.min(lvl, MAX_LEVEL);
	}

	public int totalLevels() {
		return level(0) + level(1) + level(2);
	}

	public int rank(int skill) {
		return skill >= 0 && skill < ranks.size() ? ranks.get(skill) : 0;
	}

	/** Сколько очков уже потрачено в классе. */
	public int spent(int cls) {
		int sum = 0;
		for (int i = 0; i < Skills.size(cls); i++) {
			sum += rank(Skills.first(cls) + i);
		}
		return sum;
	}

	/** Свободные очки класса: 1 очко за каждый уровень класса, начиная со 2-го. */
	public int points(int cls) {
		return level(cls) - 1 - spent(cls);
	}

	public double powerMultiplier() {
		return 1.0 + 0.25 * rank(Skills.POWER);
	}

	public double cooldownMultiplier() {
		return 1.0 - 0.15 * rank(Skills.HASTE);
	}

	public boolean hasSpell(int spell) {
		return switch (spell) {
			case 0 -> true;
			case 1 -> rank(Skills.LIGHTNING) > 0;
			case 2 -> rank(Skills.TELEPORT) > 0;
			case 3 -> rank(Skills.HEAL) > 0;
			case 4 -> rank(Skills.FROST) > 0;
			case 5 -> rank(Skills.METEOR) > 0;
			default -> false;
		};
	}

	public int nextSpell() {
		for (int i = 1; i <= Skills.SPELL_COUNT; i++) {
			int s = (selected + i) % Skills.SPELL_COUNT;
			if (hasSpell(s)) {
				return s;
			}
		}
		return 0;
	}

	public ArcaneData withXp(int cls, int newXp) {
		List<Integer> n = new ArrayList<>(Collections.nCopies(Skills.CLASS_COUNT, 0));
		for (int i = 0; i < Math.min(xps.size(), Skills.CLASS_COUNT); i++) {
			n.set(i, xps.get(i));
		}
		n.set(cls, Math.max(0, newXp));
		return new ArcaneData(List.copyOf(n), ranks, selected);
	}

	public ArcaneData withSelected(int s) {
		return new ArcaneData(xps, ranks, s);
	}

	public boolean canUpgrade(int skill) {
		if (skill < 0 || skill >= Skills.COUNT) return false;
		int cls = Skills.classOf(skill);
		return points(cls) > 0 && rank(skill) < Skills.maxRank(skill) && level(cls) >= Skills.requiredLevel(skill);
	}

	/** Возвращает новое состояние с улучшенным навыком (или то же самое, если улучшить нельзя). */
	public ArcaneData upgraded(int skill) {
		if (!canUpgrade(skill)) return this;
		List<Integer> n = new ArrayList<>(Collections.nCopies(Skills.COUNT, 0));
		for (int i = 0; i < Math.min(ranks.size(), Skills.COUNT); i++) {
			n.set(i, ranks.get(i));
		}
		n.set(skill, n.get(skill) + 1);
		return new ArcaneData(xps, List.copyOf(n), selected);
	}
}
