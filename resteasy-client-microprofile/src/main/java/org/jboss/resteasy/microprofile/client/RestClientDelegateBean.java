/**
 * Copyright 2015-2017 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.resteasy.microprofile.client;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.PassivationCapable;
import javax.enterprise.util.AnnotationLiteral;
import javax.net.ssl.HostnameVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class RestClientDelegateBean implements Bean<Object>, PassivationCapable {

    public static final String REST_URL_FORMAT = "%s/mp-rest/url";

    public static final String REST_URI_FORMAT = "%s/mp-rest/uri";

    public static final String REST_SCOPE_FORMAT = "%s/mp-rest/scope";

    public static final String REST_CONNECT_TIMEOUT_FORMAT = "%s/mp-rest/connectTimeout";

    public static final String REST_READ_TIMEOUT_FORMAT = "%s/mp-rest/readTimeout";

    public static final String REST_PROVIDERS = "%s/mp-rest/providers";

    public static final String TRUST_STORE = "%s/mp-rest/trustStore";

    public static final String TRUST_STORE_PASSWORD = "%s/mp-rest/trustStorePassword";

    public static final String TRUST_STORE_TYPE = "%s/mp-rest/trustStoreType";

    public static final String KEY_STORE = "%s/mp-rest/keyStore";

    public static final String KEY_STORE_PASSWORD = "%s/mp-rest/keyStorePassword";

    public static final String KEY_STORE_TYPE = "%s/mp-rest/keyStoreType";

    public static final String HOSTNAME_VERIFIER = "%s/mp-rest/hostnameVerifier";


    private static final String PROPERTY_PREFIX = "%s/property/";

    private final Class<?> proxyType;

    private final Class<? extends Annotation> scope;

    private final BeanManager beanManager;

    private final Config config;

    private final Optional<String> baseUri;

    RestClientDelegateBean(final Class<?> proxyType, final BeanManager beanManager, final Optional<String> baseUri) {
        this.proxyType = proxyType;
        this.beanManager = beanManager;
        this.config = ConfigProvider.getConfig();
        this.scope = this.resolveScope();
        this.baseUri = baseUri;
    }

    @Override
    public String getId() {
        return proxyType.getName();
    }

    @Override
    public Class<?> getBeanClass() {
        return proxyType;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public Object create(CreationalContext<Object> creationalContext) {
        RestClientBuilder builder = RestClientBuilder.newBuilder();

        configureUri(builder);

        configureTimeouts(builder);

        configureProviders(builder);

        configureSsl(builder);

        getConfigProperties().forEach(builder::property);
        return builder.build(proxyType);
    }

    private void configureSsl(RestClientBuilder builder) {
        Optional<String> maybeTrustStore = getOptionalProperty(TRUST_STORE, String.class);

        maybeTrustStore.ifPresent(trustStore -> registerTrustStore(trustStore, builder));

        Optional<String> maybeKeyStore = getOptionalProperty(KEY_STORE, String.class);
        maybeKeyStore.ifPresent(keyStore -> registerKeyStore(keyStore, builder));

        Optional<String> maybeHostnameVerifier = getOptionalProperty(HOSTNAME_VERIFIER, String.class);
        maybeHostnameVerifier.ifPresent(verifier -> registerHostnameVerifier(verifier, builder));

    }

    private void registerHostnameVerifier(String verifier, RestClientBuilder builder) {
        try {
            Class<?> verifierClass = Class.forName(verifier, true, Thread.currentThread().getContextClassLoader());
            builder.hostnameVerifier((HostnameVerifier) verifierClass.newInstance());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find hostname verifier class" + verifier, e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Failed to instantiate hostname verifier class. Make sure it has a public, no-argument constructor", e);
        } catch (ClassCastException e) {
            throw new RuntimeException("The provided hostname verifier " + verifier + " is not an instance of HostnameVerifier", e);
        }
    }

    private void registerKeyStore(String keyStorePath, RestClientBuilder builder) {
        Optional<String> keyStorePassword = getOptionalProperty(KEY_STORE_PASSWORD, String.class);
        Optional<String> keyStoreType = getOptionalProperty(KEY_STORE_TYPE, String.class);

        try {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType.orElse("JKS"));
            String password = keyStorePassword.orElseThrow(() -> new IllegalArgumentException("No password provided for keystore"));

            try (InputStream input = locateStream(keyStorePath)) {
                keyStore.load(input, password.toCharArray());
            } catch (IOException | CertificateException | NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Failed to initialize trust store from classpath resource " + keyStorePath, e);
            }

            builder.keyStore(keyStore, password);
        } catch (KeyStoreException e) {
            throw new IllegalArgumentException("Failed to initialize trust store from " + keyStorePath, e);
        }
    }

    private void registerTrustStore(String trustStorePath, RestClientBuilder builder) {
        Optional<String> maybeTrustStorePassword = getOptionalProperty(TRUST_STORE_PASSWORD, String.class);
        Optional<String> maybeTrustStoreType = getOptionalProperty(TRUST_STORE_TYPE, String.class);

        try {
            KeyStore trustStore = KeyStore.getInstance(maybeTrustStoreType.orElse("JKS"));
            String password = maybeTrustStorePassword.orElseThrow(() -> new IllegalArgumentException("No password provided for truststore"));

            try (InputStream input = locateStream(trustStorePath)) {
                trustStore.load(input, password.toCharArray());
            } catch (IOException | CertificateException | NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Failed to initialize trust store from classpath resource " + trustStorePath, e);
            }

            builder.trustStore(trustStore);
        } catch (KeyStoreException e) {
            throw new IllegalArgumentException("Failed to initialize trust store from " + trustStorePath, e);
        }
    }

    private InputStream locateStream(String path) throws FileNotFoundException {
        if (path.startsWith("classpath:")) {
            if (path.startsWith("classpath:")) {
                path = path.replaceFirst("classpath:", "");
            }
            InputStream resultStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if (resultStream == null) {
                resultStream = getClass().getResourceAsStream(path);
            }
            if (resultStream == null) {
                throw new IllegalArgumentException("Classpath resource " + path + " not found for MicroProfile Rest Client SSL configuration");
            }
            return resultStream;
        } else {
            if (path.startsWith("file:")) {
                path = path.replaceFirst("file:", "");
            }
            File certificateFile = new File(path);
            if (!certificateFile.isFile()) {
                throw new IllegalArgumentException("Certificate file: " + path + " not found for MicroProfile Rest Client SSL configuration");
            }
            return new FileInputStream(certificateFile);
        }
    }

    private void configureProviders(RestClientBuilder builder) {
        Optional<String> maybeProviders = getOptionalProperty(REST_PROVIDERS, String.class);
        maybeProviders.ifPresent(providers -> registerProviders(builder, providers));
    }

    private void registerProviders(RestClientBuilder builder, String providersAsString) {
        Stream.of(providersAsString.split(","))
                .map(String::trim)
                .map(this::providerClassForName)
                .forEach(builder::register);
    }

    private Class<?> providerClassForName(String name) {
        try {
            return Class.forName(name, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find provider class: " + name);
        }
    }

    private void configureTimeouts(RestClientBuilder builder) {
        Optional<Long> connectTimeout = getOptionalProperty(REST_CONNECT_TIMEOUT_FORMAT, Long.class);
        connectTimeout.ifPresent(timeout -> builder.connectTimeout(timeout, TimeUnit.MILLISECONDS));

        Optional<Long> readTimeout = getOptionalProperty(REST_READ_TIMEOUT_FORMAT, Long.class);
        readTimeout.ifPresent(timeout -> builder.readTimeout(timeout, TimeUnit.MILLISECONDS));
    }

    private void configureUri(RestClientBuilder builder) {
        Optional<String> baseUriFromConfig = getOptionalProperty(REST_URI_FORMAT, String.class);
        Optional<String> baseUrlFromConfig = getOptionalProperty(REST_URL_FORMAT, String.class);

        if (baseUriFromConfig.isPresent()) {
            builder.baseUri(uriFromString(baseUriFromConfig.get()));
        } else if (baseUrlFromConfig.isPresent()) {
            builder.baseUrl(urlFromString(baseUrlFromConfig, baseUrlFromConfig.get()));
        } else {
            baseUri.ifPresent(uri -> builder.baseUri(uriFromString(uri)));
        }
    }

    private <T> Optional<T> getOptionalProperty(String propertyFormat, Class<T> type) {
        return config.getOptionalValue(String.format(propertyFormat, proxyType.getName()), type);
    }

    private URL urlFromString(Optional<String> baseUrlFromConfig, String urlString) {
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("The value of URL was invalid " + baseUrlFromConfig);
        }
    }

    private static URI uriFromString(String uriString) {
        try {
            return new URI(uriString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("The value of URI was invalid " + uriString);
        }
    }

    @Override
    public void destroy(Object instance, CreationalContext<Object> creationalContext) {
    }

    @Override
    public Set<Type> getTypes() {
        return Collections.singleton(proxyType);
    }

    @Override
    public Set<Annotation> getQualifiers() {
        Set<Annotation> qualifiers = new HashSet<Annotation>();
        qualifiers.add(new AnnotationLiteral<Default>() {
        });
        qualifiers.add(new AnnotationLiteral<Any>() {
        });
        qualifiers.add(RestClient.LITERAL);
        return qualifiers;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    @Override
    public String getName() {
        return proxyType.getName();
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    private Map<String, Integer> getConfigProperties() {

        String property = String.format(PROPERTY_PREFIX, proxyType.getName());
        Map<String, Integer> configProperties = new HashMap<>();

        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(property)) {
                Integer value = config.getValue(propertyName, Integer.class);
                String strippedProperty = propertyName.replace(property, "");
                configProperties.put(strippedProperty, value);
            }
        }
        return configProperties;
    }

    private Class<? extends Annotation> resolveScope() {

        String property = String.format(REST_SCOPE_FORMAT, proxyType.getName());
        String configuredScope = config.getOptionalValue(property, String.class).orElse(null);

        if (configuredScope != null) {
            try {
                return (Class<? extends Annotation>) Class.forName(configuredScope);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid scope: " + configuredScope, e);
            }
        }

        List<Annotation> possibleScopes = new ArrayList<>();
        Annotation[] annotations = proxyType.getDeclaredAnnotations();
        for (Annotation annotation : annotations) {
            if (beanManager.isScope(annotation.annotationType())) {
                possibleScopes.add(annotation);
            }
        }
        if (possibleScopes.isEmpty()) {
            return Dependent.class;
        } else if (possibleScopes.size() == 1) {
            return possibleScopes.get(0).annotationType();
        } else {
            throw new IllegalArgumentException("Ambiguous scope definition on " + proxyType + ": " + possibleScopes);
        }
    }

}