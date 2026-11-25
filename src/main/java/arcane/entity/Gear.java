package arcane.entity;

import arcane.ArcaneMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Вспомогательное: предметы для мобов. */
final class Gear {
	private Gear() {
	}

	/** Настоящий лук (чтобы скелет стрелял), но с внешним видом «Арканного лука». */
	static ItemStack arcaneBow() {
		ItemStack stack = new ItemStack(Items.BOW);
		stack.set(DataComponentTypes.ITEM_MODEL, ArcaneMod.id("arcane_bow"));
		return stack;
	}
}
