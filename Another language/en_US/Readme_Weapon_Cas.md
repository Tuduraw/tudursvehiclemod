# Readme_Weapon_Cas.md - Fields specific to support-aircraft launch weapons (`CAS` / `Carrier`)

Applies to: `CAS` (Close Air Support) / `Carrier` (carrier aircraft
launch)

Of the common fields in `Readme_Weapon.md`, the only ones that actually
have meaning for these weapon types are `DisplayName` and `Type` (damage,
projectile speed, etc. are defined separately on the actual weapon config
of the launched vehicle itself). Instead of firing a real projectile,
these types spawn a vehicle definition (a vehicle JSON) on the spot and
fly it automatically along a configured route.
These fields are mostly additions specific to this project (there is no
correspondence with MC Heli's own `CAS`).

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
CasAircraft = Airstriker
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
- **Example**: `CasAircraft = Airstriker`

### `CasWeaponIndex`
- **Format**: integer (default: `0`)
- **Description**: The weapon slot number the support aircraft itself
  uses when attacking (an index into that aircraft's own `weapons`
  array). Note that it starts from 0.
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
  progress (a safety measure).
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

### "Attack the designated entity" in lock mode

With a `CAS` weapon selected, turning lock mode on (the **B key** by default)
makes the strike **attack the highlighted entity under the crosshair**, as
with `Carrier` (see the section of the same name under `Carrier` for the
details).

- What can be highlighted, and that nothing is launched (and no ammo is
  spent) when nothing is highlighted, are the same as for `Carrier`
- When a formation is launched, every aircraft attacks the same target
- `CAS` has no take-off route, so the aircraft head for the target as soon
  as they appear
- Once the target is destroyed (or lost) they fly their ordinary route
  (`CasWaypoint`)
- With lock mode off, it flies the route `CasTargetMode` determines, as
  before

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
    indirect-fire weapon. When aiming level or downward, it targets the
    point where the line of sight meets the water or ground (or, if
    nothing is hit within 1000 blocks, a point that far away
    horizontally at firing altitude). When a target is designated by
    looking down at it in lock mode, this point is also the route's
    reference point.
  - `Raycast`: raycasts directly along the aim direction, and targets the
    first point that hits a block (or a fixed maximum-distance point if
    nothing is hit)
  - `Collision`: uses the same ballistic physics calculation as
    `Ballistic`, but actually checks for a block collision at each step
    of the simulation, and only targets the actual impact point (where
    it actually hit a block). Unlike `Ballistic`'s own early cutoff at
    "the point it returns to firing altitude", it keeps simulating the
    trajectory for as long as needed - for example if the terrain is
    lower than the firing point - and it can also follow terrain that is
    higher than the firing point.
- **Example**: `CasTargetMode = Collision`

### `CasAttackStartAltitude` / `CasAttackStopAltitude`
- **Format**: number, in blocks (default: `CasAttackStartAltitude = 200`,
  `CasAttackStopAltitude = 40`)
- **Description**: The altitude thresholds used when Carrier's own
  wingman target-lock feature attacks a ground/surface target. Simply using "a fixed offset altitude above the
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
- **For an aerial torpedo (`Torpedo`)**: when a ground/surface target is
  attacked with `Torpedo`, these two are not altitudes but **horizontal
  distances from the target** (with the same defaults, 200 and 40).
  - `CasAttackStartAltitude`: where the run starts. The aircraft first
    swings round to a point this far from the target, square off the
    target's side (perpendicular to its heading), on whichever side the
    aircraft is already, and runs in toward the target from there.
  - `CasAttackStopAltitude`: where the torpedo is released. It releases
    once the distance to the target drops to this value or below.
  - During the run it flies along the line through the target square off
    its side (following the target's current heading if it turns). It
    flies level at `CasTorpedoAltitude` throughout the attack and never
    dives.
  - From the release point it keeps flying level without changing course
    for 5 ticks. If the release is delayed (still reloading, say), it is
    still released if possible within that time (one torpedo per run).
  - If, at the release point, the nose is not pointed at the target (off
    by more than 15 degrees) or the run is coming in more than 30 degrees
    off square to the target's side, it does not release, carries on
    through, and releases on the next run.
  - After releasing (or not), it carries straight on until it is
    `CasAttackStartAltitude` away from the target, then sets up the next
    run. It never turns back toward the target and circles it.
  - For a Drone Center dummy pilot, the attack start/stop altitudes set at
    the Drone Center are used in the same way, as the run-start and
    release distances.
  - Once it has no torpedo left to fire, it stops attacking and returns to
    its ordinary route (a `Carrier` aircraft launched in lock mode heads
    back to land instead). A CAS or Carrier aircraft does not reload in
    flight, so it returns as soon as its magazine is empty; a dummy
    pilot's aircraft keeps attacking for as long as it can reload from its
    reserve.
  - **When a target point is marked** (an ordinary CAS or Carrier strike,
    without lock mode): on reaching an attack leg of the route (a
    `CasWaypoint`/`CarrierWaypoint` whose attack flag is `true`), instead
    of firing along the route it makes the same run against the marked
    point (at the water or ground surface). A point has no heading, so the
    side the aircraft is on is taken as square on. It drops one torpedo
    per attack leg, and returns to the route once it has released and is
    breaking away.
  - In a formation, every aircraft makes its own run. A wingman that has
    finished its run and dropped back into formation keeps to the height
    of the route's waypoint, not the leader's, for as long as the leader
    is still attacking (so it does not follow a leader making its low
    torpedo run down to the water).
- **Example**: `CasAttackStartAltitude = 200` / `CasAttackStopAltitude = 40`

### `CasTorpedoAltitude`
- **Format**: number, in blocks (default: `20`)
- **Description**: Only used when the weapon used for the attack is
  `Torpedo`. An aerial torpedo can only be released in level flight at a
  fixed altitude, so throughout the attack (positioning, the run, the
  release and breaking away) the aircraft holds this altitude (height
  above the target), level. Where the run starts and where the torpedo is
  released are set by `CasAttackStartAltitude`/`CasAttackStopAltitude` as
  described above. A torpedo cannot be released from above its
  `TorpedoMaxAltitude`, so the altitude actually flown is capped 3 blocks
  below `TorpedoMaxAltitude`.
- **Example**: `CasTorpedoAltitude = 20`

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
- **Description**: A single use launches this many support aircraft in
  formation, rather than a single one. The first aircraft (the flight
  leader) always follows the route exactly as configured; every
  subsequent one follows the formation specified by `CasFormationType`,
  flying along with the leader (every aircraft flies the same route
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
DisplayName = Launch F4U
Type = Carrier
CarrierAircraft = f4u_corsair
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
  CarrierAircraft = f4u_corsair
  CarrierWeaponIndex = 0
  CarrierAccuracy = 3.0
  CarrierTimeout = 300
  CarrierStuckTimeout = 30
  CarrierYawOffset = 0
  ```

### "Attack the designated entity" in lock mode

With a `Carrier` weapon selected, turning lock mode on (the **B key** by default)
makes every launch go **after the entity under the crosshair**: it is launched to
attack that entity. The designation method such as `CasTargetMode` is not used.

- While lock mode is on and a `Carrier` weapon is selected, the entity nearest the
  crosshair (within 15 degrees of it, up to 1000 blocks away) is **highlighted**. A
  target is a living entity or a vehicle that has not been destroyed (this vehicle,
  the vehicle you are riding, and the aircraft it has launched are never targets)
- Pressing the fire key then launches against the highlighted entity. When a
  formation is launched, **every aircraft attacks the same target**
- The launched aircraft first flies its take-off waypoints
  (`CarrierLaunchWaypoint`) as usual, and only after taking off heads for the
  target and attacks (it never makes straight for the target during take-off)
- Altitude during the attack is handled the same way as when the lead of a
  formation sends a wingman after a target, following `CasAttackStartAltitude`
  and `CasAttackStopAltitude`
- **The ordinary route (`CarrierWaypoint`) is not used.** Once the target is
  destroyed (or lost) the aircraft heads straight back and lands through its
  landing route (`CarrierLandingWaypoint`)
- It also stops attacking and heads back once the weapon it attacks with has
  run out (whatever the weapon). A launched aircraft does not reload in flight,
  so it heads back as soon as its magazine is empty
- When launched as a formation, the aircraft do not follow the leader either:
  each heads back as soon as its own attack is over. The landing queue is the
  same as after finishing the ordinary route
- When the lead of a formation sends a wingman after a target (below), the
  wingman goes back to its ordinary route once the lock ends
- **If nothing is highlighted, nothing is launched** (and no ammo is spent)
- With lock mode off, or with a weapon other than `Carrier` selected, it behaves
  as before (flying the route that `CasTargetMode` determines)

When riding the lead of a formation that has already launched, commanding the
wingmen (the B key toggles the mode, the fire key assigns a target, Alt+B
releases every wingman's lock) works as it always has.

### `CasTargetMode`
- **Format/description**: Exactly the same field as `CAS`'s own. For a
  `Carrier` weapon too, the key name stays **`CasTargetMode`**, not
  `CarrierTargetMode` (this field is treated as a single key shared
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
- **Description**: The ordinary patrol/attack route. Horizontally
  (relative X and Z) it is relative to the marked point, as with
  `CasWaypoint`. What the height (relative Y) is relative to depends on
  the attack flag:
  - Attack flag `false` (moving): relative to **the launch point** (this
    weapon's own `AddWeapon` position, at its height at the moment of
    launch) - the same reference as `CarrierLaunchWaypoint`.
  - Attack flag `true` (a bombing, torpedo or strafing leg): relative to
    **the marked point**, so the attack is flown at a height relative to
    the target.
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
  - For `gear`, `1` deploys and `0` retracts. `gear`/`bay` take effect at
    every waypoint (launch and landing routes alike), the moment the
    aircraft starts heading for that waypoint
- **Example** (retracts the gear right after launch, catapult-accelerates
  to 250 km/h):
  ```
  CarrierLaunchWaypoint = 0,10,60,100,0,-,250
  ```

### `CarrierLandingWaypoint`
- **Format**: the same format as `CarrierLaunchWaypoint` (required, at
  least 1)
- **Description**: The return/landing route. Coordinates are relative to
  the mothership's own current position and heading, recalculated every
  tick (since landing takes time, and the mothership may keep moving
  during that time). The last waypoint becomes the reference point for
  the final approach (a straight-line approach directly toward the
  weapon's own AddWeapon position itself). The speed (%) is the speed
  of the leg leading to that waypoint (the same as any other waypoint);
  when the leg changes, the speed eases toward the new value. The last
  waypoint's speed is the throttle the final approach starts with. A
  value of 0 or less is treated as 30%, and anything above 100 as 100%.
  The first waypoint is flown to like any ordinary waypoint, smoothly and
  within the aircraft's own turning performance (so it does not swing round
  abruptly when it heads home from its route or from an attack). From the
  second waypoint on it is guided firmly, regardless of the aircraft's own
  physics, so that it lands quickly and surely.
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
- **Carrier-specific note (landing sequence)**: a formation goes in to land one
  aircraft at a time (going in together makes aircraft collide when the
  formation is tightly spaced). Waiting aircraft orbit the mothership; as soon
  as the aircraft that went in reaches its first landing waypoint
  (`CarrierLandingWaypoint`), the waiting aircraft furthest forward in the
  formation order goes in (it does not wait for the one ahead to finish
  landing, so the whole formation is recovered sooner and an aircraft that
  fails to land does not hold up the rest). Normally they land in order from the leader.
  When launched in lock mode, aircraft start waiting as they finish their
  attacks. If the formation's information has been lost (after a game restart,
  say), each goes in after a delay based on its place in the formation order.
- **Example**: `CarrierFormationSize = 3` / `CarrierFormationType = Delta`


---

## `Midget`(launching a midget submarine)

> **On the autopilot**: a midget's autopilot bypasses the submarine's own ordinary piloting
> systems (the ascend/descend keys, hatch animation, etc.), writing yaw, pitch and velocity
> directly instead. It does not support some of what `Carrier` does, such as launching in
> formation or counting ammo as "in flight".

Same idea as `Carrier` (launching an aircraft): launches one submersible craft (typically a
vehicle whose `entity_type` is `submarine`) from **this weapon's own `AddWeapon` mount
position** to fly a fixed route by itself. The main differences from `Carrier`:

- **Always exactly one** craft is launched (no formation, unlike `Carrier`)
- A waypoint's Y is a **depth below the water surface**, not an altitude (the same idea as a
  torpedo's own `TargetDepth` - positive is deeper, 0 is the surface)
- To avoid underwater terrain and land, it probes ahead at a fixed interval and surfaces to
  avoid whatever it finds (details below)
- Recovery follows the recovery waypoints (`MidgetLandingWaypoint`, the counterpart of
  `CarrierLandingWaypoint`). Reaching the last of them recovers the midget, and this
  weapon's ammo on the mothership goes back up by one. Without `MidgetLandingWaypoint`,
  it retraces the launch route (`MidgetLaunchWaypoint`) in reverse back to the launch
  point instead.
- As with `Carrier`, the player who launched it can switch seats between the mothership
  and the midget with Alt+Y (default). While a player pilots it the autopilot stops, and
  it picks up from where it was once they go back to the mothership. A player aboard
  when it is recovered is put back in a mothership seat.
- Its autonomous state (the waypoint it is on, its target, its mothership and so on) is
  saved, so after a world reload it carries on from where it was.
- While under way, the engine sound and the propeller (including `spinning_parts`)
  follow its actual speed. Fuel is consumed as usual, but while flying its route it
  does not stop when the fuel runs out - it flies the route to the end (the same as a
  Drone Center ground route).

### Collision avoidance

- Probes ahead every `MidgetDetectRange` (default **50** blocks)
- The probing interval is `MidgetDetectInterval` (default **10** ticks)
- While the way ahead is blocked, it **keeps rising** toward the surface. Each time it has
  risen another `MidgetAvoidStep` (default **1** block), it probes again at once, ignoring
  the interval. If still blocked it keeps rising.
- It levels off at the depth where the way is clear, and holds that depth as a ceiling: it
  does not go back deeper than that without the way ahead being clear too. The return to the
  route's own depth is likewise one block at a time, each time confirming the way ahead is
  clear.
- If it rises all the way to the surface and is still blocked (land, say), it stops and
  waits there.
- Since it is slow, the detection range and interval are both set looser than an aircraft's
  own ground-attack terrain detection.

### "Attack the designated entity" in lock mode

With a `Midget` weapon selected, turning lock mode on (the **B key** by
default) makes the launch go **after the highlighted entity under the
crosshair**, as with `Carrier` (what can be highlighted, and that nothing is
launched when nothing is highlighted, are the same as for `Carrier`).

- After launch it first follows its launch waypoints
  (`MidgetLaunchWaypoint`), then heads for the target
- While closing on the target it runs at the depth and speed of the first
  `MidgetWaypoint`. Collision avoidance works as usual
- Once within `MidgetAttackRange` of the target and pointed at it, it
  attacks with its attack weapon (`MidgetWeaponIndex`). If that weapon is a
  guided torpedo (`GuidedTorpedo = true`), the torpedo chases that target
- It keeps attacking for as long as it has something to fire. If it runs
  past the target at close range, it does not turn back on it at once: it
  carries on outward (to `MidgetAttackRange` plus two turn radii), then comes
  back in for another attack (so it never ends up circling the target)
- The attack ends when there is nothing left to fire, or the target is
  destroyed (or lost). "Something to fire" follows the weapon's own reload
  rules: while it can still reload from its reserve, it counts as able to fire
- **The ordinary route (`MidgetWaypoint`) is not used.** Once the attack is
  over it heads for its recovery waypoints (`MidgetLandingWaypoint`) and is
  recovered

### `MidgetAttackRange`
- **Format**: number, in blocks (default: `60`)
- **Description**: How close to the target a midget launched in lock mode
  gets before attacking it.
- **Example**: `MidgetAttackRange = 60`

### `MidgetVehicle`
- **Format**: string (this weapon type does nothing if left unset)
- **Description**: the file name (without extension) of the vehicle to launch. If it names a
  vehicle whose `entity_type` is not `submarine`, it still launches, but the autopilot does
  nothing.
- **Example**: `MidgetVehicle = ko_hyoteki`

### `MidgetWeaponIndex`
- **Format**: integer (default `0`)
- **Description**: the index (into the launched craft's own `weapons` array, starting from
  0) of the weapon it fires on an attack waypoint (below).
- **Example**: `MidgetWeaponIndex = 0`

### `MidgetAccuracy`
- **Format**: number (default `0`)
- **Description**: the same idea as `CasAccuracy` - scatter on where each waypoint lands.
- **Example**: `MidgetAccuracy = 2`

### `MidgetTimeout` / `MidgetStuckTimeout`
- **Format**: seconds (default `600` / `60`)
- **Description**: the same idea as `CasTimeout`/`CasStuckTimeout`. `MidgetStuckTimeout` also
  applies to time spent stopped at the surface, blocked.
- **Example**: `MidgetTimeout = 600`

### `MidgetYawOffset` / `MidgetTargetYawOffset`
- **Format/Description**: exactly the same as `CasYawOffset`/`CasTargetYawOffset`.

### `MidgetDetectRange` / `MidgetDetectInterval` / `MidgetAvoidStep`
- **Format**: number (default `50` / `10` / `1`)
- **Description**: see "Collision avoidance" above.
- **Example**: `MidgetDetectRange = 50` / `MidgetDetectInterval = 10` / `MidgetAvoidStep = 1`

### `MidgetRecovery`
- **Format**: boolean (default `true`)
- **Description**: with `false`, it is removed once the route (in lock mode, the attack)
  is over, without returning to the mothership (and the ammo is not given back).
- **Example**: `MidgetRecovery = false`

### `MidgetLaunchWaypoint` / `MidgetWaypoint`
- **Format**: repeated lines of `relX,depth,relZ,speed(%),attack(true/false)`
- **Description**: the same relative-coordinate idea as `CarrierLaunchWaypoint`/
  `CarrierWaypoint` (`MidgetLaunchWaypoint` relative to this weapon's own mount position,
  `MidgetWaypoint` relative to the marked point), except the Y component is a **depth**, not
  an altitude. A negative depth cannot be given (it would be above the surface). Recovery
  retraces `MidgetLaunchWaypoint` **in reverse**.
- **Example**:
  ```
  MidgetLaunchWaypoint = 0,0,10,60,false
  MidgetLaunchWaypoint = 0,5,30,60,false
  MidgetWaypoint = 0,15,200,80,false
  MidgetWaypoint = -20,15,250,60,true
  ```

### `MidgetLandingWaypoint`
- **Format**: repeated lines in the same format as `MidgetLaunchWaypoint` (the attack
  column is not used)
- **Description**: The recovery route. Like `CarrierLandingWaypoint`, it is relative to
  **the mothership's current position and heading** (this weapon's own `AddWeapon`
  position), recalculated every tick (so it follows a moving mothership). Y is a depth
  below the surface, as for the other waypoints. Once the route (in lock mode, the
  attack) is over it follows these from the first, and reaching the last recovers the
  midget, putting this weapon's ammo on the mothership back up by one. If the mothership
  cannot be found, the launch-time position and heading are used. If omitted, it
  retraces the launch route in reverse instead.
- **Example**:
  ```
  MidgetLandingWaypoint = 0,5,-60,60,false
  MidgetLandingWaypoint = 0,0,-15,40,false
  MidgetLandingWaypoint = 0,0,0,30,false
  ```

### Example
```
DisplayName = Midget Sub Launch
Type = Midget
MidgetVehicle = ko_hyoteki
MidgetWeaponIndex = 0
MidgetTimeout = 900
MidgetLaunchWaypoint = 0,0,10,60,false
MidgetWaypoint = 0,15,200,80,false
MidgetWaypoint = -20,15,250,60,true
Round = 2
ReloadTime = 2400
```
