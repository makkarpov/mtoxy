package ru.makkarpov.mtoxy.util;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nullable;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AESCTR {
    private AESFastEngine engine;
    private byte[] counter, gamma;
    private int gammaPos;

    public AESCTR(byte[] key, byte[] iv) {
        if (iv.length != 16) {
            throw new IllegalArgumentException("Invalid IV length");
        }

        engine = new AESFastEngine();
        engine.init(true, key);

        counter = new byte[16];
        gamma = new byte[16];

        System.arraycopy(iv, 0, counter, 0, 16);
        generateGamma();
    }

    private void generateGamma() {
        engine.processBlock(counter, 0, gamma, 0);
        gammaPos = 0;

        for (int i = 15; i >= 0; i--) {
            counter[i]++;

            if (counter[i] != 0) {
                break;
            }
        }
    }

    public byte nextGamma() {
        byte r = gamma[gammaPos++];

        if (gammaPos >= 16) {
            generateGamma();
        }

        return r;
    }

    public void skipGamma(int n) {
        for (int i = 0; i < n; i++) {
            nextGamma();
        }
    }

    public void processBuffer(byte[] x) {
        processBuffer(x, 0, x.length);
    }

    public void processBuffer(byte[] x, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            x[i] = (byte) (x[i] ^ nextGamma());
        }
    }

    public void processBuffer(ByteBuf x) {
        processBuffer(x, x.readerIndex(), x.readableBytes());
    }

    public void processBuffer(ByteBuf x, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            x.setByte(i, x.getByte(i) ^ nextGamma());
        }
    }

    public static AESCTR fromKeyAndSecret(byte[] key, byte[] iv, @Nullable byte[] secret) {
        if (secret != null) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(key);
                md.update(secret);
                md.digest(key, 0, key.length);
            } catch (NoSuchAlgorithmException | DigestException e) {
                throw new RuntimeException("Failed to perform SHA-256 on key and secret");
            }
        }

        return new AESCTR(key, iv);
    }
}
