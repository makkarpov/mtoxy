package ru.makkarpov.mtoxy.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.makkarpov.mtoxy.util.Configuration;
import ru.makkarpov.mtoxy.util.Utils;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StatisticsReporter {
    private Logger LOG = LoggerFactory.getLogger(StatisticsReporter.class);

    private Configuration cfg;
    private StatisticsTracker tracker;
    private Thread reportingThread;
    private long startTime;

    @Inject
    public StatisticsReporter(Configuration cfg, StatisticsTracker tracker) {
        this.cfg = cfg;
        this.tracker = tracker;
        this.startTime = System.currentTimeMillis();
    }

    private String formatStatistics(ConnectionType connectionType) {
        return "connections: " + tracker.getConnectionCount(connectionType) +
                ", alive: " + tracker.getActiveConnections(connectionType) +
                ", upstream failures: " + tracker.getConnectionFailures(connectionType) +
                ", exceptions: " + tracker.getConnectionExceptions(connectionType) +
                "; traffic: " + Utils.formatSize(tracker.getBytesForwarded(connectionType));
    }

    private void doRun() {
        while (true) {
            try {
                Thread.sleep(cfg.getStatisticsReportInterval());
            } catch (InterruptedException e) {
                break;
            }

            LOG.info("Uptime: {}", Utils.formatTime(System.currentTimeMillis() - startTime));

            if (cfg.hasHttpBackend()) {
                LOG.info("MTProto statistics: {}", formatStatistics(ConnectionType.MTPROTO));
                LOG.info("HTTP statistics:    {}", formatStatistics(ConnectionType.HTTP));
            } else {
                LOG.info("Traffic statistics: {}", formatStatistics(ConnectionType.MTPROTO));
            }
        }
    }

    public void run() {
        if (cfg.getStatisticsReportInterval() == 0) {
            return;
        }

        reportingThread = new Thread(this::doRun);
        reportingThread.setDaemon(true);
        reportingThread.setName("Statistics reporting thread");
        reportingThread.start();
    }
}
