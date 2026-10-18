# Mimic Mod — Forge 1.19.2

A multiplayer chest predator with escalating aggression, unseen stalking, private false alarms, and tameable storage.
This fork targets **Minecraft 1.19.2 / Forge only**. Fabric is no longer built or shipped.

## Requirements

- Java 17 (for development and the game).
- Minecraft **1.19.2**, Forge **43.3.8 or newer in the 43.x series**.
- GeckoLib **3.x** for Forge / Minecraft 1.19.2 (minimum API version 3.1.14).
  The development build uses `geckolib-forge-1.19:3.1.14`; do not install GeckoLib 4 or a Fabric jar.
- Install Mimic and GeckoLib on both client and server. ToroHealth is optional and is not installed by the development runtime.

Forge is retained because no loader change is needed to support the existing code and the requested Minecraft version.

## Behavior

- Wild mimics conceal themselves as chests, consume nearby unattended chests, and preserve their contents.
- Opening a wild mimic triggers hostility after 15 ticks; taking an item, including shift-click, shortens the delay to 2 ticks.
- A concealed mimic can ambush a survival player within 2.5 blocks who is looking away. It requires line of sight.
- Provocation targets the player who opened or damaged the mimic, rather than an arbitrary nearby player.
- Pursuit follows the last **visible** position; it does not continuously track an unseen player through walls.
- Defaults: 80 health, 18 attack damage, 6 armor, 32-block pursuit range, 8-second sight memory.
- A bite has a 20-tick animation with one strike near the end. It requires line of sight and a target within 2 blocks at impact.
- Paths are recalculated at most twice per second, with backoff after failures. A target that remains unreachable for 10 seconds is abandoned.
- Axes retain their double-damage counterplay. Creative and spectator players are not hunted.
- A Mimic Lock disables hostility; stealing from a locked mimic can make it flee.
- A Mimic Key tames a wild, unlocked mimic. Only its owner can open it or toggle sitting with sneak-right-click.
- A tamed mimic follows its online owner in the same dimension and can only be opened while sitting.
- Sneak-use a Mimic Heart on a chest to schedule a transformation. Repeated use on the same pending chest is rejected without consuming another heart.
- Mimics can replace generated chests in the original supported structures: igloos, end cities, mansions, temples, fortresses, strongholds, and dungeons.

## Multiplayer horror (1.4.0)

All decisions, cooldowns, damage, and progression run on the server. Clients use the existing entity synchronization and vanilla sound packets; no client-side horror timer or new assets are required.

### Several players, not one permanent victim

- Each wild mimic keeps a bounded, expiring awareness ledger (default **4 players**). Nearby visible survival players can attract its attention without opening the chest.
- Focus rotates between visible candidates after at least **80 ticks / 4 seconds**. An attacker is remembered with higher threat, but cannot redirect a bite that has already started. Separate mimics keep separate ledgers.
- A bite still strikes once and requires the primary victim within **2 blocks** and line of sight. Up to three other survival players in the same range and a **120-degree forward cone** take **35%** of its attack attribute as secondary damage, before vanilla mitigation. Walls, players behind it, creative players, and spectators are excluded. Set `horror.cleaveDamage=0.0` to disable secondary hits.
- A calm mimic creeps toward a visible player up to 28 blocks away when nobody eligible is looking at it. Looking back freezes the stalking and restores the disguise. This does **not** freeze an already angry mimic or cancel combat.
- Tamed/locked mimics do not gain horror aggression. Ownership, inventory access, trap ownership, and private sound recipients remain per-player.

### Every world day makes it worse

Progression uses **completed overworld calendar days**, shared by all dimensions. The first calendar day is stage zero. Sleeping and advancing the calendar advance progression; freezing the daylight cycle freezes further progression. The highest recorded day persists through restarts and `/time set` rollback.

Default linear bonuses per completed day:

- **+5% base maximum health**, **+3% base attack damage**, **+1.5% base movement speed**.
- Awareness radius grows from 8 toward 32 blocks, pursuit from 32 toward 64, and unseen-target memory from 8 toward 28 seconds at the default 100-day cap.
- Encounter intervals shorten and actual-spawn probability rises from 30% toward 70% per encounter. Not every encounter creates a mob.

At stage zero: 80 health, 18 attack attribute, 0.55 base movement speed. After 20 completed days: 160 health, 28.8 attack attribute, 0.715 base movement speed. These are attributes, **not guaranteed damage to an armored player** or blocks/second.

Default escalation stops after **100 completed days** (configurable 0–365). Maximum health/attack are hard-capped at 1024 and movement speed at 1.0. Loaded entities refresh changed stages within 200 ticks, preserving current health percentage rather than healing to full. Existing old worlds start at their current calendar age, not zero; lower `escalationDays` or disable horror before upgrading if needed.

### Unpredictable encounters, day and night

- The director can create disguised mimics in **any biome or dimension**, independent of light, sky access, day, or night: outdoors, caves, buildings, Nether, or End, if a safe site exists.
- Candidate positions are 12–32 blocks horizontally from a player, at nearby elevations. They must be inside loaded chunks/world borders, on a solid safe floor, in two air blocks, without collision/fluid. No forced chunk loading, wall spawning, block replacement, or chest destruction by the director.
- No pop-in within 8 blocks of a living non-spectator observer or in an observer's clear forward view within 64 blocks. Creative observers also prevent visible pop-in. This intentionally means spawning is not literally possible at every coordinate.
- Per-player randomized encounters default to **2–5 minutes** at stage zero. Logging in, respawning, or changing dimension adds a **60–120 second** grace period; a later persisted cooldown is never shortened. Relogging cannot be used to reset the world age. High-stage intervals have a 200-tick minimum.
- Some encounters only send a quiet positional step/chest sound behind that one player. Later stages can also use vanilla cave/enderman sounds. A sound does not prove a mimic exists; another player does not automatically hear it. Sounds respect **Hostile Creatures** volume. No flashing overlays or forced volume.
- Peaceful difficulty and `doMobSpawning=false` stop director spawns and cues. Sleeping, dead, creative, and spectator players do not receive encounters. Peaceful also stops hunting.
- At most two encounters are processed per second across the server, with rotating player order. Defaults limit director spawning to **48 loaded non-tamed mimics globally**, **12 per dimension**, and **3 within 48 blocks** of a selected player. Existing locked mimics count. Caps do not delete existing mobs or cap structure/heart spawns.
- Untouched, empty director-spawned mimics become eligible for cleanup after 10 minutes, only when no player is within 48 blocks. Loot-generated, item-bearing, locked, or tamed mimics are protected from this cleanup.
- Forge spawn veto/special-spawn/entity-join hooks are respected. Claims and protection mods still need validation in the actual modpack; no universal claim API is assumed.

## Configuration and world compatibility

Forge manages `saves/<world>/serverconfig/mimic-server.toml` (dedicated server: `<level-name>/serverconfig/mimic-server.toml`).
The old manually loaded global `config/mimic-server.toml` is no longer used; copy desired settings to the world's server config.

| Setting | Default | Purpose |
| --- | --- | --- |
| `mimicSpawnChance` | 5 | One replacement per N supported generated chests |
| `health` | 80.0 | Maximum health |
| `damage` | 18.0 | Base attack damage before armor/difficulty effects |
| `armor` | 6.0 | Armor points |
| `chaseSpeed` | 0.85 | Navigation speed multiplier, not blocks per second |
| `pursuitRange` | 32 | Maximum pursuit distance |
| `memoryTicks` | 160 | Search time after losing sight |
| `eatChests` | true | Autonomous chest consumption |
| `healthBarBlacklist` | [] | Additional ToroHealth entity IDs to hide |

Autonomous chest consumption also respects Forge's `mobGriefing` hook. This does not disable explicit Mimic Heart use or chest replacement during structure generation.
Combat attributes are applied when an entity joins the server world, preserving its current health percentage. Restart/reload the world after changing combat settings.

The following keys live under `[horror]` in the same TOML file. All are enabled by default where applicable:

| Key | Default | Purpose |
| --- | --- | --- |
| `enabled` | true | Awareness, stalking, cleave, escalation and director master switch |
| `surpriseSpawns` | true | Allow director spawns; false alarms are independent |
| `paranoiaSounds` | true | Private positional false alarms; disable without disabling mobs |
| `escalationDays` | 100 | Completed-day cap; 0 disables stat/time escalation only |
| `dailyHealth` / `dailyDamage` / `dailySpeed` | 0.05 / 0.03 / 0.015 | Linear fractions of base attributes per completed day |
| `cleaveDamage` | 0.35 | Secondary bite fraction; 0 disables |
| `maxTrackedPlayers` | 4 | Ledger/crowd-hit bound, configurable 1–16 |
| `switchTicks` | 80 | Minimum focus time, never interrupts a bite |
| `encounterMinTicks` / `encounterMaxTicks` | 2400 / 6000 | Base randomized encounter interval; reversed bounds are handled |
| `loginGraceTicks` | 1200 | Minimum grace after login/death/dimension change, plus 0–1200 random ticks |
| `maxWildPerLevel` / `maxWildTotal` / `maxNearbyWild` | 12 / 48 / 3 | Director admission caps for loaded non-tamed mimics |

World progression and bounded UUID cooldown history are stored in `<level-name>/data/mimic_horror.dat`. The calendar high-water mark continues recording while horror is disabled, so re-enabling does not reset the world's age. Lowering the cap is supported without editing save files. These timers use server game time; they do not advance while the server is offline.

Registry IDs and the 27-slot inventory format are preserved. Legacy string owner UUIDs are read and migrated to UUID tags. Malformed owner data fails closed (no other player gains access); oversized inventory tags cannot overflow the 27 valid slots.
**Back up an existing world before upgrading.** Pending heart transformations are in-memory and are not persisted across a server restart.

## Build and test

From the repository root, with `JAVA_HOME` pointing to a JDK 17 installation:

```powershell
.\gradlew.bat :Forge:build
.\gradlew.bat :Forge:runGameTestServer
```

If cleaning, run `.\gradlew.bat :Forge:clean` as a **separate invocation** before the commands above. ForgeGradle 5 creates runtime classpath files during configuration; combining `clean` and a run task in one invocation can delete them before launch.

Linux/macOS: replace `.\gradlew.bat` with `./gradlew`.
The first build downloads Minecraft, Forge, mappings, and other dependencies.

- Installable jar: `Forge/build/libs/Mimic-forge-1.19.2-1.4.0.jar`.
- **21 unit tests:** pursuit deadlines, reacquisition/stuck detection/reset, daily scaling and interval bounds, bite geometry, bounded awareness, expiry, and fair target rotation.
- **22 Forge GameTests:** corrupt/legacy NBT, lock/storage safety, chest conservation, attack visibility, saved progression/cooldowns, safe day/night/unloaded spawn sites, gaze, health preservation, tame expiry, independent target ledgers, private sound packet routing, competing viewers, unrelated-menu safety, and multi-player cone damage.
- Network tests use real server-player/menu logic with recording connections, not live client TCP sessions.
- GameTest sources/templates are separate from the production jar.
- CI preserves full logs and test reports as artifacts and only prints a short failure excerpt.

### Manual release checks

Automated server tests do not validate rendering, subjective scare/audio quality, real-client latency, large-player-count performance, or optional mod compatibility:

1. Start a Forge 1.19.2 client and dedicated server with the same Mimic/GeckoLib jars.
2. Verify disguise, reveal, bite, chest opening, lock, and follow animations.
3. With two players, verify owner-only access, a wild mimic's opening trap, shift-click, and closing an unrelated menu before the trap expires.
4. Check pursuit around corners, unreachable ledges, water escape, creative/spectator transitions, and the rear ambush.
5. Check single/double/trapped chests, full inventories, death drops, `mobGriefing=false`, and chunk unloads.
6. Back up and reload an old world; verify tame ownership, contents, and health percentage.
7. With two real clients, verify focus rotation, unredirectable bites, forward crowd damage, walls/back-angle safety, private cues, and stalking while one versus both players look away.
8. In a disposable world, advance `/time set 480000`, observe scaled health/speed/encounters, restart, then roll time back. Verify the highest recorded day remains. Repeat with custom caps and horror disabled.
9. Check surprise encounters during day/night and in lit houses, caves, Nether, and End. Verify join/death/dimension grace, no visible pop-in, world border, and no newly forced chunks. Use a real survival player; creative observers may suppress spawning.
10. Verify Peaceful and `doMobSpawning=false` suppress director events, per-player sound routing, spawn vetoes from protection mods, and loaded admission caps under multiplayer load. Normal unmodified event delays are intentionally long; use a disposable world's server config for faster tests.
11. Test structure generation and ToroHealth compatibility in a separate world. ToroHealth compatibility mixins are retained but require verification with the actual 1.19.2 modpack.

## Credits and license

- [Luke Graham Landry](https://github.com/LukeGrahamLandry) — original code.
- [Redrix](https://www.curseforge.com/members/redrixttv/projects) — assets.
- [EpicNecromancer1](https://www.curseforge.com/members/epicnecromancer1) — assets.
- MIT; see [LICENSE](LICENSE).
