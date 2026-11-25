package arcane.data;

import arcane.block.ModBlocks;
import arcane.entity.Telegraph;
import arcane.particle.ModParticles;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Метеориты: настоящий огненный шар падает по диагонали и ровно к моменту, когда красный круг на земле
 * заполнится, врезается в землю. Взрыв выбивает воронку как от TNT. Используется и посохом игрока, и Архимагом.
 */
public final class Meteors {
	private Meteors() {
	}

	private static final class Flight {
		final FireballEntity ball;
		final LivingEntity owner;
		final Vec3d spot;
		final int total;
		final Telegraph telegraph;
		final float damage;
		final double radius;
		final boolean hurtsPlayers;
		final List<LivingEntity> victims;
		int age = 0;
		Vec3d last;

		Flight(FireballEntity ball, LivingEntity owner, Vec3d spot, int total, Telegraph telegraph, float damage,
			   double radius, boolean hurtsPlayers, List<LivingEntity> victims, Vec3d last) {
			this.ball = ball;
			this.owner = owner;
			this.spot = spot;
			this.total = total;
			this.telegraph = telegraph;
			this.damage = damage;
			this.radius = radius;
			this.hurtsPlayers = hurtsPlayers;
			this.victims = victims;
			this.last = last;
		}
	}

	private static final List<Flight> FLIGHTS = new ArrayList<>();

	/**
	 * Запускает метеорит.
	 * @param from старт в небе, spot — точка падения, flightTicks — время полёта (и роста круга),
	 * @param hurtsPlayers true — босс (бьёт игроков), false — игрок (бьёт монстров)
	 */
	public static void launch(ServerWorld world, LivingEntity owner, Vec3d spot, Vec3d from, int flightTicks,
							  float damage, double radius, boolean hurtsPlayers, List<LivingEntity> victims) {
		Vec3d dir = spot.subtract(from).normalize();
		FireballEntity ball = new FireballEntity(world, owner, dir, 0);
		ball.setPosition(from.x, from.y, from.z);
		world.spawnEntity(ball);
		Telegraph telegraph = Telegraph.disc(world, spot, radius + 0.8, flightTicks);
		FLIGHTS.add(new Flight(ball, owner, spot, flightTicks, telegraph, damage, radius, hurtsPlayers, victims, from));
		world.playSound(null, BlockPos.ofFloored(spot), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.6f, 0.5f);
	}

	/** Вызывается каждый тик сервера. */
	public static void tick() {
		FLIGHTS.removeIf(f -> {
			if (!(f.ball.getWorld() instanceof ServerWorld sw)) {
				return true;
			}
			f.age++;
			boolean removed = f.ball.isRemoved();
			if (!removed && f.age < f.total) {
				Vec3d pos = f.ball.getPos();
				f.last = pos;
				int left = f.total - f.age;
				f.ball.setVelocity(f.spot.subtract(pos).multiply(1.0 / Math.max(1, left)));
				f.ball.velocityModified = true;
				trail(sw, pos);
				return false;
			}
			Vec3d impact = removed ? f.last : f.spot;
			if (!removed) {
				f.ball.discard();
			}
			f.telegraph.remove();
			impact(sw, f, impact);
			return true;
		});
	}

	/** След метеорита: огонь, дым, искры. */
	private static void trail(ServerWorld sw, Vec3d p) {
		sw.spawnParticles(ModParticles.METEOR_FLAME, p.x, p.y, p.z, 10, 0.45, 0.45, 0.45, 0.02);
		sw.spawnParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 5, 0.4, 0.4, 0.4, 0.02);
		sw.spawnParticles(ModParticles.ARCANE_SPARK, p.x, p.y, p.z, 2, 0.5, 0.5, 0.5, 0.05);
		sw.spawnParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.0);
	}

	private static void impact(ServerWorld sw, Flight f, Vec3d pos) {
		sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 0.5, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
		sw.spawnParticles(ModParticles.METEOR_FLAME, pos.x, pos.y + 0.6, pos.z, 70, 1.8, 1.2, 1.8, 0.18);
		sw.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.6, pos.z, 40, 1.6, 1.2, 1.6, 0.08);
		for (int k = 0; k < 6; k++) {
			final int step = k;
			ClassAbilities.later(k, () -> {
				Fx.circle(sw, pos.add(0.0, 0.3, 0.0), 1.3 + step * 1.3, ModParticles.METEOR_FLAME, 16 + step * 6);
				Fx.circle(sw, pos.add(0.0, 0.3, 0.0), 1.0 + step * 1.3, ParticleTypes.LARGE_SMOKE, 10 + step * 4);
			});
		}
		sw.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.HOSTILE, 2.5f, 0.7f);

		double reach = f.radius * 1.7;
		Box area = new Box(pos, pos).expand(reach, reach, reach);
		if (f.hurtsPlayers) {
			for (PlayerEntity p : sw.getEntitiesByClass(PlayerEntity.class, area, pl -> pl.isAlive() && !pl.isSpectator())) {
				if (horizontal(p.getPos(), pos) <= reach) {
					p.damage(sw, sw.getDamageSources().mobAttack(f.owner), f.damage);
					p.takeKnockback(1.0, pos.x - p.getX(), pos.z - p.getZ());
				}
			}
		} else {
			for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, area,
					x -> x != f.owner && x.isAlive() && x instanceof Monster)) {
				e.damage(sw, f.owner instanceof ServerPlayerEntity sp
						? sw.getDamageSources().indirectMagic(sp, sp)
						: sw.getDamageSources().generic(), f.damage);
				e.setOnFireForTicks(100);
				e.takeKnockback(1.2, pos.x - e.getX(), pos.z - e.getZ());
			}
			if (f.owner instanceof ServerPlayerEntity player) {
				ClassAbilities.later(4, () -> {
					long dead = f.victims.stream().filter(e -> !e.isAlive()).count();
					if (dead >= 5) {
						Achievements.grant(player, "chunk_destroyer");
					}
				});
			}
		}
		if (ArcaneConfig.bossTerrainDamage) {
			crater(sw, pos, f.radius);
		}
	}

	private static double horizontal(Vec3d a, Vec3d b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Воронка как от TNT: убирает блоки в шаре (без выпадения предметов, алтари не трогает), блоки разлетаются частицами. */
	public static void crater(ServerWorld world, Vec3d c, double radius) {
		int ri = (int) Math.ceil(radius);
		int n = 0;
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dy = -ri; dy <= ri; dy++) {
				for (int dz = -ri; dz <= ri; dz++) {
					if (dx * dx + dy * dy + dz * dz > radius * radius) {
						continue;
					}
					BlockPos bp = BlockPos.ofFloored(c.x + dx, c.y - 0.6 + dy, c.z + dz);
					BlockState state = world.getBlockState(bp);
					if (state.isAir() || !state.getFluidState().isEmpty()
							|| state.isOf(ModBlocks.ARCANE_ALTAR) || state.isOf(ModBlocks.ARCHMAGE_ALTAR)) {
						continue;
					}
					if (state.getHardness(world, bp) < 0.0f) {
						continue;
					}
					if (n++ % 3 == 0) {
						world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
								bp.getX() + 0.5, bp.getY() + 0.7, bp.getZ() + 0.5, 6, 0.35, 0.35, 0.35, 0.3);
					}
					world.removeBlock(bp, false);
				}
			}
		}
	}
}
