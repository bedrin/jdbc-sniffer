package io.sniffy.socket;

import io.qameta.allure.Feature;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@RunWith(MockitoJUnitRunner.class)
public class SnifferOutputStreamTest {

    private static final byte[] DATA = new byte[]{1,2,3,4};

    @Mock
    private SnifferSocketImpl snifferSocket;

    @Test
    public void testWriteByteByByte() throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SnifferOutputStream sos = new SnifferOutputStream(snifferSocket, baos);

        for (byte b : DATA) {
            sos.write(b);
        }

        verify(snifferSocket, times(4)).logSocket(anyLong(), eq(0), eq(1));

    }

    @Test
    public void testWriteByteArray() throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SnifferOutputStream sos = new SnifferOutputStream(snifferSocket, baos);

        sos.write(DATA);

        verify(snifferSocket).logSocket(anyLong(), eq(0), eq(DATA.length));

    }

    @Test
    @Feature("issues/219")
    public void testWriteByteArrayThreeTcpPackets() throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SnifferOutputStream sos = new SnifferOutputStream(snifferSocket, baos);

        byte[] THREE_BYTES_CHUNK = {1, 2, 3};
        byte[] DATA_FOR_TWO_TCP_WINDOWS = new byte[SniffyNetworkConnection.DEFAULT_TCP_WINDOW_SIZE * 2];
        Arrays.fill(DATA_FOR_TWO_TCP_WINDOWS, (byte) 1);

        byte[] ALL_DATA = new byte[THREE_BYTES_CHUNK.length + DATA_FOR_TWO_TCP_WINDOWS.length];
        System.arraycopy(THREE_BYTES_CHUNK, 0, ALL_DATA, 0, THREE_BYTES_CHUNK.length);
        System.arraycopy(DATA_FOR_TWO_TCP_WINDOWS, 0, ALL_DATA, THREE_BYTES_CHUNK.length, DATA_FOR_TWO_TCP_WINDOWS.length);

        sos.write(THREE_BYTES_CHUNK);

        when(snifferSocket.getPotentiallyBufferedOutputBytes()).thenReturn(THREE_BYTES_CHUNK.length);

        sos.write(DATA_FOR_TWO_TCP_WINDOWS);

        verify(snifferSocket, times(2)).checkConnectionAllowed(eq(0));

        InOrder inOrder = Mockito.inOrder(snifferSocket);

        inOrder.verify(snifferSocket).checkConnectionAllowed(eq(0));
        inOrder.verify(snifferSocket).checkConnectionAllowed(eq(1));
        inOrder.verify(snifferSocket).logSocket(anyLong(), eq(0), eq(THREE_BYTES_CHUNK.length));

        inOrder.verify(snifferSocket).checkConnectionAllowed(eq(0));
        inOrder.verify(snifferSocket).checkConnectionAllowed(eq(2));
        inOrder.verify(snifferSocket).logSocket(anyLong(), eq(0), eq(DATA_FOR_TWO_TCP_WINDOWS.length));

        inOrder.verify(snifferSocket, times(0)).checkConnectionAllowed(anyInt());
        inOrder.verify(snifferSocket, times(0)).logSocket(anyLong(), anyInt(), anyInt());

        assertArrayEquals(ALL_DATA, baos.toByteArray());

    }

    @Test
    public void testWriteByteArrayWithOffset() throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SnifferOutputStream sos = new SnifferOutputStream(snifferSocket, baos);

        sos.write(DATA, 1, 2);

        verify(snifferSocket).logSocket(anyLong(), eq(0), eq(2));

    }

    @Test
    public void testFlush() throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SnifferOutputStream sos = new SnifferOutputStream(snifferSocket, baos);

        sos.flush();

        verify(snifferSocket).logSocket(anyLong());

    }

    @Test
    public void testClose() throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SnifferOutputStream sos = new SnifferOutputStream(snifferSocket, baos);

        sos.close();

        verify(snifferSocket).logSocket(anyLong());

    }

}
