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
- **GUI**: the **Vampire Codex**, one tabbed screen built on a small shared UI toolkit (`client/ui`: `Theme`, `Draw`, `Ui`, `Scroll`, pages). `V` opens it on the character pages (Overview, Disciplines with XP spending, Powers, Clan, Sect) and `N` on the world pages (Dossiers, Courts, Domains, Hunters, Your court, Chronicle, Coterie, Herd, Lineage); each remembers where you left off. `Q` / `E` (or Page Up / Down) change page, `Tab` / `Shift+Tab` move keyboard focus, `Enter` or `Space` activates it, the mouse wheel scrolls, and Up / Down browse dossiers. Narrow windows collapse the side rail to icons. The HUD's blood bar is segmented with hunger pips and the armed power beside it, and replaces the food bar; entering a city shows a banner and, while you are wanted there, a heat plate. Conversations use the same look: click or `Space` skips the typing, `1`-`9` pick an answer.
- **Commands** (op): `/vtm embrace|mortal|set|discipline|info`.

## Discipline powers
All 12 disciplines have 5 levels of powers (62 in total), defined in `power/Powers.java` (costs, cooldowns and
effects are all there). Celerity, Potence and Fortitude level 1 are passive attribute bonuses that scale with dots;
everything else is an active power that costs blood and has a cooldown.

- `Z` / `X` cycle the armed power, `G` uses it, or pick one on the **Powers** page of the Codex (`V`).
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

## Sects and membership
Vampire players can swear themselves to a sect (`sect/SectManager`, state in `sect/Membership`, a synced data
attachment kept through death). Factions in `data/<ns>/vtm/faction/*.json` gained `joinable`, `masquerade` and `ranks`
(the standing each rank needs; titles are the lang keys `faction.<ns>.<name>.rank.<n>`). Camarilla, Anarch and Sabbat
are joinable, with three ranks each.

- **Standing** is kept per faction (-100 to +100), even for sects you do not belong to. Killing an enemy of your sect
  earns standing (+5); killing a member costs you (-15); killing anyone makes their sect resent you (-10); Camarilla
  members lose standing when a mortal sees them feed (-3). Offering your services to a sect vampire earns +5, once per vampire.
- **Joining** needs a vampire with no sect, a joinable faction, and standing above the outcast line (-30). In play you
  win a sect vampire's trust in conversation and ask to be accepted. **Leaving** costs 20 standing.
- **Ranks**: a member is promoted by a vampire of higher rank, once their standing reaches the next rank's threshold.
  Vampire NPCs have a rank too (shown after their name in conversation).
- **Consequences**: vampires of your own or an allied sect are friendly; vampires of an enemy sect attack a sworn
  member on sight; a faction's outcasts, members or not, are hunted by it.
- **GUI**: the Sect tab shows your sect, rank, progress to the next rank, its allies and enemies, and your standing
  with every sect. `/vtm sect join|leave|rank|standing` for ops; `/vtm info` shows the sect.

## The world: lairs, cities, territory and courts
**Lairs** (`world/LairPiece`): three procedural structures, a Sabbat *crypt*, a Camarilla *manor haven* and an Anarch
*warehouse squat*, each with weathering, loot (`data/vtm/loot_table/chests/lair.json`) and a hidden **coffin**
that belongs to the lair's faction. A lair's coffin claims a named vampire when its chunk is live (a tenant of that
faction without a lair, or a newcomer if the faction has room); that vampire is then home there, asleep by day, whenever a
player comes near. The **Coffin** block lets a vampire sleep through the day and sets their haven. Structure
sets, biomes and loot are datapack data.

Two lairs belong to a **clan** (the structure json's optional `clan`; the coffin only takes a vampire of that clan, and
a newcomer is made one): the **Nosferatu warren** (`world/SewerLair`), a brick sewer set into the cave system (it
goes to the highest cave floor with room to stand, well below the surface; where there is no cave it is dug out at
depth) with a channel of black water between two walkways, a nest with the coffin and loot, a side tunnel that ends in
a cave-in, and a ladder shaft up to a manhole on the surface; and **The Asylum** (`world/AsylumClub`), a Malkavian-owned
nightclub in a derelict sanatorium: a forecourt and marquee, a chequered dance floor with a bar and DJ stage, a VIP
gallery railed over the floor, padded cells below and the owner's quarters behind them. Each is one entry of the
`lairs` / `sewers` structure sets.

**Generation** (`vampire/Lineage`, `VampireRules`): the first time a soul joins a server it rolls its blood once, and the result
is stored with the player (it survives death and mortality): 0.5% 1st generation, 1% 2nd, 3% 3rd, otherwise 4th–13th with
the bulk at the young end. Embracing keeps the rolled generation (thin-bloods are 14th or younger). Older blood has a higher
Blood Potency cap (`15 - generation`, up to 14), a larger blood pool, cheaper and faster-recharging powers, and extra dice
on dialogue checks. Potency is bought with experience (`+ cost xp` beside the potency row on the Overview page, up to the cap).
Every named vampire NPC has a generation too, set by its seat (leaders 5th–8th, lieutenants 8th–10th, enforcers 10th–12th,
agents 11th–13th), shown on dossiers and adding to a Nemesis's power. Vampires of a much older or younger generation than you
open different dialogue: the young pay tribute and offer intel, the old teach, condescend, or can be defied.
The **Lineage** page shows your generation on a 1st-to-13th ladder with what it grants, then every vampire whose generation
you have learned (met or studied), oldest first, with how far above or below you they stand.

**Seeking elders** (generation 6 or older): an elder is rumoured before they are met. Ask any vampire "what have they heard of the
ancients" (or an elder, after you have earned their regard) and you learn a name, how old they are and where they were last
seen, which the Lineage page shows as a rumour with a distance. Find them and an elder grants an **audience** (they must be at
least two generations older than you): tribute (3 blood) wins regard, regard buys lessons (+10 XP a day) and the names of other
elders, and at high regard a Presence check asks for **a measure of their blood**, which lowers your generation by one, once
per elder and never to their own. Each elder you win over makes you stronger, so the ancients are worth the journey.

**Elder agendas** (`nemesis/Elders`): elders do not sit still. Every simulation step an elder who is not on screen may take up a new
haven (a city's haven or another vampire's lair), by agenda: **wanderers** (half of them) drift between every haven, **patrons**
move to the weakest city of their own sect and add strength to it, and **recluses** almost never stir. The agenda is fixed
for each elder and shown on the Lineage page once you have met them. When an elder moves, what you were told about where they
sleep goes stale (the page marks it in red); asking another vampire about the ancients again, or an elder about the others, brings
you word of where they have gone, and moves are written to the chronicle.

**Clothes, robes and armor** (`item/Garb`, `registry/ModItems`): thirteen wearable pieces, all craftable, with textures drawn for the
player model. Wearing *enough of a kind* does something, and the Overview page shows what is active:

| Kind | Pieces | Worn | Effect |
|---|---|---|---|
| Street | flat cap, jacket, trousers, shoes, long duster | any 3 street or formal pieces | **Blending in**: exposure you cause is cut by 40% (never to nothing; getting cleaned up is unaffected) |
| Evening wear | top hat, tailcoat, dress trousers, dress shoes | 3 formal pieces | **Formal**: +1 die on every conversation check |
| Robes | ritual cowl, ritual robe | both | **Robed**: powers cost 15% less blood |
| Ward gear | ward vest, combat boots | any | **Warded**: hunters' blows land a quarter softer; hunters now wear the vest, and sometimes drop the vest or boots (inquisitors half the time) |

Street, evening and robe pieces (and the duster) are **dyeable** with ordinary dyes in the crafting grid. Hats and cowls shade the face under
the existing sunlight rule (any head item stops the sun burning you).

**Hunters shoot** (`item/HunterArms`, `npc/goal/HunterRangedGoal`): every armed hunter carries a **stake crossbow** in the off hand and an
inquisitor a firearm (the **hunter's rifle**). From 5 to 26 blocks (36 for a rifle) with a clear line of sight they stand and fire, and
only draw the sword when you close. Shots are ordinary arrows with the hunter as owner, so the hunters' bonus against vampires, damage
scars and resistances all apply; ward gear cuts their bolts and bullets by a third. Both weapons can be crafted and fired by players
too (no ammunition, they wear out, and a reload delay). Any item in the `vtm:hunter_firearms` item tag counts as a rifle, so a modpack
can give inquisitors another mod's guns with a datapack tag (`{"replace": false, "values": [{"id": "somegun:rifle", "required": false}]}`);
the shot itself is always ours, and a gun mod's own firing code is never called.

**Hunters** (`city/Hunters`): mortals who know what is out there. Every time a vampire is seen feeding, in frenzy, or killing a
citizen, the city's mortals work out a little more of what they are (**exposure**, per player per city, fading slowly; the
second bar on the HUD plate). At 30 an unarmed stranger starts to watch; at 50 armed hunters come for you and will not talk;
at 75 a strike team arrives with an **inquisitor** (tougher, iron-clad) at its head. Hunters hit vampires half again as hard.
Killing the inquisitor breaks the cell (-40 exposure, +3 XP); killing an ordinary hunter makes the others angrier. A
suspicious hunter who is not yet sure can be talked round (Presence) or slipped past (Obfuscate); to a mortal player a hunter
is a source of rumours. While nobody watches, hunters also raid the havens of the city's own vampires and may put one into
torpor or destroy them (chronicle `hunters_raid_*`). NPCs now draw the item in their right hand. `-Pharness=hunters` shows a team.

The **Hunters** page lists every city you know (or are exposed in) with your exposure on a bar marked at the watched, hunted and
strike-team thresholds, whether an inquisitor is in the field or a lodge reinforces the hunters, your heat, and how to lower it.
The **Herd** page lists your vessels and ghouls (`city/HerdData`, kept as you last saw them): trust, whether they gave blood
today, where they are, and whether they have been lost. `-Pharness=codex2` shows both.

**Power exposure** (`power/PowerExposure`): using a power where mortals can see you teaches them what you are. Each power has a
noise (Blood Bolt 12, shapechange 15, Blink 8, a quiet Auspex power 0); the witnesses are mortals within 24 blocks with line of
sight, more than two doubles it, invisibility halves it. Obfuscate and Dominate's Cloud Memory go the other way and lower your
exposure. **Hunters' lodges** (`world/HunterLodge`, `city/Lodges`, the Hunters' Standard block): a palisaded yard with a hall, a
watchtower, a training ground and a standard on a post. A lodge keeps a master and three hunters whenever someone is near; they
are hostile to any vampire the cities within 600 blocks have exposed (50+). While a lodge stands, every city within reach sends
two more hunters and an inquisitor at 60 exposure rather than 75. Kill the master and the lodge is broken for a week: no garrison,
no backing, -40 exposure and +6 XP. `-Pharness=lodge` shows one.

**People** (`npc/Persona`, `Household`, `Gossip`): every mortal has a temperament (cheerful, nervous, brave, suspicious,
lonely, devout, curious) and an age, worked out from their identity. Temperament sets how much trouble frightens them,
how much rapport makes a friend, and whether they take offence easily; the devout can feel a soul that has slipped far.
People whose beds share a floor are a household: they share a surname, are kin, hear at once what befalls one another,
and a death is not forgiven without amends. Neighbours gossip: a bad word (or a good one) travels between mortals who stop
to talk, heats the city a little, and fades. New trades with their own conversations: a priest (confession, once a day,
restores Humanity), a clerk (the ledger), a detective (being questioned when you are wanted). A friend can become your
**vessel** (drink once a day, no harm) and, with deeper trust and some of your blood, a **ghoul**: a retainer who follows and
fights for you. Dialogue trees gain `person` conditions, once-a-day flags (`flag_today`) and housemate arguments.
`-Pharness=people` shows these conversations.

**Metropolis** (`world/MetropolisPiece`, `world/CityPlan`, `world/CityKit`; plan in `docs/huge-cities.md`): a 228 x 228 city of
districts, avenues, towers of up to 13 floors, a church, markets, parks, depots, a wall with eight gates and The Asylum
as a landmark. It is drawn from a plan that is a pure function of its seed, one chunk at a time; a city has its own
`radius`, and its population, patrols and active range scale with it. `-Pharness=metropolis` draws one onto a levelled
site and photographs it. It is rare (`structure_set/metropolises.json`) and needs flat-ish ground in a city biome.

**Cities** (`world/CityPiece`, `city/`): a 64x64 flat plateau of nine plots: a plaza with a fountain and the **Domain
Stone**, four houses, a shop, a nightclub, a watch station and the ruler's haven, joined by lamplit streets. The Domain
Stone registers the city (beds, workplaces, places) once live. People move in: residents take the beds, and the shop,
bar and watch are staffed (shopkeeper, bartender, two armed watchmen). Civilians keep an hourly round (work, the square, the
club for night owls, bed). Trouble raises per-player **heat** (assault +8, feeding in front of citizens +12, frenzy +18,
murder +30; it fades over time): at 30 the watch is suspicious, at 40 ordinary people take fright, at 60 the watch
attacks on sight, at 90 reinforcements come. Shopkeepers, bartenders and watchmen have their own conversations.

**Territory** (`city/Territory`): every city has a ruling faction, a strength (how firmly it is held) and a **warden**, a
named vampire who keeps the haven. After dark the ruler's vampires patrol. A ruler that keeps the Masquerade makes anyone
who feeds in front of the city's people lose standing with it, and its vampires hunt a player the whole city is after.
In the simulation, factions at war take cities from their enemies by weighing a war band against the warden, the city's
strength and the ruler's best fighters; killing or binding a warden shakes a city. Players discover cities by entering them
or by asking around; the Domains tab maps the ones they know.

**Player-run courts** (`court/`): a vampire who is in a sect at its second rank or above, with 40 standing, may claim a
throne that stands empty. Destroying or binding a sect's leader leaves it empty for two nights, during which the
simulation will not fill it. A ruler: appoints and dismisses the court (lieutenants and enforcers) for influence; issues up to
two **decrees** (Masquerade, Open hunting, Tithes, War footing, Peace, Patronage) that change how the faction and its cities
behave; earns **influence** and experience **tribute** from the cities the faction holds; answers **petitions** (a quarrel,
an envoy offering a truce, a refugee, a city under pressure, a tribunal); and must keep the court loyal. A seat-holder with
low loyalty and high ambition may **rebel**, hunting the ruler; beat or spare them to end it, or lose the throne after two
nights. Courts are frozen while their ruler is away. `/vtm court claim|crown|abdicate|influence|petition`.

## The Nemesis System
The world holds a population of **named vampires**. Each is a persistent record (`nemesis/NemesisRecord`, stored with
the overworld in `NemesisData`) that exists whether or not anyone is looking; a `VampireNpc` is only the body it
happens to be standing in. Records are advanced by `NemesisSimulator` while unobserved, bound to a body by
`NemesisManager` when they appear, and hooked into combat by `NemesisEvents`.

- **Who they are**: a name, clan and faction; a level (1 to 20) that grows; **traits** (19 of them: strengths such as Iron Will,
  Deflector, Undying; weaknesses such as Flammable, Brittle, Coward, Hunger-Bound; quirks such as Vengeful, Proud, Grateful)
  with real effects in combat; **kin** (sire/childe, allies, rivals, mirrored on both sides); an **ambition**; and a seat
  in their faction's **court** (leader, 2 lieutenants, 3 enforcers, the rest agents) that sets their sect rank.
- **A living hierarchy**: each step of the simulation (every 1200 ticks, `nemesis.stepTicks`) vampires train and
  level, wake from torpor, fill empty seats from below (a fallen leader starts a chain of promotions), found new
  blood when the dead are not replaced, and fight each other: ambitious lieutenants try coups, enemy sects raid,
  rivals draw each other first. Losers go into torpor or die; kin of the dead turn rival to the victor. All of it is
  written to the **chronicle**. A vampire standing in the world is never simulated offscreen.
- **Defeat is not death**: a lethal blow on a named vampire is survived while they have deaths left (1, +1 for rank, +1 for
  Undying). They then either **kneel** (60%, unless Proud or Coward) or escape into torpor. Fire and sunlight are
  final unless Fire-Hardened, as is a defeat past their limit. Every defeat leaves a **scar** (and an epithet),
  and makes them resist what beat them (up to 30%); full resistance to fire or arrows grows them a new trait.
- **The victor's verdict** (in conversation with a kneeling vampire): execute, spare (respect, and a debt), humiliate
  (hatred), interrogate (learn about them and their kin), or **bind them with your blood**.
- **Feuds**: each pair has enmity, respect, and a tally of defeats, kills and mercies. Beating, humiliating or
  killing their kin raises enmity; at 40 a vampire starts to **hunt you**, after dark, and at 100 they **ambush** you
  with a retinue, out of the dark and with a taunt chosen from your shared history. Outcasts of a sect have a
  **blood hunt** declared on them by its whole court. Killing a player makes a vampire stronger and earns it a name.
- **Knowledge**: you only know what you have learned: a name (heard), who they are (met), traits (seen in combat or
  told), kin (studied). Intel comes from meeting, watching, conversations (civilians and vampires of any sect will tell you
  things) and interrogation. The server sends the Nemesis screen only what you know.
- **Coterie**: up to 3 bound vampires follow you after dark, fight for you, and can be sent on **errands** (spy on a
  faction, hunt a known vampire, gather tribute) that the simulation resolves. Their loyalty drains unless you feed
  them; a vampire who no longer feels the bond turns on you, with a grudge.
- **GUI** (`N`): the world pages of the Codex: Dossiers, Courts (who sits where, as far as you know), Domains (a map of the cities you know), Your court (or the way to one), Chronicle and Coterie.
  `/vtm nemesis list|step|spawn|ambush|feud|reveal|destroy` for ops. Config: `nemesis.enabled`, `stepTicks`,
  `populationPerFaction`, `ambushes`.

## Conversations
Right-click an NPC to talk. Dialogue is data (`data/<ns>/vtm/dialogue/*.json`, reloadable with `/reload`) and the server
runs everything: it picks the tree, evaluates conditions and dice, applies effects, and sends the client only the lines
and choices to show (`DialogueNodePayload`). The client sends back an option index and nothing else, and the server
ignores indices that are hidden, locked or out of range.

- **Trees** have a `when` condition and a `priority`; the highest-priority tree that applies to the NPC and player is
  used, so a Camarilla vampire, a wary civilian and a mortal approaching a vampire each get their own conversation.
  Nodes hold a line (or a list of conditional lines, so greetings can depend on night, history or rapport) and options.
- **Conditions** (all fields optional, all must hold): NPC kind, clan, faction, attitude, night, player species, clan,
  humanity, discipline level, sect membership relative to the NPC (`sect`: member, unaligned, rank, outranks, can_promote, standing), times seen, rapport range, flags, whether the NPC sees the player as a threat. Options use
  `show` (hidden unless true) and `require` (greyed out, with a `locked` hint, unless true).
- **Effects**: rapport, flags, blood given to the player, experience, humanity, standing, `join`, `leave`, `promote`, and `alarm` (the NPC glimpses
  something frightening and remembers the player as a threat).
- **Checks** use the V5 dice pool: d10s, 6+ succeeds, pairs of 10s add two successes, and a vampire rolls Hunger dice.
  A 10 on a Hunger die in a winning roll is a *messy critical* and a 1 on one in a losing roll is a *bestial failure*;
  both make the NPC witness the Beast. The roll is shown under the NPC's line.
- **Memory**: each NPC keeps per-player **rapport** (-10 to +10) and **flags** next to what it has witnessed. Rapport
  moves attitude: a civilian at +3 is a friend and a threat is forgiven at +5; a vampire at -5 turns hostile and refuses
  to talk. Hostile, sleeping, cowering and busy NPCs refuse; walking away, taking damage or the NPC turning hostile ends
  the conversation.
- Shipped trees: civilians (default, wary, friendly), vampires by sect (Camarilla, Anarch, Sabbat, independent, a
  fallback) and vampires speaking to mortals. Vampires teach a trusted vampire (3 XP, once) after three questions.

## Libraries
GeckoLib 4.9.3 (local jar in `libs/`) is the only runtime library. Citadel and Bookshelf were evaluated and left out:
Citadel overlaps GeckoLib for animation and brings about 17 core mixins and a 3 MB jar; Bookshelf mostly duplicates
NeoForge APIs and needs NeoForge 21.1.209+. Citadel's advanced pathfinding and its tick-rate modifier (real time
dilation for Celerity) remain options if NPC navigation or Celerity ever need them.

## Tests and dev tools
- `./gradlew runGameTestServer`: GameTests in `gametest/VtmGameTests` (data, embrace/learn, passives, feeding, every
  power, mob control, every bane, frenzy, forms, sense link, NPC behaviour, conversations, sects, the nemesis simulation and its combat rules, lairs, cities, territory, courts).
- `./gradlew runClient -Pharness=player|npc|dialogue|sect|nemesis|people|hunters|lodge|codex2|lairs|sewer|asylum|city|domains|court`: scripted walkthrough of the real client that writes screenshots to
  `run/screenshots/` (player: every Codex page, HUD, city banner, forms, sense link, frenzy; npc: a lineup in every animation; dialogue: a conversation with a civilian and a vampire; sect: the Sect tab and a member's conversation; nemesis: the Nemesis tabs and a verdict conversation; lairs, city, domains, court: the world and the Court tab). Needs a
  world at `run/saves/harness` and, on a headless machine, `xvfb-run`. Inert without the property.
