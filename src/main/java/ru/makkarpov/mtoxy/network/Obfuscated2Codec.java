package ru.makkarpov.mtoxy.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.makkarpov.mtoxy.util.AESCTR;

import java.util.List;

/**
 * Class that performs cryptographic operations on ByteBuf's and passes all other messages (such as HandshakeComplete)
 * as-is.
 */
public class Obfuscated2Codec extends MessageToMessageCodec<Object, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(Obfuscated2Codec.class);

    private AESCTR encrypter, decrypter;

    public Obfuscated2Codec(AESCTR encrypter, AESCTR decrypter) {
        this.encrypter = encrypter;
        this.decrypter = decrypter;
    }

    private void process(AESCTR cipher, Object msg, List<Object> out) {
        if (msg instanceof ByteBuf) {
            cipher.processBuffer((ByteBuf) msg);
        }

        ReferenceCountUtil.retain(msg);
        out.add(msg);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        process(encrypter, msg, out);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        process(decrypter, msg, out);
    }
}
