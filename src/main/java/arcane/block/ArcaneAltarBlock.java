package arcane.block;

import arcane.data.Achievements;
import arcane.entity.ArcaneBossEntity;
import arcane.entity.ModEntities;
import arcane.item.ModItems;
import arcane.particle.ModParticles;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Алтарь призыва. Обычный (5 Арканных кристаллов) призывает Арканного Стража,
 * алтарь Архимага (8 кристаллов) — Архимага. ПКМ с кристаллами в руке.
 */
public class ArcaneAltarBlock extends Block {
	private final boolean archmage;

	public ArcaneAltarBlock(boolean archmage, Settings settings) {
		super(settings);
		this.archmage = archmage;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) {
			return ActionResult.PASS;
		}
		int cost = archmage ? 8 : 5;
		ItemStack stack = player.getMainHandStack();
		if (!stack.isOf(ModItems.ARCANE_CRYSTAL) || stack.getCount() < cost) {
			sp.sendMessage(Text.translatable("message.arcane.altar_need", cost), true);
			return ActionResult.SUCCESS;
		}
		boolean exists = !sw.getEntitiesByClass(ArcaneBossEntity.class, new Box(pos).expand(64.0), e -> e.isAlive()).isEmpty();
		if (exists) {
			sp.sendMessage(Text.translatable("message.arcane.altar_busy"), true);
			return ActionResult.SUCCESS;
		}
		ArcaneBossEntity boss = archmage
				? ModEntities.ARCHMAGE.create(sw, SpawnReason.TRIGGERED)
				: ModEntities.ARCANE_WARDEN.create(sw, SpawnReason.TRIGGERED);
		if (boss == null) {
			return ActionResult.PASS;
		}
		if (!player.isCreative()) {
			stack.decrement(cost);
		}

		Vec3d c = Vec3d.ofCenter(pos.up());
		boss.setPosition(c.x, c.y, c.z);
		boss.setTarget(sp);
		sw.spawnEntity(boss);

		// Эффектное появление: три «ненастоящие» молнии вокруг и портальный вихрь.
		for (int i = 0; i < 3; i++) {
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw, SpawnReason.TRIGGERED);
			if (bolt != null) {
				double a = i * Math.PI * 2.0 / 3.0;
				bolt.setPosition(c.x + Math.cos(a) * 4.0, c.y, c.z + Math.sin(a) * 4.0);
				bolt.setCosmetic(true);
				sw.spawnEntity(bolt);
			}
		}
		sw.spawnParticles(ParticleTypes.PORTAL, c.x, c.y + 1.0, c.z, 150, 1.2, 1.8, 1.2, 0.7);
		sw.spawnParticles(ModParticles.ARCANE_SPARK, c.x, c.y + 1.0, c.z, 60, 1.0, 2.0, 1.0, 0.2);
		sw.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0f, 1.0f);
		Achievements.grant(sp, "altar_ritual");
		return ActionResult.SUCCESS;
	}

	/** Магические частицы над алтарём (только на клиенте). */
	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		for (int i = 0; i < 2; i++) {
			world.addParticle(ModParticles.RUNE,
					pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
					pos.getY() + 1.1,
					pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
					(random.nextDouble() - 0.5) * 0.4, 0.4 + random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
		}
		if (random.nextInt(4) == 0) {
			world.addParticle(ParticleTypes.PORTAL,
					pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5,
					(random.nextDouble() - 0.5) * 1.2, random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 1.2);
		}
	}
}
