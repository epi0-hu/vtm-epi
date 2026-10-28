package com.epi0.vtm.client;

import com.epi0.vtm.network.LearnDisciplinePayload;
import com.epi0.vtm.registry.Clan;
import com.epi0.vtm.registry.Discipline;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Character sheet: overview, disciplines (spend experience) and clan info.
 * All values are read from the synced {@link VampireData}; the only thing the client sends is a learn request.
 */
public class VampireScreen extends Screen {
    private enum Tab { OVERVIEW, DISCIPLINES, CLAN }

    private static final int WIDTH = 256;
    private static final int HEIGHT = 214;
    private static final int ROW_HEIGHT = 14;
    private static final int VISIBLE_ROWS = 11;
    private static final int LIST_TOP = 52;

    private static final int PANEL = 0xE0181015;
    private static final int BORDER = 0xFF7A1F1F;
    private static final int TEXT = 0xFFE6E0E0;
    private static final int MUTED = 0xFF9A9090;
    private static final int PIP_EMPTY = 0xFF3A2A2A;
    private static final int BLOOD = 0xFFB01E1E;

    private record LearnButton(Button button, ResourceLocation discipline) {}

    private Tab tab = Tab.OVERVIEW;
    private int scroll;
    private int left;
    private int top;
    private final List<LearnButton> learnButtons = new ArrayList<>();

    public VampireScreen() {
        super(Component.translatable("gui.vtm.title"));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        learnButtons.clear();

        int x = left + 8;
        for (Tab t : Tab.values()) {
            Button button = Button.builder(Component.translatable("gui.vtm.tab." + t.name().toLowerCase(java.util.Locale.ROOT)),
                    b -> {
                        tab = t;
                        scroll = 0;
                        rebuildWidgets();
                    }).bounds(x, top + 8, 76, 18).build();
            button.active = t != tab;
            addRenderableWidget(button);
            x += 80;
        }

        if (tab == Tab.DISCIPLINES && data().isVampire()) {
            List<ResourceLocation> ids = disciplineIds();
            int end = Math.min(ids.size(), scroll + VISIBLE_ROWS);
            for (int i = scroll; i < end; i++) {
                ResourceLocation id = ids.get(i);
                int y = top + LIST_TOP + (i - scroll) * ROW_HEIGHT;
                Button plus = Button.builder(Component.literal("+"),
                                b -> PacketDistributor.sendToServer(new LearnDisciplinePayload(id)))
                        .bounds(left + WIDTH - 30, y - 2, 18, 12).build();
                learnButtons.add(new LearnButton(plus, id));
                addRenderableWidget(plus);
            }
        }
        refreshLearnButtons();
    }

    @Override
    public void tick() {
        refreshLearnButtons();
    }

    private void refreshLearnButtons() {
        VampireData data = data();
        RegistryAccess access = access();
        for (LearnButton lb : learnButtons) {
            lb.button.active = canLearn(access, data, lb.discipline);
        }
    }

    private static boolean canLearn(RegistryAccess access, VampireData data, ResourceLocation id) {
        Discipline discipline = VtmRegistries.discipline(access, id).orElse(null);
        if (discipline == null || !data.isVampire()) {
            return false;
        }
        boolean inClan = VampireRules.isInClan(access, data, id);
        int level = data.disciplineLevel(id);
        return (!discipline.clanOnly() || inClan)
                && level < VampireRules.MAX_DISCIPLINE_LEVEL
                && data.experience() >= VampireRules.learnCost(level + 1, inClan);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.DISCIPLINES) {
            int max = Math.max(0, disciplineIds().size() - VISIBLE_ROWS);
            int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientModEvents.OPEN_MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, BORDER);
        g.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        VampireData data = data();
        if (!data.isVampire()) {
            g.drawCenteredString(font, Component.translatable("gui.vtm.mortal"), left + WIDTH / 2, top + HEIGHT / 2, MUTED);
            return;
        }
        switch (tab) {
            case OVERVIEW -> renderOverview(g, data);
            case DISCIPLINES -> renderDisciplines(g, data);
            case CLAN -> renderClan(g, data);
        }
    }

    private void renderOverview(GuiGraphics g, VampireData data) {
        int x = left + 12;
        int y = top + 38;
        Component clanName = clanName(data);
        int clanColor = data.clan().flatMap(id -> VtmRegistries.clan(access(), id)).map(Clan::color).orElse(0xAAAAAA);
        g.drawString(font, Component.translatable("gui.vtm.clan"), x, y, MUTED, false);
        g.drawString(font, clanName, x + 90, y, 0xFF000000 | clanColor, false);

        y += 16;
        row(g, x, y, "gui.vtm.generation", Component.literal(ordinal(data.generation())));
        y += 14;
        row(g, x, y, "gui.vtm.blood_potency", Component.literal(data.bloodPotency() + " / "
                + VampireRules.maxBloodPotency(data.generation())));
        y += 14;
        row(g, x, y, "gui.vtm.experience", Component.literal(String.valueOf(data.experience())));

        y += 20;
        g.drawString(font, Component.translatable("gui.vtm.blood"), x, y, MUTED, false);
        int barX = x + 90;
        int barWidth = WIDTH - 24 - 90;
        float fraction = Math.max(0F, Math.min(1F, data.blood() / data.maxBlood()));
        g.fill(barX - 1, y - 1, barX + barWidth + 1, y + 9, 0xFF000000);
        g.fill(barX, y, barX + barWidth, y + 8, 0xFF2A0A0A);
        g.fill(barX, y, barX + Math.round(barWidth * fraction), y + 8, BLOOD);
        g.drawCenteredString(font, String.format("%.1f / %.0f", data.blood(), data.maxBlood()),
                barX + barWidth / 2, y, 0xFFFFFF);

        y += 16;
        row(g, x, y, "gui.vtm.hunger", Component.literal(data.hunger() + " / " + VampireRules.MAX_HUNGER));

        y += 20;
        g.drawString(font, Component.translatable("gui.vtm.humanity"), x, y, MUTED, false);
        pips(g, x + 90, y, data.humanity(), VampireRules.MAX_HUMANITY, 0xFFD8C7A0);
        y += 14;
        g.drawString(font, Component.translatable("gui.vtm.stains"), x, y, MUTED, false);
        pips(g, x + 90, y, data.stains(), VampireRules.MAX_STAINS, 0xFF5A1A6B);

        g.drawString(font, Component.translatable("gui.vtm.hint_open", ClientModEvents.OPEN_MENU.getTranslatedKeyMessage()),
                left + 12, top + HEIGHT - 14, MUTED, false);
    }

    private void renderDisciplines(GuiGraphics g, VampireData data) {
        g.drawString(font, Component.translatable("gui.vtm.xp_available", data.experience()),
                left + 12, top + 34, TEXT, false);
        RegistryAccess access = access();
        List<ResourceLocation> ids = disciplineIds();
        int end = Math.min(ids.size(), scroll + VISIBLE_ROWS);
        for (int i = scroll; i < end; i++) {
            ResourceLocation id = ids.get(i);
            int y = top + LIST_TOP + (i - scroll) * ROW_HEIGHT;
            Discipline discipline = VtmRegistries.discipline(access, id).orElse(null);
            if (discipline == null) {
                continue;
            }
            boolean inClan = VampireRules.isInClan(access, data, id);
            int level = data.disciplineLevel(id);
            Component name = VtmRegistries.name("discipline", id);
            g.drawString(font, inClan ? Component.literal("★ ").append(name) : name,
                    left + 12, y, 0xFF000000 | discipline.color(), false);
            pips(g, left + 124, y, level, VampireRules.MAX_DISCIPLINE_LEVEL, 0xFF000000 | discipline.color());
            if (level < VampireRules.MAX_DISCIPLINE_LEVEL) {
                boolean learnable = !discipline.clanOnly() || inClan;
                g.drawString(font, learnable
                                ? Component.translatable("gui.vtm.cost", VampireRules.learnCost(level + 1, inClan))
                                : Component.translatable("gui.vtm.clan_only"),
                        left + 176, y, MUTED, false);
            }
        }
        if (ids.size() > VISIBLE_ROWS) {
            g.drawString(font, Component.translatable("gui.vtm.scroll"), left + 12, top + HEIGHT - 14, MUTED, false);
        } else {
            g.drawString(font, Component.translatable("gui.vtm.legend_clan"), left + 12, top + HEIGHT - 14, MUTED, false);
        }
    }

    private void renderClan(GuiGraphics g, VampireData data) {
        int x = left + 12;
        int y = top + 38;
        ResourceLocation clanId = data.clan().orElse(null);
        Clan clan = clanId == null ? null : VtmRegistries.clan(access(), clanId).orElse(null);
        if (clanId == null || clan == null) {
            g.drawString(font, Component.translatable("gui.vtm.no_clan"), x, y, MUTED, false);
            return;
        }
        g.drawString(font, clanName(data), x, y, 0xFF000000 | clan.color(), false);
        y += 14;
        y = paragraph(g, VtmRegistries.text("clan", clanId, "desc"), x, y, TEXT);
        y += 6;
        g.drawString(font, Component.translatable("gui.vtm.bane"), x, y, BLOOD, false);
        y += 12;
        y = paragraph(g, VtmRegistries.text("clan", clanId, "bane"), x, y, TEXT);
        y += 6;
        g.drawString(font, Component.translatable("gui.vtm.clan_disciplines"), x, y, MUTED, false);
        y += 12;
        if (clan.disciplines().isEmpty()) {
            g.drawString(font, Component.translatable("gui.vtm.none"), x, y, TEXT, false);
        }
        for (ResourceLocation id : clan.disciplines()) {
            int color = VtmRegistries.discipline(access(), id).map(Discipline::color).orElse(0xAAAAAA);
            g.drawString(font, VtmRegistries.name("discipline", id), x, y, 0xFF000000 | color, false);
            y += 11;
        }
    }

    // ---------------------------------------------------------------- helpers

    private void row(GuiGraphics g, int x, int y, String labelKey, Component value) {
        g.drawString(font, Component.translatable(labelKey), x, y, MUTED, false);
        g.drawString(font, value, x + 90, y, TEXT, false);
    }

    private void pips(GuiGraphics g, int x, int y, int filled, int total, int color) {
        for (int i = 0; i < total; i++) {
            int px = x + i * 9;
            g.fill(px, y, px + 7, y + 7, i < filled ? color : PIP_EMPTY);
        }
    }

    private int paragraph(GuiGraphics g, Component text, int x, int y, int color) {
        for (FormattedCharSequence line : font.split(text, WIDTH - 24)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private Component clanName(VampireData data) {
        return data.clan().map(id -> VtmRegistries.name("clan", id)).orElse(Component.translatable("gui.vtm.none"));
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        return n + switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private List<ResourceLocation> disciplineIds() {
        return access().registry(VtmRegistries.DISCIPLINE)
                .map(r -> r.keySet().stream().sorted(Comparator.comparing(ResourceLocation::getPath)).toList())
                .orElse(List.of());
    }

    private VampireData data() {
        return VampireData.of(Minecraft.getInstance().player);
    }

    private RegistryAccess access() {
        return Minecraft.getInstance().player.level().registryAccess();
    }
}
