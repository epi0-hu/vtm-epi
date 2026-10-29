package com.epi0.vtm.item;

import java.util.Locale;

/** What a garment is for. Wearing enough of one kind does something (see {@link Garb}). */
public enum Attire {
    /** Everyday clothes: nobody looks twice. */
    STREET,
    /** Evening wear for Elysium and the courts. */
    FORMAL,
    /** Hooded robes of the occult. */
    ROBE,
    /** Made to be hit in. */
    WARD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
