package com.epi0.vtm.client.ui;

import java.util.Locale;

/** Every page of the codex, in the order the side rail lists them. */
public enum PageId {
    OVERVIEW(Group.SELF, Icon.DROP), DISCIPLINES(Group.SELF, Icon.BOLT), POWERS(Group.SELF, Icon.FANGS),
    CLAN(Group.SELF, Icon.STAR), SECT(Group.SELF, Icon.BANNER),
    DOSSIERS(Group.WORLD, Icon.SKULL), COURTS(Group.WORLD, Icon.CROWN), DOMAINS(Group.WORLD, Icon.MAP),
    COURT(Group.WORLD, Icon.THRONE), CHRONICLE(Group.WORLD, Icon.SCROLL), COTERIE(Group.WORLD, Icon.PEOPLE);

    public enum Group { SELF, WORLD }

    private final Group group;
    private final Icon icon;

    PageId(Group group, Icon icon) {
        this.group = group;
        this.icon = icon;
    }

    public Group group() {
        return group;
    }

    public Icon icon() {
        return icon;
    }

    public String key() {
        return "gui.vtm.page." + name().toLowerCase(Locale.ROOT);
    }
}
