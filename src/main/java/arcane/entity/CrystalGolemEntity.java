package arcane.entity;

import arcane.data.ClassAbilities;
import arcane.data.Fx;
import arcane.item.ModItems;
import arcane.particle.ModParticles;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Кристальный голем с кристальной булавой.
 * Атаки с замахом: голем замирает, на земле растёт заполненная фигура, и удар идёт туда, куда он смотрел
 * в начале замаха (без автонаводки):
 *  - Сокрушающий удар — круг вокруг голема (если враг рядом);
 *  - Кристальный всплеск — полоса кристаллов вперёд (если враг подальше).
 * При смерти разлетается медленными осколками.
 */
public class CrystalGolemEntity extends ZombieEntity {

	private static final int SLAM = 0;
	private static final int ERUPTION = 1;

	private boolean shelled = false;
	private int cooldown = 60;
	private int castLeft = 0;
	private int attack = -1;
	private Vec3d aimDir = new Vec3d(0.0, 0.0, 1.0);
	private Telegraph telegraph;

	public CrystalGolemEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
		this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRYSTAL_MACE));
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.MAX_HEALTH, 40.0)
				.add(EntityAttributes.ATTACK_DAMAGE, 6.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.5);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		// Всегда держим свою булаву (ваниль иногда выдаёт зомби железный меч).
		if (this.age % 40 == 0 && !this.getMainHandStack().isOf(ModItems.CRYSTAL_MACE)) {
			this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRYSTAL_MACE));
		}
		// Мерцание кристаллов.
		if (this.age % 6 == 0) {
			world.spawnParticles(ModParticles.CRYSTAL_GLINT, this.getX(), this.getY() + 1.0, this.getZ(), 2, 0.35, 0.7, 0.35, 0.01);
			if (this.age % 24 == 0) {
				world.spawnParticles(ModParticles.RUNE, this.getX(), this.getY() + 1.6, this.getZ(), 6, 0.4, 0.4, 0.4, 0.3);
			}
		}
		if (!this.isAlive()) {
			return;
		}

		if (castLeft > 0) {
			castLeft--;
			this.getNavigation().stop();
			if (castLeft <= 0) {
				if (telegraph != null) {
					telegraph.remove();
					telegraph = null;
				}
				this.setAiDisabled(false);
				this.setGlowing(false);
				if (attack == SLAM) {
					slam(world);
				} else {
					eruption(world);
				}
				cooldown = 80;
			}
			return;
		}

		LivingEntity target = this.getTarget();
		if (target == null) {
			return;
		}
		// Кристальный панцирь: при здоровье ниже 50% — один раз небольшая стойкость.
		if (!shelled && this.getHealth() < this.getMaxHealth() * 0.5f) {
			shelled = true;
			this.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 160, 0));
			Fx.spiral(world, this.getPos(), 1.0, 2.4, 3.0, 40, ModParticles.CRYSTAL_GLINT);
			world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 1.5f, 0.6f);
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		double dist = Math.sqrt(this.squaredDistanceTo(target));
		if (dist < 4.5) {
			startCast(world, target, SLAM, 14);
			telegraph = Telegraph.disc(world, this.getPos(), 4.2, 14);
		} else if (dist < 12.0) {
			startCast(world, target, ERUPTION, 16);
			telegraph = Telegraph.line(world, this.getPos(), aimDir, 11.0, 2.6, 16);
		}
	}

	private void startCast(ServerWorld world, LivingEntity target, int id, int windup) {
		this.lookAtEntity(target, 90.0f, 90.0f);
		Vec3d to = target.getPos().subtract(this.getPos()).multiply(1.0, 0.0, 1.0);
		aimDir = to.lengthSquared() < 0.01 ? new Vec3d(0.0, 0.0, 1.0) : to.normalize();
		attack = id;
		castLeft = windup;
		this.setAiDisabled(true);
		this.setGlowing(true);
		this.swingHand(Hand.MAIN_HAND);
		world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 1.2f, 0.5f);
	}

	/** Волна кристаллов: 7 всплесков по прямой в направлении замаха, один за другим. */
	private void eruption(ServerWorld world) {
		Vec3d start = this.getPos();
		Vec3d dir = aimDir;
		List<PlayerEntity> struck = new ArrayList<>();
		for (int i = 1; i <= 7; i++) {
			final int step = i;
			ClassAbilities.later(i * 2, () -> {
				Vec3d p = start.add(dir.multiply(step * 1.6));
				world.spawnParticles(ModParticles.CRYSTAL_GLINT, p.x, p.y + 0.6, p.z, 8, 0.25, 0.5, 0.25, 0.05);
				world.spawnParticles(ModParticles.ARCANE_SPARK, p.x, p.y + 1.0, p.z, 2, 0.2, 0.6, 0.2, 0.02);
				world.playSound(null, BlockPos.ofFloored(p), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK,
						SoundCategory.HOSTILE, 0.9f, 0.8f + step * 0.05f);
				for (PlayerEntity pl : world.getEntitiesByClass(PlayerEntity.class, new Box(p, p).expand(1.3, 2.0, 1.3),
						x -> x.isAlive() && !x.isSpectator())) {
					if (!struck.contains(pl)) {
						struck.add(pl);
						pl.damage(world, world.getDamageSources().mobAttack(this), 10.0f);
						pl.takeKnockback(0.9, p.x - pl.getX(), p.z - pl.getZ());
					}
				}
			});
		}
	}

	private void slam(ServerWorld world) {
		for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, this.getBoundingBox().expand(4.2),
				pl -> pl.isAlive() && !pl.isSpectator())) {
			p.damage(world, world.getDamageSources().mobAttack(this), 11.0f);
			p.takeKnockback(1.1, this.getX() - p.getX(), this.getZ() - p.getZ());
		}
		Vec3d c = this.getPos().add(0.0, 0.15, 0.0);
		for (int i = 1; i <= 4; i++) {
			Fx.circle(world, c, i * 1.0, ModParticles.CRYSTAL_GLINT, 12 + i * 6);
		}
		world.spawnParticles(ModParticles.ARCANE_SPARK, c.x, c.y + 0.3, c.z, 30, 1.5, 0.2, 1.5, 0.15);
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.2f, 0.7f);
	}

	/** При смерти голем разлетается медленными кристальными осколками, которые ранят всех, кого заденут. */
	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		Vec3d c = this.getPos().add(0.0, 1.0, 0.0);
		List<PlayerEntity> struck = new ArrayList<>();
		for (int i = 0; i < 14; i++) {
			double a = i * Math.PI * 2.0 / 14.0 + this.random.nextDouble() * 0.3;
			Vec3d dir = new Vec3d(Math.cos(a), 0.15 + this.random.nextDouble() * 0.35, Math.sin(a)).normalize();
			for (int s = 1; s <= 8; s++) {
				final int st = s;
				ClassAbilities.later(st * 2, () -> {
					Vec3d p = c.add(dir.multiply(st * 0.8));
					world.spawnParticles(ModParticles.CRYSTAL_GLINT, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
					for (PlayerEntity pl : world.getEntitiesByClass(PlayerEntity.class, new Box(p, p).expand(1.1, 1.5, 1.1),
							x -> x.isAlive() && !x.isSpectator())) {
						if (!struck.contains(pl)) {
							struck.add(pl);
							pl.damage(world, world.getDamageSources().mobAttack(this), 8.0f);
							pl.takeKnockback(0.9, c.x - pl.getX(), c.z - pl.getZ());
						}
					}
				});
			}
		}
		world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		Fx.circle(world, this.getPos().add(0.0, 0.2, 0.0), 1.5, ModParticles.CRYSTAL_GLINT, 16);
		world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 1.6f, 0.5f);
		world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.2f, 0.7f);
	}

	@Override
	public void remove(net.minecraft.entity.Entity.RemovalReason reason) {
		super.remove(reason);
		if (telegraph != null) {
			telegraph.remove();
			telegraph = null;
		}
	}

	@Override
	protected boolean burnsInDaylight() {
		return false;
	}

	@Override
	protected boolean canConvertInWater() {
		return false;
	}
}
