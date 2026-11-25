package arcane.entity;

import arcane.ArcaneMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.Heightmap;

public final class ModEntities {
	private ModEntities() {
	}

	private static final RegistryKey<EntityType<?>> GOLEM_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, ArcaneMod.id("crystal_golem"));
	private static final RegistryKey<EntityType<?>> WARDEN_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, ArcaneMod.id("arcane_warden"));
	private static final RegistryKey<EntityType<?>> ARCHMAGE_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, ArcaneMod.id("archmage"));
	private static final RegistryKey<EntityType<?>> SKELETON_KEY =
			RegistryKey.of(RegistryKeys.ENTITY_TYPE, ArcaneMod.id("arcane_skeleton"));

	public static final EntityType<CrystalGolemEntity> CRYSTAL_GOLEM = Registry.register(
			Registries.ENTITY_TYPE, GOLEM_KEY,
			EntityType.Builder.create(CrystalGolemEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.makeFireImmune()
					.maxTrackingRange(8)
					.build(GOLEM_KEY));

	public static final EntityType<ArcaneSkeletonEntity> ARCANE_SKELETON = Registry.register(
			Registries.ENTITY_TYPE, SKELETON_KEY,
			EntityType.Builder.create(ArcaneSkeletonEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 1.99f)
					.eyeHeight(1.74f)
					.makeFireImmune()
					.maxTrackingRange(8)
					.build(SKELETON_KEY));

	public static final EntityType<ArcaneWardenEntity> ARCANE_WARDEN = Registry.register(
			Registries.ENTITY_TYPE, WARDEN_KEY,
			EntityType.Builder.create(ArcaneWardenEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.makeFireImmune()
					.maxTrackingRange(10)
					.build(WARDEN_KEY));

	public static final EntityType<ArchmageEntity> ARCHMAGE = Registry.register(
			Registries.ENTITY_TYPE, ARCHMAGE_KEY,
			EntityType.Builder.create(ArchmageEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6f, 1.95f)
					.eyeHeight(1.74f)
					.makeFireImmune()
					.maxTrackingRange(10)
					.build(ARCHMAGE_KEY));

	/** «Арканная буря»: наши мобы появляются только во время грозы (в любое время суток, даже днём). */
	private static boolean stormSpawn(EntityType<? extends HostileEntity> type, ServerWorldAccess world,
									  SpawnReason reason, BlockPos pos, Random random) {
		return world.getDifficulty() != Difficulty.PEACEFUL
				&& world.toServerWorld().isThundering()
				&& MobEntity.canMobSpawn(type, world, reason, pos, random);
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(CRYSTAL_GOLEM, CrystalGolemEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARCANE_SKELETON, ArcaneSkeletonEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARCANE_WARDEN, ArcaneWardenEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARCHMAGE, ArchmageEntity.createAttributes());

		SpawnRestriction.register(CRYSTAL_GOLEM, SpawnLocationTypes.ON_GROUND,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ModEntities::stormSpawn);
		SpawnRestriction.register(ARCANE_SKELETON, SpawnLocationTypes.ON_GROUND,
				Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ModEntities::stormSpawn);

		// Естественный спавн в обычном мире — только во время грозы (см. stormSpawn).
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, CRYSTAL_GOLEM, 10, 1, 3);
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, ARCANE_SKELETON, 12, 1, 3);
	}
}
