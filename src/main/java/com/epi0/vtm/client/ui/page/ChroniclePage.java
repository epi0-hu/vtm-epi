package com.epi0.vtm.client.ui.page;

import com.epi0.vtm.client.ui.ClientWorld;
import com.epi0.vtm.client.ui.Page;
import com.epi0.vtm.client.ui.PageId;
import com.epi0.vtm.client.ui.Rect;
import com.epi0.vtm.client.ui.Scroll;
import com.epi0.vtm.client.ui.Theme;
import com.epi0.vtm.client.ui.Ui;
import com.epi0.vtm.nemesis.ChronicleEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What has happened in the night, newest first. Public news is coloured by whoever it concerns; rumour is grey. */
public final class ChroniclePage implements Page {
    private final Scroll scroll = new Scroll();

    @Override
    public PageId id() {
        return PageId.CHRONICLE;
    }

    @Override
    public boolean wheel(double notches) {
        return scroll.wheel(notches);
    }

    @Override
    public void render(Ui ui, Rect area, Hub hub) {
        List<ChronicleEntry> news = new ArrayList<>(ClientWorld.snapshot().chronicle());
        Collections.reverse(news);
        if (news.isEmpty()) {
            ui.paragraph(Component.translatable("gui.vtm.nemesis.no_news"), area.x(), area.y() + 2, area.w(), Theme.MUTED);
            return;
        }
        int w = area.w() - 10;
        List<Component> lines = new ArrayList<>();
        int total = 0;
        for (ChronicleEntry entry : news) {
            lines.add(line(entry));
            total += ui.measure(lines.get(lines.size() - 1), w) + 3;
        }
        scroll.begin(ui, area, total);
        int y = scroll.top();
        for (int i = 0; i < news.size(); i++) {
            ChronicleEntry entry = news.get(i);
            int color = entry.publicNews() ? entry.faction().map(Common::factionColor).orElse(Theme.TEXT) : Theme.MUTED;
            int h = ui.paragraph(lines.get(i), area.x() + 2, y, w, color);
            y += h + 3;
        }
        scroll.end(ui);
    }

    private static Component line(ChronicleEntry entry) {
        Object[] args = entry.args().stream().map(a -> a.startsWith("faction.") ? (Object) Component.translatable(a) : a).toArray();
        return Component.translatable("gui.vtm.nemesis.news_line", entry.time() / 24000L + 1,
                Component.translatable("chronicle.vtm." + entry.key(), args));
    }
}
