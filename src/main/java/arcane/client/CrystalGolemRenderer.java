package arcane.client;

import arcane.ArcaneMod;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import net.minecraft.util.Identifier;

/** Использует модель зомби, но со своей текстурой. */
public class CrystalGolemRenderer extends ZombieEntityRenderer {
	private static final Identifier TEXTURE = ArcaneMod.id("textures/entity/crystal_golem.png");

	public CrystalGolemRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ZombieEntityRenderState state) {
		return TEXTURE;
	}
}
