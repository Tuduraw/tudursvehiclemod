package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** MC Heli's own AddCrawlerTrack - see Readme_Aircraft.txt's own doc:
 * "AddCrawlerTrack = 履帯の表裏逆転, 1つの履帯の間隔, 履帯のXの位置,
 * 履帯の回転ポイントY/Z, 履帯の回転ポイントY/Z,.."
 * ("track flip, spacing between individual track links, track's own X
 * position, path point Y/Z, path point Y/Z,..").
 *
 * Coordinate meanings:
 * - x is used to decide which SIDE this track is on (negative = right,
 * positive = left - same convention as AddTrackRoller), which in turn
 * drives how fast/which direction this specific track actually moves
 * during a turn (see AbstractVehicleEntity's own
 * tudursvehiclemod$updateCrawlerTrackPhase() doc for the actual
 * differential-steering formula: during a stationary pivot turn, left
 * and right move in OPPOSITE directions; during an ordinary turn while
 * moving, the inner track slows towards a stop while the outer track
 * speeds up).
 * - path (the Y/Z pairs) is a closed loop the single track-link model
 * repeats around, spaced linkSpacing apart - see
 * AbstractVehicleEntity's own tudursvehiclemod$getCrawlerTrackPlacements()
 * doc for exactly how copies are actually placed/oriented along it. */
public record CrawlerTrackPart(
		String part,
		boolean flip,
		float linkSpacing,
		double x,
		List<PathPoint> path
) {
	/** One Y/Z point along this track's own closed-loop path. */
	public record PathPoint(double y, double z) {
		public static final Codec<PathPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.DOUBLE.fieldOf("y").forGetter(PathPoint::y),
				Codec.DOUBLE.fieldOf("z").forGetter(PathPoint::z)
		).apply(instance, PathPoint::new));
	}

	public static final Codec<CrawlerTrackPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("part").forGetter(CrawlerTrackPart::part),
			Codec.BOOL.optionalFieldOf("flip", false).forGetter(CrawlerTrackPart::flip),
			Codec.FLOAT.optionalFieldOf("link_spacing", 0.5f).forGetter(CrawlerTrackPart::linkSpacing),
			Codec.DOUBLE.fieldOf("x").forGetter(CrawlerTrackPart::x),
			PathPoint.CODEC.listOf().fieldOf("path").forGetter(CrawlerTrackPart::path)
	).apply(instance, CrawlerTrackPart::new));
}
