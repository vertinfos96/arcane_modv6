package arcane.client;

import arcane.ArcaneMod;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import net.minecraft.util.Identifier;

/** Модель зомби (увеличенная атрибутом размера) с текстурой мантии Архимага. */
public class ArchmageRenderer extends ZombieEntityRenderer {
	private static final Identifier TEXTURE = ArcaneMod.id("textures/entity/archmage.png");

	public ArchmageRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(ZombieEntityRenderState state) {
		return TEXTURE;
	}
}
