package arcane.data;

import arcane.ArcaneMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Бонусы от особой одежды. Мантия Архимага — кожаная кираса с моделью archmage_robe (выпадает с Архимага). */
public final class ArcaneGear {
	private ArcaneGear() {
	}

	public static boolean wearsRobe(PlayerEntity p) {
		ItemStack s = p.getEquippedStack(EquipmentSlot.CHEST);
		Identifier model = s.get(DataComponentTypes.ITEM_MODEL);
		return model != null && model.equals(ArcaneMod.id("archmage_robe"));
	}

	/** Множитель перезарядки заклинаний (меньше — быстрее): мантия даёт -25%. */
	public static double cooldownFactor(PlayerEntity p) {
		return wearsRobe(p) ? 0.75 : 1.0;
	}

	/** Множитель восстановления маны: мантия даёт +50%. */
	public static float manaRegenFactor(PlayerEntity p) {
		return wearsRobe(p) ? 1.5f : 1.0f;
	}
}
