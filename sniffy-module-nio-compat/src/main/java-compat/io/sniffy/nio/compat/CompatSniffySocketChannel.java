package io.sniffy.nio.compat;

import io.sniffy.Sniffy;
import io.sniffy.SpyConfiguration;
import io.sniffy.configuration.SniffyConfiguration;
import io.sniffy.registry.ConnectionsRegistry;
import io.sniffy.socket.Protocol;
import io.sniffy.socket.SniffyNetworkConnection;
import io.sniffy.socket.SniffySocket;
import io.sniffy.util.ExceptionUtil;
import io.sniffy.util.JVMUtil;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @since 3.1.7
 */
public class CompatSniffySocketChannel extends CompatSniffySocketChannelAdapter implements SniffyNetworkConnection {

    private final int id = Sniffy.CONNECTION_ID_SEQUENCE.getAndIncrement();

    protected static volatile Integer defaultReceiveBufferSize;
    protected static volatile Integer defaultSendBufferSize;

    // fields related to injecting latency fault
    private volatile int potentiallyBufferedInputBytes = 0;
    private volatile int potentiallyBufferedOutputBytes = 0;

    private volatile long lastReadThreadId;
    private volatile long lastWriteThreadId;

    private volatile Integer connectionStatus;

    protected CompatSniffySocketChannel(SelectorProvider provider, SocketChannel delegate) {
        super(provider, delegate);
    }

    @Override
    public void setConnectionStatus(Integer connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    @Override
    public InetSocketAddress getInetSocketAddress() {
        try {
            if (JVMUtil.getVersion() > 6) {
                return (InetSocketAddress) getRemoteAddress();
            } else {
                return (InetSocketAddress) socket().getRemoteSocketAddress();
            }
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        }
    }


    /**
     * Adds a delay as defined for current {@link SnifferSocketImpl} in {@link ConnectionsRegistry#discoveredDataSources}
     * <p>
     * Delay is added for each <b>N</b> bytes received where <b>N</b> is the value of {@link SocketOptions#SO_RCVBUF}
     * <p>
     * If application reads <b>M</b> bytes where (k-1) * N &lt; M  &lt; k * N exactly <b>k</b> delays will be added
     * <p>
     * A call to {@link SnifferOutputStream} obtained from the same {@link SnifferSocketImpl} and made from the same thread
     * will reset the number of buffered (i.e. which can be read without delay) bytes to 0 effectively adding a guaranteed
     * delay to any subsequent {@link SnifferInputStream#read()} request
     * <p>
     * TODO: consider if {@link java.net.SocketInputStream#available()} method can be of any use here
     *
     * @param bytesDown number of bytes received from socket
     * @throws ConnectException on underlying socket exception
     */
    private void sleepIfRequired(int bytesDown) throws ConnectException {

        lastReadThreadId = Thread.currentThread().getId();

        if (lastReadThreadId == lastWriteThreadId) {
            potentiallyBufferedOutputBytes = 0;
        }

        int potentiallyBufferedInputBytes = this.potentiallyBufferedInputBytes -= bytesDown;

        if (potentiallyBufferedInputBytes < 0) {
            int estimatedNumberOfTcpPackets = 1 + (-1 * potentiallyBufferedInputBytes) / SniffyNetworkConnection.DEFAULT_TCP_WINDOW_SIZE;
            checkConnectionAllowed(estimatedNumberOfTcpPackets);
            this.potentiallyBufferedInputBytes = SniffyNetworkConnection.DEFAULT_TCP_WINDOW_SIZE;
        }

    }

    /**
     * Adds a delay as defined for current {@link SnifferSocketImpl} in {@link ConnectionsRegistry#discoveredDataSources}
     * <p>
     * Delay is added for each <b>N</b> bytes sent where <b>N</b> is the value of {@link SocketOptions#SO_SNDBUF}
     * <p>
     * If application writes <b>M</b> bytes where (k-1) * N &lt; M  &lt; k * N exactly <b>k</b> delays will be added
     * <p>
     * A call to {@link SnifferInputStream} obtained from the same {@link SnifferSocketImpl} and made from the same thread
     * will reset the number of buffered (i.e. which can be written without delay) bytes to 0 effectively adding a guaranteed
     * delay to any subsequent {@link SnifferOutputStream#write(int)} request
     *
     * @param bytesUp number of bytes sent to socket
     * @throws ConnectException on underlying socket exception
     */
    private void sleepIfRequiredForWrite(int bytesUp) throws ConnectException {

        lastWriteThreadId = Thread.currentThread().getId();

        if (lastReadThreadId == lastWriteThreadId) {
            potentiallyBufferedInputBytes = 0;
        }

        int potentiallyBufferedOutputBytes = this.potentiallyBufferedOutputBytes -= bytesUp;

        if (potentiallyBufferedOutputBytes < 0) {
            int estimatedNumberOfTcpPackets = 1 + (-1 * potentiallyBufferedOutputBytes) / SniffyNetworkConnection.DEFAULT_TCP_WINDOW_SIZE;
            checkConnectionAllowed(estimatedNumberOfTcpPackets);
            this.potentiallyBufferedOutputBytes = SniffyNetworkConnection.DEFAULT_TCP_WINDOW_SIZE;
        }

    }

    @Deprecated
    public void logSocket(long millis) {
        logSocket(millis, 0, 0);
    }

    @Deprecated
    public void logSocket(long millis, int bytesDown, int bytesUp) {

        if (!SniffyConfiguration.INSTANCE.getSocketCaptureEnabled()) return;

        Sniffy.SniffyMode sniffyMode = Sniffy.getSniffyMode();
        if (sniffyMode.isEnabled() && null != getInetSocketAddress() && (millis > 0 || bytesDown > 0 || bytesUp > 0)) {
            Sniffy.logSocket(id, getInetSocketAddress(), millis, bytesDown, bytesUp, sniffyMode.isCaptureStackTraces());
        }
    }

    public void logTraffic(boolean sent, Protocol protocol, byte[] traffic, int off, int len) {
        SpyConfiguration effectiveSpyConfiguration = Sniffy.getEffectiveSpyConfiguration();
        if (effectiveSpyConfiguration.isCaptureNetworkTraffic()) {
            Sniffy.logTraffic(
                    id, getInetSocketAddress(),
                    sent, protocol,
                    traffic, off, len,
                    effectiveSpyConfiguration.isCaptureStackTraces()
            );
        }
    }

    public void checkConnectionAllowed() throws ConnectException {
        checkConnectionAllowed(0);
    }

    public void checkConnectionAllowed(int numberOfSleepCycles) throws ConnectException {
        checkConnectionAllowed(getInetSocketAddress(), numberOfSleepCycles);
    }

    public void checkConnectionAllowed(InetSocketAddress inetSocketAddress) throws ConnectException {
        checkConnectionAllowed(inetSocketAddress, 1);
    }

    public void checkConnectionAllowed(InetSocketAddress inetSocketAddress, int numberOfSleepCycles) throws ConnectException {

        if (!SniffyConfiguration.INSTANCE.getSocketFaultInjectionEnabled()) return;

        if (null != inetSocketAddress) {
            if (null == this.connectionStatus || ConnectionsRegistry.INSTANCE.isThreadLocal()) {
                this.connectionStatus = ConnectionsRegistry.INSTANCE.resolveSocketAddressStatus(inetSocketAddress, this);
            }
            if (connectionStatus < 0) {
                if (numberOfSleepCycles > 0 && -1 != connectionStatus) try {
                    sleepImpl(-1 * connectionStatus * numberOfSleepCycles);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new ConnectException(String.format("Connection to %s refused by Sniffy", inetSocketAddress));
            } else if (numberOfSleepCycles > 0 && connectionStatus > 0) {
                try {
                    sleepImpl(connectionStatus * numberOfSleepCycles);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void sleepImpl(int millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        long start = System.currentTimeMillis();
        try {
            checkConnectionAllowed((InetSocketAddress) remote, 1);
            return super.connect(remote);
        } finally {
            logSocket(System.currentTimeMillis() - start);
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        checkConnectionAllowed(0);
        long start = System.currentTimeMillis();
        int bytesDown = 0;
        int position = dst.position();
        try {
            return bytesDown = super.read(dst);
        } finally {
            sleepIfRequired(bytesDown);
            logSocket(System.currentTimeMillis() - start, bytesDown, 0);
            SpyConfiguration effectiveSpyConfiguration = Sniffy.getEffectiveSpyConfiguration();
            if (effectiveSpyConfiguration.isCaptureNetworkTraffic()) {
                dst.position(position);
                byte[] buff = new byte[bytesDown];
                dst.get(buff, 0, bytesDown);
                logTraffic(false, Protocol.TCP, buff, 0, buff.length);
            }
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        checkConnectionAllowed(0);
        long start = System.currentTimeMillis();
        long bytesDown = 0;

        int[] positions = new int[length];
        int[] remainings = new int[length];

        for (int i = 0; i < length; i++) {
            positions[i] = dsts[offset + i].position();
            remainings[i] = dsts[offset + i].remaining();
        }

        try {
            bytesDown = super.read(dsts, offset, length);
            return bytesDown;
        } finally {
            while (bytesDown > Integer.MAX_VALUE) {
                sleepIfRequiredForWrite(Integer.MAX_VALUE);
                logSocket(System.currentTimeMillis() - start, Integer.MAX_VALUE, 0);
                bytesDown -= Integer.MAX_VALUE;
            }
            logSocket(System.currentTimeMillis() - start, (int) bytesDown, 0);

            SpyConfiguration effectiveSpyConfiguration = Sniffy.getEffectiveSpyConfiguration();
            if (effectiveSpyConfiguration.isCaptureNetworkTraffic()) {
                for (int i = 0; i < length; i++) {
                    dsts[offset + i].position(positions[i]);
                    byte[] buff = new byte[remainings[i]];
                    dsts[offset + i].get(buff, 0, remainings[i]);
                    logTraffic(false, Protocol.TCP, buff, 0, buff.length);
                }

            }
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkConnectionAllowed(0);
        long start = System.currentTimeMillis();
        int length = 0;

        int position = src.position();

        try {
            length = super.write(src);
            return length;
        } finally {
            sleepIfRequiredForWrite(length);
            logSocket(System.currentTimeMillis() - start, 0, length);
            SpyConfiguration effectiveSpyConfiguration = Sniffy.getEffectiveSpyConfiguration();
            if (effectiveSpyConfiguration.isCaptureNetworkTraffic()) {
                src.position(position);
                byte[] buff = new byte[length];
                src.get(buff, 0, length);
                logTraffic(true, Protocol.TCP, buff, 0, buff.length);
            }
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        checkConnectionAllowed(0);
        long start = System.currentTimeMillis();
        long bytesUp = 0;

        int[] positions = new int[length];
        int[] remainings = new int[length];

        for (int i = 0; i < length; i++) {
            positions[i] = srcs[offset + i].position();
            remainings[i] = srcs[offset + i].remaining();
        }

        try {
            bytesUp = super.write(srcs, offset, length);
            return bytesUp;
        } finally {
            while (bytesUp > Integer.MAX_VALUE) {
                sleepIfRequiredForWrite(Integer.MAX_VALUE);
                logSocket(System.currentTimeMillis() - start, 0, Integer.MAX_VALUE);
                bytesUp -= Integer.MAX_VALUE;
            }
            sleepIfRequiredForWrite((int) bytesUp);
            logSocket(System.currentTimeMillis() - start, 0, (int) bytesUp);
            SpyConfiguration effectiveSpyConfiguration = Sniffy.getEffectiveSpyConfiguration();
            if (effectiveSpyConfiguration.isCaptureNetworkTraffic()) {
                for (int i = 0; i < length; i++) {
                    srcs[offset + i].position(positions[i]);
                    byte[] buff = new byte[remainings[i]];
                    srcs[offset + i].get(buff, 0, remainings[i]);
                    logTraffic(true, Protocol.TCP, buff, 0, buff.length);
                }

            }
        }
    }

    @Override
    public Socket socket() {
        try {
            return new SniffySocket(super.socket(), this, id,
                JVMUtil.getVersion() > 6 ? getInetSocketAddress() : null);
        } catch (SocketException e) {
            e.printStackTrace();
            return super.socket();
        }
    }

    //

    @Override
    public int getPotentiallyBufferedInputBytes() {
        return potentiallyBufferedInputBytes;
    }

    @Override
    public void setPotentiallyBufferedInputBytes(int potentiallyBufferedInputBytes) {
        this.potentiallyBufferedInputBytes = potentiallyBufferedInputBytes;
    }

    @Override
    public int getPotentiallyBufferedOutputBytes() {
        return potentiallyBufferedOutputBytes;
    }

    @Override
    public void setPotentiallyBufferedOutputBytes(int potentiallyBufferedOutputBytes) {
        this.potentiallyBufferedOutputBytes = potentiallyBufferedOutputBytes;
    }

    @Override
    public long getLastReadThreadId() {
        return lastReadThreadId;
    }

    @Override
    public void setLastReadThreadId(long lastReadThreadId) {
        this.lastReadThreadId = lastReadThreadId;
    }

    @Override
    public long getLastWriteThreadId() {
        return lastWriteThreadId;
    }

    @Override
    public void setLastWriteThreadId(long lastWriteThreadId) {
        this.lastWriteThreadId = lastWriteThreadId;
    }

}
