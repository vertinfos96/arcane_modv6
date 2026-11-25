package arcane.block;

import arcane.ArcaneMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;

/** Блоки мода. */
public final class ModBlocks {
	private ModBlocks() {
	}

	private static final RegistryKey<Block> ALTAR_KEY =
			RegistryKey.of(RegistryKeys.BLOCK, ArcaneMod.id("arcane_altar"));
	private static final RegistryKey<Item> ALTAR_ITEM_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ArcaneMod.id("arcane_altar"));

	public static final Block ARCANE_ALTAR = Registry.register(Registries.BLOCK, ALTAR_KEY,
			new ArcaneAltarBlock(false, AbstractBlock.Settings.create()
					.registryKey(ALTAR_KEY)
					.mapColor(MapColor.PURPLE)
					.strength(3.5f, 1200.0f)
					.luminance(state -> 10)
					.sounds(BlockSoundGroup.STONE)));

	public static final Item ARCANE_ALTAR_ITEM = Registry.register(Registries.ITEM, ALTAR_ITEM_KEY,
			new BlockItem(ARCANE_ALTAR, new Item.Settings()
					.registryKey(ALTAR_ITEM_KEY)
					.useBlockPrefixedTranslationKey()));

	private static final RegistryKey<Block> ARCHMAGE_ALTAR_KEY =
			RegistryKey.of(RegistryKeys.BLOCK, ArcaneMod.id("archmage_altar"));
	private static final RegistryKey<Item> ARCHMAGE_ALTAR_ITEM_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ArcaneMod.id("archmage_altar"));

	public static final Block ARCHMAGE_ALTAR = Registry.register(Registries.BLOCK, ARCHMAGE_ALTAR_KEY,
			new ArcaneAltarBlock(true, AbstractBlock.Settings.create()
					.registryKey(ARCHMAGE_ALTAR_KEY)
					.mapColor(MapColor.LIGHT_BLUE)
					.strength(3.5f, 1200.0f)
					.luminance(state -> 10)
					.sounds(BlockSoundGroup.STONE)));

	public static final Item ARCHMAGE_ALTAR_ITEM = Registry.register(Registries.ITEM, ARCHMAGE_ALTAR_ITEM_KEY,
			new BlockItem(ARCHMAGE_ALTAR, new Item.Settings()
					.registryKey(ARCHMAGE_ALTAR_ITEM_KEY)
					.useBlockPrefixedTranslationKey()));

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(ARCANE_ALTAR_ITEM);
			entries.add(ARCHMAGE_ALTAR_ITEM);
		});
	}
}
