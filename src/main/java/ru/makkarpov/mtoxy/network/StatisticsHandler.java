package ru.makkarpov.mtoxy.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import ru.makkarpov.mtoxy.stats.ConnectionType;
import ru.makkarpov.mtoxy.stats.StatisticsTracker;

@ChannelHandler.Sharable
public class StatisticsHandler extends ChannelDuplexHandler {
    private StatisticsTracker statisticsTracker;
    private ConnectionType connectionType;

    public StatisticsHandler(StatisticsTracker statisticsTracker, ConnectionType connectionType) {
        this.statisticsTracker = statisticsTracker;
        this.connectionType = connectionType;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        statisticsTracker.connectionStarted(connectionType);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        statisticsTracker.connectionFinished(connectionType);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise);
        if (msg instanceof ByteBuf) {
            statisticsTracker.bytesForwarded(connectionType, ((ByteBuf) msg).readableBytes());
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        if (msg instanceof ByteBuf) {
            statisticsTracker.bytesForwarded(connectionType, ((ByteBuf) msg).readableBytes());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        statisticsTracker.connectionException(connectionType);
    }
}
