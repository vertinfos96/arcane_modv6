package arcane.data;

/**
 * Описание всех навыков.
 * Классы: 0 = Маг, 1 = Мечник, 2 = Лучник.
 * Маг: 0 Сила магии, 1 Быстрая магия, 2 Молния, 3 Телепорт, 4 Исцеление, 5 Ледяная волна, 6 Мудрость, 7 Метеор.
 * Мечник: 8 Сила клинка, 9 Железная кожа, 10 Живучесть, 11 Жажда крови, 12 Вихрь клинков (R), 13 Рывок клинка (Z), 14 Грозовой клинок.
 * Лучник: 15 Меткость, 16 Лёгкие ноги, 17 Лёгкое падение, 18 Огненные стрелы, 19 Азарт охотника,
 *         20 Прямой полёт стрел, 21 Град стрел (R), 22 Ливень стрел (Z).
 */
public final class Skills {
	private Skills() {
	}

	public static final int POWER = 0;
	public static final int HASTE = 1;
	public static final int LIGHTNING = 2;
	public static final int TELEPORT = 3;
	public static final int HEAL = 4;
	public static final int FROST = 5;
	public static final int WISDOM = 6;
	public static final int METEOR = 7;
	public static final int BLADE = 8;
	public static final int IRON = 9;
	public static final int VITALITY = 10;
	public static final int BLOOD = 11;
	public static final int WHIRL = 12;
	public static final int DASH = 13;
	public static final int STORM = 14;
	public static final int SHARP = 15;
	public static final int SWIFT = 16;
	public static final int LIGHT = 17;
	public static final int FIRE = 18;
	public static final int RUSH = 19;
	public static final int STRAIGHT = 20;
	public static final int HAIL = 21;
	public static final int RAIN = 22;

	public static final int COUNT = 23;
	public static final int CLASS_COUNT = 3;
	public static final int SPELL_COUNT = 6;

	private static final int[] MAX_RANK = {5, 3, 1, 1, 1, 1, 3, 1, 5, 4, 5, 3, 1, 1, 3, 5, 3, 3, 1, 3, 1, 1, 1};
	private static final int[] REQ_LEVEL = {1, 1, 2, 3, 4, 5, 2, 8, 1, 2, 3, 4, 5, 6, 4, 1, 2, 3, 4, 5, 3, 6, 8};
	private static final int[] CLASS_FIRST = {0, 8, 15};
	private static final int[] CLASS_SIZE = {8, 7, 8};

	public static int maxRank(int skill) {
		return skill >= 0 && skill < COUNT ? MAX_RANK[skill] : 0;
	}

	public static int requiredLevel(int skill) {
		return skill >= 0 && skill < COUNT ? REQ_LEVEL[skill] : 99;
	}

	/** Какому классу (0 маг, 1 мечник, 2 лучник) принадлежит навык. */
	public static int classOf(int skill) {
		for (int cls = 0; cls < CLASS_COUNT; cls++) {
			if (skill >= CLASS_FIRST[cls] && skill < CLASS_FIRST[cls] + CLASS_SIZE[cls]) {
				return cls;
			}
		}
		return 0;
	}

	/** Номер первого навыка класса. */
	public static int first(int cls) {
		return CLASS_FIRST[cls];
	}

	/** Сколько навыков у класса. */
	public static int size(int cls) {
		return CLASS_SIZE[cls];
	}
}
