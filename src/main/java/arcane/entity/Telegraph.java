package arcane.entity;

import arcane.data.ClassAbilities;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Предупреждение об атаке на земле: настоящие плоские фигуры (не частицы).
 * Контур показывает всю зону удара, а красная заливка растёт внутри него за время замаха.
 * Когда заливка достигает края — срабатывает удар. Фигуры — «дисплеи» с плоскими моделями,
 * их размер плавно анимируется на клиенте.
 */
public final class Telegraph {
	private final ServerWorld world;
	private final List<DisplayEntity.ItemDisplayEntity> parts = new ArrayList<>();

	private Telegraph(ServerWorld world) {
		this.world = world;
	}

	/** Заполненный круг: контур по краю и заливка, растущая от центра. */
	public static Telegraph disc(ServerWorld world, Vec3d center, double radius, int ticks) {
		Telegraph t = new Telegraph(world);
		float d = (float) (radius * 2.0);
		t.spawn(center, 0.03, "telegraph_ring", d, d, 0.0f, Vec3d.ZERO);
		DisplayEntity.ItemDisplayEntity fill = t.spawn(center, 0.05, "telegraph_disc", 0.1f, 0.1f, 0.0f, Vec3d.ZERO);
		t.grow(fill, d, d, 0.0f, Vec3d.ZERO, ticks);
		t.expireAfter(ticks + 8);
		return t;
	}

	/** Полоса от start в направлении dir (dir — единичный вектор на плоскости): заливка бежит от старта к концу. */
	public static Telegraph line(ServerWorld world, Vec3d start, Vec3d dir, double length, double width, int ticks) {
		Telegraph t = new Telegraph(world);
		float yaw = (float) Math.atan2(dir.x, dir.z);
		t.spawn(start, 0.03, "telegraph_frame", (float) width, (float) length, yaw, dir.multiply(length / 2.0));
		DisplayEntity.ItemDisplayEntity fill = t.spawn(start, 0.05, "telegraph_line", (float) width, 0.1f, yaw, dir.multiply(0.05));
		t.grow(fill, (float) width, (float) length, yaw, dir.multiply(length / 2.0), ticks);
		t.expireAfter(ticks + 8);
		return t;
	}

	/** Сектор 120° радиуса radius вперёд от origin по направлению dir. */
	public static Telegraph cone(ServerWorld world, Vec3d origin, Vec3d dir, double radius, int ticks) {
		Telegraph t = new Telegraph(world);
		float yaw = (float) Math.atan2(dir.x, dir.z);
		float d = (float) (radius * 2.0);
		t.spawn(origin, 0.03, "telegraph_cone_frame", d, d, yaw, Vec3d.ZERO);
		DisplayEntity.ItemDisplayEntity fill = t.spawn(origin, 0.05, "telegraph_cone", 0.1f, 0.1f, yaw, Vec3d.ZERO);
		t.grow(fill, d, d, yaw, Vec3d.ZERO, ticks);
		t.expireAfter(ticks + 8);
		return t;
	}

	/** Убирает все фигуры. */
	public void remove() {
		for (DisplayEntity.ItemDisplayEntity e : parts) {
			if (!e.isRemoved()) {
				e.discard();
			}
		}
		parts.clear();
	}

	private void expireAfter(int ticks) {
		ClassAbilities.later(ticks, this::remove);
	}

	private DisplayEntity.ItemDisplayEntity spawn(Vec3d pos, double lift, String item, float sx, float sz, float yaw, Vec3d translation) {
		DisplayEntity.ItemDisplayEntity e = EntityType.ITEM_DISPLAY.create(world, SpawnReason.TRIGGERED);
		if (e == null) {
			return null;
		}
		e.setPosition(pos.x, pos.y + lift, pos.z);
		NbtCompound nbt = new NbtCompound();
		e.writeNbt(nbt);
		NbtCompound stack = new NbtCompound();
		stack.putString("id", "arcane:" + item);
		stack.putInt("count", 1);
		nbt.put("item", stack);
		nbt.putString("item_display", "none");
		nbt.put("transformation", transform(sx, sz, yaw, translation));
		NbtCompound brightness = new NbtCompound();
		brightness.putInt("sky", 15);
		brightness.putInt("block", 15);
		nbt.put("brightness", brightness);
		nbt.putFloat("view_range", 3.0f);
		e.readNbt(nbt);
		world.spawnEntity(e);
		parts.add(e);
		return e;
	}

	/** Через 2 тика (когда клиент уже получил начальный вид) запускает плавное изменение размера. */
	private void grow(DisplayEntity.ItemDisplayEntity e, float sx, float sz, float yaw, Vec3d translation, int ticks) {
		if (e == null) {
			return;
		}
		ClassAbilities.later(2, () -> {
			if (e.isRemoved()) {
				return;
			}
			NbtCompound nbt = new NbtCompound();
			e.writeNbt(nbt);
			nbt.put("transformation", transform(sx, sz, yaw, translation));
			nbt.putInt("start_interpolation", 0);
			nbt.putInt("interpolation_duration", Math.max(1, ticks - 2));
			e.readNbt(nbt);
		});
	}

	private static NbtCompound transform(float sx, float sz, float yaw, Vec3d translation) {
		NbtCompound t = new NbtCompound();
		t.put("translation", floats((float) translation.x, (float) translation.y, (float) translation.z));
		t.put("scale", floats(sx, 1.0f, sz));
		t.put("left_rotation", floats(0.0f, (float) Math.sin(yaw / 2.0), 0.0f, (float) Math.cos(yaw / 2.0)));
		t.put("right_rotation", floats(0.0f, 0.0f, 0.0f, 1.0f));
		return t;
	}

	private static NbtList floats(float... values) {
		NbtList list = new NbtList();
		for (float v : values) {
			list.add(NbtFloat.of(v));
		}
		return list;
	}
}
