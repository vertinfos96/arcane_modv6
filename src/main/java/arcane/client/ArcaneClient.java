package arcane.client;

import arcane.ArcaneMod;
import arcane.data.ArcaneData;
import arcane.entity.ModEntities;
import arcane.item.StaffItem;
import arcane.item.Spells;
import arcane.net.AbilityPayload;
import arcane.net.DodgePayload;
import arcane.net.HudPayload;
import arcane.net.SyncPayload;
import arcane.particle.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.particle.EndRodParticle;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ArcaneClient implements ClientModInitializer {

	/** Последние данные прокачки, полученные от сервера. */
	public static ArcaneData data = ArcaneData.EMPTY;

	// Данные для полосок на экране (приходят от сервера).
	public static int mana;
	public static int maxMana;
	public static int stamina;
	public static int maxStamina;
	public static int spell;
	public static int cooldowns;

	private static KeyBinding skillsKey;
	private static KeyBinding dodgeKey;
	private static KeyBinding ability1Key;
	private static KeyBinding ability2Key;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.CRYSTAL_GOLEM, CrystalGolemRenderer::new);
		EntityRendererRegistry.register(ModEntities.ARCANE_SKELETON, ArcaneSkeletonRenderer::new);
		EntityRendererRegistry.register(ModEntities.ARCANE_WARDEN, ArcaneWardenRenderer::new);
		EntityRendererRegistry.register(ModEntities.ARCHMAGE, ArchmageRenderer::new);

		skillsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.arcane.skills", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.arcane"));

		dodgeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.arcane.dodge", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "category.arcane"));
		ability1Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.arcane.ability1", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.arcane"));
		ability2Key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.arcane.ability2", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.arcane"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (ability1Key.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					ClientPlayNetworking.send(new AbilityPayload(1));
				}
			}
			while (ability2Key.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					ClientPlayNetworking.send(new AbilityPayload(2));
				}
			}
			while (dodgeKey.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					double yaw = Math.toRadians(client.player.getYaw());
					double fwd = (client.options.forwardKey.isPressed() ? 1 : 0) - (client.options.backKey.isPressed() ? 1 : 0);
					double side = (client.options.rightKey.isPressed() ? 1 : 0) - (client.options.leftKey.isPressed() ? 1 : 0);
					double x = -Math.sin(yaw) * fwd - Math.cos(yaw) * side;
					double z = Math.cos(yaw) * fwd - Math.sin(yaw) * side;
					ClientPlayNetworking.send(new DodgePayload((int) Math.round(x * 100), (int) Math.round(z * 100)));
				}
			}
			while (skillsKey.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					client.setScreen(new SkillScreen());
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) ->
				data = new ArcaneData(payload.xps(), payload.ranks(), 0));

		ClientPlayNetworking.registerGlobalReceiver(HudPayload.ID, (payload, context) -> {
			mana = payload.mana();
			maxMana = payload.maxMana();
			stamina = payload.stamina();
			maxStamina = payload.maxStamina();
			spell = payload.spell();
			cooldowns = payload.cooldowns();
		});

		// Собственные частицы: искры/молнии «живут» дольше (как end rod), руны и пламя — короче (как огонь).
		ParticleFactoryRegistry particles = ParticleFactoryRegistry.getInstance();
		particles.register(ModParticles.ARCANE_SPARK, EndRodParticle.Factory::new);
		particles.register(ModParticles.STORM_SPARK, EndRodParticle.Factory::new);
		particles.register(ModParticles.RUNE, FlameParticle.Factory::new);
		particles.register(ModParticles.CRYSTAL_GLINT, FlameParticle.Factory::new);
		particles.register(ModParticles.METEOR_FLAME, FlameParticle.Factory::new);
		particles.register(ModParticles.GOLD_DUST, FlameParticle.Factory::new);

		HudRenderCallback.EVENT.register(ArcaneClient::renderHud);
	}

	private static void bar(DrawContext ctx, int x, int y, int w, int h, int value, int max, int color, int back) {
		ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xCC000000);
		ctx.fill(x, y, x + w, y + h, back);
		int filled = max <= 0 ? 0 : Math.round(w * Math.min(1.0f, value / (float) max));
		ctx.fill(x, y, x + filled, y + h, color);
		ctx.fill(x, y, x + filled, y + 1, 0x55FFFFFF);
	}

	/** Полоски маны и выносливости, название заклинания и перезарядки умений. */
	private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden || maxMana <= 0) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		int x = 8;
		int w = 110;
		int h = ctx.getScaledWindowHeight();
		int yMana = h - 46;
		int yStam = h - 34;

		bar(ctx, x, yMana, w, 8, mana, maxMana, 0xFF3C7BFF, 0xFF1A2A5C);
		bar(ctx, x, yStam, w, 8, stamina, maxStamina, stamina < 20 ? 0xFFFF8A33 : 0xFF45D36B, 0xFF1F4A2C);
		ctx.drawTextWithShadow(tr, Text.translatable("hud.arcane.mana", mana, maxMana), x + w + 5, yMana, 0xFF9FC0FF);
		ctx.drawTextWithShadow(tr, Text.translatable("hud.arcane.stamina", stamina, maxStamina), x + w + 5, yStam,
				stamina < 20 ? 0xFFFFB066 : 0xFF9BE8B0);

		ItemStack held = mc.player.getMainHandStack();
		if (held.getItem() instanceof StaffItem) {
			ctx.drawTextWithShadow(tr, Text.translatable("hud.arcane.spell", Spells.name(spell)), x, yMana - 12, 0xFFE0B0FF);
		} else if (held.isIn(ItemTags.SWORDS) || held.isOf(Items.BOW)) {
			int r = cooldowns / 100;
			int z = cooldowns % 100;
			Text rt = r > 0 ? Text.literal(r + " c") : Text.translatable("hud.arcane.ready");
			Text zt = z > 0 ? Text.literal(z + " c") : Text.translatable("hud.arcane.ready");
			ctx.drawTextWithShadow(tr, Text.translatable("hud.arcane.cooldowns", rt, zt), x, yMana - 12, 0xFFFFE08A);
		}
	}
}
