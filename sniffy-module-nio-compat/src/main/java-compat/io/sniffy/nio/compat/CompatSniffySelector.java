package io.sniffy.nio.compat;

import io.sniffy.log.Polyglog;
import io.sniffy.log.PolyglogFactory;
import io.sniffy.nio.SelectableChannelWrapper;
import io.sniffy.reflection.UnresolvedRefException;
import io.sniffy.reflection.UnsafeInvocationException;
import io.sniffy.reflection.field.UnresolvedNonStaticFieldRef;
import io.sniffy.util.ExceptionUtil;
import io.sniffy.util.ObjectWrapper;
import io.sniffy.util.SetWrapper;
import io.sniffy.util.WrapperFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.sniffy.reflection.Unsafe.$;

/**
 * parent class AbstractSelector contains following properties:
 * <pre>
 * {@code
 * cancelledKeys - not used; it's filled in delegate selector only
 * interruptor - this is used in delegate only
 * provider - immutable, set in constructor
 * selectorOpen - called "closed" on some JDKs; default is true, set to false in final close method; handled inside implCloseSelector method
 *
 * following methods cannot be delegated since they're final or due to modifiers:
 *
 * void cancel(SelectionKey k)
 * - adds key to cancelledKeys
 * - invoked by AbstractSelectionKey cancel in delegate; not used in SniffySelector
 *
 * protected final void deregister(AbstractSelectionKey key)
 * protected final Set<SelectionKey> cancelledKeys()
 * public final void close()
 * void cancel(SelectionKey k)
 * public final boolean isOpen()
 *
 * }
 * </pre>
 *
 * @since 3.1.14
 */
@SuppressWarnings("Convert2Diamond")
public class CompatSniffySelector extends AbstractSelector implements ObjectWrapper<AbstractSelector> {

    private static final Polyglog LOG = PolyglogFactory.log(CompatSniffySelector.class);

    private final AbstractSelector delegate;
    private final Class<? extends AbstractSelector> delegateClass;

    private volatile Set<SelectionKey> keysWrapper = null;
    private volatile Set<SelectionKey> selectedKeysWrapper = null;

    private final Set<CompatSniffySelectionKey> cancelledKeys = new HashSet<CompatSniffySelectionKey>();

    public CompatSniffySelector(SelectorProvider provider, AbstractSelector delegate) {
        super(provider);
        this.delegate = delegate;
        this.delegateClass = delegate.getClass();
        LOG.trace("Created new SniffySelector(" + provider + ", " + delegate + ") = " + this);
        // install some assertions when testing Sniffy
        assert $(AbstractSelector.class).getNonStaticField("cancelledKeys").trySet(this, null);

    }

    @Override
    public AbstractSelector getDelegate() {
        return delegate;
    }

    /**
     * close() method is final - hence we need to do similar work in implCloseSelector() method
     * Specifically set the closed (or selectorOpen depending on JDK version) flag
     * Delegate Selector removed the keys from registered channels - we're replicating similar behaviour here
     */
    @SuppressWarnings("RedundantThrows")
    @Override
    protected void implCloseSelector() throws IOException {
        try {
            LOG.trace("Closing SniffySelector(" + provider() + ", " + delegate + ") = " + this);
            if (isSelectorClosing()) { // reimplement logic in Selector.close() against delegate selector
                delegate.wakeup(); // wake up all other channels waiting in select*() calls
                synchronized (delegate) { // obtain first lock as defined in SelectorImpl.implCloseSelector()
                    synchronized ($(delegateClass, AbstractSelector.class).getNonStaticField("publicKeys").getNotNullOrDefault(delegate, delegate)) {
                        synchronized ($(delegateClass, AbstractSelector.class).getNonStaticField("publicSelectedKeys").getNotNullOrDefault(delegate, delegate)) {
                            Set<SelectionKey> delegateSelectionKeys = getPublicKeysFromDelegate();
                            $(AbstractSelector.class).getNonStaticMethod("implCloseSelector").invoke(delegate);
                            removeSniffyInvalidSelectionKeysForGivenDelegates(delegateSelectionKeys);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        }
    }

    /**
     * @return true if this invocation actually closes the delegate selector
     * Implemented using CAS on delegate selector fields "closed" or "selectorOpen" depending on JVM
     */
    private boolean isSelectorClosing() throws UnresolvedRefException, UnsafeInvocationException {
        boolean changed = false;
        UnresolvedNonStaticFieldRef<AbstractSelector, Object> closedFieldRef = $(AbstractSelector.class).getNonStaticField("closed");
        if (closedFieldRef.isResolved()) {
            changed = closedFieldRef.compareAndSet(delegate, false, true);
        } else {
            UnresolvedNonStaticFieldRef<AbstractSelector, AtomicBoolean> selectorOpenFieldRef = $(AbstractSelector.class).getNonStaticField("selectorOpen");
            if (selectorOpenFieldRef.isResolved()) {
                AtomicBoolean selectorOpen = selectorOpenFieldRef.get(delegate);
                if (null != selectorOpen) {
                    changed = selectorOpen.getAndSet(false);
                } else {
                    LOG.error("AbstractSelector.selectorOpen is null");
                }
            } else {
                LOG.error("Neither AbstractSelector.closed nor AbstractSelector.selectorOpen fields found");
            }
        }
        return changed;
    }

    @SuppressWarnings("RedundantTypeArguments")
    private Set<SelectionKey> getPublicKeysFromDelegate() {
        return new HashSet<SelectionKey>(
                $(delegateClass, AbstractSelector.class).<Set<SelectionKey>>getNonStaticField("publicKeys").
                        getNotNullOrDefault(delegate, Collections.<SelectionKey>emptySet())
        );
    }

    private static void removeSniffyInvalidSelectionKeysForGivenDelegates(Set<SelectionKey> delegateSelectionKeys) throws UnresolvedRefException, UnsafeInvocationException {
        if (null != delegateSelectionKeys) {
            for (SelectionKey delegateSelectionKey : delegateSelectionKeys) {
                if (delegateSelectionKey.channel() instanceof AbstractSelectableChannel) {
                    synchronized ($(AbstractSelectableChannel.class).getNonStaticField("keyLock").getNotNullOrDefault(
                            (AbstractSelectableChannel) delegateSelectionKey.channel(),
                            delegateSelectionKey.channel()))
                    {
                        Object attachment = delegateSelectionKey.attachment();
                        if (attachment instanceof CompatSniffySelectionKey &&
                                ((CompatSniffySelectionKey) attachment).channel() instanceof AbstractSelectableChannel
                        ) {
                            removeSelectionKeyFromChannel((CompatSniffySelectionKey) attachment);
                        }
                    }
                }
            }
        }
    }

    /**
     * Removed SelectionKey from keys array of relevant AbstractSelectableChannel
     * Works for any SelectionKey but contract specifically requires SniffySelectionKey sine we only need to do it for Sniffy
     */
    private static void removeSelectionKeyFromChannel(CompatSniffySelectionKey sniffySelectionKey) throws UnresolvedRefException, UnsafeInvocationException {
        AbstractSelectableChannel sniffyChannel = (AbstractSelectableChannel) sniffySelectionKey.channel();

        UnresolvedNonStaticFieldRef<AbstractSelectableChannel, Integer> keyCountFieldRef = $(AbstractSelectableChannel.class).getNonStaticField("keyCount");
        UnresolvedNonStaticFieldRef<AbstractSelectableChannel, SelectionKey[]> keysFieldRef = $(AbstractSelectableChannel.class).getNonStaticField("keys");

        if (keyCountFieldRef.isResolved() && keysFieldRef.isResolved()) {
            int keyCount = keyCountFieldRef.get(sniffyChannel);
            SelectionKey[] sniffyKeys = keysFieldRef.get(sniffyChannel);

            for (int i = 0; i < sniffyKeys.length; i++) {

                SelectionKey sk = sniffyKeys[i];

                if (sk == sniffySelectionKey) {
                    keyCount--;
                    sniffyKeys[i] = null;
                }

            }

            keyCountFieldRef.set(sniffyChannel, keyCount);
        }
    }

    protected void addCancelledKey(CompatSniffySelectionKey selectionKey) {
        assert !selectionKey.isValid();
        try {
            synchronized (cancelledKeys) {
                cancelledKeys.add(selectionKey);
            }
        } catch (Exception e) {
            LOG.error("Couldn't add selection key to SniffySelector.cancelledKeys set", e);
            assert false : "Couldn't add selection key to SniffySelector.cancelledKeys set";
        }
    }

    protected void processCancelledQueue() {
        synchronized (cancelledKeys) {
            Iterator<CompatSniffySelectionKey> iterator = cancelledKeys.iterator();
            while (iterator.hasNext()) {
                CompatSniffySelectionKey sniffySelectionKey = iterator.next();
                iterator.remove();
                assert !sniffySelectionKey.isValid();
                try {
                    removeSelectionKeyFromChannel(sniffySelectionKey);
                } catch (UnresolvedRefException e) {
                    throw ExceptionUtil.processException(e); // TODO: change the behaviour
                } catch (UnsafeInvocationException e) {
                    throw ExceptionUtil.processException(e); // TODO: change the behaviour
                }
            }
        }
    }

    private Set<SelectionKey> wrapKeys(final Set<SelectionKey> delegates) {
        if (null == keysWrapper) {
            synchronized (this) {
                if (null == keysWrapper && null != delegates) {
                    keysWrapper = createSelectionKeysWrapper(delegates);
                }
            }
        }
        return keysWrapper;
    }

    private Set<SelectionKey> wrapSelectedKeys(final Set<SelectionKey> delegates) {
        if (null == selectedKeysWrapper) {
            synchronized (this) {
                if (null == selectedKeysWrapper && null != delegates) {
                    selectedKeysWrapper = createSelectionKeysWrapper(delegates);
                }
            }
        }
        return selectedKeysWrapper;
    }

    private SetWrapper<CompatSniffySelectionKey, SelectionKey> createSelectionKeysWrapper(Set<SelectionKey> delegates) {
        return new SetWrapper<CompatSniffySelectionKey, SelectionKey>(delegates, new WrapperFactory<SelectionKey, CompatSniffySelectionKey>() {
            @Override
            public CompatSniffySelectionKey wrap(SelectionKey delegate) {
                return (CompatSniffySelectionKey) delegate.attachment();
            }
        });
    }

    private class SelectionKeyConsumerWrapper implements Consumer<SelectionKey> {

        private final Consumer<SelectionKey> delegate;

        public SelectionKeyConsumerWrapper(Consumer<SelectionKey> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accept(SelectionKey selectionKey) {
            delegate.accept((CompatSniffySelectionKey) selectionKey.attachment());
        }

    }

    /**
     * This method is only invoked from (Sniffy)AbstractSelectableChannel.register(Selector sel, int ops, Object att)
     * which also adds the result (Sniffy)SelectionKey to keys array
     * <p>
     * That method in AbstractSelectableChannel is final - hence we're recreating similar logic here, by adding the
     * delegate selection key to delegate selectable channel manually using reflection
     * </p>
     * <p>
     * Also storing SniffySelectionKey as an attachment in original / delegate SelectionKey
     * </p>
     */
    @Override
    protected SelectionKey register(AbstractSelectableChannel sniffyChannel, int ops, Object att) {
        try {

            AbstractSelectableChannel delegateChannel = null;

            if (sniffyChannel instanceof SelectableChannelWrapper) {
                delegateChannel = ((SelectableChannelWrapper<? extends AbstractSelectableChannel>) sniffyChannel).getDelegate();
            } else {
                LOG.error("Suspicious channel " + sniffyChannel + " is passed to SniffySelector.register() method");
                //noinspection ConstantConditions
                assert sniffyChannel instanceof SelectableChannelWrapper : "Non Sniffy channel passed to Sniffy Selector";
            }

            synchronized ($(AbstractSelectableChannel.class).getNonStaticField("regLock").getNotNullOrDefault(delegateChannel, delegateChannel)) {
                synchronized ($(AbstractSelectableChannel.class).getNonStaticField("keyLock").getNotNullOrDefault(delegateChannel, delegateChannel)) {

                    // SniffySelectionKey has a reference to delegate SelectionKey and original attachment
                    // Delegate SelectionKey stores SniffySelectionKey as an attachment
                    CompatSniffySelectionKey sniffySelectionKey = new CompatSniffySelectionKey(this, sniffyChannel, att);
                    SelectionKey selectionKeyDelegate = $(AbstractSelector.class).getNonStaticMethod(SelectionKey.class, "register",
                            AbstractSelectableChannel.class, Integer.TYPE, Object.class).invoke(
                            delegate,
                            delegateChannel, ops, sniffySelectionKey
                    );
                    sniffySelectionKey.setDelegate(selectionKeyDelegate);

                    // Add delegate selection key to delegate selectable channel
                    $(AbstractSelectableChannel.class).getNonStaticMethod("addKey", SelectionKey.class).invoke(delegateChannel, selectionKeyDelegate);

                    return sniffySelectionKey;

                }
            }

        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        }
    }

    @Override
    public Set<SelectionKey> keys() {
        return wrapKeys(delegate.keys());
    }

    @Override
    public Set<SelectionKey> selectedKeys() {
        return wrapSelectedKeys(delegate.selectedKeys());
    }

    /**
     * This methods processes de-register queue (filled-in using selectionKey.cancel() method)
     * As a result it modifies the cancelledKeys field and also removes selectionKeys from associated channels
     */
    @Override
    public int selectNow() throws IOException {
        try {
            return delegate.selectNow();
        } finally {
            processCancelledQueue();
            //updateKeysFromDelegate();
        }
    }


    /**
     * This methods processes de-register queue (filled-in using selectionKey.cancel() method)
     * As a result it modifies the cancelledKeys field and also removes selectionKeys from associated channels
     */
    @Override
    public int select(long timeout) throws IOException {
        try {
            return delegate.select(timeout);
        } finally {
            processCancelledQueue();
            //updateKeysFromDelegate();
        }
    }

    /**
     * This methods processes de-register queue (filled-in using selectionKey.cancel() method)
     * As a result it modifies the cancelledKeys field and also removes selectionKeys from associated channels
     */
    @Override
    public int select() throws IOException {
        try {
            return delegate.select();
        } finally {
            processCancelledQueue();
            //updateKeysFromDelegate();
        }
    }

    @Override
    public Selector wakeup() {
        delegate.wakeup();
        return this;
    }


    // Note: this method was absent in earlier JDKs so we cannot use @Override annotation
    //@Override
    @SuppressWarnings({"RedundantThrows", "Since15", "RedundantSuppression"})
    public int select(Consumer<SelectionKey> action, long timeout) throws IOException {
        try {
            return $(Selector.class).getNonStaticMethod(Integer.TYPE, "select", Consumer.class, Long.TYPE).invoke(
                    delegate, new SelectionKeyConsumerWrapper(action), timeout
            );
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        } finally {
            processCancelledQueue();
            //updateKeysFromDelegate();
        }
    }

    // Note: this method was absent in earlier JDKs so we cannot use @Override annotation
    //@Override
    @SuppressWarnings({"RedundantThrows", "Since15", "RedundantSuppression"})
    public int select(Consumer<SelectionKey> action) throws IOException {
        try {
            return $(Selector.class).getNonStaticMethod(Integer.TYPE, "select", Consumer.class).invoke(
                    delegate, new SelectionKeyConsumerWrapper(action)
            );
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        } finally {
            processCancelledQueue();
            //updateKeysFromDelegate();
        }
    }

    // Note: this method was absent in earlier JDKs so we cannot use @Override annotation
    //@Override
    @SuppressWarnings({"RedundantThrows", "Since15", "RedundantSuppression", "unused"})
    public int selectNow(Consumer<SelectionKey> action) throws IOException {
        try {
            return $(Selector.class).getNonStaticMethod(Integer.TYPE, "selectNow", Consumer.class).invoke(
                    delegate, new SelectionKeyConsumerWrapper(action)
            );
        } catch (Exception e) {
            throw ExceptionUtil.processException(e);
        } finally {
            processCancelledQueue();
            //updateKeysFromDelegate();
        }
    }

}
