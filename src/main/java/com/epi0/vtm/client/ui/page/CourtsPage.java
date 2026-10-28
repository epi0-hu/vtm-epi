package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ui.ClientWorld;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Scroll;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.nemesis.NemesisViews;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/** The courts of the sects and who sits where. Seats you cannot name show only that they are held. */
public final class CourtsPage implements Page {
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.COURTS;
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        List<NemesisViews.Court> courts = ClientWorld.snapshot().courts();
        if (courts.isEmpty()) {
            ui.paragraph(Component.translatable("gui.vtm.nemesis.no_courts"), area.x(), area.y() + 2, area.w(), Theme.MUTED);
            return;
        }
        int tallest = courts.stream().mapToInt(c -> 18 + c.seats().size() * 11 + 20).max().orElse(0);
        scroll.begin(ui, area, tallest);
        // Wide screens set the courts side by side; narrow ones give each court at least 100 pixels and scroll across.
        int cols = Math.max(1, Math.min(courts.size(), area.w() / 100));
        int colW = (area.w() - 6) / cols;
        int row = 0;
        int rowTop = scroll.top();
        int rowBottom = rowTop;
        for (int c = 0; c < courts.size(); c++) {
            if (c > 0 && c % cols == 0) {
                rowTop = rowBottom + 8;
                row++;
            }
            NemesisViews.Court court = courts.get(c);
            int x = area.x() + (c % cols) * colW;
            int y = rowTop;
            int color = Common.factionColor(court.faction());
            ui.shadow(Common.factionName(court.faction()), x, y, color);
            ui.g().fill(x, y + 10, x + colW - 8, y + 11, color);
            y += 16;
            String lastRole = "";
            for (NemesisViews.Seat seat : court.seats()) {
                if (!seat.role().equals(lastRole)) {
                    lastRole = seat.role();
                    ui.text(Common.roleTitle(court.faction(), seat.role()), x, y, Theme.MUTED);
                    y += 10;
                }
                Component who;
                int whoColor;
                Optional<NemesisViews.Entry> known = seat.known().flatMap(Common::entry);
                if (!seat.filled()) {
                    who = Component.translatable("gui.vtm.nemesis.vacant");
                    whoColor = Theme.DIM;
                } else if (seat.known().isPresent()) {
                    who = known.map(en -> Common.displayName(en.core())).orElse(Component.literal("?"));
                    whoColor = known.map(en -> en.core().status().equals("active") ? Theme.TEXT : Theme.MUTED).orElse(Theme.TEXT);
                } else {
                    who = Component.translatable("gui.vtm.nemesis.unknown_holder");
                    whoColor = Theme.MUTED;
                }
                Rect r = new Rect(x + 4, y - 1, colW - 14, 10);
                if (known.isPresent()) {
                    java.util.UUID target = known.get().id();
                    ui.row(r, false, () -> {
                        Common.selected = target;
                        hub.show(PageId.DOSSIERS);
                    }, Component.translatable("gui.vtm.nemesis.open_dossier"));
                }
                ui.clipped(who, x + 6, y, colW - 18, whoColor);
                y += 10;
            }
            rowBottom = Math.max(rowBottom, y);
        }
        scroll.end(ui);
    }
}
