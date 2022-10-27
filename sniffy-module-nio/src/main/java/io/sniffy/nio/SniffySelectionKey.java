package io.sniffy.nio;

import io.sniffy.util.ExceptionUtil;
import io.sniffy.util.ObjectWrapper;
import io.sniffy.util.ReflectionUtil;
import io.sniffy.util.StackTraceExtractor;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;

import static io.sniffy.util.ReflectionUtil.invokeMethod;

/**
 * @since 3.1.7
 */
public class SniffySelectionKey<SniffyChannel extends SelectableChannel & SelectableChannelWrapper<SocketChannel>> extends SelectionKey implements ObjectWrapper<SelectionKey> {

    private final SelectionKey delegate;
    private final SniffySelector sniffySelector;
    private final SniffyChannel sniffyChannel;

    protected SniffySelectionKey(SelectionKey delegate, SniffySelector sniffySelector, SniffyChannel sniffyChannel) {
        this.delegate = delegate;

        if (null != delegate) {
            attach(delegate.attachment());
        }

        this.sniffySelector = sniffySelector;
        this.sniffyChannel = sniffyChannel;
    }

    @Override
    public SelectionKey getDelegate() {
        return delegate;
    }

    @Override
    public SelectableChannel channel() {
        return sniffyChannel;
    }

    @Override
    public Selector selector() {
        if (!isValid() && StackTraceExtractor.hasClassAndMethodInStackTrace("java.nio.channels.spi.AbstractSelectableChannel", "findKey")) {
            return NoOpSelector.INSTANCE; // TODO: cleanup this and other collections
        } else {
            return sniffySelector;
        }
    }

    @Override
    public boolean isValid() {
        return delegate.isValid();
    }

    @Override
    public void cancel() {
        delegate.cancel();
        sniffyChannel.keyCancelled();
        // TODO: seems that code below is safe to be removed on Java 17; is it the same on older Java?
        /*synchronized (this) {
            delegate.cancel();
            try {
                // TODO: reevaluate copying other fields across NIO stack
                ReflectionUtil.invokeMethod(AbstractSelector.class, sniffySelector, "cancel", SelectionKey.class, this);
            } catch (Exception e) {
                throw ExceptionUtil.processException(e);
            }
        }*/
    }

    @Override
    public int interestOps() {
        return delegate.interestOps();
    }

    @Override
    public SelectionKey interestOps(int ops) {
        delegate.interestOps(ops);
        return this;
    }

    @Override
    public int readyOps() {
        return delegate.readyOps();
    }

    // No @Override annotation here because this method is available in Java 11+ only
    //@Override
    @SuppressWarnings("Since15")
    public int interestOpsOr(int ops) {
        try {
            return invokeMethod(SelectionKey.class, delegate, "interestOpsOr", Integer.TYPE, ops, Integer.TYPE);
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        }
    }

    // No @Override annotation here because this method is available in Java 11+ only
    //@Override
    @SuppressWarnings("Since15")
    public int interestOpsAnd(int ops) {
        try {
            return invokeMethod(SelectionKey.class, delegate, "interestOpsAnd", Integer.TYPE, ops, Integer.TYPE);
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        }
    }

}
