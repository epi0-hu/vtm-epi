package com.epi0.vtm.client;

import com.epi0.vtm.network.LearnDisciplinePayload;
import com.epi0.vtm.power.Power;
import com.epi0.vtm.power.PowerKind;
import com.epi0.vtm.power.Powers;
import com.epi0.vtm.registry.Clan;
import com.epi0.vtm.registry.Discipline;
import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.sect.Membership;
import com.epi0.vtm.sect.SectManager;
import com.epi0.vtm.registry.VtmRegistries;
import com.epi0.vtm.vampire.VampireData;
import com.epi0.vtm.vampire.VampireRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
    enum Tab { OVERVIEW, DISCIPLINES, POWERS, CLAN, SECT }

    private static final int WIDTH = 330;
    private static final int HEIGHT = 214;
    private static final int ROW_HEIGHT = 14;
    private static final int VISIBLE_ROWS = 10;
    private static final int LIST_TOP = 52;

    private static final int PANEL = 0xE0181015;
    private static final int BORDER = 0xFF7A1F1F;
    private static final int TEXT = 0xFFE6E0E0;
    private static final int MUTED = 0xFF9A9090;
    private static final int PIP_EMPTY = 0xFF3A2A2A;
    private static final int BLOOD = 0xFFB01E1E;

    private record LearnButton(Button button, ResourceLocation discipline) {}

    private Tab tab;
    private int scroll;
    private int left;
    private int top;
    private final List<LearnButton> learnButtons = new ArrayList<>();

    public VampireScreen() {
        this(Tab.OVERVIEW);
    }

    VampireScreen(Tab tab) {
        super(Component.translatable("gui.vtm.title"));
        this.tab = tab;
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
                    }).bounds(x, top + 8, 60, 18).build();
            button.active = t != tab;
            addRenderableWidget(button);
            x += 63;
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
                int next = data().disciplineLevel(id) + 1;
                if (next <= VampireRules.MAX_DISCIPLINE_LEVEL) {
                    List<Power> unlocks = Powers.at(id, next);
                    if (!unlocks.isEmpty()) {
                        MutableComponent tip = Component.empty();
                        for (Power p : unlocks) {
                            if (!tip.getSiblings().isEmpty()) {
                                tip.append("\n\n");
                            }
                            tip.append(Component.translatable("gui.vtm.unlocks", p.name())).append("\n").append(p.description());
                        }
                        plus.setTooltip(Tooltip.create(tip));
                    }
                }
                learnButtons.add(new LearnButton(plus, id));
                addRenderableWidget(plus);
            }
        }
        if (tab == Tab.POWERS && data().isVampire()) {
            List<Power> powers = Powers.unlocked(data());
            int end = Math.min(powers.size(), scroll + VISIBLE_ROWS);
            for (int i = scroll; i < end; i++) {
                Power power = powers.get(i);
                int y = top + LIST_TOP + (i - scroll) * ROW_HEIGHT;
                if (power.kind() == PowerKind.ACTIVE) {
                    Button select = Button.builder(Component.translatable("gui.vtm.select"),
                                    b -> PowerSelection.select(power))
                            .bounds(left + WIDTH - 52, y - 2, 40, 12).build();
                    select.setTooltip(Tooltip.create(power.description()));
                    addRenderableWidget(select);
                }
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
        if (tab == Tab.DISCIPLINES || tab == Tab.POWERS) {
            int rows = tab == Tab.DISCIPLINES ? disciplineIds().size() : Powers.unlocked(data()).size();
            int max = Math.max(0, rows - VISIBLE_ROWS);
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
            case POWERS -> renderPowers(g, data);
            case CLAN -> renderClan(g, data);
            case SECT -> renderSect(g);
        }
    }

    private void renderOverview(GuiGraphics g, VampireData data) {
        int x = left + 12;
        int y = top + 38;
        Component clanName = clanName(data);
        int clanColor = data.clan().flatMap(id -> VtmRegistries.clan(access(), id)).map(Clan::color).orElse(0xAAAAAA);
        g.drawString(font, Component.translatable("gui.vtm.clan"), x, y, MUTED, false);
        g.drawString(font, clanName, x + 90, y, readable(clanColor), false);

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
                    left + 12, y, readable(discipline.color()), false);
            pips(g, left + 124, y, level, VampireRules.MAX_DISCIPLINE_LEVEL, readable(discipline.color()));
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

    private void renderPowers(GuiGraphics g, VampireData data) {
        List<Power> powers = Powers.unlocked(data);
        if (powers.isEmpty()) {
            g.drawString(font, Component.translatable("gui.vtm.no_powers"), left + 12, top + 38, MUTED, false);
            return;
        }
        Power armed = PowerSelection.current(data);
        g.drawString(font, Component.translatable("gui.vtm.power_keys",
                ClientModEvents.PREVIOUS_POWER.getTranslatedKeyMessage(),
                ClientModEvents.NEXT_POWER.getTranslatedKeyMessage(),
                ClientModEvents.USE_POWER.getTranslatedKeyMessage()), left + 12, top + 34, MUTED, false);
        RegistryAccess access = access();
        int end = Math.min(powers.size(), scroll + VISIBLE_ROWS);
        for (int i = scroll; i < end; i++) {
            Power power = powers.get(i);
            int y = top + LIST_TOP + (i - scroll) * ROW_HEIGHT;
            int color = readable(VtmRegistries.discipline(access, power.discipline()).map(Discipline::color).orElse(0xAAAAAA));
            boolean isArmed = armed != null && armed.id().equals(power.id());
            g.drawString(font, Component.literal(isArmed ? "\u25B6 " : "").append(power.name()), left + 12, y, color, false);
            g.drawString(font, power.kind() == PowerKind.PASSIVE
                            ? Component.translatable("gui.vtm.passive")
                            : Component.translatable("gui.vtm.blood_cost", power.bloodCost()),
                    left + 160, y, MUTED, false);
        }
        if (powers.size() > VISIBLE_ROWS) {
            g.drawString(font, Component.translatable("gui.vtm.scroll_powers"), left + 12, top + HEIGHT - 14, MUTED, false);
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
        g.drawString(font, clanName(data), x, y, readable(clan.color()), false);
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
            g.drawString(font, VtmRegistries.name("discipline", id), x, y, readable(color), false);
            y += 11;
        }
    }

    private void renderSect(GuiGraphics g) {
        Membership m = Membership.of(minecraft.player);
        int x = left + 12;
        int y = top + 38;
        ResourceLocation id = m.faction().orElse(null);
        Faction faction = id == null ? null : VtmRegistries.faction(access(), id).orElse(null);
        if (faction == null) {
            g.drawString(font, Component.translatable("gui.vtm.sect.none"), x, y, MUTED, false);
            y = paragraph(g, Component.translatable("gui.vtm.sect.none_hint"), x, y + 12, TEXT);
        } else {
            g.drawString(font, VtmRegistries.name("faction", id), x, y, readable(faction.color()), false);
            y += 12;
            g.drawString(font, Component.translatable("gui.vtm.sect.rank", SectManager.rankTitle(access(), id, m.rank()),
                    m.rank() + 1, faction.rankCount()), x, y, TEXT, false);
            y += 12;
            int standing = m.standingWith(id);
            if (m.rank() + 1 < faction.rankCount()) {
                g.drawString(font, Component.translatable("gui.vtm.sect.next", SectManager.rankTitle(access(), id, m.rank() + 1),
                        standing, faction.standingFor(m.rank() + 1)), x, y, MUTED, false);
            } else {
                g.drawString(font, Component.translatable("gui.vtm.sect.top"), x, y, MUTED, false);
            }
            y += 14;
            y = relationLine(g, "gui.vtm.sect.allies", faction.allies(), x, y);
            y = relationLine(g, "gui.vtm.sect.enemies", faction.enemies(), x, y);
        }
        y += 6;
        g.drawString(font, Component.translatable("gui.vtm.sect.standing"), x, y, MUTED, false);
        y += 12;
        int barLeft = x + 110;
        int barWidth = WIDTH - 24 - 110 - 30;
        for (ResourceLocation other : access().registry(VtmRegistries.FACTION)
                .map(r -> r.keySet().stream().sorted(Comparator.comparing(ResourceLocation::getPath)).toList()).orElse(List.of())) {
            Faction def = VtmRegistries.faction(access(), other).orElse(null);
            if (def == null || !def.joinable() && m.standingWith(other) == 0) {
                continue;
            }
            int value = m.standingWith(other);
            g.drawString(font, VtmRegistries.name("faction", other), x, y, readable(def.color()), false);
            g.fill(barLeft, y + 2, barLeft + barWidth, y + 7, PIP_EMPTY);
            int mid = barLeft + barWidth / 2;
            int end = mid + value * (barWidth / 2) / Membership.MAX_STANDING;
            g.fill(Math.min(mid, end), y + 2, Math.max(mid, end), y + 7, value >= 0 ? readable(def.color()) : BLOOD);
            g.fill(mid, y, mid + 1, y + 9, MUTED);
            g.drawString(font, String.valueOf(value), barLeft + barWidth + 6, y, value <= SectManager.OUTCAST ? BLOOD : TEXT, false);
            y += 12;
        }
    }

    private int relationLine(GuiGraphics g, String labelKey, List<ResourceLocation> factions, int x, int y) {
        if (factions.isEmpty()) {
            return y;
        }
        net.minecraft.network.chat.MutableComponent names = Component.empty();
        for (ResourceLocation other : factions) {
            if (!names.getSiblings().isEmpty()) {
                names.append(", ");
            }
            names.append(VtmRegistries.name("faction", other));
        }
        g.drawString(font, Component.translatable(labelKey, names), x, y, MUTED, false);
        return y + 11;
    }

    // ---------------------------------------------------------------- helpers

    /** Opaque, and lifted towards white if the colour would be hard to read on the dark panel. */
    private static int readable(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int gr = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        double luma = 0.299 * r + 0.587 * gr + 0.114 * b;
        if (luma < 120) {
            double mix = (120 - luma) / (255 - luma);
            r += (int) ((255 - r) * mix);
            gr += (int) ((255 - gr) * mix);
            b += (int) ((255 - b) * mix);
        }
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

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
