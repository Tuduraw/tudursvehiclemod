package com.example.tudursvehiclemod.client.debug;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HITDIAG: TEMPORARY - logs the nearby vehicles as the client sees them, and (in singleplayer) the server's copy of each, when the hit mesh display is toggled. */
public final class HitDiagClient {
	private static final Logger LOG = LoggerFactory.getLogger("TVM-HITDIAG");

	private HitDiagClient() {
	}

	public static void logSnapshot(boolean enabled) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return;
		}
		var server = client.getServer();
		var serverWorld = server == null ? null : server.getWorld(client.world.getRegistryKey());
		LOG.info("[TVM-HITDIAG] mesh display {} - client vehicles within 128 blocks (server={}):", enabled ? "ON" : "OFF", server == null ? "remote" : "integrated");
		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof AbstractVehicleEntity vehicle) || entity.distanceTo(client.player) > 128) {
				continue;
			}
			String serverSide = "n/a";
			if (serverWorld != null) {
				Entity serverEntity = serverWorld.getEntity(vehicle.getUuid());
				if (serverEntity instanceof AbstractVehicleEntity sv) {
					serverSide = String.format("id=%d pos=%.1f,%.1f,%.1f yaw=%.1f removed=%s lastCheckTicksAgo=%s reason=%s tickEntityHere=%s",
							sv.getId(), sv.getX(), sv.getY(), sv.getZ(), sv.getYaw(), sv.isRemoved(),
							sv.tudursvehiclemod$diagLastCheckTime < 0 ? "never" : String.valueOf(serverWorld.getTime() - sv.tudursvehiclemod$diagLastCheckTime),
							sv.tudursvehiclemod$diagLastCheckReason, serverWorld.shouldTickEntityAt(sv.getBlockPos()));
				} else {
					serverSide = "MISSING on server (uuid " + vehicle.getUuid() + ")";
				}
			}
			LOG.info("[TVM-HITDIAG]   client id={} type={} pos={},{},{} yaw={} dist={} | server: {}", vehicle.getId(),
					net.minecraft.entity.EntityType.getId(vehicle.getType()), String.format("%.1f", vehicle.getX()), String.format("%.1f", vehicle.getY()), String.format("%.1f", vehicle.getZ()),
					String.format("%.1f", vehicle.getYaw()), String.format("%.1f", entity.distanceTo(client.player)), serverSide);
		}
	}
}
