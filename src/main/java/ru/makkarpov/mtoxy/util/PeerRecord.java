package ru.makkarpov.mtoxy.util;

import javax.annotation.Nullable;
import javax.xml.bind.DatatypeConverter;
import java.net.InetSocketAddress;
import java.net.URI;

public class PeerRecord {
    private InetSocketAddress address;

    @Nullable
    private byte[] secret;

    public PeerRecord(InetSocketAddress address, @Nullable byte[] secret) {
        this.address = address;
        this.secret = secret;
    }

    public PeerRecord(URI url) {
        int port = url.getPort();
        if (port == -1) {
            port = 443;
        }

        address = new InetSocketAddress(url.getHost(), port);

        switch (url.getScheme()) {
            case "direct":
                secret = null;
                if (!url.getPath().isEmpty()) {
                    throw new IllegalArgumentException(url + ": direct peers cannot have secret value!");
                }
                break;

            case "proxy":
                if (url.getPath().isEmpty()) {
                    throw new IllegalArgumentException(url + ": proxy peers must have a secret value!");
                }

                secret = DatatypeConverter.parseHexBinary(url.getPath().substring(1));
                break;

            default:
                throw new IllegalArgumentException(url + ": unknown peer protocol");
        }
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    @Nullable
    public byte[] getSecret() {
        return secret;
    }

    @Override
    public String toString() {
        if (secret != null) {
            return String.format("proxy %s, secret %s", address, DatatypeConverter.printHexBinary(secret));
        } else {
            return String.format("direct %s", address);
        }
    }
}
