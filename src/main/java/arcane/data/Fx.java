package arcane.data;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Помощники для красивых эффектов из частиц: круги, магические печати, спирали, линии. */
public final class Fx {
	private Fx() {
	}

	/** Горизонтальное кольцо. */
	public static void circle(ServerWorld w, Vec3d c, double r, ParticleEffect p, int points) {
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2.0 / points;
			w.spawnParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Кольцо в вертикальной плоскости, повёрнутой лицом к игроку (магическая печать). */
	public static void sigil(ServerWorld w, Vec3d center, Vec3d forward, double r, ParticleEffect p, int points, double rotation) {
		Vec3d right = forward.crossProduct(new Vec3d(0.0, 1.0, 0.0));
		if (right.lengthSquared() < 1.0E-4) {
			right = new Vec3d(1.0, 0.0, 0.0);
		}
		right = right.normalize();
		Vec3d up = right.crossProduct(forward).normalize();
		for (int i = 0; i < points; i++) {
			double a = rotation + i * Math.PI * 2.0 / points;
			Vec3d pt = center.add(right.multiply(Math.cos(a) * r)).add(up.multiply(Math.sin(a) * r));
			w.spawnParticles(p, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Спираль, поднимающаяся вверх. */
	public static void spiral(ServerWorld w, Vec3d base, double radius, double height, double turns, int points, ParticleEffect p) {
		for (int i = 0; i < points; i++) {
			double t = i / (double) points;
			double a = t * turns * Math.PI * 2.0;
			w.spawnParticles(p, base.x + Math.cos(a) * radius, base.y + t * height, base.z + Math.sin(a) * radius,
					1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Линия из частиц. */
	public static void line(ServerWorld w, Vec3d from, Vec3d to, ParticleEffect p, double step) {
		Vec3d diff = to.subtract(from);
		double len = diff.length();
		if (len < 0.01) {
			return;
		}
		Vec3d dir = diff.multiply(1.0 / len);
		for (double t = 0; t < len; t += step) {
			Vec3d pt = from.add(dir.multiply(t));
			w.spawnParticles(p, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}
}
