# Huge cities: plan

Today's `vtm:city` is one 64 x 64 plateau of nine plots. A **Metropolis** is the same idea at about 14 times the area
(228 x 228 blocks, 14 x 14 chunks): districts, avenues, landmarks, a wall with gates, and thousands of lit windows.
It is a new structure (`vtm:metropolis`); small cities stay as the towns around it.

## What has to change, and why

1. **Generation cost.** Structures are drawn one chunk at a time, so a city cannot be drawn in one go.
   - A `CityPlan` is a pure function of the structure's seed (lot grid, districts, what stands where). It is rebuilt
     from the seed whenever a chunk needs it (and cached), so nothing but the seed has to be saved.
   - Each chunk draws only what intersects it: its columns of ground and road, the wall, and the lots that overlap it.
   - Gameplay records (beds, workplaces, plaza, club, watch, haven) come from running every building with a canvas
     that draws nothing, once, in the chunk that holds the Domain Stone. They never depend on which chunks are loaded.
2. **Terrain.** One flat pad at the median height of a 7 x 7 sample of the site. Hills are cut away above it and
   valleys filled below it; a wall hides the edge. Water and steep sites are rejected (`max_slope`, default 12).
3. **Layout.** An 11 x 11 grid of 16-block lots with 4-block streets and two 6-block avenues either side of the central
   plaza lot. Districts by distance from the plaza:
   - the **civic core** (plaza with the Domain Stone, the ruler's haven, the watch HQ, a market, a club),
   - **downtown** (office and apartment towers of 5 to 13 floors, shops, clubs),
   - **commercial** (shops, tenements, one church, more clubs),
   - **residential** (houses, tenements, parks),
   - **the edge** (parks, depots; the depots gather on one side chosen by the seed).
   One corner of downtown is a 2 x 2 lot, **The Asylum**, with its Malkavian owner.
4. **Buildings.** The four existing buildings (house, shop, club, watch) and the manor haven are reused; new ones are a
   tower (glass, brick or deco, with lobby, floors, a ladder core and penthouse beds), a tenement (shop below,
   rooms above), a church (nave, pews, bell tower), a market (stalls), a park and a depot.
5. **Gameplay at scale.** A city now has its own `radius` (44 for towns, 120 for a metropolis).
   - **Presence:** it is "active" when a player is within 60 blocks of its edge, not 110 of its centre.
   - **Population:** the cap grows with size, and people are settled in the beds and workplaces nearest to players.
   - **Watch:** the patrol grows with size.
6. **Placement.** Rare (`spacing 40, separation 18`), and small cities and lairs keep 16 chunks clear of it.

## Milestones

- **M1 (this change):** everything above, with GameTests (plan invariants, full generation and registration,
  scaling) and a `-Pharness=metropolis` walkthrough.
- **M2:** districts with their own character (slums, an Elysium hall, a cemetery, canals), rivers and bridges, the
  Nosferatu sewers running under the streets, subway stations as fast travel, and more than one court seat.
- **M3:** city simulation depth: shifts and crowds that follow the hour across the whole city, factional
  turf inside a single metropolis, and riots.
