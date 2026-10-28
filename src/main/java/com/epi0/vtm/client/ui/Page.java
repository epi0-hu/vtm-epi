package com.epi0.vtm.client.ui;

import net.minecraft.network.chat.Component;

/** One page of the codex. Pages draw themselves every frame (see {@link Ui}) and keep their own state between frames. */
public interface Page {
    PageId id();

    /** Draws the page inside {@code area}. */
    void render(Ui ui, Rect area, Hub hub);

    /** A line for the footer: what can be done here. */
    default Component hint() {
        return Component.empty();
    }

    /** Called when the page is brought to the front. */
    default void onShow() {}

    default void tick() {}

    /** The mouse wheel turned over the content. Returns whether the page used it. */
    default boolean wheel(double notches) {
        return false;
    }

    /** A key was pressed while this page is showing. Returns whether the page used it. */
    default boolean key(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** Services the pages can ask of the codex that shows them. */
    interface Hub {
        void show(PageId page);

        void refresh();
    }
}
