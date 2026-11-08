package com.elysium.goldclaimmap;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.Test;
import static org.junit.Assert.*;

public class RswMapNetworkingTest {
    @Test
    public void decodesServerGenerationPayload() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1);
            buf.writeLong(123);
            assertEquals(123, RswMapNetworking.decode(buf));
        } finally { buf.release(); }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownProtocol() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(2);
            buf.writeLong(0);
            RswMapNetworking.decode(buf);
        } finally { buf.release(); }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeGeneration() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1);
            buf.writeLong(-1);
            RswMapNetworking.decode(buf);
        } finally { buf.release(); }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTrailingData() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1);
            buf.writeLong(0);
            buf.writeByte(1);
            RswMapNetworking.decode(buf);
        } finally { buf.release(); }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void rejectsTruncatedPayload() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1);
            buf.writeInt(0);
            RswMapNetworking.decode(buf);
        } finally { buf.release(); }
    }
}
