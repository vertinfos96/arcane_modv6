package arcane.data;

/** Простые настройки (меняются командами). */
public final class ArcaneConfig {
	private ArcaneConfig() {
	}

	/** Ломают ли метеоры босса землю (команда /arcane terrain on|off). */
	public static boolean bossTerrainDamage = true;
}
