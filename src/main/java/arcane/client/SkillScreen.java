package arcane.client;

import arcane.data.ArcaneData;
import arcane.data.Skills;
import arcane.net.UpgradePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Окно прокачки навыков (клавиша K). Стрелки < > переключают класс: Маг / Мечник / Лучник. */
public class SkillScreen extends Screen {
	private static final int ROW_H = 23;
	private static final int MAX_ROWS = 8;
	private static final int[] CLASS_COLORS = {0xB080FF, 0xFF7070, 0x70D070};

	/** Запоминаем вкладку между открытиями окна. */
	private static int lastTab = 0;

	private int tab = lastTab;
	private final List<ButtonWidget> buttons = new ArrayList<>();

	public SkillScreen() {
		super(Text.translatable("screen.arcane.skills"));
	}

	private void switchTab(int dir) {
		tab = (tab + dir + Skills.CLASS_COUNT) % Skills.CLASS_COUNT;
		lastTab = tab;
	}

	private int baseY() {
		return Math.max(0, (this.height - 270) / 2);
	}

	@Override
	protected void init() {
		buttons.clear();
		int cx = this.width / 2;
		int base = baseY();
		int left = cx - 175;
		int rowsTop = base + 60;

		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> switchTab(-1))
				.dimensions(cx - 110, base + 38, 24, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> switchTab(1))
				.dimensions(cx + 86, base + 38, 24, 20).build());

		for (int i = 0; i < MAX_ROWS; i++) {
			final int row = i;
			ButtonWidget button = ButtonWidget.builder(Text.literal("+"), b ->
							ClientPlayNetworking.send(new UpgradePayload(Skills.first(tab) + row)))
					.dimensions(left + 310, rowsTop + i * ROW_H, 30, 20)
					.build();
			buttons.add(button);
			this.addDrawableChild(button);
		}
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> this.close())
				.dimensions(cx - 50, rowsTop + MAX_ROWS * ROW_H + 4, 100, 20)
				.build());
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_LEFT) {
			switchTab(-1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_RIGHT) {
			switchTab(1);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		ArcaneData d = ArcaneClient.data;
		int first = Skills.first(tab);
		int size = Skills.size(tab);
		for (int i = 0; i < buttons.size(); i++) {
			ButtonWidget b = buttons.get(i);
			b.visible = i < size;
			b.active = i < size && d.canUpgrade(first + i);
		}
		super.render(context, mouseX, mouseY, delta);

		int cx = this.width / 2;
		int base = baseY();
		int left = cx - 175;
		int rowsTop = base + 60;

		context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, base + 8, 0xFFFFFF);

		int level = d.level(tab);
		int next = ArcaneData.xpForLevel(level + 1);
		Text header = level >= ArcaneData.MAX_LEVEL
				? Text.translatable("screen.arcane.header_max", level, d.points(tab))
				: Text.translatable("screen.arcane.header", level, d.xp(tab), next, d.points(tab));
		context.drawCenteredTextWithShadow(this.textRenderer, header, cx, base + 22, 0xFFD966);

		Text className = Text.translatable("class.arcane." + tab);
		context.drawCenteredTextWithShadow(this.textRenderer, className, cx, base + 44, CLASS_COLORS[tab]);

		for (int i = 0; i < size; i++) {
			int skill = first + i;
			int y = rowsTop + i * ROW_H;
			Text name = Text.translatable("skill.arcane." + skill);
			context.drawTextWithShadow(this.textRenderer, name, left, y, 0xFFFFFF);
			Text desc = Text.translatable("skill.arcane." + skill + ".desc");
			context.drawTextWithShadow(this.textRenderer, desc, left, y + 10, 0xAAAAAA);
			Text rank = Text.literal(d.rank(skill) + " / " + Skills.maxRank(skill));
			int color = d.rank(skill) > 0 ? 0x66FF66 : 0xFF8888;
			context.drawTextWithShadow(this.textRenderer, rank, left + 265, y + 6, color);
			if (d.level(tab) < Skills.requiredLevel(skill) && d.rank(skill) == 0) {
				Text req = Text.translatable("screen.arcane.need_level", Skills.requiredLevel(skill));
				context.drawTextWithShadow(this.textRenderer, req, left + 215, y + 6, 0xFFAA55);
			}
		}
	}
}
