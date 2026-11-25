package arcane.item;

import arcane.particle.ModParticles;
import arcane.data.Achievements;
import arcane.data.ArcaneData;
import arcane.data.ClassAbilities;
import arcane.data.Fx;
import arcane.data.Meteors;
import arcane.data.Resources;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Optional;

/** Реализация заклинаний посоха. */
public final class Spells {
	private Spells() {
	}

	public static Text name(int spell) {
		return Text.translatable("spell.arcane." + spell);
	}

	public static int baseCooldown(int spell) {
		return switch (spell) {
			case 0 -> 15;
			case 1 -> 50;
			case 2 -> 40;
			case 3 -> 100;
			case 4 -> 80;
			default -> 240;
		};
	}

	/** Стоимость заклинания в мане. */
	public static int manaCost(int spell) {
		return switch (spell) {
			case 0 -> 8;
			case 1 -> 15;
			case 2 -> 20;
			case 3 -> 25;
			case 4 -> 30;
			default -> 70;
		};
	}

	public static boolean cast(ServerPlayerEntity player, ServerWorld world, int spell, ArcaneData data, double bonus) {
		int cost = manaCost(spell);
		if (!Resources.spendMana(player, cost)) {
			return false;
		}
		boolean ok = castSpell(player, world, spell, data, bonus);
		if (!ok) {
			Resources.refundMana(player, cost);
		}
		return ok;
	}

	private static boolean castSpell(ServerPlayerEntity player, ServerWorld world, int spell, ArcaneData data, double bonus) {
		player.sendMessage(Text.translatable("message.arcane.cast", name(spell)), true);
		double mult = data.powerMultiplier() * bonus;
		return switch (spell) {
			case 0 -> fireBeam(player, world, mult);
			case 1 -> lightning(player, world, mult);
			case 2 -> teleport(player, world);
			case 3 -> heal(player, world, mult);
			case 4 -> frostNova(player, world, mult);
			default -> meteor(player, world, mult);
		};
	}

	/** Результат луча: куда он попал и в какое существо. */
	private record Beam(Vec3d end, LivingEntity target, boolean hitBlock, Vec3d dir) {
	}

	private static Beam trace(ServerPlayerEntity player, ServerWorld world, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d far = eye.add(look.multiply(range));
		BlockHitResult blockHit = world.raycast(new RaycastContext(eye, far,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
		Vec3d end = hitBlock ? blockHit.getPos() : far;

		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		Vec3d bestPos = end;
		Box area = new Box(eye, end).expand(1.0);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area, x -> x != player && x.isAlive())) {
			Optional<Vec3d> hit = e.getBoundingBox().expand(0.3).raycast(eye, end);
			if (hit.isPresent()) {
				double dist = eye.squaredDistanceTo(hit.get());
				if (dist < bestDist) {
					bestDist = dist;
					best = e;
					bestPos = hit.get();
				}
			}
		}
		if (best != null) {
			return new Beam(bestPos, best, false, look);
		}
		return new Beam(end, null, hitBlock, look);
	}

	private static void drawLine(ServerWorld world, Vec3d from, Vec3d to, ParticleEffect particle, double step) {
		Vec3d diff = to.subtract(from);
		double len = diff.length();
		if (len < 0.01) return;
		Vec3d dir = diff.multiply(1.0 / len);
		for (double t = 0; t < len; t += step) {
			Vec3d p = from.add(dir.multiply(t));
			world.spawnParticles(particle, p.x, p.y, p.z, 2, 0.04, 0.04, 0.04, 0.0);
		}
	}

	private static DamageSource source(ServerPlayerEntity player, ServerWorld world) {
		return world.getDamageSources().indirectMagic(player, player);
	}

	/** Заклинание 0: огненный луч. */
	private static boolean fireBeam(ServerPlayerEntity player, ServerWorld world, double mult) {
		Beam beam = trace(player, world, 24.0);
		Vec3d start = player.getEyePos().add(0, -0.25, 0).add(beam.dir().multiply(0.8));
		drawLine(world, start, beam.end(), ParticleTypes.FLAME, 0.4);
		Fx.sigil(world, start, beam.dir(), 0.6, ParticleTypes.FLAME, 12, 0.0);
		Fx.sigil(world, start, beam.dir(), 0.3, ParticleTypes.SMALL_FLAME, 8, 1.0);
		world.spawnParticles(ParticleTypes.EXPLOSION, beam.end().x, beam.end().y, beam.end().z, 1, 0.0, 0.0, 0.0, 0.0);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
		if (beam.target() != null) {
			beam.target().damage(world, source(player, world), (float) (6.0 * mult));
			beam.target().setOnFireForTicks(80);
			world.spawnParticles(ParticleTypes.LAVA, beam.end().x, beam.end().y, beam.end().z, 8, 0.3, 0.3, 0.3, 0.0);
		}
		return true;
	}

	/** Заклинание 1: молния в точку прицеливания. */
	private static boolean lightning(ServerPlayerEntity player, ServerWorld world, double mult) {
		Beam beam = trace(player, world, 32.0);
		Vec3d pos = beam.target() != null ? beam.target().getPos() : beam.end();
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setPosition(pos);
			bolt.setChanneler(player);
			world.spawnEntity(bolt);
		}
		if (beam.target() != null) {
			beam.target().damage(world, source(player, world), (float) (4.0 * mult));
		}
		for (int k = 1; k <= 3; k++) {
			Fx.circle(world, pos.add(0.0, 0.2, 0.0), k * 1.0, ParticleTypes.ELECTRIC_SPARK, 10 + k * 6);
		}
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1.0, pos.z, 40, 0.6, 1.2, 0.6, 0.3);
		world.spawnParticles(ModParticles.STORM_SPARK, pos.x, pos.y + 1.2, pos.z, 14, 0.5, 1.2, 0.5, 0.1);
		return true;
	}

	/** Заклинание 2: телепорт к точке прицеливания. */
	private static boolean teleport(ServerPlayerEntity player, ServerWorld world) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d far = eye.add(look.multiply(30.0));
		BlockHitResult hit = world.raycast(new RaycastContext(eye, far,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d target = hit.getType() == HitResult.Type.MISS ? far : hit.getPos().subtract(look.multiply(0.8));

		BlockPos safe = findSafe(world, target);
		if (safe == null) {
			player.sendMessage(Text.translatable("message.arcane.no_place"), true);
			return false;
		}
		world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
		Fx.spiral(world, player.getPos(), 0.8, 2.2, 3.0, 36, ParticleTypes.PORTAL);
		Fx.circle(world, player.getPos().add(0.0, 0.1, 0.0), 1.2, ModParticles.ARCANE_SPARK, 16);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
		player.requestTeleport(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
		player.fallDistance = 0;
		world.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.1);
		Fx.spiral(world, player.getPos(), 0.8, 2.2, 3.0, 36, ParticleTypes.REVERSE_PORTAL);
		Fx.circle(world, player.getPos().add(0.0, 0.1, 0.0), 1.2, ModParticles.ARCANE_SPARK, 16);
		return true;
	}

	private static boolean passable(ServerWorld world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	/** Ищет рядом с точкой место, где игрок поместится (2 блока свободно и пол под ногами). */
	private static BlockPos findSafe(ServerWorld world, Vec3d target) {
		BlockPos fallback = null;
		for (int dy = -2; dy <= 2; dy++) {
			BlockPos p = BlockPos.ofFloored(target.x, target.y + dy, target.z);
			if (passable(world, p) && passable(world, p.up())) {
				if (!passable(world, p.down())) {
					return p;
				}
				if (fallback == null) {
					fallback = p;
				}
			}
		}
		return fallback;
	}

	/** Заклинание 3: исцеление. */
	private static boolean heal(ServerPlayerEntity player, ServerWorld world, double mult) {
		player.heal((float) (6.0 * mult));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
		world.spawnParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.4, 0.5, 0.4, 0.02);
		for (int k = 0; k < 8; k++) {
			final int step = k;
			ClassAbilities.later(k * 2, () -> {
				Fx.spiral(world, player.getPos(), 0.9, 2.2, 1.5, 20, ParticleTypes.HAPPY_VILLAGER);
				Fx.circle(world, player.getPos().add(0.0, 0.1, 0.0), 0.5 + step * 0.15, ModParticles.ARCANE_SPARK, 12);
			});
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.6f);
		return true;
	}

	/** Заклинание 4: ледяная волна — урон и замедление всех монстров вокруг. */
	private static boolean frostNova(ServerPlayerEntity player, ServerWorld world, double mult) {
		Box area = player.getBoundingBox().expand(6.0);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area,
				x -> x != player && x.isAlive() && x instanceof Monster)) {
			e.damage(world, source(player, world), (float) (5.0 * mult));
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
		}
		world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(),
				90, 3.0, 0.8, 3.0, 0.02);
		for (int k = 0; k < 7; k++) {
			final int step = k;
			ClassAbilities.later(k, () -> {
				Vec3d c = player.getPos().add(0.0, 0.2, 0.0);
				Fx.circle(world, c, 1.0 + step * 0.85, ParticleTypes.SNOWFLAKE, 14 + step * 6);
				Fx.circle(world, c.add(0.0, 0.3, 0.0), 0.8 + step * 0.85, ParticleTypes.CLOUD, 8 + step * 3);
			});
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
		return true;
	}

	/** Точка, куда смотрит игрок (до первого блока, но не дальше range). */
	public static Vec3d aimPoint(ServerPlayerEntity player, ServerWorld world, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d far = eye.add(player.getRotationVec(1.0f).multiply(range));
		BlockHitResult hit = world.raycast(new RaycastContext(eye, far,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? far : hit.getPos();
	}

	/**
	 * Заклинание 5: метеор. С неба по диагонали летит настоящий огненный шар, на земле растёт красный круг,
	 * при ударе взрыв выбивает воронку как от TNT. Игрока взрыв не ранит — урон получают монстры вокруг.
	 */
	private static boolean meteor(ServerPlayerEntity player, ServerWorld world, double mult) {
		Vec3d target = aimPoint(player, world, 45.0);
		Vec3d back = player.getRotationVec(1.0f).multiply(1.0, 0.0, 1.0).normalize().multiply(-9.0);
		Vec3d from = target.add(back).add(0.0, 26.0, 0.0);
		List<LivingEntity> victims = world.getEntitiesByClass(LivingEntity.class, new Box(target, target).expand(8.0),
				x -> x != player && x.isAlive() && x instanceof Monster);
		Meteors.launch(world, player, target, from, 22, (float) (16.0 * mult), 3.2, false, victims);
		Fx.sigil(world, player.getEyePos().add(player.getRotationVec(1.0f).multiply(1.5)), player.getRotationVec(1.0f),
				1.2, ModParticles.RUNE, 20, 0.0);
		return true;
	}
}
