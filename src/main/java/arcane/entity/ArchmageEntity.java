package arcane.entity;

import arcane.data.ClassAbilities;
import arcane.data.Fx;
import arcane.data.Meteors;
import arcane.item.ModItems;
import arcane.particle.ModParticles;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Архимаг — босс-маг в мантии с мощным посохом. Держится на расстоянии 8–14 блоков и бьёт издалека:
 * метеоритный дождь, рой самонаводящихся сфер, звездопад. Если подойти ближе 5 блоков, отбрасывает
 * взрывом (круг-предупреждение) или телепортируется — но телепорт перезаряжается 12 секунд,
 * поэтому в остальное время его можно достать и мечом.
 */
public class ArchmageEntity extends ArcaneBossEntity {

	private static final int METEORS = 0;
	private static final int ORBS = 1;
	private static final int STARFALL = 2;
	private static final int BURST = 3;
	private static final int BLINK = 4;

	private static final double BURST_RADIUS = 5.5;

	private int blinkCd = 100;
	private int burstCd = 0;
	private boolean volleyNext = false;
	private int lastAttack = -1;
	private final List<Vec3d> spots = new ArrayList<>();

	public ArchmageEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world, Text.translatable("entity.arcane.archmage"),
				Text.translatable("boss.arcane.archmage.subtitle"), BossBar.Color.BLUE);
		this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ARCHMAGE_STAFF));
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.MAX_HEALTH, 260.0)
				.add(EntityAttributes.ATTACK_DAMAGE, 5.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.30)
				.add(EntityAttributes.ARMOR, 4.0)
				.add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(EntityAttributes.FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.SCALE, 1.5);
	}

	/** Маг не бежит в ближний бой: только выбирает цель, а дистанцию держит сам (см. think). */
	@Override
	protected void initCustomGoals() {
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	@Override
	protected void ensureGear() {
		if (!this.getMainHandStack().isOf(ModItems.ARCHMAGE_STAFF)) {
			this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.ARCHMAGE_STAFF));
		}
	}

	@Override
	protected ParticleEffect deathParticle() {
		return ModParticles.STORM_SPARK;
	}

	// ------------------------------------------------------------------
	// Поведение
	// ------------------------------------------------------------------

	@Override
	protected void think(ServerWorld world, LivingEntity target) {
		if (target == null) {
			return;
		}
		float ratio = this.getHealth() / this.getMaxHealth();
		if (ratio < 0.6f && stage < 1) {
			stage = 1;
		}
		if (ratio < 0.35f && stage < 2) {
			stage = 2;
			summon(world, ModEntities.ARCANE_SKELETON, 2);
		}
		if (blinkCd > 0) {
			blinkCd--;
		}
		if (burstCd > 0) {
			burstCd--;
		}
		double dist = Math.sqrt(this.squaredDistanceTo(target));

		// Держим дистанцию 8–14 блоков.
		if (this.age % 8 == 0) {
			if (dist < 8.0) {
				Vec3d away = this.getPos().subtract(target.getPos()).multiply(1.0, 0.0, 1.0);
				if (away.lengthSquared() > 0.01) {
					Vec3d dest = this.getPos().add(away.normalize().multiply(5.0));
					this.getNavigation().startMovingTo(dest.x, dest.y, dest.z, 1.2);
				}
			} else if (dist > 15.0) {
				this.getNavigation().startMovingTo(target, 1.0);
			} else {
				this.getNavigation().stop();
			}
		}
		if (this.age % 2 == 0) {
			this.lookAtEntity(target, 30.0f, 30.0f);
		}

		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (dist > 45.0) {
			return;
		}

		int pick;
		if (volleyNext) {
			pick = ORBS;
			volleyNext = false;
		} else if (dist < 5.0 && burstCd == 0 && (blinkCd > 0 || this.random.nextInt(100) < 55)) {
			pick = BURST;
		} else if (dist < 5.0 && blinkCd == 0) {
			pick = BLINK;
		} else {
			int roll = this.random.nextInt(90);
			pick = roll < 35 ? METEORS : roll < 65 ? ORBS : STARFALL;
			if (pick == lastAttack) {
				pick = (pick + 1) % 3;
			}
		}
		lastAttack = pick;
		startAttack(world, target, pick);
	}

	private void startAttack(ServerWorld world, LivingEntity target, int pick) {
		lockAim(target);
		Vec3d here = this.getPos();
		String key = "boss.arcane.archmage." + pick;
		switch (pick) {
			case METEORS -> {
				beginCast(world, METEORS, 22, key);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.8f, 0.6f);
			}
			case ORBS -> {
				beginCast(world, ORBS, 14, key);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.5f, 1.2f);
			}
			case STARFALL -> {
				spots.clear();
				int n = stage >= 2 ? 5 : 3;
				for (int i = 0; i < n; i++) {
					double a = this.random.nextDouble() * Math.PI * 2.0;
					double r = i == 0 ? 0.0 : 3.0 + this.random.nextDouble() * 3.0;
					double x = target.getX() + Math.cos(a) * r;
					double z = target.getZ() + Math.sin(a) * r;
					Vec3d spot = new Vec3d(x, floorY(world, x, target.getY(), z), z);
					spots.add(spot);
					telegraphs.add(Telegraph.disc(world, spot, 2.4, 18));
				}
				beginCast(world, STARFALL, 18, key);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.5f, 1.0f);
			}
			case BURST -> {
				telegraphs.add(Telegraph.disc(world, here, BURST_RADIUS, 12));
				beginCast(world, BURST, 12, key);
				burstCd = stage >= 1 ? 100 : 140;
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.6f, 0.8f);
			}
			default -> {
				telegraphs.add(Telegraph.disc(world, here, 2.2, 10));
				beginCast(world, BLINK, 10, key);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 0.6f);
			}
		}
	}

	private int baseCooldown() {
		return stage >= 2 ? 28 : 45;
	}

	@Override
	protected void castTick(ServerWorld world, LivingEntity target, double progress) {
		// Искры собираются над посохом; перед метеорами по арене проходит большое кольцо рун.
		world.spawnParticles(ModParticles.STORM_SPARK, this.getX(), this.getY() + 3.2, this.getZ(), 3, 0.6, 0.5, 0.6, 0.03);
		if (attack == METEORS && this.age % 5 == 0) {
			Fx.circle(world, this.getPos().add(0.0, 0.12, 0.0), 13.0, ModParticles.RUNE, 40);
		}
	}

	// ------------------------------------------------------------------
	// Удары
	// ------------------------------------------------------------------

	@Override
	protected void execute(ServerWorld world, int id) {
		switch (id) {
			case METEORS -> {
				int count = stage >= 2 ? 11 : 7;
				launchMeteors(world, count);
				cooldown = baseCooldown() + count * 3;
			}
			case ORBS -> {
				fireOrbs(world);
				cooldown = baseCooldown();
			}
			case STARFALL -> {
				starfall(world);
				cooldown = baseCooldown();
			}
			case BURST -> {
				burst(world);
				cooldown = 30;
			}
			default -> {
				blink(world);
			}
		}
	}

	private void launchMeteors(ServerWorld world, int count) {
		Vec3d center = this.getPos();
		LivingEntity target = this.getTarget();
		for (int i = 0; i < count; i++) {
			double a = this.random.nextDouble() * Math.PI * 2.0;
			Vec3d base;
			double r;
			if (i < 2 && target != null) {
				// два метеора падают рядом с игроком (не точно в него)
				base = target.getPos();
				r = 2.8 + this.random.nextDouble() * 1.2;
			} else {
				// остальные — вокруг самого босса
				base = center;
				r = 4.0 + this.random.nextDouble() * 9.0;
			}
			double x = base.x + Math.cos(a) * r;
			double z = base.z + Math.sin(a) * r;
			Vec3d spot = new Vec3d(x, floorY(world, x, center.y, z), z);
			Vec3d from = spot.add(8.0, 26.0, 5.0);
			ClassAbilities.later(i * 3, () -> Meteors.launch(world, this, spot, from, 18, 12.0f, 2.9, true, List.of()));
		}
	}

	private void fireOrbs(ServerWorld world) {
		LivingEntity target = this.getTarget();
		if (target == null) {
			return;
		}
		int n = stage >= 1 ? 5 : 3;
		for (int i = 0; i < n; i++) {
			double spread = Math.toRadians((i - (n - 1) / 2.0) * 14.0);
			ArrowEntity orb = new ArrowEntity(world, this, new ItemStack(Items.ARROW), null);
			orb.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
			Vec3d dir = target.getEyePos().subtract(orb.getPos()).rotateY((float) spread);
			orb.setVelocity(dir.x, dir.y + 0.4, dir.z, 0.9f, 0.0f);
			orb.setDamage(8.0);
			world.spawnEntity(orb);
			ClassAbilities.homing(orb, target);
			ClassAbilities.trail(orb, ModParticles.STORM_SPARK);
		}
		world.spawnParticles(ModParticles.ARCANE_SPARK, this.getX(), this.getEyeY(), this.getZ(), 20, 0.6, 0.4, 0.6, 0.1);
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 1.4f);
	}

	private void starfall(ServerWorld world) {
		for (Vec3d spot : new ArrayList<>(spots)) {
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.setPosition(spot);
				bolt.setCosmetic(true);
				world.spawnEntity(bolt);
			}
			for (PlayerEntity p : playersNear(world, spot, 2.6)) {
				hit(world, p, 13.0f, 0.6, spot.x, spot.z);
			}
			world.spawnParticles(ModParticles.STORM_SPARK, spot.x, spot.y + 0.6, spot.z, 30, 1.0, 1.4, 1.0, 0.25);
			Fx.circle(world, spot.add(0.0, 0.2, 0.0), 2.0, ModParticles.ARCANE_SPARK, 16);
		}
	}

	/** Взрыв вокруг мага: сильно отбрасывает всех, кто подошёл вплотную. */
	private void burst(ServerWorld world) {
		Vec3d c = this.getPos();
		for (PlayerEntity p : playersNear(world, c, BURST_RADIUS)) {
			hit(world, p, 8.0f, 2.4, c.x, c.z);
		}
		for (int k = 0; k < 6; k++) {
			final int step = k;
			ClassAbilities.later(k, () -> {
				Fx.circle(world, c.add(0.0, 0.3, 0.0), 1.0 + step * 1.0, ModParticles.STORM_SPARK, 14 + step * 5);
				Fx.circle(world, c.add(0.0, 0.3, 0.0), 1.0 + step * 1.0, ParticleTypes.CLOUD, 8 + step * 3);
			});
		}
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.HOSTILE, 1.6f, 1.1f);
	}

	/** Телепорт на 9–13 блоков от игрока; сразу после него — залп сфер. Перезарядка 12 секунд. */
	private void blink(ServerWorld world) {
		LivingEntity target = this.getTarget();
		cooldown = 12;
		if (target == null) {
			return;
		}
		for (int attempt = 0; attempt < 14; attempt++) {
			double a = this.random.nextDouble() * Math.PI * 2.0;
			double r = 9.0 + this.random.nextDouble() * 4.0;
			double x = target.getX() + Math.cos(a) * r;
			double z = target.getZ() + Math.sin(a) * r;
			double y = floorY(world, x, this.getY(), z);
			BlockPos bp = BlockPos.ofFloored(x, y, z);
			boolean free = passable(world, bp) && passable(world, bp.up()) && passable(world, bp.up(2)) && !passable(world, bp.down());
			if (free && this.getPos().squaredDistanceTo(x, y, z) > 36.0) {
				Fx.spiral(world, this.getPos(), 0.8, 2.6, 2.5, 30, ModParticles.STORM_SPARK);
				this.refreshPositionAfterTeleport(x, y, z);
				Fx.spiral(world, this.getPos(), 0.8, 2.6, 2.5, 30, ModParticles.ARCANE_SPARK);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.8f);
				blinkCd = stage >= 1 ? 180 : 240;
				volleyNext = true;
				return;
			}
		}
		blinkCd = 60;
	}

	// ------------------------------------------------------------------
	// Аура
	// ------------------------------------------------------------------

	@Override
	protected void aura(ServerWorld world) {
		double t = this.age * 0.05;
		Vec3d c = this.getPos();
		if (this.age % 4 == 0) {
			for (int i = 0; i < 10; i++) {
				double a = t * 1.2 + i * Math.PI * 2.0 / 10.0;
				world.spawnParticles(ModParticles.RUNE, c.x + Math.cos(a) * 2.5, c.y + 0.1, c.z + Math.sin(a) * 2.5, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (this.age % 2 == 0) {
			for (int k = 0; k < 3; k++) {
				double a = t * 2.2 + k * Math.PI * 2.0 / 3.0;
				double r = 1.9 + 0.3 * Math.sin(t * 3.0 + k);
				double y = 1.5 + 0.9 * Math.sin(t * 2.0 + k * 2.1);
				world.spawnParticles(ModParticles.STORM_SPARK, c.x + Math.cos(a) * r, c.y + y, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (this.age % 5 == 0) {
			world.spawnParticles(ModParticles.ARCANE_SPARK, c.x, c.y + 3.0, c.z, 1, 0.4, 0.1, 0.4, 0.01);
		}
	}
}
