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
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Арканный Страж — рыцарь с клинком. Бой ближний, атаки как для мечника:
 * Сокрушающий круг, Рывок, Веер клинка, Комбо из трёх ударов, Прыжок с ударом.
 * Перед каждой атакой на земле растёт заполненная фигура (круг/линия/сектор), удар идёт туда,
 * куда босс смотрел в начале замаха.
 */
public class ArcaneWardenEntity extends ArcaneBossEntity {

	private static final int SLAM = 0;
	private static final int CHARGE = 1;
	private static final int SWEEP = 2;
	private static final int COMBO = 3;
	private static final int LEAP = 4;

	private static final double SLAM_RADIUS = 6.5;
	private static final double CHARGE_LENGTH = 14.0;
	private static final double SWEEP_RADIUS = 7.0;
	private static final double LEAP_RADIUS = 4.8;

	private int comboHits = 0;
	private int lastAttack = -1;
	private Vec3d leapSpot = Vec3d.ZERO;

	public ArcaneWardenEntity(EntityType<? extends net.minecraft.entity.mob.ZombieEntity> type, World world) {
		super(type, world, Text.translatable("entity.arcane.arcane_warden"),
				Text.translatable("boss.arcane.subtitle"), BossBar.Color.PURPLE);
		// Броня и корона нарисованы на текстуре, в руке — Клинок Стража.
		this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.WARDEN_BLADE));
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return net.minecraft.entity.mob.ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.MAX_HEALTH, 300.0)
				.add(EntityAttributes.ATTACK_DAMAGE, 14.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.27)
				.add(EntityAttributes.ARMOR, 10.0)
				.add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.SCALE, 1.8);
	}

	@Override
	protected void ensureGear() {
		if (!this.getMainHandStack().isOf(ModItems.WARDEN_BLADE)) {
			this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.WARDEN_BLADE));
		}
	}

	@Override
	protected ParticleEffect deathParticle() {
		return ModParticles.GOLD_DUST;
	}

	// ------------------------------------------------------------------
	// Выбор атаки
	// ------------------------------------------------------------------

	@Override
	protected void think(ServerWorld world, LivingEntity target) {
		if (target == null) {
			return;
		}
		float ratio = this.getHealth() / this.getMaxHealth();
		if (ratio < 0.66f && stage < 1) {
			stage = 1;
			summon(world, ModEntities.CRYSTAL_GOLEM, 2);
		}
		if (ratio < 0.33f && stage < 2) {
			stage = 2;
			summon(world, ModEntities.CRYSTAL_GOLEM, 3);
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		double dist = Math.sqrt(this.squaredDistanceTo(target));
		if (dist > 45.0) {
			return;
		}
		// Порядок: круг, рывок, веер, комбо, прыжок.
		int[] weights = dist < 6.0 ? new int[]{25, 10, 25, 30, 10}
				: dist < 14.0 ? new int[]{10, 30, 10, 15, 35}
				: new int[]{0, 35, 0, 0, 65};
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		int roll = this.random.nextInt(total);
		int pick = LEAP;
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0) {
				pick = i;
				break;
			}
		}
		if (pick == lastAttack && weights[(pick + 1) % 5] > 0) {
			pick = (pick + 1) % 5;
		}
		lastAttack = pick;
		startAttack(world, target, pick);
	}

	private void startAttack(ServerWorld world, LivingEntity target, int pick) {
		Vec3d dir = lockAim(target);
		Vec3d here = this.getPos();
		String key = "boss.arcane.warden." + pick;
		switch (pick) {
			case SLAM -> {
				telegraphs.add(Telegraph.disc(world, here, SLAM_RADIUS, 18));
				beginCast(world, SLAM, 18, key);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.5f, 0.5f);
			}
			case CHARGE -> {
				telegraphs.add(Telegraph.line(world, here, dir, CHARGE_LENGTH, 2.6, 16));
				beginCast(world, CHARGE, 16, key);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 1.5f, 0.6f);
			}
			case SWEEP -> {
				telegraphs.add(Telegraph.cone(world, here, dir, SWEEP_RADIUS, 14));
				beginCast(world, SWEEP, 14, key);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 0.5f);
			}
			case COMBO -> {
				comboHits = 0;
				telegraphs.add(Telegraph.cone(world, here, dir, SWEEP_RADIUS - 0.5, 14));
				beginCast(world, COMBO, 14, key);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 0.6f);
			}
			default -> {
				// Прыжок: место приземления фиксируется сейчас — можно успеть отойти.
				leapSpot = new Vec3d(target.getX(), floorY(world, target.getX(), target.getY(), target.getZ()), target.getZ());
				telegraphs.add(Telegraph.disc(world, leapSpot, LEAP_RADIUS, 16));
				beginCast(world, LEAP, 16, key);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 1.5f, 0.4f);
			}
		}
	}

	private int baseCooldown() {
		return stage >= 2 ? 28 : 45;
	}

	// ------------------------------------------------------------------
	// Удары
	// ------------------------------------------------------------------

	@Override
	protected void execute(ServerWorld world, int id) {
		switch (id) {
			case SLAM -> {
				doSlam(world);
				cooldown = baseCooldown();
			}
			case CHARGE -> {
				doCharge(world);
				cooldown = baseCooldown();
			}
			case SWEEP -> {
				doSweep(world, SWEEP_RADIUS, 14.0f);
				cooldown = baseCooldown();
			}
			case COMBO -> {
				doSweep(world, SWEEP_RADIUS - 0.5, 11.0f);
				comboHits++;
				LivingEntity target = this.getTarget();
				if (comboHits < 3 && target != null) {
					// Следующий удар комбо: направление фиксируется заново, замах короче.
					Vec3d dir = lockAim(target);
					telegraphs.add(Telegraph.cone(world, this.getPos(), dir, SWEEP_RADIUS - 0.5, 10));
					beginCast(world, COMBO, 10, "boss.arcane.warden.3");
				} else {
					cooldown = baseCooldown() + 10;
				}
			}
			default -> {
				doLeap(world);
				cooldown = baseCooldown();
			}
		}
	}

	private void doSlam(ServerWorld world) {
		for (PlayerEntity p : playersNear(world, this.getPos(), SLAM_RADIUS)) {
			hit(world, p, 15.0f, 1.5, this.getX(), this.getZ());
		}
		Vec3d c = this.getPos().add(0.0, 0.15, 0.0);
		for (int k = 0; k < 6; k++) {
			final int step = k;
			ClassAbilities.later(k, () -> {
				Fx.circle(world, c, 1.0 + step * 1.1, ModParticles.GOLD_DUST, 12 + step * 6);
				Fx.circle(world, c, 1.0 + step * 1.1, ParticleTypes.CLOUD, 8 + step * 3);
			});
		}
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.3, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.HOSTILE, 1.5f, 0.7f);
	}

	private void doCharge(ServerWorld world) {
		Vec3d start = this.getPos();
		Vec3d end = start;
		for (double t = 1.0; t <= CHARGE_LENGTH; t += 1.0) {
			Vec3d p = start.add(aimDir.multiply(t));
			BlockPos bp = BlockPos.ofFloored(p);
			if (!passable(world, bp) || !passable(world, bp.up())) {
				break;
			}
			end = p;
		}
		for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, new Box(start, end).expand(2.5, 3.0, 2.5),
				pl -> pl.isAlive() && !pl.isSpectator())) {
			if (segmentDistance(p.getPos(), start, end) <= 1.6) {
				hit(world, p, 20.0f, 1.4, start.x, start.z);
			}
		}
		Fx.line(world, start.add(0.0, 1.0, 0.0), end.add(0.0, 1.0, 0.0), ParticleTypes.SWEEP_ATTACK, 1.4);
		Fx.line(world, start.add(0.0, 0.4, 0.0), end.add(0.0, 0.4, 0.0), ModParticles.GOLD_DUST, 0.7);
		this.refreshPositionAfterTeleport(end.x, end.y, end.z);
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 1.6f, 0.5f);
		world.spawnParticles(ParticleTypes.EXPLOSION, end.x, end.y + 0.5, end.z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	private void doSweep(ServerWorld world, double radius, float damage) {
		Vec3d c = this.getPos();
		for (PlayerEntity p : playersNear(world, c, radius)) {
			Vec3d to = p.getPos().subtract(c).multiply(1.0, 0.0, 1.0);
			if (to.lengthSquared() < 0.01 || aimDir.dotProduct(to.normalize()) >= 0.5) {
				hit(world, p, damage, 1.3, c.x, c.z);
			}
		}
		for (int a = -60; a <= 60; a += 10) {
			Vec3d d = aimDir.rotateY((float) Math.toRadians(a));
			for (double r = 1.5; r <= radius; r += 1.5) {
				world.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x + d.x * r, c.y + 1.0, c.z + d.z * r, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.8f, 0.6f);
	}

	private void doLeap(ServerWorld world) {
		Vec3d from = this.getPos();
		Fx.line(world, from.add(0.0, 1.0, 0.0), leapSpot.add(0.0, 1.0, 0.0), ModParticles.GOLD_DUST, 0.8);
		this.refreshPositionAfterTeleport(leapSpot.x, leapSpot.y, leapSpot.z);
		for (PlayerEntity p : playersNear(world, leapSpot, LEAP_RADIUS)) {
			hit(world, p, 16.0f, 1.6, leapSpot.x, leapSpot.z);
		}
		for (int k = 0; k < 5; k++) {
			final int step = k;
			ClassAbilities.later(k, () -> Fx.circle(world, leapSpot.add(0.0, 0.2, 0.0), 1.0 + step * 1.0, ModParticles.GOLD_DUST, 12 + step * 5));
		}
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, leapSpot.x, leapSpot.y + 0.3, leapSpot.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.HOSTILE, 1.5f, 0.8f);
	}

	// ------------------------------------------------------------------
	// Аура
	// ------------------------------------------------------------------

	@Override
	protected void aura(ServerWorld world) {
		double t = this.age * 0.05;
		Vec3d c = this.getPos();
		if (this.age % 4 == 0) {
			for (int i = 0; i < 12; i++) {
				double a = t * 1.3 + i * Math.PI * 2.0 / 12.0;
				world.spawnParticles(ModParticles.RUNE, c.x + Math.cos(a) * 2.9, c.y + 0.1, c.z + Math.sin(a) * 2.9, 1, 0.0, 0.0, 0.0, 0.0);
			}
			for (int i = 0; i < 8; i++) {
				double a = -t * 1.9 + i * Math.PI * 2.0 / 8.0;
				world.spawnParticles(ModParticles.GOLD_DUST, c.x + Math.cos(a) * 1.8, c.y + 0.1, c.z + Math.sin(a) * 1.8, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (this.age % 2 == 0) {
			for (int k = 0; k < 4; k++) {
				double a = t * 2.4 + k * Math.PI / 2.0;
				double r = 2.3 + 0.35 * Math.sin(t * 3.0 + k);
				double y = 1.6 + 1.0 * Math.sin(t * 2.0 + k * 1.7);
				world.spawnParticles(ModParticles.GOLD_DUST, c.x + Math.cos(a) * r, c.y + y, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (this.age % 5 == 0) {
			world.spawnParticles(ModParticles.ARCANE_SPARK, c.x, c.y + 3.6, c.z, 1, 0.45, 0.1, 0.45, 0.01);
		}
	}
}
