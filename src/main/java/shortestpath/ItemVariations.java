package shortestpath;

import net.runelite.api.gameval.ItemID;
import lombok.Getter;

public enum ItemVariations {
    AIR_RUNE(ItemID.AIRRUNE,
        ItemID.MISTRUNE,
        ItemID.DUSTRUNE,
        ItemID.SMOKERUNE),
    ARDOUGNE_CLOAK(ItemID.ARDY_CAPE_EASY,
        ItemID.ARDY_CAPE_MEDIUM,
        ItemID.ARDY_CAPE_HARD,
        ItemID.ARDY_CAPE_ELITE,
        ItemID.SKILLCAPE_MAX_ARDY),
    ASTRAL_RUNE(ItemID.ASTRALRUNE),
    AXE(ItemID.BRONZE_AXE,
        ItemID.IRON_AXE,
        ItemID.STEEL_AXE,
        ItemID.BLACK_AXE,
        ItemID.MITHRIL_AXE,
        ItemID.ADAMANT_AXE,
        ItemID.RUNE_AXE,
        ItemID.DRAGON_AXE,
        ItemID.INFERNAL_AXE,
        ItemID._3A_AXE,
        ItemID.CRYSTAL_AXE),
    BANANA(ItemID.BANANA),
    BLOOD_RUNE(ItemID.BLOODRUNE),
    BROWN_APRON(ItemID.BROWN_APRON,
        ItemID.GOLDEN_APRON,
        ItemID.SKILLCAPE_CRAFTING,
        ItemID.SKILLCAPE_CRAFTING_TRIMMED,
        ItemID.SKILLCAPE_CRAFTING_HOOD),
    BRYOPHYTAS_STAFF(ItemID.NATURE_STAFF_CHARGED),
    CAPESLOT(ItemID.CASTLEWARS_HOOD_SARADOMIN_PRIZE, // TODO: also use slot or item category
        ItemID.CASTLEWARS_HOOD_ZAMORAK_PRIZE),
    CLIMBING_BOOTS(ItemID.DEATH_CLIMBINGBOOTS,
        ItemID.CLIMBING_BOOTS_G),
    COINS(ItemID.COINS),
    CROSSBOW(ItemID.CROSSBOW,
        ItemID.PHOENIX_CROSSBOW,
        ItemID.DTTD_BONE_CROSSBOW,
        ItemID.HUNTING_CROSSBOW,
        ItemID.XBOWS_CROSSBOW_BRONZE,
        ItemID.XBOWS_CROSSBOW_IRON,
        ItemID.XBOWS_CROSSBOW_STEEL,
        ItemID.XBOWS_CROSSBOW_MITHRIL,
        ItemID.XBOWS_CROSSBOW_ADAMANTITE,
        ItemID.XBOWS_CROSSBOW_RUNITE,
        ItemID.XBOWS_CROSSBOW_DRAGON,
        ItemID.DRAGONHUNTER_XBOW,
        ItemID.BARROWS_KARIL_WEAPON,
        ItemID.BARROWS_KARIL_WEAPON_BROKEN,
        ItemID.BARROWS_KARIL_WEAPON_25,
        ItemID.BARROWS_KARIL_WEAPON_50,
        ItemID.BARROWS_KARIL_WEAPON_75,
        ItemID.BARROWS_KARIL_WEAPON_100,
        ItemID.ACB,
        ItemID.ZARYTE_XBOW),
    DUST_BATTLESTAFF(ItemID.DUST_BATTLESTAFF,
        ItemID.MYSTIC_DUST_BATTLESTAFF),
    DUST_RUNE(ItemID.DUSTRUNE),
    DUSTY_KEY(ItemID.DUSTY_KEY),
    EARTH_RUNE(ItemID.EARTHRUNE,
        ItemID.DUSTRUNE,
        ItemID.MUDRUNE,
        ItemID.LAVARUNE),
    ECTO_TOKEN(ItemID.ECTOTOKEN),
    FIRE_RUNE(ItemID.FIRERUNE,
        ItemID.SMOKERUNE,
        ItemID.STEAMRUNE,
        ItemID.LAVARUNE),
    GAMES_NECKLACE(ItemID.NECKLACE_OF_MINIGAMES_8,
        ItemID.NECKLACE_OF_MINIGAMES_7,
        ItemID.NECKLACE_OF_MINIGAMES_6,
        ItemID.NECKLACE_OF_MINIGAMES_5,
        ItemID.NECKLACE_OF_MINIGAMES_4,
        ItemID.NECKLACE_OF_MINIGAMES_3,
        ItemID.NECKLACE_OF_MINIGAMES_2,
        ItemID.NECKLACE_OF_MINIGAMES_1),
    GLOWING_FUNGUS(ItemID.GLOWING_FUNGUS),
    HEADSLOT(ItemID.CASTLEWARS_CLOAK_SARADOMIN_PRIZE, // TODO: also use slot or item category
        ItemID.CASTLEWARS_CLOAK_ZAMORAK_PRIZE),
    LAVA_BATTLESTAFF(ItemID.LAVA_BATTLESTAFF,
        ItemID.MYSTIC_LAVA_STAFF),
    LAVA_RUNE(ItemID.LAVARUNE),
    LAW_RUNE(ItemID.LAWRUNE),
    MAX_CAPE(ItemID.SKILLCAPE_MAX,
        ItemID.SKILLCAPE_MAX_WORN,
        ItemID.SKILLCAPE_MAX_FIRECAPE,
        ItemID.SKILLCAPE_MAX_FIRECAPE_DUMMY,
        ItemID.SKILLCAPE_MAX_FIRECAPE_TROUVER,
        ItemID.SKILLCAPE_MAX_SARADOMIN,
        ItemID.SKILLCAPE_MAX_ZAMORAK,
        ItemID.SKILLCAPE_MAX_GUTHIX,
        ItemID.SKILLCAPE_MAX_ANMA,
        ItemID.SKILLCAPE_MAX_ARDY,
        ItemID.SKILLCAPE_MAX_INFERNALCAPE,
        ItemID.SKILLCAPE_MAX_INFERNALCAPE_DUMMY,
        ItemID.SKILLCAPE_MAX_INFERNALCAPE_TROUVER,
        ItemID.SKILLCAPE_MAX_SARADOMIN2,
        ItemID.SKILLCAPE_MAX_SARADOMIN2_TROUVER,
        ItemID.SKILLCAPE_MAX_ZAMORAK2,
        ItemID.SKILLCAPE_MAX_ZAMORAK2_TROUVER,
        ItemID.SKILLCAPE_MAX_GUTHIX2,
        ItemID.SKILLCAPE_MAX_GUTHIX2_TROUVER,
        ItemID.SKILLCAPE_MAX_ASSEMBLER,
        ItemID.SKILLCAPE_MAX_ASSEMBLER_TROUVER,
        ItemID.SKILLCAPE_MAX_MYTHICAL,
        ItemID.SKILLCAPE_MAX_ASSEMBLER_MASORI,
        ItemID.SKILLCAPE_MAX_ASSEMBLER_MASORI_TROUVER,
        ItemID.SKILLCAPE_MAX_DIZANAS,
        ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER),
    MAX_HOOD(ItemID.SKILLCAPE_MAX_HOOD,
        ItemID.SKILLCAPE_MAX_HOOD_FIRECAPE,
        ItemID.SKILLCAPE_MAX_HOOD_SARADOMIN,
        ItemID.SKILLCAPE_MAX_HOOD_ZAMORAK,
        ItemID.SKILLCAPE_MAX_HOOD_GUTHIX,
        ItemID.SKILLCAPE_MAX_HOOD_ANMA,
        ItemID.SKILLCAPE_MAX_HOOD_ARDY,
        ItemID.SKILLCAPE_MAX_HOOD_INFERNALCAPE,
        ItemID.SKILLCAPE_MAX_HOOD_SARADOMIN2,
        ItemID.SKILLCAPE_MAX_HOOD_ZAMORAK2,
        ItemID.SKILLCAPE_MAX_HOOD_GUTHIX2,
        ItemID.SKILLCAPE_MAX_HOOD_ASSEMBLER,
        ItemID.SKILLCAPE_MAX_HOOD_MYTHICAL,
        ItemID.SKILLCAPE_MAX_HOOD_ASSEMBLER_MASORI,
        ItemID.SKILLCAPE_MAX_HOOD_DIZANAS),
    MAZE_KEY(ItemID.MELZARKEY),
    MIND_RUNE(ItemID.MINDRUNE),
    MIST_BATTLESTAFF(ItemID.MIST_BATTLESTAFF,
        ItemID.MYSTIC_MIST_BATTLESTAFF),
    MIST_RUNE(ItemID.MISTRUNE),
    MITH_GRAPPLE(ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE),
    MUD_BATTLESTAFF(ItemID.MUD_BATTLESTAFF,
        ItemID.MYSTIC_MUD_STAFF),
    MUD_RUNE(ItemID.MUDRUNE),
    MYSTIC_DUST_STAFF(ItemID.MYSTIC_DUST_BATTLESTAFF),
    MYSTIC_LAVA_STAFF(ItemID.MYSTIC_LAVA_STAFF),
    MYSTIC_MIST_STAFF(ItemID.MYSTIC_MIST_BATTLESTAFF),
    MYSTIC_MUD_STAFF(ItemID.MYSTIC_MUD_STAFF),
    MYSTIC_SMOKE_STAFF(ItemID.MYSTIC_SMOKE_BATTLESTAFF),
    MYSTIC_STEAM_STAFF(ItemID.MYSTIC_STEAM_BATTLESTAFF),
    NATURE_RUNE(ItemID.NATURERUNE),
    ROPE(ItemID.ROPE),
    SHANTAY_PASS(ItemID.SHANTAY_PASS),
    SKAVID_MAP(ItemID.SKAVIDMAP),
    SMOKE_BATTLESTAFF(ItemID.SMOKE_BATTLESTAFF,
        ItemID.MYSTIC_SMOKE_BATTLESTAFF),
    SMOKE_RUNE(ItemID.SMOKERUNE),
    SOUL_RUNE(ItemID.SOULRUNE),
    STAFF_OF_AIR(ItemID.STAFF_OF_AIR,
        ItemID.AIR_BATTLESTAFF,
        ItemID.MIST_BATTLESTAFF,
        ItemID.DUST_BATTLESTAFF,
        ItemID.SMOKE_BATTLESTAFF,
        ItemID.MYSTIC_MIST_BATTLESTAFF,
        ItemID.MYSTIC_DUST_BATTLESTAFF,
        ItemID.MYSTIC_SMOKE_BATTLESTAFF),
    STAFF_OF_EARTH(ItemID.STAFF_OF_EARTH,
        ItemID.EARTH_BATTLESTAFF,
        ItemID.DUST_BATTLESTAFF,
        ItemID.MUD_BATTLESTAFF,
        ItemID.LAVA_BATTLESTAFF,
        ItemID.MYSTIC_DUST_BATTLESTAFF,
        ItemID.MYSTIC_MUD_STAFF,
        ItemID.MYSTIC_LAVA_STAFF),
    STAFF_OF_FIRE(ItemID.STAFF_OF_FIRE,
        ItemID.FIRE_BATTLESTAFF,
        ItemID.SMOKE_BATTLESTAFF,
        ItemID.STEAM_BATTLESTAFF,
        ItemID.LAVA_BATTLESTAFF,
        ItemID.MYSTIC_SMOKE_BATTLESTAFF,
        ItemID.MYSTIC_STEAM_BATTLESTAFF,
        ItemID.MYSTIC_LAVA_STAFF),
    STAFF_OF_WATER(ItemID.STAFF_OF_WATER,
        ItemID.WATER_BATTLESTAFF,
        ItemID.MIST_BATTLESTAFF,
        ItemID.MUD_BATTLESTAFF,
        ItemID.STEAM_BATTLESTAFF,
        ItemID.MYSTIC_MIST_BATTLESTAFF,
        ItemID.MYSTIC_MUD_STAFF,
        ItemID.MYSTIC_STEAM_BATTLESTAFF),
    STEAM_BATTLESTAFF(ItemID.STEAM_BATTLESTAFF,
        ItemID.MYSTIC_STEAM_BATTLESTAFF),
    STEAM_RUNE(ItemID.STEAMRUNE),
    TOME_OF_EARTH(ItemID.TOME_OF_EARTH),    
    TOME_OF_FIRE(ItemID.TOME_OF_FIRE),
    TOME_OF_WATER(ItemID.TOME_OF_WATER),
    WATER_RUNE(ItemID.WATERRUNE,
        ItemID.MISTRUNE,
        ItemID.MUDRUNE,
        ItemID.STEAMRUNE),
    ;

    @Getter
    private final int[] ids;

    ItemVariations(int... ids) {
        this.ids = ids;
    }

    public static int[] staves(ItemVariations itemVariation) {
        if (itemVariation == null) {
            return null;
        }
        switch (itemVariation) {
            case AIR_RUNE:
                return STAFF_OF_AIR.ids;
            case DUST_RUNE:
                return DUST_BATTLESTAFF.ids;
            case EARTH_RUNE:
                return STAFF_OF_EARTH.ids;
            case FIRE_RUNE:
                return STAFF_OF_FIRE.ids;
            case LAVA_RUNE:
                return LAVA_BATTLESTAFF.ids;
            case MIST_RUNE:
                return MIST_BATTLESTAFF.ids;
            case MUD_RUNE:
                return MUD_BATTLESTAFF.ids;
            case NATURE_RUNE:
                return BRYOPHYTAS_STAFF.ids;
            case SMOKE_RUNE:
                return SMOKE_BATTLESTAFF.ids;
            case STEAM_RUNE:
                return STEAM_BATTLESTAFF.ids;
            case WATER_RUNE:
                return STAFF_OF_WATER.ids;
            default:
                return null;
        }
    }

    public static int[] offhands(ItemVariations itemVariation) {
        if (itemVariation == null) {
            return null;
        }
        switch (itemVariation) {
            case EARTH_RUNE:
                return TOME_OF_EARTH.ids;
            case FIRE_RUNE:
                return TOME_OF_FIRE.ids;
            case WATER_RUNE:
                return TOME_OF_WATER.ids;
            default:
                return null;
        }
    }

    public static ItemVariations fromName(String name) {
        for (ItemVariations itemVariations : ItemVariations.values()) {
            if (itemVariations.name().equals(name)) {
                return itemVariations;
            }
        }
        return null;
    }
}