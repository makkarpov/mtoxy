package ru.makkarpov.mtoxy.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.makkarpov.mtoxy.util.AESCTR;
import ru.makkarpov.mtoxy.util.PeerRecord;
import ru.makkarpov.mtoxy.util.Utils;

import javax.annotation.Nullable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Performs Obfuscated2 handshake and replaces itself with Obfuscated2 codec when complete. Can act either as client or
 * as server.
 */
public class Obfuscated2Handshaker extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(Obfuscated2Handshaker.class);
    public static final int HANDSHAKE_LENGTH = 64;
    public static final int PROTOCOL_SIGNATURE_OFFSET = 56;
    public static final int PROTOCOL_SIGNATURE_LENGTH = 4;
    public static final int KEY_IV_OFFSET   = 8;
    public static final int KEY_LENGTH      = 32;
    public static final int IV_LENGTH       = 16;
    public static final byte ABRIDGED_SIGNATURE = (byte) 0xFE;
    public static final int PROXY_MAGIC_VALUE   = 0xEFEFEFEF;

    /**
     * A message that gets injected into pipeline after the successfull handshake on server side. Used to carry
     * information about chosen datacenter number.
     */
    public static class HandshakeCompletedMessage {
        private int datacenterNumber;

        public HandshakeCompletedMessage(int datacenterNumber) {
            this.datacenterNumber = datacenterNumber;
        }

        public int getDatacenterNumber() {
            return datacenterNumber;
        }
    }

    /**
     * Whether we are acting as client.
     */
    private boolean isClient;

    /**
     * Secret key to use. Can be null which means there is no secret at all (e.g. Proxy <-> Telegram connections).
     */
    @Nullable
    private byte[] secret;

    /**
     * Datacenter number when connecting as client. Note that this number will be sent as-is, without any processing.
     */
    private int datacenterNumber;

    /**
     * Buffer to accumulate incoming handshake, needed only for server.
     */
    private ByteBuf handshakeBuffer;

    /**
     * Ciphers that will be used by this handshaker and later by codec.
     */
    private AESCTR encrypter, decrypter;

    /**
     * Promise that will be fulfilled when handshake completes, either client or server.
     */
    private ChannelPromise handshakePromise;

    public Obfuscated2Handshaker(boolean isClient, @Nullable byte[] secret) {
        this.isClient = isClient;
        this.secret = secret;
        this.datacenterNumber = 0;
    }

    public Obfuscated2Handshaker(@Nullable byte[] secret, int datacenterNumber) {
        this.isClient = true;
        this.secret = secret;
        this.datacenterNumber = datacenterNumber;
    }

    public ChannelPromise getHandshakePromise() {
        return handshakePromise;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (!isClient) {
            // Create buffer to accumulate handshake data sent by client.
            handshakeBuffer = ctx.alloc().directBuffer(HANDSHAKE_LENGTH, HANDSHAKE_LENGTH);
        }

        handshakePromise = ctx.newPromise();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (handshakeBuffer != null) {
            handshakeBuffer.release();
            handshakeBuffer = null;
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (isClient) {
            // Perform client handshake
            byte[] randomData = new byte[HANDSHAKE_LENGTH];
            ByteBuf randomBuf = Unpooled.wrappedBuffer(randomData);

            // Generate random encryption key and IV. Not that this random doesn't need to be secure.
            do {
                ThreadLocalRandom.current().nextBytes(randomData);
            } while (checkForSignatures(randomData));

            Utils.intToLittleEndian(PROXY_MAGIC_VALUE, randomData, PROTOCOL_SIGNATURE_OFFSET);

            randomBuf.markWriterIndex();
            randomBuf.writerIndex(PROTOCOL_SIGNATURE_OFFSET + PROTOCOL_SIGNATURE_LENGTH);
            randomBuf.writeShortLE(datacenterNumber);
            randomBuf.resetWriterIndex();

            setupCiphers(randomBuf);

            // Encrypt protocol signature with fresh ciphers:
            encrypter.skipGamma(PROTOCOL_SIGNATURE_OFFSET);
            encrypter.processBuffer(randomBuf, PROTOCOL_SIGNATURE_OFFSET, HANDSHAKE_LENGTH - PROTOCOL_SIGNATURE_OFFSET);

            // Send handshake and replace ourselves:
            ctx.writeAndFlush(randomBuf);
            ctx.pipeline().replace(this, "codec", new Obfuscated2Codec(encrypter, decrypter));
            handshakePromise.setSuccess();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        int toRead = Math.min(handshakeBuffer.writableBytes(), buf.readableBytes());
        buf.readBytes(handshakeBuffer, toRead);

        if (handshakeBuffer.readableBytes() == HANDSHAKE_LENGTH) {
            // Complete handshake was received, handle it.
            setupCiphers(handshakeBuffer);
            decrypter.processBuffer(handshakeBuffer);
            handshakeBuffer.skipBytes(PROTOCOL_SIGNATURE_OFFSET);

            int protocolSignature = handshakeBuffer.readIntLE();
            if (protocolSignature != PROXY_MAGIC_VALUE) {
                ctx.close();
                return;
            }

            int dcIndex = handshakeBuffer.readShortLE();

            // Replace ourselves and fire channel read for the rest of message:
            ctx.pipeline().replace(this, "codec", new Obfuscated2Codec(encrypter, decrypter));
            ctx.pipeline().fireChannelRead(new HandshakeCompletedMessage(dcIndex));
            ctx.pipeline().fireChannelRead(buf);
            handshakePromise.setSuccess();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.error("Caught exception during Obfuscated2 handshake with {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
        handshakePromise.setFailure(cause);
    }

    private void setupCiphers(ByteBuf handshakeBytes) {
        byte[] forwardKey = new byte[KEY_LENGTH];
        byte[] forwardIv = new byte[IV_LENGTH];

        handshakeBytes.getBytes(KEY_IV_OFFSET, forwardKey);
        handshakeBytes.getBytes(KEY_IV_OFFSET + KEY_LENGTH, forwardIv);

        byte[] reverseKey = new byte[KEY_LENGTH];
        byte[] reverseIv = new byte[IV_LENGTH];

        handshakeBytes.getBytes(KEY_IV_OFFSET + IV_LENGTH, reverseKey);
        handshakeBytes.getBytes(KEY_IV_OFFSET, reverseIv);

        Utils.reverse(reverseKey);
        Utils.reverse(reverseIv);

        AESCTR forward = AESCTR.fromKeyAndSecret(forwardKey, forwardIv, secret);
        AESCTR reverse = AESCTR.fromKeyAndSecret(reverseKey, reverseIv, secret);

        encrypter = isClient ? forward : reverse;
        decrypter = isClient ? reverse : forward;
    }

    private static boolean checkForSignatures(byte[] data) {
        if (data[0] == ABRIDGED_SIGNATURE) {
            return true; // Signature for abridged MTProto
        }

        int check2 = Utils.littleEndianToInt(data, 4);
        if (check2 == 0) {
            return true; // For some reason Telegram client do not use this.
        }

        int check1 = Utils.littleEndianToInt(data, 0);

        // Signatures for HTTP (GET, POST, HEAD, OPTIONS) and intermediate MTProto
        return check1 == 0x44414548 || check1 == 0x54534f50 || check1 == 0x20544547 || check1 == 0x4954504f ||
                check1 == 0xEEEEEEEE;
    }

    public static Obfuscated2Handshaker fromPeer(PeerRecord pr) {
        return fromPeer(pr, 0);
    }

    public static Obfuscated2Handshaker fromPeer(PeerRecord pr, int datacenterNumber) {
        return new Obfuscated2Handshaker(pr.getSecret(), datacenterNumber);
    }
}
