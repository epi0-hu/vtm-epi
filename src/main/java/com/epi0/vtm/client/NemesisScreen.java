package com.epi0.vtm.client;

import com.epi0.vtm.nemesis.ChronicleEntry;
import com.epi0.vtm.nemesis.NemesisViews;
import com.epi0.vtm.nemesis.Trait;
import com.epi0.vtm.network.NemesisActionPayload;
import com.epi0.vtm.registry.Faction;
import com.epi0.vtm.registry.VtmRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The Nemesis screen: dossiers on every named vampire you know of, the courts of the sects and who sits where, the
 * chronicle of what has happened in the night, and the vampires bound to you. It only ever shows what the server
 * has told it you know.
 */
public class NemesisScreen extends Screen {
    enum Tab { DOSSIERS, COURTS, CHRONICLE, COTERIE }

    private static final int PANEL = 0xE0181015;
    private static final int BORDER = 0xFF7A1F1F;
    private static final int TEXT = 0xFFE6E0E0;
    private static final int MUTED = 0xFF9A9090;
    private static final int DIM = 0xFF3A2A2A;
    private static final int BLOOD = 0xFFB01E1E;
    private static final int GOOD = 0xFF6FBF73;
    private static final int WARN = 0xFFD9A441;
    private static final int ROW = 11;

    private static NemesisViews.Snapshot snapshot = NemesisViews.Snapshot.EMPTY;
    private static UUID selected;
    private static UUID huntTarget;
    private static int spyIndex;
    private static Tab lastTab = Tab.DOSSIERS;

    private Tab tab = lastTab;
    private int left;
    private int top;
    private int width_;
    private int height_;
    private int scroll;
    private int refresh;
    private final List<Button> coterieButtons = new ArrayList<>();

    public NemesisScreen() {
        super(Component.translatable("gui.vtm.nemesis.title"));
    }

    NemesisScreen(Tab tab) {
        this();
        this.tab = tab;
        lastTab = tab;
    }

    /** A fresh snapshot arrived from the server. */
    public static void receive(NemesisViews.Snapshot fresh) {
        snapshot = fresh;
        if (Minecraft.getInstance().screen instanceof NemesisScreen screen) {
            screen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        width_ = Math.min(width - 16, 420);
        height_ = Math.min(height - 16, 232);
        left = (width - width_) / 2;
        top = (height - height_) / 2;
        int x = left + 8;
        for (Tab t : Tab.values()) {
            Button button = Button.builder(Component.translatable("gui.vtm.nemesis.tab." + t.name().toLowerCase(Locale.ROOT)), b -> {
                tab = t;
                lastTab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(x, top + 8, 70, 18).build();
            button.active = t != tab;
            addRenderableWidget(button);
            x += 73;
        }
        coterieButtons.clear();
        if (tab == Tab.DOSSIERS) {
            entry(selected).ifPresent(e -> {
                Button mark = Button.builder(Component.translatable(e.id().equals(huntTarget) ? "gui.vtm.nemesis.unmark" : "gui.vtm.nemesis.mark"),
                        b -> {
                            huntTarget = e.id().equals(huntTarget) ? null : e.id();
                            rebuildWidgets();
                        }).bounds(left + width_ - 118, top + height_ - 24, 110, 16).build();
                mark.setTooltip(Tooltip.create(Component.translatable("gui.vtm.nemesis.mark.tip")));
                mark.active = e.core().status().equals("active") || e.core().status().equals("torpor");
                addRenderableWidget(mark);
            });
        }
        if (tab == Tab.COTERIE) {
            buildCoterie();
        }
    }

    @Override
    public void tick() {
        if (++refresh % 100 == 0) {
            PacketDistributor.sendToServer(NemesisActionPayload.refresh());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- data

    private static Optional<NemesisViews.Entry> entry(UUID id) {
        return id == null ? Optional.empty() : snapshot.entries().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    private static List<NemesisViews.Entry> sorted() {
        return snapshot.entries().stream().sorted(Comparator
                .comparing((NemesisViews.Entry e) -> e.core().status().equals("destroyed"))
                .thenComparing(e -> -e.core().tier())
                .thenComparing(e -> -e.core().level())
                .thenComparing(e -> e.core().name())).toList();
    }

    private int factionColor(ResourceLocation id) {
        return readable(Minecraft.getInstance().level == null ? 0xAAAAAA
                : VtmRegistries.faction(Minecraft.getInstance().level.registryAccess(), id).map(Faction::color).orElse(0xAAAAAA));
    }

    private static int readable(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        double luma = 0.299 * r + 0.587 * g + 0.114 * b;
        if (luma < 120) {
            double mix = (120 - luma) / (255 - luma);
            r += (int) ((255 - r) * mix);
            g += (int) ((255 - g) * mix);
            b += (int) ((255 - b) * mix);
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static Component factionName(ResourceLocation id) {
        return VtmRegistries.name("faction", id);
    }

    private static Component roleTitle(ResourceLocation faction, String role) {
        return Component.translatable("role.vtm." + faction.getPath() + "." + role);
    }

    private static Component statusName(String status) {
        return Component.translatable("gui.vtm.nemesis.status." + status);
    }

    private static Component displayName(NemesisViews.Core core) {
        MutableComponent name = Component.literal(core.name());
        core.epithet().ifPresent(e -> name.append(" ").append(Component.translatable("epithet.vtm." + e)));
        return name;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll = Math.max(0, scroll - (int) Math.signum(dy));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == Tab.DOSSIERS && mx >= left + 8 && mx < left + 142 && my >= top + 34) {
            int index = (int) ((my - (top + 34)) / ROW) + scroll;
            List<NemesisViews.Entry> list = sorted();
            if (index >= 0 && index < list.size() && index - scroll < visibleRows()) {
                selected = list.get(index).id();
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private int visibleRows() {
        return (height_ - 34 - 10) / ROW;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(left - 1, top - 1, left + width_ + 1, top + height_ + 1, BORDER);
        g.fill(left, top, left + width_, top + height_, PANEL);
        switch (tab) {
            case DOSSIERS -> renderDossiers(g);
            case COURTS -> renderCourts(g);
            case CHRONICLE -> renderChronicle(g);
            case COTERIE -> renderCoterie(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderDossiers(GuiGraphics g) {
        int listX = left + 8;
        int y = top + 34;
        List<NemesisViews.Entry> list = sorted();
        if (list.isEmpty()) {
            paragraph(g, Component.translatable("gui.vtm.nemesis.empty"), listX, y, width_ - 16, MUTED);
            return;
        }
        if (selected == null || entry(selected).isEmpty()) {
            selected = list.get(0).id();
        }
        scroll = Math.min(scroll, Math.max(0, list.size() - visibleRows()));
        g.fill(listX + 134, top + 32, listX + 135, top + height_ - 8, DIM);
        for (int i = scroll; i < Math.min(list.size(), scroll + visibleRows()); i++) {
            NemesisViews.Entry e = list.get(i);
            boolean sel = e.id().equals(selected);
            if (sel) {
                g.fill(listX - 2, y - 1, listX + 132, y + ROW - 2, 0x40FFFFFF);
            }
            boolean dead = e.core().status().equals("destroyed");
            int color = dead ? DIM + 0x303030 : factionColor(e.core().faction());
            String mark = e.bond().bound() ? "+ " : e.history().enmity() >= 50 ? "! " : "  ";
            g.drawString(font, font.plainSubstrByWidth(mark + e.core().name(), 126), listX, y, color, false);
            y += ROW;
        }
        entry(selected).ifPresent(e -> renderDossier(g, e, left + 150));
    }

    private void renderDossier(GuiGraphics g, NemesisViews.Entry e, int x) {
        NemesisViews.Core c = e.core();
        int w = left + width_ - 8 - x;
        int y = top + 34;
        g.drawString(font, displayName(c), x, y, factionColor(c.faction()), false);
        y += 11;
        MutableComponent sub = Component.empty().append(factionName(c.faction()));
        c.clan().ifPresent(clan -> sub.append(" | ").append(VtmRegistries.name("clan", clan)));
        c.role().ifPresent(role -> sub.append(" | ").append(roleTitle(c.faction(), role)));
        g.drawString(font, font.plainSubstrByWidth(sub.getString(), w), x, y, MUTED, false);
        y += 11;
        if (c.tier() >= 1) {
            g.drawString(font, Component.translatable("gui.vtm.nemesis.level_status", c.level(), statusName(c.status())), x, y, TEXT, false);
        } else {
            g.drawString(font, statusName(c.status()), x, y, TEXT, false);
        }
        y += 13;

        if (c.tier() < 1) {
            y = paragraph(g, Component.translatable("gui.vtm.nemesis.only_heard"), x, y, w, MUTED);
        }
        // Traits
        g.drawString(font, Component.translatable("gui.vtm.nemesis.traits"), x, y, MUTED, false);
        y += 10;
        for (String id : c.traits()) {
            Trait trait = Trait.byId(id).orElse(null);
            if (trait == null) {
                continue;
            }
            int color = trait.kind() == Trait.Kind.STRENGTH ? BLOOD : trait.kind() == Trait.Kind.WEAKNESS ? GOOD : WARN;
            g.drawString(font, Component.translatable(trait.nameKey()), x + 4, y, color, false);
            y += 10;
        }
        if (c.unknownTraits() > 0) {
            g.drawString(font, Component.translatable("gui.vtm.nemesis.unknown_traits", c.unknownTraits()), x + 4, y, DIM + 0x404040, false);
            y += 10;
        }
        if (!c.scars().isEmpty()) {
            MutableComponent scars = Component.empty();
            for (String s : c.scars()) {
                if (!scars.getSiblings().isEmpty()) {
                    scars.append(", ");
                }
                scars.append(Component.translatable("scar.vtm." + s));
            }
            g.drawString(font, Component.translatable("gui.vtm.nemesis.scars", scars), x, y, TEXT, false);
            y += 11;
        }
        if (c.place().isPresent()) {
            GlobalPos place = c.place().get();
            g.drawString(font, Component.translatable("gui.vtm.nemesis.last_seen", place.pos().getX(), place.pos().getY(), place.pos().getZ()), x, y, MUTED, false);
            y += 11;
        }
        NemesisViews.History h = e.history();
        if (h.enmity() > 0 || h.respect() > 0 || h.defeats() > 0 || h.losses() > 0 || h.spared() > 0) {
            y += 3;
            g.drawString(font, Component.translatable("gui.vtm.nemesis.between"), x, y, MUTED, false);
            y += 10;
            meter(g, x + 4, y, "gui.vtm.nemesis.enmity", h.enmity(), BLOOD);
            y += 10;
            meter(g, x + 4, y, "gui.vtm.nemesis.respect", h.respect(), GOOD);
            y += 11;
            g.drawString(font, Component.translatable("gui.vtm.nemesis.tally", h.defeats(), h.losses(), h.spared()), x + 4, y, TEXT, false);
            y += 11;
            if (h.bloodHunt()) {
                g.drawString(font, Component.translatable("gui.vtm.nemesis.blood_hunt"), x + 4, y, BLOOD, false);
                y += 11;
            }
        }
        if (!e.kin().isEmpty()) {
            y += 3;
            g.drawString(font, Component.translatable("gui.vtm.nemesis.kin"), x, y, MUTED, false);
            y += 10;
            for (NemesisViews.Kin kin : e.kin()) {
                g.drawString(font, Component.translatable("gui.vtm.nemesis.kin_line", Component.translatable("relation.vtm." + kin.type()), kin.name()), x + 4, y, TEXT, false);
                y += 10;
            }
        }
        if (e.id().equals(huntTarget)) {
            g.drawString(font, Component.translatable("gui.vtm.nemesis.marked"), x, top + height_ - 20, WARN, false);
        }
    }

    private void meter(GuiGraphics g, int x, int y, String key, int value, int color) {
        g.drawString(font, Component.translatable(key), x, y, TEXT, false);
        int bx = x + 60;
        g.fill(bx, y + 2, bx + 80, y + 7, DIM);
        g.fill(bx, y + 2, bx + value * 80 / 100, y + 7, color);
        g.drawString(font, String.valueOf(value), bx + 86, y, MUTED, false);
    }

    private void renderCourts(GuiGraphics g) {
        List<NemesisViews.Court> courts = snapshot.courts();
        if (courts.isEmpty()) {
            paragraph(g, Component.translatable("gui.vtm.nemesis.no_courts"), left + 8, top + 34, width_ - 16, MUTED);
            return;
        }
        int colWidth = (width_ - 16) / courts.size();
        for (int c = 0; c < courts.size(); c++) {
            NemesisViews.Court court = courts.get(c);
            int x = left + 8 + c * colWidth;
            int y = top + 34;
            int color = factionColor(court.faction());
            g.drawString(font, factionName(court.faction()), x, y, color, false);
            g.fill(x, y + 10, x + colWidth - 8, y + 11, color);
            y += 16;
            String lastRole = "";
            for (NemesisViews.Seat seat : court.seats()) {
                if (!seat.role().equals(lastRole)) {
                    lastRole = seat.role();
                    g.drawString(font, roleTitle(court.faction(), seat.role()), x, y, MUTED, false);
                    y += 10;
                }
                Component who;
                int whoColor;
                if (!seat.filled()) {
                    who = Component.translatable("gui.vtm.nemesis.vacant");
                    whoColor = DIM + 0x404040;
                } else if (seat.known().isPresent()) {
                    Optional<NemesisViews.Entry> e = entry(seat.known().get());
                    who = e.map(en -> displayName(en.core())).orElse(Component.literal("?"));
                    whoColor = e.map(en -> en.core().status().equals("active") ? TEXT : MUTED).orElse(TEXT);
                } else {
                    who = Component.translatable("gui.vtm.nemesis.unknown_holder");
                    whoColor = MUTED;
                }
                g.drawString(font, font.plainSubstrByWidth(who.getString(), colWidth - 18), x + 6, y, whoColor, false);
                y += 10;
            }
        }
    }

    private void renderChronicle(GuiGraphics g) {
        List<ChronicleEntry> news = new ArrayList<>(snapshot.chronicle());
        java.util.Collections.reverse(news);
        if (news.isEmpty()) {
            paragraph(g, Component.translatable("gui.vtm.nemesis.no_news"), left + 8, top + 34, width_ - 16, MUTED);
            return;
        }
        int y = top + 34;
        int rows = height_ - 34 - 6;
        scroll = Math.min(scroll, Math.max(0, news.size() - 1));
        for (int i = scroll; i < news.size() && y < top + height_ - 12; i++) {
            ChronicleEntry entry = news.get(i);
            Component line = Component.translatable("gui.vtm.nemesis.news_line", entry.time() / 24000L + 1,
                    Component.translatable("chronicle.vtm." + entry.key(), entry.args().toArray()));
            int color = entry.faction().map(this::factionColor).orElse(TEXT);
            for (FormattedCharSequence part : font.split(line, width_ - 24)) {
                if (y >= top + height_ - 12) {
                    break;
                }
                g.drawString(font, part, left + 10, y, entry.publicNews() ? color : MUTED, false);
                y += 10;
            }
            y += 2;
        }
    }

    // ---------------------------------------------------------------- coterie

    private List<NemesisViews.Entry> bound() {
        return snapshot.entries().stream().filter(e -> e.bond().bound()).toList();
    }

    private List<ResourceLocation> spyTargets() {
        return snapshot.courts().stream().map(NemesisViews.Court::faction).toList();
    }

    private void buildCoterie() {
        List<ResourceLocation> targets = spyTargets();
        if (!targets.isEmpty()) {
            spyIndex = Math.floorMod(spyIndex, targets.size());
            Button spy = Button.builder(Component.translatable("gui.vtm.nemesis.spy_target", factionName(targets.get(spyIndex))), b -> {
                spyIndex++;
                rebuildWidgets();
            }).bounds(left + width_ - 168, top + 29, 160, 16).build();
            addRenderableWidget(spy);
        }
        int y = top + 54;
        for (NemesisViews.Entry e : bound()) {
            boolean away = e.bond().mission().isPresent();
            int x = left + 8;
            if (away) {
                add(x, y + 30, 90, "gui.vtm.nemesis.recall", () -> act("recall", e, null, null));
            } else {
                if (!targets.isEmpty()) {
                    ResourceLocation faction = targets.get(Math.floorMod(spyIndex, targets.size()));
                    add(x, y + 30, 64, "gui.vtm.nemesis.spy", () -> act("mission:spy", e, null, faction));
                }
                Button hunt = add(x + 67, y + 30, 90, "gui.vtm.nemesis.hunt", () -> act("mission:hunt", e, huntTarget, null));
                hunt.active = huntTarget != null;
                hunt.setTooltip(Tooltip.create(Component.translatable(huntTarget == null ? "gui.vtm.nemesis.hunt.none" : "gui.vtm.nemesis.hunt.tip",
                        entry(huntTarget).map(t -> t.core().name()).orElse("?"))));
                add(x + 160, y + 30, 70, "gui.vtm.nemesis.tribute", () -> act("mission:tribute", e, null, null));
            }
            add(left + width_ - 148, y + 30, 70, "gui.vtm.nemesis.tithe", () -> act("tithe", e, null, null));
            add(left + width_ - 76, y + 30, 68, "gui.vtm.nemesis.release", () -> act("release", e, null, null));
            y += 56;
        }
    }

    private Button add(int x, int y, int w, String key, Runnable action) {
        Button button = Button.builder(Component.translatable(key), b -> action.run()).bounds(x, y, w, 16).build();
        coterieButtons.add(button);
        return addRenderableWidget(button);
    }

    private void act(String action, NemesisViews.Entry e, UUID target, ResourceLocation faction) {
        PacketDistributor.sendToServer(new NemesisActionPayload(action, e.id(), Optional.ofNullable(target), Optional.ofNullable(faction)));
    }

    private void renderCoterie(GuiGraphics g) {
        List<NemesisViews.Entry> bound = bound();
        g.drawString(font, Component.translatable("gui.vtm.nemesis.coterie_count", bound.size(), com.epi0.vtm.nemesis.NemesisManager.MAX_COTERIE), left + 10, top + 34, MUTED, false);
        if (bound.isEmpty()) {
            paragraph(g, Component.translatable("gui.vtm.nemesis.no_coterie"), left + 10, top + 50, width_ - 20, MUTED);
            return;
        }
        long now = snapshot.now();
        int y = top + 48;
        for (NemesisViews.Entry e : bound) {
            g.fill(left + 6, y - 2, left + width_ - 6, y + 50, 0x30FFFFFF);
            g.drawString(font, displayName(e.core()), left + 10, y, factionColor(e.core().faction()), false);
            g.drawString(font, Component.translatable("gui.vtm.nemesis.level_short", e.core().level()), left + 190, y, TEXT, false);
            int bx = left + width_ - 160;
            g.drawString(font, Component.translatable("gui.vtm.nemesis.loyalty"), bx, y, MUTED, false);
            g.fill(bx + 50, y + 2, bx + 150, y + 7, DIM);
            g.fill(bx + 50, y + 2, bx + 50 + e.bond().loyalty(), y + 7, e.bond().loyalty() < 25 ? BLOOD : GOOD);
            Component state;
            if (e.bond().mission().isPresent()) {
                long hours = Math.max(0, (e.bond().missionEnds() - now) / 1000L);
                state = Component.translatable("gui.vtm.nemesis.on_mission", Component.translatable("gui.vtm.nemesis.mission." + e.bond().mission().get()), hours);
            } else if (e.bond().wakeAt() > now) {
                state = Component.translatable("gui.vtm.nemesis.recovering");
            } else {
                state = Component.translatable("gui.vtm.nemesis.at_side");
            }
            g.drawString(font, state, left + 10, y + 13, MUTED, false);
            MutableComponent traits = Component.empty();
            for (String id : e.core().traits()) {
                if (!traits.getSiblings().isEmpty()) {
                    traits.append(", ");
                }
                traits.append(Component.translatable("trait.vtm." + id));
            }
            g.drawString(font, font.plainSubstrByWidth(traits.getString(), width_ - 24), left + 10, y + 23, DIM + 0x505050, false);
            y += 56;
        }
    }

    // ---------------------------------------------------------------- helpers

    private int paragraph(GuiGraphics g, Component text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : font.split(text, w)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The panel is drawn in render(); the default would blur it.
    }

    /** Opens the screen and asks the server for a snapshot. */
    public static void open() {
        PacketDistributor.sendToServer(NemesisActionPayload.refresh());
        Minecraft.getInstance().setScreen(new NemesisScreen());
    }
}
