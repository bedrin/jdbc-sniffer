package io.sniffy;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import io.sniffy.configuration.SniffyConfiguration;
import io.sniffy.socket.NetworkPacket;
import io.sniffy.socket.SocketMetaData;
import io.sniffy.socket.SocketStats;
import io.sniffy.sql.SqlStats;
import io.sniffy.sql.StatementMetaData;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @since 3.1
 */
public abstract class BaseSpy<C extends BaseSpy<C>> {

    private final SpyConfiguration spyConfiguration;

    protected volatile ConcurrentLinkedHashMap<StatementMetaData, SqlStats> executedStatements =
            new ConcurrentLinkedHashMap.Builder<StatementMetaData, SqlStats>().
                    maximumWeightedCapacity(Long.MAX_VALUE).
                    build();

    protected volatile ConcurrentLinkedHashMap<SocketMetaData, SocketStats> socketOperations =
            new ConcurrentLinkedHashMap.Builder<SocketMetaData, SocketStats>().
                    maximumWeightedCapacity(Long.MAX_VALUE).
                    build();

    protected volatile ConcurrentLinkedHashMap<SocketMetaData, Deque<NetworkPacket>> networkTraffic =
            new ConcurrentLinkedHashMap.Builder<SocketMetaData, Deque<NetworkPacket>>().
                    maximumWeightedCapacity(Long.MAX_VALUE).
                    build();

    protected volatile ConcurrentLinkedHashMap<SocketMetaData, Deque<NetworkPacket>> decryptedNetworkTraffic =
            new ConcurrentLinkedHashMap.Builder<SocketMetaData, Deque<NetworkPacket>>().
                    maximumWeightedCapacity(Long.MAX_VALUE).
                    build();

    protected void addNetworkTraffic(
            SocketMetaData socketMetaData,
            boolean sent, long timestamp,
            String stackTrace, ThreadMetaData threadMetaData,
            byte[] traffic, int off, int len) {
        Deque<NetworkPacket> networkPackets = networkTraffic.get(socketMetaData);
        if (null == networkPackets) {
            networkTraffic.putIfAbsent(socketMetaData, networkPackets = new ConcurrentLinkedDeque<NetworkPacket>());
        }
        NetworkPacket lastPacket = networkPackets.peekLast();
        if (null == lastPacket || !lastPacket.combine(sent, timestamp, stackTrace, threadMetaData, traffic, off, len, SniffyConfiguration.INSTANCE.getPacketMergeThreshold())) {
            networkPackets.add(new NetworkPacket(sent, timestamp, stackTrace, threadMetaData, traffic, off, len));
        }
    }

    protected void addDecryptedNetworkTraffic(
            SocketMetaData socketMetaData,
            boolean sent, long timestamp,
            String stackTrace, ThreadMetaData threadMetaData,
            byte[] traffic, int off, int len) {
        Deque<NetworkPacket> networkPackets = decryptedNetworkTraffic.get(socketMetaData);
        if (null == networkPackets) {
            decryptedNetworkTraffic.putIfAbsent(socketMetaData, networkPackets = new ConcurrentLinkedDeque<NetworkPacket>());
        }
        NetworkPacket lastPacket = networkPackets.peekLast();
        if (null == lastPacket || !lastPacket.combine(sent, timestamp, stackTrace, threadMetaData, traffic, off, len, SniffyConfiguration.INSTANCE.getPacketMergeThreshold())) {
            networkPackets.add(new NetworkPacket(sent, timestamp, stackTrace, threadMetaData, traffic, off, len));
        }
    }

    protected BaseSpy(SpyConfiguration spyConfiguration) {
        this.spyConfiguration = spyConfiguration;
    }

    public C reset() {
        resetExecutedStatements();
        resetSocketOpertions();
        return self();
    }

    @SuppressWarnings("unchecked")
    protected C self() {
        return (C) this;
    }

    protected SpyConfiguration getSpyConfiguration() {
        return spyConfiguration;
    }

    protected void resetExecutedStatements() {
        executedStatements =
                new ConcurrentLinkedHashMap.Builder<StatementMetaData, SqlStats>().
                        maximumWeightedCapacity(Long.MAX_VALUE).
                        build();
    }

    protected void resetSocketOpertions() {
        socketOperations = new ConcurrentLinkedHashMap.Builder<SocketMetaData, SocketStats>().
                maximumWeightedCapacity(Long.MAX_VALUE).
                build();
    }


    protected void addExecutedStatement(StatementMetaData statementMetaData, long elapsedTime, int bytesDown, int bytesUp, long rowsUpdated) {
        SqlStats sqlStats = executedStatements.get(statementMetaData);
        if (null == sqlStats) {
            sqlStats = executedStatements.putIfAbsent(statementMetaData, new SqlStats(elapsedTime, bytesDown, bytesUp, rowsUpdated, 1));
        }
        if (null != sqlStats) {
            sqlStats.accumulate(elapsedTime, bytesDown, bytesUp, rowsUpdated, 1);
        }
    }

    protected void addReturnedRow(StatementMetaData statementMetaData) {
        SqlStats sqlStats = executedStatements.get(statementMetaData);
        if (null != sqlStats) {
            sqlStats.accumulate(0, 0, 0, 1, 0);
        }
    }

    protected void addSocketOperation(SocketMetaData socketMetaData, long elapsedTime, int bytesDown, int bytesUp) {
        SocketStats socketStats = socketOperations.get(socketMetaData);
        if (null == socketStats) {
            socketStats = socketOperations.putIfAbsent(socketMetaData, new SocketStats(elapsedTime, bytesDown, bytesUp));
        }
        if (null != socketStats) {
            socketStats.accumulate(elapsedTime, bytesDown, bytesUp);
        }
    }

}
