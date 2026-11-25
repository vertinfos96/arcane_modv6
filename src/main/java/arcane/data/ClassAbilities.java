package arcane.data;

import arcane.particle.ModParticles;
import arcane.ArcaneMod;
import arcane.item.StaffItem;
import arcane.item.Spells;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Способности Мечника и Лучника.
 * Клавиши (с оружием в руке):
 *  - Меч:  R — Вихрь клинков, Z — Рывок клинка;
 *  - Лук:  R — Град стрел,    Z — Ливень стрел;
 *  - Посох: R или Z — переключить заклинание.
 */
public final class ClassAbilities {
	private ClassAbilities() {
	}

	private static final Map<String, Long> COOLDOWNS = new HashMap<>();
	private static final List<PersistentProjectileEntity> STRAIGHT_ARROWS = new ArrayList<>();

	private record Trail(PersistentProjectileEntity arrow, ParticleEffect particle) {
	}

	private record Homing(PersistentProjectileEntity arrow, LivingEntity target) {
	}

	private static final List<Homing> HOMING = new ArrayList<>();
	private static final Map<UUID, Long> DODGE_UNTIL = new HashMap<>();

	/** Стрела-самонаводящийся шар: летит без падения и доворачивает к цели. */
	public static void homing(PersistentProjectileEntity arrow, LivingEntity target) {
		arrow.setNoGravity(true);
		HOMING.add(new Homing(arrow, target));
	}

	private static final List<Trail> TRAILS = new ArrayList<>();

	/** Стрела оставляет светящийся след в полёте. */
	public static void trail(PersistentProjectileEntity arrow, ParticleEffect particle) {
		TRAILS.add(new Trail(arrow, particle));
	}

	private static final class Delayed {
		int ticks;
		final Runnable action;

		Delayed(int ticks, Runnable action) {
			this.ticks = ticks;
			this.action = action;
		}
	}

	private static final List<Delayed> DELAYED = new ArrayList<>();

	/** Выполнить действие через указанное число тиков. */
	public static void later(int ticks, Runnable action) {
		DELAYED.add(new Delayed(ticks, action));
	}

	public static void register() {
		// Стрелы игрока: меткость, огонь, прямой полёт.
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof PersistentProjectileEntity arrow && !(entity instanceof TridentEntity)
					&& entity.age == 0 && arrow.getOwner() instanceof ServerPlayerEntity player) {
				ArcaneData d = ArcaneData.get(player);
				int sharp = d.rank(Skills.SHARP);
				if (sharp > 0) {
					arrow.setDamage(arrow.getDamage() + 0.5 * sharp);
				}
				if (d.rank(Skills.FIRE) > 0) {
					arrow.setOnFireForTicks(100);
				}
				if (d.rank(Skills.STRAIGHT) > 0) {
					arrow.setNoGravity(true);
					STRAIGHT_ARROWS.add(arrow);
				}
			}
		});

		// Грозовой клинок: шанс удара молнии при ударе мечом.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient || !(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)
					|| !(entity instanceof LivingEntity target)) {
				return ActionResult.PASS;
			}
			if (!sp.getMainHandStack().isIn(ItemTags.SWORDS)) {
				return ActionResult.PASS;
			}
			int storm = ArcaneData.get(sp).rank(Skills.STORM);
			if (storm > 0 && sw.getRandom().nextFloat() < 0.15f * storm) {
				later(2, () -> {
					if (!target.isAlive()) {
						return;
					}
					LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw, SpawnReason.TRIGGERED);
					if (bolt != null) {
						bolt.setPosition(target.getPos());
						bolt.setCosmetic(true);
						sw.spawnEntity(bolt);
					}
					target.damage(sw, sw.getDamageSources().playerAttack(sp), 6.0f + 2.0f * storm);
					target.setOnFireForTicks(60);
				});
			}
			return ActionResult.PASS;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickDelayed();
			tickArrows();
			tickTrails();
			Meteors.tick();
			tickHoming();
			Resources.tick(server);
		});

		// Во время переката игрок неуязвим (~0,4 с).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity p) {
				Long until = DODGE_UNTIL.get(p.getUuid());
				if (until != null && p.getWorld().getTime() < until) {
					return false;
				}
			}
			return true;
		});

		// После возрождения заново применяем бонусы навыков и сбрасываем ресурсы.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Resources.reset(newPlayer);
			ArcaneLogic.sync(newPlayer);
		});
	}

	private static void tickDelayed() {
		if (DELAYED.isEmpty()) {
			return;
		}
		List<Delayed> snapshot = new ArrayList<>(DELAYED);
		DELAYED.clear();
		for (Delayed d : snapshot) {
			d.ticks--;
			if (d.ticks <= 0) {
				d.action.run();
			} else {
				DELAYED.add(d);
			}
		}
	}

	private static void tickArrows() {
		STRAIGHT_ARROWS.removeIf(arrow -> {
			if (arrow.isRemoved()) {
				return true;
			}
			if (arrow.getWorld() instanceof ServerWorld sw) {
				sw.spawnParticles(ModParticles.ARCANE_SPARK, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			}
			if (arrow.age > 60) {
				arrow.discard();
				return true;
			}
			return false;
		});
	}

	private static void tickHoming() {
		HOMING.removeIf(h -> {
			PersistentProjectileEntity a = h.arrow();
			if (a.isRemoved() || a.age > 120 || !h.target().isAlive()) {
				return true;
			}
			Vec3d want = h.target().getEyePos().subtract(a.getPos()).normalize();
			a.setVelocity(a.getVelocity().multiply(0.82).add(want.multiply(0.18)));
			a.velocityModified = true;
			return false;
		});
	}

	private static void tickTrails() {
		TRAILS.removeIf(t -> {
			PersistentProjectileEntity arrow = t.arrow();
			if (arrow.isRemoved() || arrow.age > 120) {
				return true;
			}
			if (arrow.getWorld() instanceof ServerWorld sw) {
				sw.spawnParticles(t.particle(), arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			}
			return false;
		});
	}

	// ------------------------------------------------------------------
	// Клавиши способностей
	// ------------------------------------------------------------------

	/** key: 1 = R, 2 = Z. Что произойдёт, зависит от предмета в руке. */
	public static void handleKey(ServerPlayerEntity player, int key) {
		if (!(player.getWorld() instanceof ServerWorld world)) {
			return;
		}
		ItemStack stack = player.getMainHandStack();
		ArcaneData d = ArcaneData.get(player);

		if (stack.getItem() instanceof StaffItem) {
			ArcaneLogic.cycleSpell(player);
		} else if (stack.isIn(ItemTags.SWORDS)) {
			if (key == 1) {
				whirlwind(player, world, d);
			} else {
				dash(player, world, d);
			}
		} else if (stack.isOf(Items.BOW)) {
			if (key == 1) {
				hail(player, world, d);
			} else {
				rain(player, world, d);
			}
		}
	}

	private static boolean learned(ServerPlayerEntity player, ArcaneData d, int skill) {
		if (d.rank(skill) > 0) {
			return true;
		}
		player.sendMessage(Text.translatable("message.arcane.not_learned"), true);
		return false;
	}

	private static boolean ready(ServerPlayerEntity player, ServerWorld world, int skill, int cooldown) {
		String key = player.getUuid() + ":" + skill;
		long now = world.getTime();
		long until = COOLDOWNS.getOrDefault(key, 0L);
		if (now < until) {
			player.sendMessage(Text.translatable("message.arcane.cooldown", (until - now + 19) / 20), true);
			return false;
		}
		COOLDOWNS.put(key, now + cooldown);
		return true;
	}

	/** Сколько секунд осталось до готовности умения (0 — готово). */
	public static int cooldownSeconds(ServerPlayerEntity player, int skill) {
		Long until = COOLDOWNS.get(player.getUuid() + ":" + skill);
		if (until == null) {
			return 0;
		}
		long left = until - player.getWorld().getTime();
		return left <= 0 ? 0 : (int) ((left + 19) / 20);
	}

	/** Перекат (клавиша X): рывок в направлении движения и короткая неуязвимость. */
	public static void dodge(ServerPlayerEntity player, int dx, int dz) {
		if (!(player.getWorld() instanceof ServerWorld world) || player.hasVehicle()) {
			return;
		}
		if (!Resources.canStamina(player, 18) || !ready(player, world, 100, 24)) {
			return;
		}
		Vec3d dir = new Vec3d(dx / 100.0, 0.0, dz / 100.0);
		if (dir.lengthSquared() < 0.01) {
			dir = player.getRotationVec(1.0f).multiply(-1.0, 0.0, -1.0);
		}
		if (dir.lengthSquared() < 0.01) {
			return;
		}
		Resources.spendStamina(player, 18);
		dir = dir.normalize();
		player.setVelocity(dir.x * 1.2, 0.2, dir.z * 1.2);
		player.velocityModified = true;
		player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
		DODGE_UNTIL.put(player.getUuid(), world.getTime() + 8);
		Fx.circle(world, player.getPos().add(0.0, 0.1, 0.0), 0.9, ParticleTypes.CLOUD, 12);
		world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.7f);
	}

	/** Каким классом убит монстр: 0 маг, 1 мечник, 2 лучник (для распределения опыта). */
	public static int classOf(ServerPlayerEntity player, DamageSource source) {
		if (source.getSource() instanceof PersistentProjectileEntity) {
			return 2;
		}
		ItemStack held = player.getMainHandStack();
		if (held.getItem() instanceof StaffItem) {
			return 0;
		}
		if (held.isOf(Items.BOW)) {
			return 2;
		}
		if (source.isOf(DamageTypes.INDIRECT_MAGIC) || source.isOf(DamageTypes.MAGIC)
				|| source.isOf(DamageTypes.LIGHTNING_BOLT) || source.isOf(DamageTypes.FIREBALL)
				|| source.isOf(DamageTypes.EXPLOSION) || source.isOf(DamageTypes.PLAYER_EXPLOSION)) {
			return 0;
		}
		return 1;
	}

	// ------------------------------------------------------------------
	// Мечник
	// ------------------------------------------------------------------

	/** R с мечом: вихрь клинков — три вращающихся лезвия, два удара, враги подлетают вверх. */
	private static void whirlwind(ServerPlayerEntity player, ServerWorld world, ArcaneData d) {
		if (!learned(player, d, Skills.WHIRL) || !Resources.canStamina(player, 30)
				|| !ready(player, world, Skills.WHIRL, 120)) {
			return;
		}
		Resources.spendStamina(player, 30);
		player.sendMessage(Text.translatable("skill.arcane." + Skills.WHIRL), true);
		float damage = 6.0f + 2.0f * d.rank(Skills.BLADE);
		int firstHits = spinHit(player, world, damage, true);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.4f, 0.6f);
		for (int k = 0; k < 10; k++) {
			final int step = k;
			later(k, () -> {
				double r = 1.2 + step * 0.35;
				double rot = step * 0.75;
				Vec3d c = player.getPos().add(0.0, 0.9, 0.0);
				for (int blade = 0; blade < 3; blade++) {
					double a0 = rot + blade * Math.PI * 2.0 / 3.0;
					for (int t = 0; t < 7; t++) {
						double aa = a0 - t * 0.1;
						world.spawnParticles(t == 0 ? ParticleTypes.SWEEP_ATTACK : ParticleTypes.CRIT,
								c.x + Math.cos(aa) * r, c.y + step * 0.04, c.z + Math.sin(aa) * r, 1, 0.0, 0.0, 0.0, 0.0);
					}
				}
				Fx.circle(world, c.add(0.0, -0.8, 0.0), r, ParticleTypes.CLOUD, 14);
				if (step == 5) {
					world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 0.9f);
					int second = spinHit(player, world, damage * 0.6f, false);
					if (firstHits + second >= 3) {
						Achievements.grant(player, "blade_dancer");
					}
				}
			});
		}
	}

	/** Один оборот: урон и подбрасывание всех монстров вокруг. Возвращает число пострадавших. */
	private static int spinHit(ServerPlayerEntity player, ServerWorld world, float damage, boolean launch) {
		int hits = 0;
		Box area = player.getBoundingBox().expand(4.0, 1.0, 4.0);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area,
				x -> x != player && x.isAlive() && x instanceof Monster)) {
			e.damage(world, world.getDamageSources().playerAttack(player), damage);
			e.takeKnockback(1.0, player.getX() - e.getX(), player.getZ() - e.getZ());
			if (launch) {
				e.addVelocity(0.0, 0.55, 0.0);
			}
			hits++;
		}
		return hits;
	}

	/** Z с мечом: рывок вперёд, режущий всех монстров на пути. */
	private static void dash(ServerPlayerEntity player, ServerWorld world, ArcaneData d) {
		if (!learned(player, d, Skills.DASH)) {
			return;
		}
		Vec3d start = player.getPos();
		Vec3d look = player.getRotationVec(1.0f).multiply(1.0, 0.0, 1.0).normalize();
		Vec3d end = start;
		for (double t = 1.0; t <= 9.0; t += 1.0) {
			Vec3d p = start.add(look.multiply(t));
			BlockPos bp = BlockPos.ofFloored(p);
			if (!passable(world, bp) || !passable(world, bp.up())) {
				break;
			}
			end = p;
		}
		if (end.squaredDistanceTo(start) < 1.0) {
			player.sendMessage(Text.translatable("message.arcane.no_room"), true);
			return;
		}
		if (!Resources.canStamina(player, 25) || !ready(player, world, Skills.DASH, 80)) {
			return;
		}
		Resources.spendStamina(player, 25);
		float damage = 8.0f + 2.0f * d.rank(Skills.BLADE);
		Box path = new Box(start, end).expand(1.5, 1.0, 1.5);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, path,
				x -> x != player && x.isAlive() && x instanceof Monster)) {
			e.damage(world, world.getDamageSources().playerAttack(player), damage);
			e.takeKnockback(1.2, start.x - e.getX(), start.z - e.getZ());
		}
		Vec3d delta = end.subtract(start);
		int steps = (int) (delta.length() * 3.0);
		for (int i = 0; i <= steps; i++) {
			Vec3d p = start.add(delta.multiply(i / (double) Math.max(1, steps)));
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 1.0, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			world.spawnParticles(ParticleTypes.CRIT, p.x, p.y + 1.0, p.z, 3, 0.2, 0.4, 0.2, 0.05);
			world.spawnParticles(ParticleTypes.CLOUD, p.x, p.y + 0.2, p.z, 1, 0.1, 0.0, 0.1, 0.0);
		}
		Fx.spiral(world, start, 0.7, 2.0, 2.0, 24, ModParticles.ARCANE_SPARK);
		player.requestTeleport(end.x, end.y, end.z);
		player.fallDistance = 0.0f;
		for (int k = 1; k <= 3; k++) {
			Fx.circle(world, end.add(0.0, 0.2, 0.0), k * 0.9, ParticleTypes.SWEEP_ATTACK, 8 + k * 4);
		}
		world.playSound(null, BlockPos.ofFloored(end), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.3f);
	}

	private static boolean passable(ServerWorld world, BlockPos pos) {
		return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	// ------------------------------------------------------------------
	// Лучник
	// ------------------------------------------------------------------

	private static ArrowEntity newArrow(ServerPlayerEntity player, ServerWorld world) {
		ArrowEntity arrow = new ArrowEntity(world, player, new ItemStack(Items.ARROW), null);
		arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
		return arrow;
	}

	/** R с луком: град стрел — 5 залпов подряд, перед игроком вращается магическая печать. */
	private static void hail(ServerPlayerEntity player, ServerWorld world, ArcaneData d) {
		if (!learned(player, d, Skills.HAIL) || !Resources.canStamina(player, 40)
				|| !ready(player, world, Skills.HAIL, 160)) {
			return;
		}
		Resources.spendStamina(player, 40);
		player.sendMessage(Text.translatable("skill.arcane." + Skills.HAIL), true);
		for (int wave = 0; wave < 5; wave++) {
			final int w = wave;
			later(1 + wave * 3, () -> {
				Vec3d look = player.getRotationVec(1.0f);
				Vec3d center = player.getEyePos().add(look.multiply(2.2));
				Fx.sigil(world, center, look, 1.7, ModParticles.ARCANE_SPARK, 28, w * 0.5);
				Fx.sigil(world, center, look, 0.9, ModParticles.ARCANE_SPARK, 14, -w * 0.5);
				Fx.sigil(world, center, look, 0.3, ParticleTypes.CRIT, 6, 0.0);
				float pitchShift = (w % 2 == 0) ? -4.0f : 4.0f;
				for (int col = -2; col <= 2; col++) {
					ArrowEntity arrow = newArrow(player, world);
					arrow.setVelocity(player, player.getPitch() + pitchShift, player.getYaw() + col * 7.0f, 0.0f, 3.2f, 0.6f);
					world.spawnEntity(arrow);
					trail(arrow, ModParticles.ARCANE_SPARK);
				}
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS,
						1.3f, 0.8f + w * 0.1f);
			});
		}
		Achievements.grant(player, "arrow_storm");
	}

	/** Z с луком: ливень стрел — сначала круг расходится по земле, затем 12 волн стрел с неба. */
	private static void rain(ServerPlayerEntity player, ServerWorld world, ArcaneData d) {
		if (!learned(player, d, Skills.RAIN) || !Resources.canStamina(player, 55)
				|| !ready(player, world, Skills.RAIN, 300)) {
			return;
		}
		Resources.spendStamina(player, 55);
		player.sendMessage(Text.translatable("skill.arcane." + Skills.RAIN), true);
		Vec3d target = Spells.aimPoint(player, world, 45.0);
		for (int t = 0; t < 20; t += 2) {
			final int tt = t;
			later(t + 1, () -> {
				Fx.circle(world, target.add(0.0, 0.15, 0.0), 1.0 + tt * 0.2, ModParticles.ARCANE_SPARK, 26);
				Fx.circle(world, target.add(0.0, 0.15, 0.0), 0.6 + tt * 0.1, ModParticles.RUNE, 14);
				world.spawnParticles(ParticleTypes.CRIT, target.x, target.y + 0.5 + tt * 0.5, target.z, 4, 0.2, 0.2, 0.2, 0.02);
			});
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.5f, 0.4f);
		for (int wave = 0; wave < 12; wave++) {
			later(20 + wave * 2, () -> {
				for (int i = 0; i < 3; i++) {
					double ox = (world.getRandom().nextDouble() - 0.5) * 8.0;
					double oz = (world.getRandom().nextDouble() - 0.5) * 8.0;
					ArrowEntity arrow = newArrow(player, world);
					arrow.setPosition(target.x + ox, target.y + 18.0 + world.getRandom().nextDouble() * 4.0, target.z + oz);
					arrow.setVelocity(0.0, -2.8, 0.0);
					world.spawnEntity(arrow);
					trail(arrow, ModParticles.ARCANE_SPARK);
					world.spawnParticles(ParticleTypes.CRIT, target.x + ox, target.y + 0.2, target.z + oz, 3, 0.1, 0.1, 0.1, 0.1);
				}
				world.playSound(null, BlockPos.ofFloored(target), SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.8f, 1.2f);
			});
		}
	}

	// ------------------------------------------------------------------
	// Пассивные бонусы
	// ------------------------------------------------------------------

	/** Вызывается, когда игрок убил монстра. */
	public static void onKill(ServerPlayerEntity player, DamageSource source) {
		ArcaneData d = ArcaneData.get(player);
		int blood = d.rank(Skills.BLOOD);
		if (blood > 0) {
			player.heal(2.0f * blood);
		}
		int rush = d.rank(Skills.RUSH);
		if (rush > 0 && source.getSource() instanceof PersistentProjectileEntity) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60 * rush, 1));
		}
	}

	/** Применяет пассивные бонусы навыков к характеристикам игрока. */
	public static void applyAttributes(ServerPlayerEntity player) {
		ArcaneData d = ArcaneData.get(player);
		setModifier(player, EntityAttributes.ATTACK_DAMAGE, "skill_blade",
				1.0 * d.rank(Skills.BLADE), EntityAttributeModifier.Operation.ADD_VALUE);
		setModifier(player, EntityAttributes.ARMOR, "skill_iron",
				2.0 * d.rank(Skills.IRON), EntityAttributeModifier.Operation.ADD_VALUE);
		setModifier(player, EntityAttributes.MAX_HEALTH, "skill_vitality",
				2.0 * d.rank(Skills.VITALITY), EntityAttributeModifier.Operation.ADD_VALUE);
		setModifier(player, EntityAttributes.MOVEMENT_SPEED, "skill_swift",
				0.10 * d.rank(Skills.SWIFT), EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(player, EntityAttributes.SAFE_FALL_DISTANCE, "skill_light",
				2.0 * d.rank(Skills.LIGHT), EntityAttributeModifier.Operation.ADD_VALUE);
	}

	static void setModifier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, String name,
									double value, EntityAttributeModifier.Operation operation) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null) {
			return;
		}
		Identifier id = ArcaneMod.id(name);
		instance.removeModifier(id);
		if (value != 0.0) {
			instance.addTemporaryModifier(new EntityAttributeModifier(id, value, operation));
		}
	}
}
