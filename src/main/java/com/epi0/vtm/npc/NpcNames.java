package com.epi0.vtm.npc;

import net.minecraft.util.RandomSource;

/** Plain, period-neutral names for the people of the night. */
public final class NpcNames {
    private static final String[] FIRST = {
            "Adrian", "Beatrice", "Cormac", "Delphine", "Edmund", "Fiona", "Gideon", "Helena", "Ivo", "Juno",
            "Konrad", "Lucia", "Marcus", "Nadia", "Oren", "Petra", "Quentin", "Rosalind", "Silas", "Talia",
            "Ulric", "Vera", "Wystan", "Xanthe", "Yusuf", "Zora", "Aldous", "Bianca", "Cassius", "Dorothea",
            "Elias", "Freya", "Gareth", "Hana", "Ignatius", "Jolene", "Kieran", "Livia", "Mateo", "Nell"};
    private static final String[] LAST = {
            "Ashworth", "Blackwood", "Calloway", "Draycott", "Ellery", "Fairchild", "Greaves", "Hawthorne", "Ironside",
            "Jessop", "Kestrel", "Lockhart", "Marlowe", "Nightingale", "Oakes", "Pemberton", "Quill", "Rookwood",
            "Sinclair", "Thorne", "Underhill", "Vance", "Whitlock", "Yarrow", "Zeller", "Abbot", "Brandt", "Corvin",
            "Dunmore", "Everhart"};

    public static String random(RandomSource random) {
        return FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
    }

    private NpcNames() {}
}
