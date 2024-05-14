package dev.ftb.mods.ftbchunks.net;

import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AddWaypointPacket(String name, BlockPos position, int color) implements CustomPacketPayload {
    public static final Type<AddWaypointPacket> TYPE = new Type<>(FTBChunksAPI.rl("add_waypoint_packet"));

    public static final StreamCodec<FriendlyByteBuf, AddWaypointPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AddWaypointPacket::name,
            BlockPos.STREAM_CODEC, AddWaypointPacket::position,
            ByteBufCodecs.INT, AddWaypointPacket::color,
            AddWaypointPacket::new
    );

    @Override
    public Type<AddWaypointPacket> type() {
        return TYPE;
    }

    public static void handle(AddWaypointPacket message, NetworkManager.PacketContext context) {
        context.queue(() -> FTBChunksClient.addWaypoint(context.getPlayer(), message.name, message.position, message.color));
    }
}
