# UTS objects suites (`:liveobjects`)

Skill-generated tests for the UTS `objects` specs (`/uts-to-kotlin`), one class per spec. All three
objects tiers live here in `:liveobjects`'s own test source set:

| Tier | Package | Run with |
|---|---|---|
| unit | `io.ably.lib.liveobjects.uts.unit` | `./gradlew :liveobjects:runLiveObjectsUnitTests` |
| integration (direct sandbox) | `io.ably.lib.liveobjects.uts.integration` | `./gradlew :liveobjects:runLiveObjectsIntegrationTests` |
| proxy | `io.ably.lib.liveobjects.uts.proxy` | `./gradlew :liveobjects:runLiveObjectsIntegrationTests` |

(`runLiveObjectsIntegrationTests` covers **both** the `integration` and `proxy` packages.)

- These suites live in `:liveobjects`'s own test source set so the internal-graph specs can reach
  `internal` members — the symbol map is `.claude/skills/uts-to-kotlin/references/objects-mapping.md` §17.
- **Convention:** public-tier spec tests use only the public API + the tier's `Helpers.kt`; only the
  five internal-graph unit specs (`internal_live_counter`, `internal_live_map`, `object_id`,
  `objects_pool`, `parent_references`) and documented deviations may reference
  `io.ably.lib.liveobjects` internals.
- Transport bootstrap comes from the shared `:uts` infra (`io.ably.lib.uts.infra.*`, consumed via
  `testImplementation(project(":uts"))`); message builders are the typed `Wire*` constructions in each
  tier's `Helpers.kt` (`unit/Helpers.kt`, `integration/Helpers.kt`).
- Deviations for all three tiers are recorded in [`deviations.md`](deviations.md) (same discipline as
  the realtime/rest tiers' `:java`-hosted `lib/src/test/kotlin/io/ably/lib/uts/deviations.md`).
