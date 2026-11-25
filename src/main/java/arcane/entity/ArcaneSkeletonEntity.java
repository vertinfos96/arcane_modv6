package arcane.entity;

import arcane.data.ClassAbilities;
import arcane.data.Fx;
import arcane.particle.ModParticles;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Арканный скелет-маг с арканным луком.
 * Способности: стреляет залпом из 3 стрел (каждый 4-й выстрел — огненный залп из 7)
 * и «Мерцание» — телепортируется назад, если враг подошёл близко.
 */
public class ArcaneSkeletonEntity extends SkeletonEntity {

	private int shots = 0;
	private int blinkReady = 0;

	public ArcaneSkeletonEntity(EntityType<? extends SkeletonEntity> type, World world) {
		super(type, world);
		this.equipStack(EquipmentSlot.MAINHAND, Gear.arcaneBow());
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return AbstractSkeletonEntity.createAbstractSkeletonAttributes()
				.add(EntityAttributes.MAX_HEALTH, 30.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.30);
	}

	@Override
	public void shootAt(LivingEntity target, float pullProgress) {
		super.shootAt(target, pullProgress); // обычная стрела
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		shots++;
		boolean fiery = shots % 4 == 0;
		int extra = fiery ? 6 : 2;
		for (int i = 0; i < extra; i++) {
			double side = (i % 2 == 0) ? 1.0 : -1.0;
			double spread = Math.toRadians(side * (7.0 + 7.0 * (i / 2)));
			ArrowEntity arrow = new ArrowEntity(world, this, new ItemStack(Items.ARROW), null);
			arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
			Vec3d from = arrow.getPos();
			Vec3d dir = target.getEyePos().subtract(from);
			double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
			Vec3d rotated = new Vec3d(dir.x, 0.0, dir.z).rotateY((float) spread);
			arrow.setVelocity(rotated.x, dir.y + horizontal * 0.2, rotated.z, 1.6f, 3.0f);
			if (fiery) {
				arrow.setOnFireForTicks(100);
			}
			world.spawnEntity(arrow);
			ClassAbilities.trail(arrow, fiery ? ParticleTypes.FLAME : ModParticles.ARCANE_SPARK);
		}
		// Каждый 5-й выстрел — сумеречный шар: медленный, самонаводящийся, со светящимся следом.
		if (shots % 5 == 0) {
			ArrowEntity orb = new ArrowEntity(world, this, new ItemStack(Items.ARROW), null);
			orb.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
			Vec3d dir = target.getEyePos().subtract(orb.getPos());
			orb.setVelocity(dir.x, dir.y, dir.z, 0.9f, 0.0f);
			world.spawnEntity(orb);
			ClassAbilities.homing(orb, target);
			ClassAbilities.trail(orb, ModParticles.ARCANE_SPARK);
		}
		world.spawnParticles(ModParticles.ARCANE_SPARK, this.getX(), this.getEyeY(), this.getZ(), 12, 0.4, 0.3, 0.4, 0.05);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		// Всегда держим арканный лук (внешний вид задаётся компонентом, это настоящий лук).
		if (this.age % 40 == 0) {
			ItemStack held = this.getMainHandStack();
			if (!held.isOf(Items.BOW) || !held.contains(DataComponentTypes.ITEM_MODEL)) {
				this.equipStack(EquipmentSlot.MAINHAND, Gear.arcaneBow());
			}
		}
		if (this.age % 4 == 0) {
			world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.25, 0.05, 0.25, 0.01);
			if (this.age % 16 == 0) {
				world.spawnParticles(ModParticles.ARCANE_SPARK, this.getX(), this.getY() + 1.8, this.getZ(), 3, 0.25, 0.3, 0.25, 0.0);
			}
		}
		// Мерцание: враг вплотную — отпрыгиваем на 7 блоков назад.
		LivingEntity target = this.getTarget();
		if (blinkReady > 0) {
			blinkReady--;
		}
		if (target != null && blinkReady == 0 && this.squaredDistanceTo(target) < 25.0) {
			if (blink(world, target)) {
				blinkReady = 140;
			}
		}
	}

	private boolean blink(ServerWorld world, LivingEntity target) {
		Vec3d away = this.getPos().subtract(target.getPos()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSquared() < 0.01) {
			return false;
		}
		Vec3d dest = this.getPos().add(away.normalize().multiply(7.0));
		for (int dy = 2; dy >= -3; dy--) {
			BlockPos p = BlockPos.ofFloored(dest.x, this.getY() + dy, dest.z);
			boolean free = world.getBlockState(p).getCollisionShape(world, p).isEmpty()
					&& world.getBlockState(p.up()).getCollisionShape(world, p.up()).isEmpty();
			boolean floor = !world.getBlockState(p.down()).getCollisionShape(world, p.down()).isEmpty();
			if (free && floor) {
				Vec3d from = this.getPos();
				Fx.spiral(world, from, 0.6, 2.0, 2.0, 24, ParticleTypes.PORTAL);
				this.refreshPositionAfterTeleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
				Fx.spiral(world, this.getPos(), 0.6, 2.0, 2.0, 24, ParticleTypes.REVERSE_PORTAL);
				world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 1.3f);
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean isAffectedByDaylight() {
		return false;
	}
}
