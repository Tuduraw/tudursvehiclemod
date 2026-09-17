import os

WEAPONS = {}

def W(name, text):
    WEAPONS[name] = text.strip() + "\n"

# ---------------- direct-fire ----------------
W("smp_minigun", """
; Fixed forward gun on the aircraft. Heat-based (no magazine), high projectile speed,
; cartridge ejection, muzzle flash + smoke, grouped with the bay missile.
DisplayName = 20mm Rotary Cannon
Type = MachineGun1
Group = Nose
Power = 6
DamageFactor = plane, 1.5
DamageFactor = heli, 1.5
DamageFactor = player, 0.8
Acceleration = 12.0
Gravity = -0.01
Accuracy = 1.2
Piercing = 1
Delay = 1
HeatCount = 4
MaxHeatCount = 200
BulletColor = 255, 255, 220, 120
TimeFuse = 60
Sound = smp_gun
SoundVolume = 2.0
SoundPitch = 1.0
SoundPitchRandom = 0.1
SoundDelay = 2
ModelBullet = shell
AddMuzzleFlash = 0.8, 0.25, 1, 200, 254, 219, 184
AddMuzzleFlashSmoke = 1.2, 1, 3.0, 1.0, 10, 150, 240, 240, 240
SetCartridge = cartridge, 0.05, -90, 0, 1.0, -0.04, 0.4
Recoil = 0.2
RecoilBufCount = 20, 5
Sight = MoveSight
""")

W("smp_ciws", """
; Ship close-in gun (turret, seat 2). Magazine + reserve + resupply items, HE mode switch,
; and RotationSpeed for the spins_while_firing barrel part.
DisplayName = CIWS 20mm
Type = MachineGun2
Power = 5
Acceleration = 10.0
Gravity = -0.01
Accuracy = 0.8
Round = 300
ReloadTime = 200
Delay = 1
MaxAmmo = 1500
SuppliedNum = 300
Item = 2, iron_ingot
Item = 1, gunpowder
ModeNum = 2
Explosion = 1
ExplosionBlock = 0
TimeFuse = 80
RotationSpeed = 30
Sound = smp_gun
SoundVolume = 1.5
SoundDelay = 1
ModelBullet = shell
BulletColor = 255, 255, 200, 80
AddMuzzleFlash = 1.0, 0.3, 1, 200, 254, 219, 184
DisplayMortarDistance = true
""")

W("smp_turret_gun", """
; Half-track turret gun (seat 1, pilot fallback). Heavy recoil animation and slow, fine pitch aiming.
DisplayName = 12.7mm HMG
Type = MachineGun2
Power = 9
Acceleration = 8.0
Gravity = -0.02
Accuracy = 1.5
Round = 100
ReloadTime = 120
Delay = 3
MaxAmmo = 800
SuppliedNum = 100
Item = 3, iron_ingot
Item = 2, gunpowder
Item = 1, redstone
Piercing = 2
Sound = smp_gun
SoundVolume = 2.5
SoundPitch = 0.8
ModelBullet = shell
Recoil = 1.1
RecoilBufCount = 40, 5
CameraRotationSpeedPitch = 0.4
AddMuzzleFlash = 0.9, 0.35, 1, 200, 254, 219, 184
SetCartridge = cartridge, 0.08, 90, 10, 1.5, -0.04, 0.5
""")

W("smp_flak", """
; Static emplacement twin flak. Airburst (ExplosionAltitude) + cluster submunitions,
; range readout, fixed camera pitch. Slow projectile so the arc is visible.
DisplayName = 40mm Flak (Cluster)
Type = MachineGun1
Power = 12
Acceleration = 3.5
Gravity = -0.03
Accuracy = 0.5
Round = 8
ReloadTime = 60
Delay = 8
MaxAmmo = 200
SuppliedNum = 40
Item = 4, iron_ingot
Item = 2, gunpowder
Explosion = 2
ExplosionBlock = 1
ExplosionAltitude = 6
Bomblet = 6
BombletSTime = 12
BombletDiff = 0.6
ModelBomblet = shell
ModelBullet = shell
TrajectoryParticle = smoke
TrajectoryParticleStartTick = 4
DisableSmoke = false
BulletColor = 255, 255, 120, 60
DisplayMortarDistance = true
CasTargetMode = Collision
FixCameraPitch = false
Sound = smp_launch
SoundVolume = 3.0
Recoil = 0.6
""")

W("smp_rockets", """
; Helicopter rocket pods. ModeNum 2 = HEIAP submunition round. Flame trail, incendiary.
DisplayName = 70mm Rockets
Type = Rocket
Power = 25
Acceleration = 3.0
Gravity = -0.02
Accuracy = 2.0
Round = 19
ReloadTime = 300
Delay = 4
MaxAmmo = 76
SuppliedNum = 19
Item = 4, iron_ingot
Item = 4, gunpowder
Explosion = 3
ExplosionBlock = 2
Flaming = true
ModeNum = 2
Bomblet = 12
BombletSTime = 8
BombletDiff = 0.7
ModelBomblet = shell
ModelBullet = rocket
TrajectoryParticle = flame
TrajectoryParticleStartTick = 2
Sound = smp_launch
SoundVolume = 2.0
SoundPitchRandom = 0.15
AddMuzzleFlashSmoke = 1.5, 2, 4.0, 1.5, 20, 180, 250, 245, 240
DisplayMortarDistance = true
""")

# ---------------- guided ----------------
W("smp_at_missile", """
; Helicopter anti-tank missile. ModeNum 2 = top-attack profile. Lock only on ground/surface targets.
DisplayName = AT Missile
Type = ATMissile
Power = 60
DamageFactor = tank, 2.0
DamageFactor = vehicle, 1.5
Acceleration = 2.8
Gravity = -0.01
Round = 4
ReloadTime = 600
Delay = 20
MaxAmmo = 8
SuppliedNum = 4
Item = 6, iron_ingot
Item = 4, redstone
Explosion = 4
ExplosionBlock = 2
LockTime = 40
RigidityTime = 7
ProximityFuseDist = 2.0
RidableOnly = true
ModeNum = 2
Sight = MissileSight
ModelBullet = missile
TrajectoryParticle = largesmoke
Sound = smp_launch
SoundVolume = 3.0
""")

W("smp_aa_missile", """
; Aircraft air-to-air missile. Locks only on airborne targets.
DisplayName = AA Missile
Type = AAMissile
Power = 45
DamageFactor = plane, 1.5
DamageFactor = heli, 1.5
Acceleration = 3.8
Gravity = 0.0
Round = 2
ReloadTime = 900
Delay = 30
MaxAmmo = 4
SuppliedNum = 2
Item = 5, iron_ingot
Item = 3, redstone
Explosion = 3
LockTime = 30
RigidityTime = 5
ProximityFuseDist = 3.0
Sight = MissileSight
ModelBullet = missile
TrajectoryParticle = smoke
Sound = smp_launch
SoundVolume = 3.0
""")

W("smp_as_missile", """
; Aircraft internal-bay air-to-surface missile (aim-point guided, no lock). Grouped with the gun.
DisplayName = AS Missile (Bay)
Type = ASMissile
Group = Nose
Power = 50
Acceleration = 3.2
Gravity = -0.02
Round = 2
ReloadTime = 700
Delay = 25
MaxAmmo = 2
Explosion = 5
ExplosionBlock = 3
ModelBullet = missile
TrajectoryParticle = largesmoke
DisplayMortarDistance = true
CasTargetMode = Collision
Sound = smp_launch
SoundVolume = 3.0
""")

W("smp_mk_rocket", """
; VTOL unguided-style aim-point rocket (MkRocket behaves like ASMissile).
DisplayName = Mk Rocket
Type = MkRocket
Power = 30
Acceleration = 3.0
Gravity = -0.03
Round = 6
ReloadTime = 200
Delay = 6
Explosion = 3
ModelBullet = rocket
TrajectoryParticle = flame
Sound = smp_launch
""")

W("smp_missile", """
; VTOL general-purpose lock-on missile (no air/ground filtering).
DisplayName = Multi-role Missile
Type = Missile
Power = 40
Acceleration = 3.5
Gravity = 0.0
Round = 2
ReloadTime = 600
Delay = 30
Explosion = 3
LockTime = 35
RigidityTime = 6
ProximityFuseDist = 2.5
Sight = MissileSight
ModelBullet = missile
TrajectoryParticle = smoke
Sound = smp_launch
""")

W("smp_tv_missile", """
; Emplacement TV-guided missile (pilot steers it after launch). ModeNum 2 = normal guided mode.
DisplayName = TV Missile
Type = TVMissile
Power = 55
Acceleration = 2.5
Gravity = 0.0
Round = 2
ReloadTime = 800
Delay = 30
Explosion = 4
ExplosionBlock = 2
ModeNum = 2
ModelBullet = missile
TrajectoryParticle = largesmoke
Sound = smp_launch
SoundVolume = 3.0
""")

W("smp_asw", """
; Ship anti-submarine rocket-torpedo. Flies to the aim point, dives at DiveDistance, then cruises as a torpedo.
DisplayName = ASW Rocket
Type = ASWeapon
Power = 55
Acceleration = 3.0
Gravity = -0.01
Round = 2
ReloadTime = 600
Delay = 30
MaxAmmo = 8
SuppliedNum = 2
Item = 6, iron_ingot
Item = 4, gunpowder
DiveDistance = 12.0
AccelerationInWater = 2.0
VelocityInWater = 0.3
TargetDepth = 4.0
GravityInWater = 0.0
Explosion = 4
ExplosionInWater = 6
BulletColor = 255, 230, 230, 230
BulletColorInWater = 255, 40, 80, 160
ModelBullet = torpedo
TrajectoryParticle = largesmoke
Sound = smp_launch
SoundVolume = 3.0
""")

W("smp_torpedo", """
; Submarine torpedo. Usable while submerged, guided to the aim point, cruises at TargetDepth.
DisplayName = Heavy Torpedo
Type = Torpedo
Power = 80
Acceleration = 1.5
Gravity = -0.03
GravityInWater = 0.0
AccelerationInWater = 2.5
VelocityInWater = 0.25
TargetDepth = 3.0
GuidedTorpedo = true
UsableWhileDiving = true
Round = 2
ReloadTime = 900
Delay = 40
MaxAmmo = 6
SuppliedNum = 2
Item = 8, iron_ingot
Item = 4, gunpowder
Explosion = 5
ExplosionInWater = 7
BulletColorInWater = 255, 25, 25, 75
ModelBullet = torpedo
Sound = smp_launch
SoundVolume = 2.0
SoundPitch = 0.6
""")

# ---------------- drop weapons ----------------
W("smp_bomb", """
; VTOL bay bomb. Bounces then detonates on a delay fuse (DelayFuse + Bound), big block damage.
DisplayName = 500lb Bomb
Type = Bomb
Power = 70
Gravity = -0.05
Round = 2
ReloadTime = 1200
Delay = 15
MaxAmmo = 4
SuppliedNum = 2
Item = 8, iron_ingot
Item = 6, gunpowder
Explosion = 5
ExplosionBlock = 5
Bound = 0.3
DelayFuse = 10
ModelBullet = bomb
DisplayMortarDistance = true
Sound = smp_launch
SoundVolume = 1.0
""")

W("smp_fae_bomb", """
; VTOL fuel-air bomb: big blast, flaming, but never breaks blocks (FAE).
DisplayName = FAE Bomb
Type = Bomb
Power = 60
Gravity = -0.05
Round = 1
ReloadTime = 1200
Delay = 15
Explosion = 7
ExplosionBlock = 4
FAE = true
Flaming = true
ModelBullet = bomb
DisplayMortarDistance = true
""")

W("smp_depth_charge", """
; Ship stern depth charges - the Depth type sinks through the surface and detonates below.
DisplayName = Depth Charge
Type = Depth
Power = 35
Gravity = -0.03
Round = 2
ReloadTime = 200
Delay = 10
MaxAmmo = 12
SuppliedNum = 2
Item = 4, iron_ingot
Item = 3, gunpowder
Explosion = 2
ExplosionInWater = 5
ModelBullet = bomb
""")

W("smp_kamikaze", """
; UAV self-destruct charge. Destruct only works on an unmanned helicopter (the sample UAV).
DisplayName = Self-destruct
Type = Bomb
Power = 40
Gravity = -0.05
Round = 1
Explosion = 4
ExplosionBlock = 2
Destruct = true
""")

# ---------------- special ----------------
W("smp_drop_tank", """
; Aircraft centreline drop tank. +80 fuel per remaining round, falls like a bomb when jettisoned.
DisplayName = Drop Tank
Type = DropTank
Round = 1
MaxAmmo = 1
FuelPerAmmo = 80.0
Gravity = -0.05
ModelBullet = bomb
""")

W("smp_smoke", """
; Aircraft wingtip smoke generators (air-show trail).
DisplayName = Smoke Trail
Type = Smoke
SmokeColor = 200, 255, 255, 255
SmokeSize = 2.5
SmokeMaxAge = 400
Delay = 1
""")

W("smp_dispenser", """
; Half-track fire extinguisher: applies a water bucket effect around the impact point.
DisplayName = Extinguisher
Type = Dispenser
DispenseItem = water_bucket
DispenseRange = 4
Acceleration = 2.0
Gravity = -0.03
Round = 5
ReloadTime = 100
Delay = 10
""")

W("smp_dummy", """
DisplayName = --- (empty rack) ---
Type = Dummy
""")

W("smp_targeting_pod", """
; Half-track commander's targeting pod: spots vehicles/monsters/players in a 45deg cone.
DisplayName = Targeting Pod
Type = TargetingPod
Target = planes/helicopters/vehicles/monsters/players
Length = 120
Radius = 45
MarkTime = 12
Delay = 20
Zoom = 2.0, 4.0
""")

W("smp_cas", """
; Emplacement CAS call. Launches a 3-ship VFormation of sample_aircraft from the marked point.
DisplayName = Call Airstrike
Type = CAS
CasAircraft = sample_aircraft
CasWeaponIndex = 0
CasAccuracy = 2.0
CasTimeout = 90
CasStuckTimeout = 30
CasYawOffset = 0
CasTargetMode = Collision
CasAttackStartAltitude = 120
CasAttackStopAltitude = 30
CasWaypoint = 0,90,-220,90,false
CasWaypoint = 0,50,-60,70,true
CasWaypoint = 0,40,40,70,true
CasWaypoint = 0,90,220,90,false
CasFormationSize = 3
CasFormationType = VFormation
CasFormationSpacing = 10.0
CasFormationElementSpacing = 24.0
Round = 3
ReloadTime = 1200
Delay = 100
""")

W("smp_carrier", """
; Carrier launch weapon on the sample ship. Launches sample_aircraft off the deck with a catapult
; boost, flies a patrol/attack loop, and returns to land on the deck (2-ship Delta formation).
DisplayName = Launch Fighter
Type = Carrier
CarrierAircraft = sample_aircraft
CarrierWeaponIndex = 0
CarrierAccuracy = 2.0
CarrierTimeout = 400
CarrierStuckTimeout = 40
CarrierYawOffset = 0
CarrierLandingYawOffset = 0
CarrierTargetYawOffset = 0
CasTargetMode = Collision
CasAttackStartAltitude = 150
CasAttackStopAltitude = 30
CarrierLaunchWaypoint = 0,6,70,100,1,-,220
CarrierLaunchWaypoint = 0,40,180,90,-,-,-
CarrierWaypoint = 0,90,400,80,false
CarrierWaypoint = 0,90,700,80,true
CarrierWaypoint = 120,90,400,80,false
CarrierLandingWaypoint = 0,60,-260,40,1,0,-
CarrierLandingWaypoint = 0,20,-90,25,-,-,-
CarrierLandingToAmmoRadius = 18.0
CarrierRecoveryPoint = -4,0,-18,8.0
CarrierFormationSize = 2
CarrierFormationType = Delta
CarrierFormationSpacing = 12.0
CarrierFormationElementSpacing = 30.0
Round = 2
MaxAmmo = 4
ReloadTime = 600
Delay = 60
""")

def build(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for n, t in WEAPONS.items():
        with open(os.path.join(out_dir, n + ".txt"), "w", encoding="utf-8") as f:
            f.write(t)
    print("weapons:", len(WEAPONS))

if __name__ == "__main__":
    import sys; build(sys.argv[1])
