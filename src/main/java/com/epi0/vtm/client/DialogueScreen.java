package com.epi0.vtm.client;

import com.epi0.vtm.network.DialogueChoicePayload;
import com.epi0.vtm.network.DialogueNodePayload;
import com.epi0.vtm.npc.Attitude;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The conversation window: the speaker's portrait, what they say (typed out), and what you can answer.
 * Holds no game state; the server sends each line and each choice, and the screen sends back an option index.
 */
public class DialogueScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int PORTRAIT = 62;
    private static final int PAD = 8;
    private static final int PANEL = 0xE0181015;
    private static final int BORDER = 0xFF7A1F1F;
    private static final int TEXT = 0xFFE6E0E0;
    private static final int MUTED = 0xFF9A9090;
    private static final int CHARS_PER_TICK = 3;

    private DialogueNodePayload.Node node;
    private int revealed;
    private int top;
    private int left;
    private int panelHeight;
    private List<FormattedCharSequence> lines = List.of();
    private List<FormattedCharSequence> noteLines = List.of();
    private boolean closing;

    private DialogueScreen(DialogueNodePayload.Node node) {
        super(node.speaker());
        this.node = node;
    }

    /** Handles a payload from the server on the client thread. */
    public static void receive(DialogueNodePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.node().isEmpty()) {
            if (mc.screen instanceof DialogueScreen screen) {
                screen.closing = true;
                mc.setScreen(null);
            }
            return;
        }
        DialogueNodePayload.Node node = payload.node().get();
        if (mc.screen instanceof DialogueScreen screen && screen.node.npcId() == node.npcId()) {
            screen.node = node;
            screen.revealed = 0;
            screen.rebuildWidgets();
        } else {
            mc.setScreen(new DialogueScreen(node));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        lines = font.split(node.text(), WIDTH - PORTRAIT - PAD * 3);
        noteLines = node.note().map(note -> font.split(note, WIDTH - PORTRAIT - PAD * 3)).orElse(List.of());
        List<Integer> visible = new java.util.ArrayList<>();
        for (int i = 0; i < node.choices().size(); i++) {
            if (!node.choices().get(i).text().getString().isEmpty()) {
                visible.add(i);
            }
        }
        boolean terminal = visible.isEmpty();
        int textHeight = Math.max(PORTRAIT, 14 + lines.size() * 10 + (noteLines.isEmpty() ? 0 : 4 + noteLines.size() * 10));
        int rows = terminal ? 1 : visible.size();
        panelHeight = PAD * 2 + textHeight + 6 + rows * 22;
        left = (width - WIDTH) / 2;
        top = height - panelHeight - 20;

        int y = top + PAD * 2 + textHeight + 6;
        if (terminal) {
            addRenderableWidget(Button.builder(Component.translatable("gui.vtm.dialogue.leave"), b -> leave())
                    .bounds(left + PAD, y, WIDTH - PAD * 2, 20).build());
            return;
        }
        int number = 1;
        for (int index : visible) {
            DialogueNodePayload.Choice choice = node.choices().get(index);
            Button button = Button.builder(Component.literal(number++ + ". ").append(choice.text()), b -> choose(index))
                    .bounds(left + PAD, y, WIDTH - PAD * 2, 20).build();
            button.active = choice.enabled();
            choice.hint().ifPresent(hint -> button.setTooltip(Tooltip.create(hint)));
            addRenderableWidget(button);
            y += 22;
        }
    }

    private void choose(int index) {
        PacketDistributor.sendToServer(new DialogueChoicePayload(index));
    }

    private void leave() {
        PacketDistributor.sendToServer(new DialogueChoicePayload(-1));
        closing = true;
        onClose();
    }

    @Override
    public void onClose() {
        if (!closing) {
            PacketDistributor.sendToServer(new DialogueChoicePayload(-1));
            closing = true;
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int wanted = key - GLFW.GLFW_KEY_1;
            int n = 0;
            for (int i = 0; i < node.choices().size(); i++) {
                if (node.choices().get(i).text().getString().isEmpty()) {
                    continue;
                }
                if (n++ == wanted) {
                    if (node.choices().get(i).enabled()) {
                        choose(i);
                    }
                    return true;
                }
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void tick() {
        revealed += CHARS_PER_TICK;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left, top, left + WIDTH, top + panelHeight, PANEL);
        graphics.renderOutline(left, top, WIDTH, panelHeight, BORDER);

        int px = left + PAD;
        int py = top + PAD;
        graphics.fill(px, py, px + PORTRAIT, py + PORTRAIT, 0xFF0C080A);
        graphics.renderOutline(px, py, PORTRAIT, PORTRAIT, BORDER);
        LivingEntity speaker = minecraft.level != null && minecraft.level.getEntity(node.npcId()) instanceof LivingEntity living ? living : null;
        if (speaker != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, px, py, px + PORTRAIT, py + PORTRAIT,
                    26, 0.1F, mouseX, mouseY, speaker);
        }

        int tx = px + PORTRAIT + PAD;
        graphics.drawString(font, node.speaker(), tx, py, 0xFF000000 | node.color());
        Attitude attitude = Attitude.values()[Math.floorMod(node.attitude(), Attitude.values().length)];
        Component mood = Component.translatable("gui.vtm.dialogue.mood", Component.translatable("attitude.vtm." + attitude.name().toLowerCase(java.util.Locale.ROOT)),
                (node.rapport() > 0 ? "+" : "") + node.rapport());
        graphics.drawString(font, mood, left + WIDTH - PAD - font.width(mood), py, MUTED);

        int remaining = revealed;
        int y = py + 14;
        for (FormattedCharSequence line : lines) {
            if (remaining <= 0) {
                break;
            }
            graphics.drawString(font, clip(line, remaining), tx, y, TEXT);
            remaining -= length(line);
            y += 10;
        }
        y += 4;
        for (FormattedCharSequence line : noteLines) {
            if (remaining <= 0) {
                break;
            }
            graphics.drawString(font, line, tx, y, MUTED);
            y += 10;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No dimming or blur: the conversation happens in the world.
    }

    private static int length(FormattedCharSequence sequence) {
        int[] n = {0};
        sequence.accept((i, style, cp) -> {
            n[0]++;
            return true;
        });
        return n[0];
    }

    private static FormattedCharSequence clip(FormattedCharSequence sequence, int chars) {
        return sink -> {
            int[] seen = {0};
            return sequence.accept((i, style, cp) -> seen[0]++ < chars && sink.accept(i, style, cp));
        };
    }
}
