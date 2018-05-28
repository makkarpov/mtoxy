package ru.makkarpov.mtoxy.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.makkarpov.mtoxy.MTServer;
import ru.makkarpov.mtoxy.util.Configuration;

import java.net.InetSocketAddress;

@ChannelHandler.Sharable
public class ProtocolDetector extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ProtocolDetector.class);

    private MTServer mtServer;
    private Configuration cfg;

    public ProtocolDetector(MTServer mtServer, Configuration cfg) {
        this.mtServer = mtServer;
        this.cfg = cfg;
    }

    private void setupHttpConnection(ChannelHandlerContext ctx, Object msg) {
        if (!cfg.hasHttpBackend()) {
            ctx.close();
            return;
        }

        Bootstrap bs = new Bootstrap()
                .group(mtServer.getWorkerGroup())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        // Dummy, that is needed.
                    }
                });

        InetSocketAddress backend = cfg.getHttpBackend();
        ChannelFuture future = bs.connect(backend);
        future.addListener(f -> {
            Channel ch = future.channel();
            if (future.isSuccess()) {
                ForwardingHandler.setupForwarding(ctx.channel(), ch);
                ctx.pipeline().fireChannelRead(msg);
            } else {
                LOG.info("Cannot set up HTTP forwarding {} -> {}", ctx.channel().remoteAddress(), backend,
                        future.cause());
                ctx.close();
            }
        });
    }

    private void setupMtConnection(ChannelHandlerContext ctx, Object msg) {
        ctx.pipeline().remove(this);
        ctx.pipeline().addLast(new Obfuscated2Handshaker(false, cfg.getSecretKey()));
        ctx.pipeline().addLast(new DatacenterConnectionHandler(mtServer, cfg));
        ctx.pipeline().fireChannelRead(msg);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;

        // Is that HTTP?
        if (buf.readableBytes() >= 4) {
            buf.markReaderIndex();
            int marker = buf.readInt();
            buf.resetReaderIndex();

            if (isHttpMarker(marker)) {
                setupHttpConnection(ctx, msg);
                return;
            }
        }

        // TODO: Check for TLS signature if possible to allow listening on 443 ports.

        setupMtConnection(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.error("Exception caught in protocol detector from {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    private static boolean isHttpMarker(int marker) {
        return marker == 0x504f5354 /* "POST" */ || marker == 0x47455420 /* "GET " */ ||
                marker == 0x48454144 /* "HEAD" */ || marker == 0x4f505449 /* "OPTI" */;
    }
}
