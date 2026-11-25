package arcane.item;

import arcane.ArcaneMod;
import arcane.entity.ModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Rarity;

import java.util.function.Function;

public final class ModItems {
	private ModItems() {
	}

	public static final Item ARCANE_CRYSTAL = register("arcane_crystal", Item::new, new Item.Settings());

	public static final Item ARCANE_STAFF = register("arcane_staff", StaffItem::new, new Item.Settings()
			.maxCount(1)
			.rarity(Rarity.EPIC)
			.component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true));

	/** Оружие мобов (3D-модели). */
	public static final Item WARDEN_BLADE = register("warden_blade", Item::new, new Item.Settings()
			.maxCount(1).rarity(Rarity.EPIC));

	public static final Item CRYSTAL_MACE = register("crystal_mace", Item::new, new Item.Settings()
			.maxCount(1).rarity(Rarity.RARE));

	/** Посох Архимага: +50% к урону и лечению заклинаний. */
	public static final Item ARCHMAGE_STAFF = register("archmage_staff", s -> new StaffItem(s, 1.5), new Item.Settings()
			.maxCount(1)
			.rarity(Rarity.EPIC)
			.component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true));

	/** Плоские фигуры для предупреждений об атаках (не для игроков). */
	public static final Item TELEGRAPH_DISC = register("telegraph_disc", Item::new, new Item.Settings());
	public static final Item TELEGRAPH_RING = register("telegraph_ring", Item::new, new Item.Settings());
	public static final Item TELEGRAPH_LINE = register("telegraph_line", Item::new, new Item.Settings());
	public static final Item TELEGRAPH_FRAME = register("telegraph_frame", Item::new, new Item.Settings());
	public static final Item TELEGRAPH_CONE = register("telegraph_cone", Item::new, new Item.Settings());
	public static final Item TELEGRAPH_CONE_FRAME = register("telegraph_cone_frame", Item::new, new Item.Settings());

	public static final Item CRYSTAL_GOLEM_SPAWN_EGG = register("crystal_golem_spawn_egg",
			s -> new SpawnEggItem(ModEntities.CRYSTAL_GOLEM, s), new Item.Settings());

	public static final Item ARCANE_SKELETON_SPAWN_EGG = register("arcane_skeleton_spawn_egg",
			s -> new SpawnEggItem(ModEntities.ARCANE_SKELETON, s), new Item.Settings());

	public static final Item ARCANE_WARDEN_SPAWN_EGG = register("arcane_warden_spawn_egg",
			s -> new SpawnEggItem(ModEntities.ARCANE_WARDEN, s), new Item.Settings());

	public static final Item ARCHMAGE_SPAWN_EGG = register("archmage_spawn_egg",
			s -> new SpawnEggItem(ModEntities.ARCHMAGE, s), new Item.Settings());

	private static Item register(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, ArcaneMod.id(name));
		return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(ARCANE_STAFF);
			entries.add(ARCHMAGE_STAFF);
			entries.add(WARDEN_BLADE);
			entries.add(CRYSTAL_MACE);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(ARCANE_CRYSTAL));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
			entries.add(CRYSTAL_GOLEM_SPAWN_EGG);
			entries.add(ARCANE_SKELETON_SPAWN_EGG);
			entries.add(ARCANE_WARDEN_SPAWN_EGG);
			entries.add(ARCHMAGE_SPAWN_EGG);
		});
	}
}
