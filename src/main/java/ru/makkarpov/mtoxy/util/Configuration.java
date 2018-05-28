package ru.makkarpov.mtoxy.util;

import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.xml.bind.DatatypeConverter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
public class Configuration {
    private static final Logger LOG = LoggerFactory.getLogger(Configuration.class);

    private Config underlying;

    private byte[] secretKey;
    private List<PeerRecord> peers;
    private List<InetSocketAddress> listenAddresses;

    @Nullable
    private InetSocketAddress httpBackend;

    private int bossThreads, workerThreads;
    private long statisticsReportInterval;

    @Inject
    public Configuration(Config underlying) {
        this.underlying = underlying.getConfig("mtoxy");

        initValues();
    }

    private void initValues() {
        boolean hasSecret = underlying.hasPath("secret");
        boolean hasPassphrase = underlying.hasPath("secret-passphrase");

        if (!hasSecret && !hasPassphrase) {
            throw new IllegalArgumentException("Either 'secret' or 'secret-passphrase' must be specified in config!");
        }

        if (hasSecret && hasPassphrase) {
            throw new IllegalArgumentException("Only one of 'secret' or 'secret-passphrase' must be specified in config!");
        }

        if (hasSecret) {
            try {
                secretKey = DatatypeConverter.parseHexBinary(underlying.getString("secret"));
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot parse 'secret' value in config", e);
            }

            if (secretKey.length != 16) {
                throw new IllegalArgumentException("Secret key must be exactly 16 bytes long");
            }
        }

        if (hasPassphrase) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.update(underlying.getString("secret-passphrase").getBytes(StandardCharsets.UTF_8));
                secretKey = md.digest();
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot hash 'secret-passphrase' value", e);
            }
        }

        peers = new ArrayList<>();
        for (String s: underlying.getStringList("peers")) {
            try {
                peers.add(new PeerRecord(new URI(s)));
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Cannot parse peer URI: " + s, e);
            } catch (Exception e) {
                throw new IllegalArgumentException("Incorrect peer URI specified: " + s, e);
            }
        }
        peers = Collections.unmodifiableList(peers);

        listenAddresses = new ArrayList<>();
        for (String s: underlying.getStringList("listen-addresses")) {
            try {
                listenAddresses.add(Utils.parseAddress(s));
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot parse listen address: " + s, e);
            }
        }
        listenAddresses = Collections.unmodifiableList(listenAddresses);

        if (underlying.hasPath("http-backend")) {
            String s = underlying.getString("http-backend");

            try {
                httpBackend = Utils.parseAddress(s);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot parse HTTP backend address: " + s, e);
            }
        } else {
            httpBackend = null;
        }

        bossThreads = underlying.getInt("boss-threads");
        workerThreads = underlying.getInt("worker-threads");

        statisticsReportInterval = underlying.getDuration("statistics-report-interval", TimeUnit.MILLISECONDS);

        LOG.info("Loaded configuration values:");
        LOG.info(" .. secret key: {}", DatatypeConverter.printHexBinary(secretKey));
        LOG.info(" .. peers:");

        for (PeerRecord pr: peers) {
            LOG.info("      {}", pr);
        }

        LOG.info(" .. listen addresses:");

        for (InetSocketAddress ia: listenAddresses) {
            LOG.info("      {}:{}", ia.getHostString(), ia.getPort());
        }

        LOG.info(" .. HTTP backend address: {}", Optional.ofNullable(httpBackend)
                .map(InetSocketAddress::toString).orElse("<none, drop connections>"));

        LOG.info(" .. boss threads: {}, worker threads: {}", bossThreads, workerThreads);
        LOG.info(" .. statistics report interval: {}",
                (statisticsReportInterval == 0) ? "<disabled>" : Utils.formatTime(statisticsReportInterval));
    }

    public byte[] getSecretKey() {
        return secretKey;
    }

    public List<PeerRecord> getPeers() {
        return peers;
    }

    public List<InetSocketAddress> getListenAddresses() {
        return listenAddresses;
    }

    public boolean hasHttpBackend() {
        return httpBackend != null;
    }

    @Nullable
    public InetSocketAddress getHttpBackend() {
        return httpBackend;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public long getStatisticsReportInterval() {
        return statisticsReportInterval;
    }
}
