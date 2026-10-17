/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.util;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;

/** Keeps Debug coroutine SPI resources inside Android v1 signature coverage. */
public final class DebugCoroutineServices {
    private static final String ORIGINAL = "META-INF/services/";
    private static final String RELOCATED = "trime-services/";

    private DebugCoroutineServices() {}

    private static boolean isCoroutineService(String name) {
        return "kotlinx.coroutines.internal.MainDispatcherFactory".equals(name)
                || "kotlinx.coroutines.CoroutineExceptionHandler".equals(name);
    }

    /** Uses the platform parser and provider construction, with the original defining loader. */
    public static <S> ServiceLoader<S> load(Class<S> service, ClassLoader loader) {
        if (service == null || !isCoroutineService(service.getName())) {
            return ServiceLoader.load(service, loader);
        }
        ClassLoader delegate = loader == null ? ClassLoader.getSystemClassLoader() : loader;
        return ServiceLoader.load(service, new ResourceLoader(delegate));
    }

    /** Also serves the coroutine fast loader's public ClassLoader resource lookup. */
    public static Enumeration<URL> getResources(ClassLoader loader, String name)
            throws IOException {
        if (!name.startsWith(ORIGINAL)
                || !isCoroutineService(name.substring(ORIGINAL.length()))) {
            return loader.getResources(name);
        }
        Enumeration<URL> original = loader.getResources(name);
        Enumeration<URL> relocated =
                loader.getResources(RELOCATED + name.substring(ORIGINAL.length()));
        // Retain external providers. Ordering across the two locations is not a
        // general ClassLoader equivalence guarantee; the APK guard permits only
        // the single reviewed built-in provider for each coroutine service.
        return new Enumeration<URL>() {
            @Override
            public boolean hasMoreElements() {
                return original.hasMoreElements() || relocated.hasMoreElements();
            }

            @Override
            public URL nextElement() {
                if (original.hasMoreElements()) {
                    return original.nextElement();
                }
                if (relocated.hasMoreElements()) {
                    return relocated.nextElement();
                }
                throw new NoSuchElementException();
            }
        };
    }

    private static final class ResourceLoader extends ClassLoader {
        ResourceLoader(ClassLoader delegate) {
            super(delegate);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return DebugCoroutineServices.getResources(getParent(), name);
        }
    }
}
