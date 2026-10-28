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
Phase 1 (core vampire data) and Phase 2 (character sheet GUI) are implemented.

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

Discipline *powers* are not implemented yet; levels are tracked and purchasable.
