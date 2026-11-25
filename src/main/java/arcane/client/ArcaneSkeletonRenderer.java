package arcane.client;

import arcane.ArcaneMod;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.SkeletonEntityRenderer;
import net.minecraft.client.render.entity.state.SkeletonEntityRenderState;
import net.minecraft.util.Identifier;

/** Использует модель скелета, но со своей текстурой. */
public class ArcaneSkeletonRenderer extends SkeletonEntityRenderer {
	private static final Identifier TEXTURE = ArcaneMod.id("textures/entity/arcane_skeleton.png");

	public ArcaneSkeletonRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(SkeletonEntityRenderState state) {
		return TEXTURE;
	}
}
