package ru.makkarpov.mtoxy.stats;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class StatisticsTracker {
    private AtomicInteger[] connectionCounts;
    private AtomicInteger[] activeConnections;
    private AtomicInteger[] connectionFailures;
    private AtomicInteger[] connectionExceptions;
    private AtomicLong[] bytesForwarded;

    @Inject
    public StatisticsTracker() {
        connectionCounts = new AtomicInteger[ConnectionType.values().length];
        activeConnections = new AtomicInteger[ConnectionType.values().length];
        connectionFailures = new AtomicInteger[ConnectionType.values().length];
        connectionExceptions = new AtomicInteger[ConnectionType.values().length];
        bytesForwarded = new AtomicLong[ConnectionType.values().length];
        for (int i = 0; i < connectionCounts.length; i++) {
            connectionCounts[i] = new AtomicInteger(0);
            activeConnections[i] = new AtomicInteger(0);
            connectionFailures[i] = new AtomicInteger(0);
            connectionExceptions[i] = new AtomicInteger(0);
            bytesForwarded[i] = new AtomicLong(0);
        }
    }

    public void connectionStarted(ConnectionType type) {
        connectionCounts[type.ordinal()].incrementAndGet();
        activeConnections[type.ordinal()].incrementAndGet();
    }

    public void connectionFinished(ConnectionType type) {
        activeConnections[type.ordinal()].decrementAndGet();
    }

    public void connectionFailed(ConnectionType type) {
        connectionFailures[type.ordinal()].incrementAndGet();
    }

    public void connectionException(ConnectionType type) {
        connectionExceptions[type.ordinal()].incrementAndGet();
    }

    public void bytesForwarded(ConnectionType type, long bytes) {
        bytesForwarded[type.ordinal()].addAndGet(bytes);
    }

    public int getConnectionCount(ConnectionType type) {
        return connectionCounts[type.ordinal()].get();
    }

    public int getActiveConnections(ConnectionType type) {
        return activeConnections[type.ordinal()].get();
    }

    public int getConnectionFailures(ConnectionType type) {
        return connectionFailures[type.ordinal()].get();
    }

    public int getConnectionExceptions(ConnectionType type) {
        return connectionExceptions[type.ordinal()].get();
    }

    public long getBytesForwarded(ConnectionType type) {
        return bytesForwarded[type.ordinal()].get();
    }
}
