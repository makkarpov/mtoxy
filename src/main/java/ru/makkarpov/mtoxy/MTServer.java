package ru.makkarpov.mtoxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.makkarpov.mtoxy.network.ProtocolDetector;
import ru.makkarpov.mtoxy.util.Configuration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class MTServer {
    private static final Logger LOG = LoggerFactory.getLogger(MTServer.class);

    private Set<Channel> masterChannels = new HashSet<>();

    private Configuration cfg;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    @Inject
    public MTServer(Configuration cfg) {
        this.cfg = cfg;
        bossGroup = new NioEventLoopGroup(cfg.getBossThreads());
        workerGroup = new NioEventLoopGroup(cfg.getWorkerThreads());
    }

    public void start() {

        ServerBootstrap sb = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new ProtocolDetector(MTServer.this, cfg));
                    }
                });

        for (InetSocketAddress a: cfg.getListenAddresses()) {
            masterChannels.add(sb.bind(a).awaitUninterruptibly().channel());
        }

        LOG.info("MTProto server was started successfully");
    }

    public NioEventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public NioEventLoopGroup getWorkerGroup() {
        return workerGroup;
    }
}
