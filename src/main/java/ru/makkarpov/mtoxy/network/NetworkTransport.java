package ru.makkarpov.mtoxy.network;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public enum NetworkTransport {
    NIO     (true, NioEventLoopGroup.class, NioSocketChannel.class, NioServerSocketChannel.class),
    EPOLL   (Epoll.isAvailable(), EpollEventLoopGroup.class, EpollSocketChannel.class, EpollServerSocketChannel.class),
    KQUEUE  (KQueue.isAvailable(), KQueueEventLoopGroup.class, KQueueSocketChannel.class, KQueueServerSocketChannel.class);

    public final boolean isAvailable;
    public final Class<? extends EventLoopGroup> eventLoopGroup;
    public final Class<? extends SocketChannel> socketChannel;
    public final Class<? extends ServerSocketChannel> serverSocketChannel;

    private Constructor<? extends EventLoopGroup> eventLoopGroupCtor;

    NetworkTransport(boolean isAvailable, Class<? extends EventLoopGroup> eventLoopGroup,
                     Class<? extends SocketChannel> socketChannel,
                     Class<? extends ServerSocketChannel> serverSocketChannel) {
        this.isAvailable = isAvailable;
        this.eventLoopGroup = eventLoopGroup;
        this.socketChannel = socketChannel;
        this.serverSocketChannel = serverSocketChannel;

        try {
            eventLoopGroupCtor = eventLoopGroup.getConstructor(int.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Event loop group class has no (int) constructor: " + eventLoopGroup);
        }
    }

    public EventLoopGroup createEventLoopGroup(int nThreads) {
        try {
            return eventLoopGroupCtor.newInstance(nThreads);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create event loop group for " + this + " transport", e);
        }
    }
}
