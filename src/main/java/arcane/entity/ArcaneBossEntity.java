package arcane.entity;

import arcane.data.ClassAbilities;
import arcane.data.Fx;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Общая основа боссов: полоса здоровья, заголовок при появлении, цикл «замах — предупреждение на земле — удар».
 * Во время замаха босс замирает и НЕ поворачивается: удар идёт туда, куда он смотрел в начале замаха
 * (никакой автонаводки), так что от него можно увернуться.
 */
public abstract class ArcaneBossEntity extends ZombieEntity {

	protected final ServerBossBar bossBar;
	private final Text bossName;
	private final Text bossSubtitle;

	protected int stage = 0;
	protected int cooldown = 80;
	protected int castLeft = 0;
	protected int castTotal = 1;
	protected int attack = -1;
	protected Vec3d aimDir = new Vec3d(0.0, 0.0, 1.0);
	protected final List<Telegraph> telegraphs = new ArrayList<>();
	private boolean announced = false;

	protected ArcaneBossEntity(EntityType<? extends ZombieEntity> type, World world, Text name, Text subtitle, BossBar.Color color) {
		super(type, world);
		this.bossName = name;
		this.bossSubtitle = subtitle;
		this.bossBar = new ServerBossBar(name, color, BossBar.Style.NOTCHED_10);
		this.setPersistent();
	}

	// ------------------------------------------------------------------
	// Что должны реализовать конкретные боссы
	// ------------------------------------------------------------------

	/** Постоянная аура из частиц. */
	protected abstract void aura(ServerWorld world);

	/** Проверка и возврат оружия в руку. */
	protected abstract void ensureGear();

	/** Поведение вне замаха: фазы, перемещение, выбор следующей атаки. */
	protected abstract void think(ServerWorld world, LivingEntity target);

	/** Удар: вызывается, когда замах закончился. */
	protected abstract void execute(ServerWorld world, int attackId);

	/** Подсказка по частицам в замахе (по умолчанию ничего). */
	protected void castTick(ServerWorld world, LivingEntity target, double progress) {
	}

	/** Частица для эффекта смерти. */
	protected abstract ParticleEffect deathParticle();

	// ------------------------------------------------------------------
	// Цикл
	// ------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		bossBar.setPercent(this.getHealth() / this.getMaxHealth());
		aura(world);
		if (this.age % 40 == 0) {
			ensureGear();
		}
		if (!announced && this.age > 5) {
			announced = true;
			announce(world);
		}
		if (!this.isAlive()) {
			return;
		}
		LivingEntity target = this.getTarget();
		if (castLeft > 0) {
			castLeft--;
			this.getNavigation().stop();
			castTick(world, target, 1.0 - Math.max(0, castLeft) / (double) castTotal);
			if (castLeft <= 0) {
				int finished = attack;
				endCast();
				execute(world, finished);
			}
			return;
		}
		think(world, target);
	}

	/** Начинает замах: босс замирает, светится, взмахивает рукой. Направление удара фиксируется заранее. */
	protected void beginCast(ServerWorld world, int id, int windup, String nameKey) {
		attack = id;
		castLeft = windup;
		castTotal = windup;
		this.setAiDisabled(true);
		this.setGlowing(true);
		this.swingHand(Hand.MAIN_HAND);
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.squaredDistanceTo(this) < 50.0 * 50.0) {
				p.sendMessage(Text.translatable(nameKey), true);
			}
		}
	}

	protected void endCast() {
		this.setAiDisabled(false);
		this.setGlowing(false);
		for (Telegraph t : telegraphs) {
			t.remove();
		}
		telegraphs.clear();
	}

	/** Единичный вектор от босса к цели (на плоскости); поворачивает босса лицом к цели. */
	protected Vec3d lockAim(LivingEntity target) {
		if (target != null) {
			this.lookAtEntity(target, 90.0f, 90.0f);
		}
		Vec3d to = target == null ? Vec3d.ZERO : target.getPos().subtract(this.getPos()).multiply(1.0, 0.0, 1.0);
		aimDir = to.lengthSquared() < 0.01 ? new Vec3d(0.0, 0.0, 1.0) : to.normalize();
		return aimDir;
	}

	// ------------------------------------------------------------------
	// Помощники
	// ------------------------------------------------------------------

	protected List<PlayerEntity> playersNear(ServerWorld world, Vec3d pos, double radius) {
		List<PlayerEntity> list = new ArrayList<>();
		for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class,
				new Box(pos, pos).expand(radius, 4.0, radius), pl -> pl.isAlive() && !pl.isSpectator())) {
			double dx = p.getX() - pos.x;
			double dz = p.getZ() - pos.z;
			if (dx * dx + dz * dz <= radius * radius) {
				list.add(p);
			}
		}
		return list;
	}

	protected void hit(ServerWorld world, PlayerEntity p, float damage, double knockback, double fromX, double fromZ) {
		p.damage(world, world.getDamageSources().mobAttack(this), damage);
		p.takeKnockback(knockback, fromX - p.getX(), fromZ - p.getZ());
	}

	protected static boolean passable(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return state.getCollisionShape(world, pos).isEmpty();
	}

	/** Высота пола рядом с точкой (от +3 до -6 блоков). */
	protected static double floorY(ServerWorld world, double x, double y, double z) {
		for (int dy = 3; dy >= -6; dy--) {
			BlockPos p = BlockPos.ofFloored(x, y + dy, z);
			if (passable(world, p) && !passable(world, p.down())) {
				return p.getY();
			}
		}
		return y;
	}

	protected static double segmentDistance(Vec3d p, Vec3d a, Vec3d b) {
		Vec3d pp = new Vec3d(p.x, 0.0, p.z);
		Vec3d aa = new Vec3d(a.x, 0.0, a.z);
		Vec3d ab = new Vec3d(b.x, 0.0, b.z).subtract(aa);
		double len2 = ab.lengthSquared();
		if (len2 < 1.0E-6) {
			return pp.distanceTo(aa);
		}
		double t = Math.max(0.0, Math.min(1.0, pp.subtract(aa).dotProduct(ab) / len2));
		return pp.distanceTo(aa.add(ab.multiply(t)));
	}

	/** Призывает мобов вокруг босса. */
	protected void summon(ServerWorld world, EntityType<? extends MobEntity> type, int count) {
		for (int i = 0; i < count; i++) {
			MobEntity mob = type.create(world, SpawnReason.MOB_SUMMONED);
			if (mob != null) {
				double angle = this.random.nextDouble() * Math.PI * 2.0;
				mob.setPosition(this.getX() + Math.cos(angle) * 4.0, this.getY(), this.getZ() + Math.sin(angle) * 4.0);
				world.spawnEntity(mob);
			}
		}
		for (int k = 0; k < 5; k++) {
			final int step = k;
			ClassAbilities.later(k * 2, () -> Fx.circle(world, this.getPos().add(0.0, 0.2, 0.0), 1.5 + step * 1.6,
					arcane.particle.ModParticles.STORM_SPARK, 20 + step * 6));
		}
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0f, 1.2f);
	}

	/** Крупный заголовок при появлении босса. */
	private void announce(ServerWorld world) {
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.squaredDistanceTo(this) < 80.0 * 80.0) {
				p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 50, 20));
				p.networkHandler.sendPacket(new SubtitleS2CPacket(bossSubtitle));
				p.networkHandler.sendPacket(new TitleS2CPacket(bossName));
			}
		}
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		super.onDeath(damageSource);
		if (this.getWorld() instanceof ServerWorld world) {
			Vec3d c = this.getPos();
			ParticleEffect particle = deathParticle();
			for (int k = 0; k < 10; k++) {
				final int step = k;
				ClassAbilities.later(k * 2, () -> {
					Fx.circle(world, c.add(0.0, 0.2 + step * 0.3, 0.0), 1.0 + step * 0.7, particle, 24);
					world.spawnParticles(particle, c.x, c.y + 1.5 + step * 0.3, c.z, 12, 1.2, 0.8, 1.2, 0.2);
				});
			}
			for (int i = 0; i < 3; i++) {
				LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
				if (bolt != null) {
					double a = i * Math.PI * 2.0 / 3.0;
					bolt.setPosition(c.x + Math.cos(a) * 4.0, c.y, c.z + Math.sin(a) * 4.0);
					bolt.setCosmetic(true);
					world.spawnEntity(bolt);
				}
			}
			world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5f, 0.6f);
		}
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		bossBar.addPlayer(player);
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		bossBar.clearPlayers();
		for (Telegraph t : telegraphs) {
			t.remove();
		}
		telegraphs.clear();
	}

	@Override
	public boolean isBaby() {
		return false;
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
