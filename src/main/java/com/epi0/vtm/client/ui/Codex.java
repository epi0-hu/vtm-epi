package com.epi0.vtm.client.ui;

import com.epi0.vtm.client.ClientModEvents;
import com.epi0.vtm.client.ui.page.ChroniclePage;
import com.epi0.vtm.client.ui.page.ClanPage;
import com.epi0.vtm.client.ui.page.CourtPage;
import com.epi0.vtm.client.ui.page.CourtsPage;
import com.epi0.vtm.client.ui.page.CoteriePage;
import com.epi0.vtm.client.ui.page.DisciplinesPage;
import com.epi0.vtm.client.ui.page.DomainsPage;
import com.epi0.vtm.client.ui.page.DossiersPage;
import com.epi0.vtm.client.ui.page.OverviewPage;
import com.epi0.vtm.client.ui.page.PowersPage;
import com.epi0.vtm.client.ui.page.SectPage;
import com.epi0.vtm.network.NemesisActionPayload;
import com.epi0.vtm.vampire.VampireData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Vampire Codex: one screen for everything a vampire keeps track of. A rail on the left lists the pages (what
 * you are, then what the world is like); the chosen page fills the rest. Opens with {@code V} (the character) or
 * {@code N} (the world).
 */
public class Codex extends Screen implements Page.Hub {
    private static final int RAIL = 80;
    private static final int RAIL_COMPACT = 24;
    private static PageId lastSelf = PageId.OVERVIEW;
    private static PageId lastWorld = PageId.DOSSIERS;

    private final Map<PageId, Page> pages = new EnumMap<>(PageId.class);
    private final Ui ui = new Ui();
    private PageId current;
    private Rect frame = new Rect(0, 0, 0, 0);
    private int refreshTimer;

    public Codex(PageId start) {
        super(Component.translatable("gui.vtm.codex"));
        pages.put(PageId.OVERVIEW, new OverviewPage());
        pages.put(PageId.DISCIPLINES, new DisciplinesPage());
        pages.put(PageId.POWERS, new PowersPage());
        pages.put(PageId.CLAN, new ClanPage());
        pages.put(PageId.SECT, new SectPage());
        pages.put(PageId.DOSSIERS, new DossiersPage());
        pages.put(PageId.COURTS, new CourtsPage());
        pages.put(PageId.DOMAINS, new DomainsPage());
        pages.put(PageId.COURT, new CourtPage());
        pages.put(PageId.CHRONICLE, new ChroniclePage());
        pages.put(PageId.COTERIE, new CoteriePage());
        this.current = start;
        remember(start);
    }

    public static void open(PageId page) {
        Minecraft mc = Minecraft.getInstance();
        if (page.group() == PageId.Group.WORLD) {
            PacketDistributor.sendToServer(NemesisActionPayload.refresh());
        }
        mc.setScreen(new Codex(page));
    }

    /** Reopens where you left off in the character pages ({@code V}) or the world pages ({@code N}). */
    public static void openSelf() {
        open(lastSelf);
    }

    public static void openWorld() {
        open(lastWorld);
    }

    private static void remember(PageId page) {
        if (page.group() == PageId.Group.WORLD) {
            lastWorld = page;
        } else {
            lastSelf = page;
        }
    }

    public PageId current() {
        return current;
    }

    // ---------------------------------------------------------------- Hub

    @Override
    public void show(PageId page) {
        if (page != current) {
            current = page;
            remember(page);
            ui.clearFocus();
            pages.get(page).onShow();
            if (page.group() == PageId.Group.WORLD) {
                refresh();
            }
        }
    }

    @Override
    public void refresh() {
        PacketDistributor.sendToServer(NemesisActionPayload.refresh());
    }

    public void onSnapshot() {
        // Pages draw from ClientWorld every frame; nothing to rebuild.
    }

    // ---------------------------------------------------------------- layout

    private boolean compact() {
        return width < 380;
    }

    private void layout() {
        int w = Math.min(width - 12, 452);
        int h = Math.min(height - 12, 264);
        frame = new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    private Rect rail() {
        int rw = compact() ? RAIL_COMPACT : RAIL;
        return new Rect(frame.x() + 4, frame.y() + 26, rw, frame.h() - 30);
    }

    private Rect content() {
        Rect rail = rail();
        int x = rail.right() + 6;
        return new Rect(x, frame.y() + 26, frame.right() - 6 - x, frame.h() - 26 - 18);
    }

    @Override
    protected void init() {
        layout();
        pages.get(current).onShow();
        if (current.group() == PageId.Group.WORLD) {
            refresh();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        pages.get(current).tick();
        if (current.group() == PageId.Group.WORLD && ++refreshTimer % 100 == 0) {
            refresh();
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return button == 0 && ui.click(mx, my) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return content().contains(mx, my) && pages.get(current).wheel(dy) || super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientModEvents.OPEN_MENU.matches(key, scan) || ClientModEvents.OPEN_NEMESES.matches(key, scan)) {
            onClose();
            return true;
        }
        if (pages.get(current).key(key, scan, mods)) {
            return true;
        }
        PageId[] all = PageId.values();
        if (key == GLFW.GLFW_KEY_TAB) {
            ui.cycleFocus((mods & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            if (ui.activateFocus()) {
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_PAGE_UP) {
            show(all[Math.floorMod(current.ordinal() - 1, all.length)]);
            return true;
        }
        if (key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            show(all[Math.floorMod(current.ordinal() + 1, all.length)]);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, Theme.BACKDROP);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        layout();
        renderBackground(g, mx, my, partial);
        ui.begin(g, mx, my);
        Draw.panel(g, frame);
        renderHeader(g);
        renderRail();
        Rect content = content();
        Draw.inset(g, content.inset(-2));
        pages.get(current).render(ui, content.inset(4), this);
        renderFooter(g, content);
        ui.end();
    }

    private void renderHeader(GuiGraphics g) {
        Icon.DROP.draw(g, frame.x() + 8, frame.y() + 7, Theme.ACCENT, 1);
        g.drawString(font, Component.translatable("gui.vtm.codex"), frame.x() + 20, frame.y() + 8, Theme.TEXT, false);
        Component page = Component.translatable(current.key());
        g.drawString(font, page, frame.x() + 20 + font.width(Component.translatable("gui.vtm.codex")) + 8, frame.y() + 8, Theme.GOLD, false);
        Rect close = new Rect(frame.right() - 18, frame.y() + 5, 13, 13);
        ui.button(close, Component.literal("x"), Ui.Style.QUIET, true, this::onClose, Component.translatable("gui.vtm.close"));
        Draw.divider(g, frame.x() + 4, frame.y() + 22, frame.w() - 8);
    }

    private void renderRail() {
        Rect rail = rail();
        GuiGraphics g = ui.g();
        int y = rail.y();
        PageId.Group group = null;
        boolean vampire = VampireData.of(minecraft.player).isVampire();
        for (PageId id : PageId.values()) {
            if (id.group() != group) {
                group = id.group();
                if (!compact()) {
                    g.drawString(font, Component.translatable("gui.vtm.group." + group.name().toLowerCase(java.util.Locale.ROOT)), rail.x() + 2, y + 1, Theme.DIM, false);
                }
                y += compact() ? 3 : 10;
            }
            Rect row = new Rect(rail.x(), y, rail.w(), 13);
            boolean selected = id == current;
            Component name = Component.translatable(id.key());
            boolean enabled = vampire || id == PageId.OVERVIEW || id.group() == PageId.Group.WORLD;
            ui.row(row, selected, () -> show(id), compact() ? name : null);
            id.icon().draw(g, row.x() + 4, row.y() + 3, selected ? Theme.GOLD : enabled ? Theme.MUTED : Theme.DIM, 1);
            if (!compact()) {
                g.drawString(font, name, row.x() + 16, row.y() + 3, selected ? Theme.TEXT : enabled ? Theme.MUTED : Theme.DIM, false);
            }
            y += 14;
        }
    }

    private void renderFooter(GuiGraphics g, Rect content) {
        int y = frame.bottom() - 13;
        Draw.divider(g, frame.x() + 4, y - 3, frame.w() - 8);
        Component hint = pages.get(current).hint();
        if (hint.getString().isEmpty()) {
            hint = Component.translatable("gui.vtm.hint.default");
        }
        Draw.clipped(g, font, hint, content.x(), y, content.w() - 44, Theme.DIM);
        g.drawString(font, (current.ordinal() + 1) + "/" + PageId.values().length, frame.right() - 8 - font.width(PageId.values().length + "/" + PageId.values().length), y, Theme.DIM, false);
    }
}
