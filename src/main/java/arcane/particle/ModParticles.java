package arcane.particle;

import arcane.ArcaneMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Собственные частицы мода (текстуры — assets/arcane/textures/particle, описание — assets/arcane/particles). */
public final class ModParticles {
	private ModParticles() {
	}

	/** Фиолетовая мерцающая искра (магия). */
	public static final SimpleParticleType ARCANE_SPARK = register("arcane_spark");
	/** Голубая грозовая искра с зигзагом молнии. */
	public static final SimpleParticleType STORM_SPARK = register("storm_spark");
	/** Руна — используется в кругах-предупреждениях и магических печатях. */
	public static final SimpleParticleType RUNE = register("rune");
	/** Кристальный блик (голем). */
	public static final SimpleParticleType CRYSTAL_GLINT = register("crystal_glint");
	/** Огненный метеоритный шар. */
	public static final SimpleParticleType METEOR_FLAME = register("meteor_flame");
	/** Золотая пыль (корона и золото босса). */
	public static final SimpleParticleType GOLD_DUST = register("gold_dust");

	private static SimpleParticleType register(String name) {
		return Registry.register(Registries.PARTICLE_TYPE, ArcaneMod.id(name), FabricParticleTypes.simple());
	}

	/** Вызывается при запуске мода, чтобы все частицы зарегистрировались. */
	public static void register() {
	}
}
