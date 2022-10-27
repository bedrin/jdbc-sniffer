package io.sniffy.nio.compat;

import io.sniffy.util.ExceptionUtil;
import io.sniffy.util.ReflectionCopier;
import io.sniffy.util.StackTraceExtractor;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import sun.nio.ch.SocketChannelDelegate;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

import static io.sniffy.util.ReflectionUtil.invokeMethod;

/**
 * @since 3.1.7
 */
public class CompatSniffySocketChannelAdapter extends SocketChannelDelegate implements SelectableChannelWrapper<SocketChannel> {

    private static final ReflectionCopier<SocketChannel> socketChannelFieldsCopier = new ReflectionCopier<SocketChannel>(SocketChannel.class, "provider");

    protected final SocketChannel delegate;

    private volatile boolean hasCancelledKeys;

    protected CompatSniffySocketChannelAdapter(SelectorProvider provider, SocketChannel delegate) {
        super(provider, delegate);
        this.delegate = delegate;
    }

    @Override
    public SocketChannel getDelegate() {
        return delegate;
    }

    @Override
    public void keyCancelled() {
        hasCancelledKeys = true;
    }

    private void copyToDelegate() {
        socketChannelFieldsCopier.copy(this, delegate);
    }

    private void copyFromDelegate() {
        socketChannelFieldsCopier.copy(delegate, this);
    }

    @Override
    public FileDescriptor getFD() {
        if (StackTraceExtractor.hasClassAndMethodInStackTrace("sun.nio.ch.FileChannelImpl", "transferToDirectly")) {
            return null; // disable zero-copy in order to intercept traffic
        } else {
            return super.getFD();
        }
    }

    @SuppressWarnings("Since15")
    @Override
    @IgnoreJRERequirement
    public SocketChannel bind(SocketAddress local) throws IOException {
        try {
            copyToDelegate();
            delegate.bind(local);
            return this;
        } finally {
            copyFromDelegate();
        }
    }

    @SuppressWarnings("Since15")
    @Override
    @IgnoreJRERequirement
    public <T> SocketChannel setOption(java.net.SocketOption<T> name, T value) throws IOException {
        try {
            copyToDelegate();
            delegate.setOption(name, value);
            return this;
        } finally {
            copyFromDelegate();
        }
    }

    @SuppressWarnings("Since15")
    @Override
    @IgnoreJRERequirement
    public SocketChannel shutdownInput() throws IOException {
        try {
            copyToDelegate();
            delegate.shutdownInput();
            return this;
        } finally {
            copyFromDelegate();
        }
    }

    @SuppressWarnings("Since15")
    @Override
    @IgnoreJRERequirement
    public SocketChannel shutdownOutput() throws IOException {
        try {
            copyToDelegate();
            delegate.shutdownOutput();
            return this;
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public Socket socket() {
        try {
            copyToDelegate();
            return delegate.socket(); // TODO: should we wrap it with SniffySocket ??
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public boolean isConnected() {
        try {
            copyToDelegate();
            return delegate.isConnected();
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public boolean isConnectionPending() {
        try {
            copyToDelegate();
            return delegate.isConnectionPending();
        } finally {
            copyFromDelegate();
        }
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        copyToDelegate();
        try {
            return delegate.connect(remote);
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public boolean finishConnect() throws IOException {
        try {
            copyToDelegate();
            return delegate.finishConnect();
        } finally {
            copyFromDelegate();
        }
    }

    @SuppressWarnings("Since15")
    @Override
    @IgnoreJRERequirement
    public SocketAddress getRemoteAddress() throws IOException {
        try {
            copyToDelegate();
            return delegate.getRemoteAddress();
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        try {
            copyToDelegate();
            return delegate.read(dst);
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        try {
            copyToDelegate();
            return delegate.read(dsts, offset, length);
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        try {
            copyToDelegate();
            return delegate.write(src);
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        try {
            copyToDelegate();
            return delegate.write(srcs, offset, length);
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        try {
            copyToDelegate();
            return delegate.getLocalAddress();
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public void implCloseSelectableChannel() {
        try {
            copyToDelegate();
            invokeMethod(AbstractSelectableChannel.class, delegate, "implCloseSelectableChannel", Void.class);
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        } finally {
            copyFromDelegate();
        }
    }

    @Override
    public void implConfigureBlocking(boolean block) {
        try {
            copyToDelegate();
            invokeMethod(AbstractSelectableChannel.class, delegate, "implConfigureBlocking", Boolean.TYPE, block, Void.class);
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        } finally {
            copyFromDelegate();
        }
    }

    @SuppressWarnings("Since15")
    @Override
    @IgnoreJRERequirement
    public <T> T getOption(java.net.SocketOption<T> name) throws IOException {
        try {
            copyToDelegate();
            return delegate.getOption(name);
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        } finally {
            copyFromDelegate();
        }
    }

    @SuppressWarnings("Since15")
    @Override
    @IgnoreJRERequirement
    public Set<java.net.SocketOption<?>> supportedOptions() {
        try {
            copyToDelegate();
            return delegate.supportedOptions();
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        } finally {
            copyFromDelegate();
        }
    }

}
