# Readme_Weapon_Cas.md - Fields specific to support-aircraft launch weapons (`CAS` / `Carrier`)

Applies to: `CAS` (Close Air Support) / `Carrier` (carrier aircraft
launch)

Of the common fields in `Readme_Weapon.md`, the only one that actually
has meaning for these weapon types is `DisplayName` (damage, projectile
speed, etc. are defined separately on the actual weapon config of the
launched vehicle itself). Instead of firing a real projectile, these
types spawn another vehicle definition (a vehicle JSON) on the spot and
fly it automatically along a configured route. These fields are mostly
additions specific to this project (there is no direct correspondence
with the original MC Heli's own `CAS`/`Carrier`).

---

## `CAS` (Close Air Support)

When used, targets the point where the occupant's own aim reticle meets
the ground (or water surface), spawns a separately-specified support
aircraft near the start of a route close to that target point, and has
it attack automatically while flying the configured path. It despawns
automatically once it reaches the end of the route (it does not return
or land).

**Example**:
```
DisplayName = Call Airstrike
Type = CAS
CasAircraft = a10_thunderbolt
CasWeaponIndex = 0
CasAccuracy = 3.0
CasTimeout = 60
CasStuckTimeout = 30
CasYawOffset = 0
CasWaypoint = 0,80,-150,80,false
CasWaypoint = 0,60,-40,60,true
CasWaypoint = 0,60,0,60,true
CasWaypoint = 0,60,40,60,true
CasWaypoint = 0,80,150,80,false
Round = 3
ReloadTime = 1200
```

### `CasAircraft`
- **Format**: string (required)
- **Description**: The support aircraft's own vehicle file name
  (`data/<namespace>/vehicles/<name>.json`, no namespace needed).
- **Example**: `CasAircraft = a10_thunderbolt`

### `CasWeaponIndex`
- **Format**: integer (default: `0`)
- **Description**: The weapon slot number the support aircraft itself
  uses when attacking (an index into that aircraft's own `weapons`
  array).
- **Example**: `CasWeaponIndex = 0`

### `CasAccuracy`
- **Format**: number (default: `0`)
- **Description**: The support aircraft's own navigation error (a random
  amount of drift in the route itself). This is a separate field from
  the weapon's own actual accuracy (`Accuracy`).
- **Example**: `CasAccuracy = 3.0`

### `CasTimeout`
- **Format**: number, in seconds (default: `60`)
- **Description**: The cap on the support aircraft's own total flight
  time. Once this time elapses, it despawns forcibly regardless of route
  progress.
- **Example**: `CasTimeout = 60`

### `CasStuckTimeout`
- **Format**: number, in seconds (default: `60`)
- **Description**: If this much time elapses without being able to
  advance to the next waypoint, it despawns forcibly regardless of
  progress (a separate timeout from `CasTimeout`, dedicated specifically
  to stalled progress).
- **Example**: `CasStuckTimeout = 30`

### `CasYawOffset`
- **Format**: number (default: `0`)
- **Description**: A correction angle (degrees) added to the route's own
  rotation reference (the occupant's own line of sight direction).
- **Example**: `CasYawOffset = 0`

### `CasTargetMode`
- **Format**: string (default: `Ballistic`, case-insensitive)
- **Description**: Selects how the target point (the center point that
  the route's own relative coordinates are based on) is determined.
  - `Ballistic` (default): a purely mathematical calculation that
    doesn't account for obstacles at all. From the weapon's own
    `Velocity` / `Gravity` (default values if unset) and the occupant's
    own aim direction, computes "the point it would return to firing
    altitude" via ballistic calculation. The reticle's own elevation
    angle directly determines impact distance, behaving like a typical
    indirect-fire weapon
  - `Raycast`: raycasts directly along the aim direction, and targets the
    first point that hits a block (or a fixed maximum-distance point if
    nothing is hit)
  - `Collision`: uses the same ballistic physics calculation as
    `Ballistic`, but actually checks for a block collision at each step
    of the simulation, and only targets the actual impact point (where
    it actually hit a block). Unlike `Ballistic`'s own early cutoff at
    "the point it returns to firing altitude", it keeps simulating the
    trajectory for as long as needed - for example if the terrain is
    lower than the firing point
- **Example**: `CasTargetMode = Collision`

### `CasAttackStartAltitude` / `CasAttackStopAltitude`
- **Format**: number, in blocks (default: `CasAttackStartAltitude = 200`,
  `CasAttackStopAltitude = 40`)
- **Description**: The altitude thresholds used when Carrier's own
  wingman target-lock feature (the `/tvm` lock mode) attacks a
  ground/surface target. Simply using "a fixed offset altitude above the
  target" alone can't guarantee that the flight path taken to reach that
  altitude is itself safe, so a two-stage hysteresis scheme is used
  instead.
  - `CasAttackStartAltitude`: the altitude at which an attack (turning
    toward, closing on, and firing at the target) begins/resumes. Only
    once it actually climbs to at least this height above the target
    does it transition into attack behavior
  - `CasAttackStopAltitude`: dropping below this height aborts the
    attack and climbs, nose up, back to `CasAttackStartAltitude`. The
    attack doesn't resume just because it's back above
    `CasAttackStopAltitude` alone - it waits until it's back up to
    `CasAttackStartAltitude` (this prevents rapid toggling near the
    boundary)
  - Both of these values come from the settings of the weapon slot the
    wingman is using at the moment of locking (whichever weapon the
    flight leader had selected at the time of the lock)
- **Example**: `CasAttackStartAltitude = 200` / `CasAttackStopAltitude = 40`

### `CasWaypoint`
- **Format**: `relative X, relative Y, relative Z, speed%, whether to attack`
  (multiple lines allowed)
- **Description**: A waypoint on the route the support aircraft follows.
  Coordinates are relative to the marked target point. Speed% is a
  fraction (0-100) of the support aircraft's own top speed. During a leg
  where the attack flag is `true` (or `1`), it automatically attacks
  using the `CasWeaponIndex` weapon while flying that leg. A route
  typically consists of ingress/egress legs with the attack flag `false`,
  and an attack run leg with `true`.
- **Example** (ingress → attack run → egress):
  ```
  CasWaypoint = 0,80,-150,80,false
  CasWaypoint = 0,60,-40,60,true
  CasWaypoint = 0,60,40,60,true
  CasWaypoint = 0,80,150,80,false
  ```

### `CasFormationSize`
- **Format**: integer (default: `1`)
- **Description**: An extension specific to this project. A single use
  launches this many support aircraft in formation, rather than a single
  one. The first aircraft (the flight leader) always follows the route
  exactly as configured; every subsequent one follows the exact same
  route, simply translated according to the formation specified by
  `CasFormationType` (every aircraft flies the same route
  simultaneously, keeping its own relative position within the
  formation). The default `1` (if omitted) launches a single aircraft
  with no formation, exactly as before.
- **Example**: `CasFormationSize = 4`

### `CasFormationType`
- **Format**: string (default: `LineAbreast`)
- **Description**: The formation used when `CasFormationSize` is 2 or
  more.
  - `LineAbreast`: side-by-side. Aircraft alternate left and right of the
    leader (with an even count, one side ends up with one more, making it
    asymmetric)
  - `LineAstern`: in a single-file line. Aircraft line up directly
    behind the leader at equal spacing
  - `VFormation`: a V shape. With the leader at the front, aircraft
    alternate left and right while progressively dropping back (same as
    `LineAbreast`, an even count is asymmetric)
  - `Diamond`: forms a 4-aircraft diamond (lead, right, left, tail). With
    5 or more aircraft, the diamond block itself (the "element") is
    further expanded using the same idea as `VFormation`, forming an
    overall combat box
  - `Delta`: forms a 3-aircraft delta (a small V) formation. With 4 or
    more aircraft, the delta element itself is further expanded, the
    same as `Diamond`
- **Example**: `CasFormationType = VFormation`

### `CasFormationSpacing`
- **Format**: number, in blocks (default: `8.0`)
- **Description**: The spacing between aircraft within the formation.
  For `Diamond`/`Delta`, this is used as the spacing between aircraft
  within an element (a small sub-formation). Spacing between elements
  themselves is set separately via `CasFormationElementSpacing`.
- **Example**: `CasFormationSpacing = 10.0`

### `CasFormationElementSpacing`
- **Format**: number, in blocks (default: computed automatically if
  omitted)
- **Description**: `Diamond`/`Delta` only. The center-to-center spacing
  between elements (sub-formations). If omitted, `CasFormationSpacing`
  times 1.5x the number of aircraft per element is used automatically
  (the same behavior as before). Has no meaning for any formation other
  than `Diamond`/`Delta`.
- **Example**: `CasFormationElementSpacing = 20.0`

---

## `Carrier` (carrier aircraft launch)

A similar mechanism to `CAS`, but the launched aircraft takes off on the
spot from this weapon's own `AddWeapon` mount position (i.e. the
mothership itself), rather than spawning near a distant target point (as
`CAS` does). The occupant can move back and forth between the launched
aircraft and the mothership with Alt+Y. It doesn't despawn once it
reaches the end of the route - it instead enters a return/landing
sequence. The runway itself is defined on the mothership's own vehicle
JSON (`runways`, see `Readme_Vehicle.md`'s "12. Carrier deck / runway").
The mothership isn't limited to ships - it can be set on any vehicle,
regardless of `entity_type`.

**This weapon and a runway (`runways`) aren't required by each other.**
This weapon works fine on a mothership with no runway defined at all
(a returning aircraft is recovered by position check alone - actually
landing on a runway tile isn't required). Conversely, a runway can be
used purely as a deck with no weapon of this type set at all. Combining
both, so a launched aircraft actually lands on the runway, is the
originally-intended basic form of a carrier.

**Example**:
```
DisplayName = Launch F-14
Type = Carrier
CarrierAircraft = f14_tomcat
CarrierWeaponIndex = 0
CarrierAccuracy = 3.0
CarrierTimeout = 300
CarrierStuckTimeout = 30
CarrierYawOffset = 0
CarrierLaunchWaypoint = 0,10,60,100,1,-,250
CarrierWaypoint = 0,80,300,80,false
CarrierWaypoint = 0,80,600,80,true
CarrierLandingWaypoint = 0,60,-200,40,1,1,-
CarrierLandingWaypoint = 0,20,-60,20,-,-,-
CarrierLandingToAmmoRadius = 15.0
```

### `CarrierAircraft` / `CarrierWeaponIndex` / `CarrierAccuracy` /
### `CarrierTimeout` / `CarrierStuckTimeout` / `CarrierYawOffset`
- **Format/description**: Exactly the same meaning as `CAS`'s own
  identically-named fields (`CasAircraft`, etc.).
- **Example**:
  ```
  CarrierAircraft = f14_tomcat
  CarrierWeaponIndex = 0
  CarrierAccuracy = 3.0
  CarrierTimeout = 300
  CarrierStuckTimeout = 30
  CarrierYawOffset = 0
  ```

### `CasTargetMode`
- **Format/description**: Exactly the same field as `CAS`'s own. For a
  `Carrier` weapon too, the key name stays **`CasTargetMode`**, not
  `CarrierTargetMode` (this one field is treated as a single key shared
  between CAS/Carrier).
- **Example**: `CasTargetMode = Collision`

### `CasAttackStartAltitude` / `CasAttackStopAltitude`
- **Format/description**: Exactly the same fields as `CAS`'s own. Like
  `CasTargetMode`, the key names stay `CasAttackStartAltitude` /
  `CasAttackStopAltitude` for a `Carrier` weapon too.
- **Example**: `CasAttackStartAltitude = 200` / `CasAttackStopAltitude = 40`

### `CarrierLandingYawOffset`
- **Format**: number (default: `0`)
- **Description**: A correction angle applied only to the landing
  approach direction, in addition to `CarrierYawOffset`.
- **Example**: `CarrierLandingYawOffset = 0`

### `CarrierTargetYawOffset`
- **Format**: number (default: `0`)
- **Description**: A correction angle applied only to the route's own
  rotation, in addition to `CarrierYawOffset`.
- **Example**: `CarrierTargetYawOffset = 0`

### `CarrierWaypoint`
- **Format**: the same format as `CasWaypoint`
- **Description**: The ordinary patrol/attack route.
- **Example**:
  ```
  CarrierWaypoint = 0,80,300,80,false
  CarrierWaypoint = 0,80,600,80,true
  ```

### `CarrierLaunchWaypoint`
- **Format**: `relative X, relative Y, relative Z, speed%[, gear[, bay[, speedBoostKmh]]]`
  (multiple lines allowed)
- **Description**: A dedicated takeoff route flown first, right after
  launch (executed before `CarrierWaypoint`). There's no attack flag.
  The last 3 columns are optional:
  - `gear` (`0` / `1` / `-`, default `-` = no change): the instant this
    waypoint is reached, forcibly sets the landing gear's own state
    (retracted/deployed)
  - `bay` (same as above): forcibly sets the weapon bay's own open/closed
    state
  - `speedBoostKmh` (a number, or `-`, default `-` = disabled): the
    instant this waypoint is reached, instantly changes speed to this
    km/h value while keeping the current horizontal direction (a
    catapult-launch effect)
  - Currently, `gear`/`bay` only actually take effect at the very first
    waypoint (index 0, right after launch) and the final waypoint of the
    landing route (they're ignored at any waypoint in between)
- **Example** (retracts the gear right after launch, catapult-accelerates
  to 250 km/h):
  ```
  CarrierLaunchWaypoint = 0,10,60,100,1,-,250
  ```

### `CarrierLandingWaypoint`
- **Format**: the same format as `CarrierLaunchWaypoint` (required, at
  least 1)
- **Description**: The return/landing route. Coordinates are relative to
  the mothership's own current position and heading, recalculated every
  tick (since landing takes time, and the mothership may keep moving
  during that time). The last waypoint becomes the reference point for
  the final approach (a straight-line approach directly toward the
  weapon's own AddWeapon position itself).
- **Example** (deploys gear/opens bay on approach, no change on final
  approach):
  ```
  CarrierLandingWaypoint = 0,60,-200,40,1,1,-
  CarrierLandingWaypoint = 0,20,-60,20,-,-,-
  ```

### `CarrierLandingToAmmoRadius`
- **Format**: number (default: `15.0`)
- **Description**: When a mounted aircraft of the corresponding vehicle
  file comes within this radius (blocks) of this weapon's own AddWeapon
  position (regardless of speed), it's automatically converted into +1
  ammo (as long as `MaxAmmo` has room). This applies to any
  player-piloted aircraft of the same vehicle file, not only one that
  was actually launched from this weapon.
- **Example**: `CarrierLandingToAmmoRadius = 15.0`

### `CarrierRecoveryPoint`
- **Format**: `relative X, relative Y, relative Z, radius` (multiple
  lines allowed)
- **Description**: Adds an additional recovery zone, on top of the
  `CarrierLandingToAmmoRadius` area (for example, an elevator separate
  from the main deck). Each line has its own independent coordinate and
  radius. If omitted, only the single default zone is active.
- **Example** (adds the area near a side elevator as an extra recovery
  zone):
  ```
  CarrierRecoveryPoint = 15,0,-30,10.0
  ```

### `CarrierFormationSize` / `CarrierFormationType` / `CarrierFormationSpacing` / `CarrierFormationElementSpacing`
- **Format/description**: Exactly the same format and meaning as `CAS`'s
  own `CasFormationSize` / `CasFormationType` / `CasFormationSpacing` /
  `CasFormationElementSpacing`.
- **Carrier-specific note (launch sequence)**: Formation aircraft each
  launch from the same launch position (this weapon's own `AddWeapon`
  mount position) and heading, about 1 second (20 ticks) apart from each
  other (to avoid aircraft colliding with each other by launching
  simultaneously). Once each aircraft finishes its own dedicated launch
  route (`CarrierLaunchWaypoint`), if the whole formation's own launch
  isn't complete yet, it circles overhead near the mothership and waits.
  The instant the last aircraft in the formation launches, the whole
  formation - including any aircraft that were waiting - begins flying
  the ordinary cruise/attack route (`CarrierWaypoint`) together at once.
  The formation's own offset itself is not applied during the dedicated
  launch route - it only applies on the ordinary route. Seat switching
  (Alt+Y) targets whichever aircraft in the formation launched last.
- **Example**: `CarrierFormationSize = 3` / `CarrierFormationType = Delta`
