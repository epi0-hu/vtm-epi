# Vampire: The Masquerade (NeoForge 1.21.1)

A multiplayer-compatible VtM mod with its own spin on the V5 rules.

## Requirements
- JDK 21
- NeoForge 1.21.1 (`neo_version` in `gradle.properties`)
- GeckoLib 4.9.3 (local jar in `libs/`, used from the NPC phase onwards)

## Build / run
```
./gradlew build
./gradlew runClient
./gradlew runServer
```

## Status
Phases 1 (core vampire data), 2 (character sheet GUI) and discipline powers are implemented.

- **Player state** (`vampire/VampireData`): species, clan, generation, blood potency, blood, humanity, stains,
  experience, discipline levels. A NeoForge data attachment, kept through death, server-authoritative and
  synced to the owning client by `SyncVampirePayload`.
- **Clans and disciplines are datapack registries**: `data/<ns>/vtm/clan/*.json`, `data/<ns>/vtm/discipline/*.json`.
  All V5 clans (plus Caitiff and Thin-Blood) and the 12 disciplines ship as data. Display text is in the lang file.
- **Rules** live in `vampire/VampireRules` (our spin on V5). Tunables are in the server config (`vtm-server.toml`).
- **Loop**: passive blood loss, sunlight, blood healing (no food), starvation penalties, passive XP, biting
  (sneak + empty hand on a creature), humanity stains for killing innocents.
- **GUI**: press `V` for the character sheet (overview, disciplines with XP spending, clan). Blood bar replaces the food bar.
- **Commands** (op): `/vtm embrace|mortal|set|discipline|info`.

## Discipline powers
All 12 disciplines have 5 levels of powers (62 in total), defined in `power/Powers.java` (costs, cooldowns and
effects are all there). Celerity, Potence and Fortitude level 1 are passive attribute bonuses that scale with dots;
everything else is an active power that costs blood and has a cooldown.

- `Z` / `X` cycle the armed power, `G` uses it, or pick one in the **Powers** tab of the sheet (`V`).
- Activation is server-validated (`PowerManager`): the client only sends a power id.
- Mind powers (Dominate, Presence, Obfuscate, ...) use `MobControl`: calmed mobs and hidden players are enforced by
  rewriting target changes, so vanilla retargeting cannot undo them.
- Effect durations stretch 20% per Blood Potency above 1.
- **Shapechange** (Protean 5) is real: wolf, bat (flies) and mist (invisible, flying, nearly untouchable but unable
  to act) are infinite effects, so they sync to nearby clients and persist. `FormRenderer` draws the animal in place
  of the player. Holding a form drains blood slowly; toggle powers can always be released.
- **Share the Senses** (Auspex 5) moves your camera into the creature you look at (`SenseLink` /
  `SenseLinkClient`) while your body stands helpless. Being hurt, the host dying or straying, or time ends it.

## Frenzy and clan banes
Frenzy (`Frenzy`) is an effect: stronger and faster, no powers except Quell the Beast, and the vampire feeds on
whatever is near. It triggers from hunger next to prey, or from a clan bane. Clan banes are data: each clan JSON has an
optional `bane` object (`registry/Bane`) combining any of: `frenzy_hunger`, `rage_frenzy`, `vampire_blood_frenzy`,
`bestial_features`, `unease`, `bite_damage`, `bite_self_damage`, `prey_tag`, `blood_gain_multiplier`, `derangement`,
`doomed_minutes`, `grounded_blocks`, `dark_fatigue`; plus `sun_damage_multiplier` on the clan. Where tabletop banes have
no Minecraft equivalent they are mapped to a close analogue (see each clan's `.bane` lang text).

## NPCs
Two kinds of NPC share one base (`npc/VtmNpc`): a name, a look, a faction, a home, a daily rhythm, a bounded
memory of players (`NpcMemory`, the foundation for the Nemesis System), and GeckoLib animations.

- **Civilians** (`CivilianNpc`): stroll near home by day, walk home and sleep at night, flee monsters, hungry vampires
  and players they have seen feeding, frenzying or fighting.
- **Vampires** (`VampireNpc`): a clan (with its passive disciplines and sun vulnerability), a sect from the clan's
  `faction`, and a hunger. They sleep in shelter by day, burn in sunlight, prowl at night, and feed on isolated
  civilians (Sabbat don't bother hiding). Sects that are enemies fight on sight; players who harm an NPC are remembered.
- **Factions** are a datapack registry (`data/<ns>/vtm/faction/*.json`): Camarilla, Anarch Movement, Sabbat,
  Independents, Hunters, Mortals, with `allies`, `enemies` and `hunts_mortals`.
- **Animations** (GeckoLib): idle, walk, run, attack, bite, cower, sleep, talk, on one shared humanoid rig that uses the
  vanilla player skin layout. `tools/gen_npc_assets.py` regenerates the rig, animations and skins (8 civilian looks,
  2 per clan).
- Spawn with the creative tab eggs or `/vtm npc spawn civilian|vampire [clan]`. Civilians spawn at world generation and
  vampires rarely at night (biome modifiers in `data/vtm/neoforge/biome_modifier/`).

## Libraries
GeckoLib 4.9.3 (local jar in `libs/`) is the only runtime library. Citadel and Bookshelf were evaluated and left out:
Citadel overlaps GeckoLib for animation and brings about 17 core mixins and a 3 MB jar; Bookshelf mostly duplicates
NeoForge APIs and needs NeoForge 21.1.209+. Citadel's advanced pathfinding and its tick-rate modifier (real time
dilation for Celerity) remain options if NPC navigation or Celerity ever need them.

## Tests and dev tools
- `./gradlew runGameTestServer`: GameTests in `gametest/VtmGameTests` (data, embrace/learn, passives, feeding, every
  power, mob control, every bane, frenzy, forms, sense link).
- `./gradlew runClient -Pharness=player|npc`: scripted walkthrough of the real client that writes screenshots to
  `run/screenshots/` (player: GUI tabs, HUD, forms, sense link, frenzy; npc: a lineup in every animation). Needs a
  world at `run/saves/harness` and, on a headless machine, `xvfb-run`. Inert without the property.
