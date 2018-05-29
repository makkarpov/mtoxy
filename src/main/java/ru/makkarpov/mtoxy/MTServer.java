package ru.makkarpov.mtoxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.makkarpov.mtoxy.network.ProtocolDetector;
import ru.makkarpov.mtoxy.stats.StatisticsTracker;
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
    private StatisticsTracker statisticsTracker;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @Inject
    public MTServer(Configuration cfg, StatisticsTracker statisticsTracker) {
        this.cfg = cfg;
        this.statisticsTracker = statisticsTracker;
        bossGroup = cfg.getNetworkTransport().createEventLoopGroup(cfg.getBossThreads());
        workerGroup = cfg.getNetworkTransport().createEventLoopGroup(cfg.getWorkerThreads());
    }

    public void start() {
        ServerBootstrap sb = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(cfg.getNetworkTransport().serverSocketChannel)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new ProtocolDetector(MTServer.this));
                    }
                });

        for (InetSocketAddress a: cfg.getListenAddresses()) {
            masterChannels.add(sb.bind(a).awaitUninterruptibly().channel());
        }

        LOG.info("MTProto server was started successfully");
    }

    public Configuration getConfiguration() {
        return cfg;
    }

    public StatisticsTracker getStatisticsTracker() {
        return statisticsTracker;
    }

    public EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    public EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    public Bootstrap getBootstrap(InetSocketAddress remote) {
        return new Bootstrap()
                .group(getWorkerGroup())
                .channel(cfg.getNetworkTransport().socketChannel)
                .remoteAddress(remote);
    }
}
