package com.example.tudursvehiclemod.asset;

/**
 * The tilt of a weapon mount's own reference frame - see {@link WeaponAimRange#defaultPitch()} and
 * {@link WeaponAimRange#defaultRoll()}.
 *
 * <p>A turret is authored in the pose it rests in, and normally swivels about the vehicle's vertical
 * axis. A turret mounted TILTED (on a sloped deck, on its side) has to swivel about its own tilted
 * axis instead, and its yaw/pitch limits are angles in its own frame, not the vehicle's. So every
 * angle this mod works with for such a weapon - the aim limits, the cached current direction,
 * the pitch zones - is an angle in the TILTED frame, and the vehicle's own frame only appears at the
 * boundaries: the occupant's view comes in through {@link #toMountFrame}, and the direction a
 * projectile leaves along (and the rotation the renderer applies) goes out through
 * {@link #toVehicleFrame} / {@link #conjugate}.
 *
 * <p>The tilt is the rotation T = Rx(defaultPitch) * Rz(defaultRoll) about the part's own pivot.
 * The part's drawn rotation is T * R * T^-1, with R the ordinary yaw/pitch rotation: at rest R is
 * the identity, so the part is drawn exactly as authored (tilted), and any turning happens about the
 * tilted axis. A weapon with no tilt (both angles 0, which is every existing weapon) takes
 * none of these paths at all - callers test {@link #isTilted()} first - so its behaviour is
 * bit-for-bit what it was before tilt existed, rather than merely numerically close.
 *
 * <p>Conventions match the rest of the mod: pitch is positive DOWNWARD (Minecraft's own), yaw 0 faces
 * +Z, and a direction is {@code (-sin(yaw)cos(pitch), -sin(pitch), cos(yaw)cos(pitch))} as
 * produced by {@code Vec3d.fromPolar}. This class deliberately has no Minecraft or JOML
 * dependency: it is plain arithmetic, so it can be tested on its own.
 */
public final class WeaponMountTilt {
	public static final WeaponMountTilt NONE = new WeaponMountTilt(0.0, 0.0);

	private final double pitchDegrees;
	private final double rollDegrees;
	/** Row-major T (tilt -> vehicle), and its transpose = inverse. Only meaningful when tilted. */
	private final double[] t;

	public WeaponMountTilt(double pitchDegrees, double rollDegrees) {
		this.pitchDegrees = pitchDegrees;
		this.rollDegrees = rollDegrees;
		this.t = isTilted() ? matrix(pitchDegrees, rollDegrees) : null;
	}

	public boolean isTilted() {
		return pitchDegrees != 0.0 || rollDegrees != 0.0;
	}

	/** T = Rx(pitch) * Rz(roll), row-major. */
	private static double[] matrix(double pitchDeg, double rollDeg) {
		double p = Math.toRadians(pitchDeg);
		double r = Math.toRadians(rollDeg);
		double cp = Math.cos(p), sp = Math.sin(p), cr = Math.cos(r), sr = Math.sin(r);
		double[] rx = {1, 0, 0, 0, cp, -sp, 0, sp, cp};
		double[] rz = {cr, -sr, 0, sr, cr, 0, 0, 0, 1};
		return mul(rx, rz);
	}

	private static double[] mul(double[] a, double[] b) {
		double[] o = new double[9];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				o[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
			}
		}
		return o;
	}

	private static double[] transpose(double[] m) {
		return new double[]{m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]};
	}

	private static double[] apply(double[] m, double[] v) {
		return new double[]{
				m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
				m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
				m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
	}

	/** Minecraft's Vec3d.fromPolar(pitch, yaw) as a plain array. */
	public static double[] direction(double pitchDegrees, double yawDegrees) {
		double p = Math.toRadians(pitchDegrees), y = Math.toRadians(yawDegrees);
		double cp = Math.cos(p);
		return new double[]{-Math.sin(y) * cp, -Math.sin(p), Math.cos(y) * cp};
	}

	/** Inverse of {@link #direction}: {pitch, yaw} in degrees of a (not necessarily unit) direction. */
	public static double[] angles(double[] d) {
		double horizontal = Math.sqrt(d[0] * d[0] + d[2] * d[2]);
		double pitch = Math.toDegrees(-Math.atan2(d[1], horizontal));
		double yaw = horizontal < 1.0e-9 ? 0.0 : Math.toDegrees(Math.atan2(-d[0], d[2]));
		return new double[]{pitch, yaw};
	}

	/**
	 * The view direction given in the vehicle's frame -> {pitch, yaw} in the tilted frame. With no
	 * tilt it returns the input untouched (no arithmetic at all, so no rounding).
	 */
	public double[] toMountFrame(double pitchDegrees, double yawDegrees) {
		if (!isTilted()) {
			return new double[]{pitchDegrees, yawDegrees};
		}
		return angles(apply(transpose(t), direction(pitchDegrees, yawDegrees)));
	}

	/** {pitch, yaw} in the tilted frame -> {pitch, yaw} in the vehicle's frame. Untouched with no tilt. */
	public double[] toVehicleFrame(double pitchDegrees, double yawDegrees) {
		if (!isTilted()) {
			return new double[]{pitchDegrees, yawDegrees};
		}
		return angles(apply(t, direction(pitchDegrees, yawDegrees)));
	}

	/** The same conversion as {@link #toVehicleFrame}, as the unit direction a projectile leaves along. */
	public double[] directionInVehicleFrame(double pitchDegrees, double yawDegrees) {
		double[] d = direction(pitchDegrees, yawDegrees);
		return isTilted() ? apply(t, d) : d;
	}

	/**
	 * T * R * T^-1 for a rotation R given as a quaternion (x, y, z, w) - the rotation the part is
	 * actually drawn with. Returns R itself, untouched, with no tilt.
	 */
	public double[] conjugate(double[] q) {
		if (!isTilted()) {
			return q;
		}
		double[] tq = quaternion(t);
		double[] tqInv = {-tq[0], -tq[1], -tq[2], tq[3]};
		return quatMul(quatMul(tq, q), tqInv);
	}

	/** Rotation matrix (row-major, orthonormal) -> unit quaternion (x, y, z, w). */
	private static double[] quaternion(double[] m) {
		double trace = m[0] + m[4] + m[8];
		double x, y, z, w;
		if (trace > 0) {
			double s = Math.sqrt(trace + 1.0) * 2;
			w = 0.25 * s;
			x = (m[7] - m[5]) / s;
			y = (m[2] - m[6]) / s;
			z = (m[3] - m[1]) / s;
		} else if (m[0] > m[4] && m[0] > m[8]) {
			double s = Math.sqrt(1.0 + m[0] - m[4] - m[8]) * 2;
			w = (m[7] - m[5]) / s;
			x = 0.25 * s;
			y = (m[1] + m[3]) / s;
			z = (m[2] + m[6]) / s;
		} else if (m[4] > m[8]) {
			double s = Math.sqrt(1.0 + m[4] - m[0] - m[8]) * 2;
			w = (m[2] - m[6]) / s;
			x = (m[1] + m[3]) / s;
			y = 0.25 * s;
			z = (m[5] + m[7]) / s;
		} else {
			double s = Math.sqrt(1.0 + m[8] - m[0] - m[4]) * 2;
			w = (m[3] - m[1]) / s;
			x = (m[2] + m[6]) / s;
			y = (m[5] + m[7]) / s;
			z = 0.25 * s;
		}
		return new double[]{x, y, z, w};
	}

	/** Hamilton product a * b, both (x, y, z, w). */
	private static double[] quatMul(double[] a, double[] b) {
		return new double[]{
				a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
				a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
				a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
				a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
	}
}
