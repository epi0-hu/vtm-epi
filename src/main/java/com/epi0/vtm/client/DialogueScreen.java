package com.epi0.vtm.client;

import com.epi0.vtm.client.ui.Draw;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.network.DialogueChoicePayload;
import com.epi0.vtm.network.DialogueNodePayload;
import com.epi0.vtm.npc.Attitude;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final int ROW = 17;
    private static final int TEXT = Theme.TEXT;
    private static final int MUTED = Theme.MUTED;
    private static final int CHARS_PER_TICK = 3;

    private DialogueNodePayload.Node node;
    private int revealed;
    private int top;
    private int left;
    private int panelHeight;
    private List<FormattedCharSequence> lines = List.of();
    private List<FormattedCharSequence> noteLines = List.of();
    private boolean closing;
    private final Ui ui = new Ui();
    private List<Integer> visibleChoices = List.of();

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
        panelHeight = PAD * 2 + textHeight + 6 + rows * ROW;
        left = (width - Math.min(WIDTH, width - 8)) / 2;
        top = Math.max(4, height - panelHeight - 20);

        visibleChoices = visible;
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
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!typed()) {
                revealed = Integer.MAX_VALUE / 2;
            } else if (visibleChoices.size() == 1 && node.choices().get(visibleChoices.get(0)).enabled()) {
                choose(visibleChoices.get(0));
            } else if (visibleChoices.isEmpty()) {
                leave();
            } else {
                ui.activateFocus();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            ui.cycleFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
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

    /** Whether the whole line has been typed out. */
    private boolean typed() {
        int total = 0;
        for (FormattedCharSequence line : lines) {
            total += length(line);
        }
        return revealed >= total;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !typed() && my < top + panelHeight - visibleChoices.size() * ROW - PAD) {
            revealed = Integer.MAX_VALUE / 2;
            return true;
        }
        return button == 0 && ui.click(mx, my) || super.mouseClicked(mx, my, button);
    }

    @Override
    public void tick() {
        revealed += CHARS_PER_TICK;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ui.begin(graphics, mouseX, mouseY);
        int w = Math.min(WIDTH, width - 8);
        Draw.panel(graphics, new Rect(left, top, w, panelHeight));
        int accent = 0xFF000000 | node.color();
        graphics.fill(left, top, left + 2, top + panelHeight, accent);

        int px = left + PAD;
        int py = top + PAD;
        graphics.fill(px, py, px + PORTRAIT, py + PORTRAIT, 0xFF0C080A);
        graphics.renderOutline(px, py, PORTRAIT, PORTRAIT, Theme.BORDER);
        LivingEntity speaker = minecraft.level != null && minecraft.level.getEntity(node.npcId()) instanceof LivingEntity living ? living : null;
        if (speaker != null) {
            // The name is already written beside the portrait; a name tag inside it would only be clutter.
            boolean tag = speaker.isCustomNameVisible();
            Component name = speaker.getCustomName();
            speaker.setCustomNameVisible(false);
            speaker.setCustomName(null);
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, px, py, px + PORTRAIT, py + PORTRAIT,
                    26, 0.1F, mouseX, mouseY, speaker);
            speaker.setCustomName(name);
            speaker.setCustomNameVisible(tag);
        }

        int tx = px + PORTRAIT + PAD;
        Attitude attitude = Attitude.values()[Math.floorMod(node.attitude(), Attitude.values().length)];
        Component mood = Component.translatable("gui.vtm.dialogue.mood", Component.translatable("attitude.vtm." + attitude.name().toLowerCase(java.util.Locale.ROOT)),
                (node.rapport() > 0 ? "+" : "") + node.rapport());
        int moodColor = node.rapport() > 0 ? Theme.GOOD : node.rapport() < 0 ? Theme.BAD : MUTED;
        graphics.drawString(font, mood, left + w - PAD - font.width(mood), py, moodColor);
        int room = w - PORTRAIT - PAD * 3 - font.width(mood) - 6;
        graphics.drawString(font, font.plainSubstrByWidth(node.speaker().getString(), room), tx, py, accent, true);
        Draw.divider(graphics, tx, py + 10, w - PORTRAIT - PAD * 3);

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
            graphics.drawString(font, line, tx, y, Theme.DIM);
            y += 10;
        }

        // The answers appear once the line has been spoken.
        int textHeight = Math.max(PORTRAIT, 14 + lines.size() * 10 + (noteLines.isEmpty() ? 0 : 4 + noteLines.size() * 10));
        int cy = top + PAD * 2 + textHeight + 6;
        if (typed()) {
            Rect row = new Rect(left + PAD, cy, w - PAD * 2, ROW - 2);
            if (visibleChoices.isEmpty()) {
                ui.button(row, Component.translatable("gui.vtm.dialogue.leave"), Ui.Style.QUIET, true, this::leave, null);
            }
            int number = 1;
            for (int index : visibleChoices) {
                DialogueNodePayload.Choice choice = node.choices().get(index);
                Rect r = new Rect(row.x(), cy, row.w(), row.h());
                ui.button(r, Component.literal(number++ + ". ").append(choice.text()), Ui.Style.NORMAL, choice.enabled(),
                        () -> choose(index), choice.hint().orElse(null));
                cy += ROW;
            }
        } else {
            graphics.drawString(font, Component.translatable("gui.vtm.dialogue.skip"), left + w - PAD - font.width(Component.translatable("gui.vtm.dialogue.skip")),
                    top + panelHeight - 10, Theme.DIM, false);
        }
        ui.end();
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
