package io.sniffy.tls;

import io.sniffy.Constants;
import io.sniffy.log.Polyglog;
import io.sniffy.log.PolyglogFactory;
import io.sniffy.reflection.UnresolvedRefException;
import io.sniffy.reflection.Unsafe;
import io.sniffy.reflection.UnsafeInvocationException;
import io.sniffy.reflection.field.FieldFilters;
import io.sniffy.reflection.field.UnresolvedStaticFieldRef;
import io.sniffy.util.StackTraceExtractor;
import sun.security.jca.ProviderList;
import sun.security.jca.Providers;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLSocketFactory;
import java.security.Provider;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.sniffy.reflection.Unsafe.$;
import static io.sniffy.tls.SniffySecurityUtil.SSLCONTEXT;

class SniffyThreadLocalProviderList extends ThreadLocal<ProviderList> {

    private static final Polyglog LOG = PolyglogFactory.log(SniffyThreadLocalProviderList.class);

    private final ThreadLocal<ProviderList> delegate = new ThreadLocal<ProviderList>();

    private final ThreadLocal<Boolean> insideSetProviderList = new ThreadLocal<Boolean>();

    @Override
    public ProviderList get() {

        ProviderList providerList = delegate.get();

        if (null == providerList) {
            if (!Boolean.TRUE.equals(insideSetProviderList.get()) && StackTraceExtractor.hasClassAndMethodInStackTrace(Providers.class.getName(), "setProviderList")) {
                insideSetProviderList.set(true);
                LOG.info("Sniffy detected call to Providers.setProviderList() - setting flag insideSetProviderList to true");
                return ProviderList.newList();
            }
        }

        return providerList;
    }

    protected static Map.Entry<ProviderList, SniffySSLContextSpiProvider> wrapProviderList(ProviderList value) throws UnresolvedRefException, UnsafeInvocationException {

        List<Provider> wrappedProviderList = new ArrayList<Provider>();

        SniffySSLContextSpiProvider firstSniffySSLContextSpiProviderWithDefaultSSLContextSpi = null;

        List<Provider> originalSecurityProviders = value.providers();

        boolean wrappedAtLeastOneProvider = false;

        for (Provider originalSecurityProvider : originalSecurityProviders) {

            if (!(originalSecurityProvider instanceof SniffySSLContextSpiProvider)) {

                boolean hasSSLContextService = false;
                boolean hasDefaultSSLContextService = false;

                for (Provider.Service service : originalSecurityProvider.getServices()) {
                    if (SSLCONTEXT.equals(service.getType())) {
                        hasSSLContextService = true;
                        if ("Default".equalsIgnoreCase(service.getAlgorithm())) {
                            hasDefaultSSLContextService = true;
                            break;
                        }
                    }
                }

                if (hasSSLContextService) {

                    wrappedAtLeastOneProvider = true;

                    String originalProviderName = originalSecurityProvider.getName();

                    SniffySSLContextSpiProvider sniffySSLContextSpiProvider = new SniffySSLContextSpiProvider(
                            originalSecurityProvider,
                            "Sniffy-" + originalProviderName,
                            Constants.MAJOR_VERSION,
                            "SniffySSLContextProvider"
                    );

                    if (hasDefaultSSLContextService && null == firstSniffySSLContextSpiProviderWithDefaultSSLContextSpi) {
                        firstSniffySSLContextSpiProviderWithDefaultSSLContextSpi = sniffySSLContextSpiProvider;
                    }

                    wrappedProviderList.add(sniffySSLContextSpiProvider);
                    wrappedProviderList.add(new SniffySSLContextSpiProvider(originalSecurityProvider));

                } else {
                    wrappedProviderList.add(originalSecurityProvider);
                }

            } else {
                if (null == firstSniffySSLContextSpiProviderWithDefaultSSLContextSpi) {
                    firstSniffySSLContextSpiProviderWithDefaultSSLContextSpi = (SniffySSLContextSpiProvider) originalSecurityProvider;
                }
                wrappedProviderList.add(originalSecurityProvider);
            }

        }

        return new AbstractMap.SimpleImmutableEntry<ProviderList, SniffySSLContextSpiProvider>(
                wrappedAtLeastOneProvider ? ProviderList.newList(wrappedProviderList.toArray(new Provider[0])) : value,
                firstSniffySSLContextSpiProviderWithDefaultSSLContextSpi
        );

    }

    @Override
    public void set(ProviderList value) {

        try {
            if (null == delegate.get() &&
                    Boolean.TRUE.equals(insideSetProviderList.get()) &&
                    1 == ($(Providers.class).<Number>getStaticField("threadListsUsed").getOrDefault(0)).intValue()) {
                if (StackTraceExtractor.hasClassAndMethodInStackTrace(Providers.class.getName(), "setProviderList")) {
                    // call callback
                    try {

                        LOG.info("Wrapping ProviderList " + value);

                        Map.Entry<ProviderList, SniffySSLContextSpiProvider> tuple = wrapProviderList(value);

                        LOG.info("Wrapped ProviderList " + tuple.getKey());
                        LOG.info("First SniffySSLContextSpiProvider with default SSLContextSPI is " + tuple.getValue());

                        if (value == tuple.getKey()) {
                            LOG.info("ProviderList wasn't changed - invoking original Providers.setSystemProviderList() method");
                            $(Providers.class).getStaticMethod("setSystemProviderList", ProviderList.class).invoke(value);
                        } else {

                            value = tuple.getKey();
                            LOG.info("ProviderList wasn't changed - invoking original Providers.setSystemProviderList() method with wrapped list");
                            $(Providers.class).getStaticMethod("setSystemProviderList", ProviderList.class).invoke(value);

                            SniffySSLContextSpiProvider firstSniffySSLContextSpiProviderWithDefaultSSLContextSpi = tuple.getValue();

                            if (null != firstSniffySSLContextSpiProviderWithDefaultSSLContextSpi) {
                                Provider.Service defaultSniffySSLContextSpiProviderService =
                                        firstSniffySSLContextSpiProviderWithDefaultSSLContextSpi.getService(SSLCONTEXT, "Default");
                                if (null != defaultSniffySSLContextSpiProviderService) {
                                    try {
                                        SniffySSLContext defaultSniffySSLContext = new SniffySSLContext(
                                                (SSLContextSpi) defaultSniffySSLContextSpiProviderService.newInstance(null),
                                                firstSniffySSLContextSpiProviderWithDefaultSSLContextSpi,
                                                "Default"
                                        );
                                        LOG.info("Setting SSLContext.default to " + defaultSniffySSLContext);
                                        // TODO: why do we do it here? we already doing it in SniffySecurityUtil
                                        SSLContext.setDefault(defaultSniffySSLContext);
                                        if (Unsafe.tryGetJavaVersion() >= 13) {
                                            try {
                                                LOG.info("Java 13+ detected - attempt to update javax.net.ssl.SSLSocketFactory$DefaultFactoryHolder");
                                                UnresolvedStaticFieldRef<SSLSocketFactory> sslSocketFactoryFieldRef =
                                                        $("javax.net.ssl.SSLSocketFactory$DefaultFactoryHolder").tryFindFirstStaticField(FieldFilters.ofType(SSLSocketFactory.class), true);
                                                if (sslSocketFactoryFieldRef.isResolved()) {
                                                    SSLSocketFactory originalSSLSocketFactory = sslSocketFactoryFieldRef.get();
                                                    if (null != originalSSLSocketFactory) {
                                                        SniffySSLSocketFactory sniffySSLSocketFactory = new SniffySSLSocketFactory(originalSSLSocketFactory);
                                                        LOG.info("Replacing " + originalSSLSocketFactory + " with " + sniffySSLSocketFactory);
                                                        sslSocketFactoryFieldRef.set(sniffySSLSocketFactory);
                                                    } else {
                                                        LOG.error("Original SSLSocketFactory was null");
                                                    }
                                                }
                                            } catch (UnresolvedRefException e) {
                                                throw Unsafe.throwException(e);
                                            } catch (UnsafeInvocationException e) {
                                                throw Unsafe.throwException(e);
                                            }
                                        } else {
                                            LOG.info("Java 12- detected - attempt to update singleton inside javax.net.ssl.SSLSocketFactory");
                                            try {
                                                LOG.info("Java 12- detected - attempt to update javax.net.ssl.SSLSocketFactory$DefaultFactoryHolder");
                                                UnresolvedStaticFieldRef<SSLSocketFactory> sslSocketFactoryFieldRef =
                                                        $("javax.net.ssl.SSLSocketFactory$DefaultFactoryHolder").tryFindFirstStaticField(FieldFilters.ofType(SSLSocketFactory.class), true);
                                                if (sslSocketFactoryFieldRef.isResolved()) {
                                                    SSLSocketFactory originalSSLSocketFactory = sslSocketFactoryFieldRef.get();
                                                    if (null != originalSSLSocketFactory) {
                                                        SniffySSLSocketFactory sniffySSLSocketFactory = new SniffySSLSocketFactory(originalSSLSocketFactory);
                                                        LOG.info("Replacing " + originalSSLSocketFactory + " with " + sniffySSLSocketFactory);
                                                        sslSocketFactoryFieldRef.set(sniffySSLSocketFactory);
                                                    } else {
                                                        LOG.error("Original SSLSocketFactory was null");
                                                    }
                                                }
                                            } catch (UnresolvedRefException e) {
                                                throw Unsafe.throwException(e);
                                            } catch (UnsafeInvocationException e) {
                                                throw Unsafe.throwException(e);
                                            }
                                            // TODO: warn if couldn't find all sslsocket factories
                                        }
                                    } catch (Throwable e) {
                                        // TODO: do the same in other dangerous places
                                        LOG.error(e);
                                    }
                                }
                            }

                        }

                        LOG.info("Sniffy detected exit from Providers.setProviderList() - setting flag insideSetProviderList to false");
                        insideSetProviderList.set(false);
                        return;

                    } catch (Exception e) {
                        LOG.error(e);
                    }

                }
                LOG.info("Sniffy detected exit from Providers.setProviderList() - setting flag insideSetProviderList to false");
                insideSetProviderList.set(false);
            }
        } catch (Exception e) {
            LOG.error(e);
        }

        delegate.set(value);
    }

    @Override
    public void remove() {
        delegate.remove();
    }

}
