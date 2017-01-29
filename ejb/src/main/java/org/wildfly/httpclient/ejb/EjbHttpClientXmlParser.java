package org.wildfly.httpclient.ejb;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;

import javax.xml.stream.XMLInputFactory;

import org.wildfly.client.config.ClientConfiguration;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.client.config.ConfigurationXMLStreamReader;
import org.wildfly.security.auth.client.AuthenticationContext;

/**
 * @author Stuart Douglas
 */
final class EjbHttpClientXmlParser {
    private static final String NS_EJB_HTTP_CLIENT = "urn:ejb-http-client:1.0";

    static HttpContext parseHttpContext() throws ConfigXMLParseException, IOException {
        final ClientConfiguration clientConfiguration = ClientConfiguration.getInstance();
        final HttpContextBuilder builder = new HttpContextBuilder();
        if (clientConfiguration != null) try (final ConfigurationXMLStreamReader streamReader = clientConfiguration.readConfiguration(Collections.singleton(NS_EJB_HTTP_CLIENT))) {
            parseDocument(streamReader, builder);
            return builder.build();
        } else {
            return null;
        }
    }

    //for testing
    static HttpContextBuilder parseConfig(URI uri) throws ConfigXMLParseException {
        final HttpContextBuilder builder = new HttpContextBuilder();
        try (final ConfigurationXMLStreamReader streamReader = ConfigurationXMLStreamReader.openUri(uri, XMLInputFactory.newFactory())) {
            parseDocument(streamReader, builder);
            return builder;
        }
    }

    private static void parseDocument(final ConfigurationXMLStreamReader reader, final HttpContextBuilder builder) throws ConfigXMLParseException {
        if (reader.hasNext()) switch (reader.nextTag()) {
            case START_ELEMENT: {
                switch (reader.getNamespaceURI()) {
                    case NS_EJB_HTTP_CLIENT: break;
                    default: throw reader.unexpectedElement();
                }
                switch (reader.getLocalName()) {
                    case "ejb-http": {
                        parseRootElement(reader, builder);
                        break;
                    }
                    default: throw reader.unexpectedElement();
                }
                break;
            }
            default: {
                throw reader.unexpectedContent();
            }
        }
    }

    private static void parseRootElement(final ConfigurationXMLStreamReader reader, final HttpContextBuilder builder) throws ConfigXMLParseException {

        final int attributeCount = reader.getAttributeCount();
        for (int i = 0; i < attributeCount; i++) {
            switch (reader.getAttributeLocalName(i)) {
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    switch (reader.getNamespaceURI()) {
                        case NS_EJB_HTTP_CLIENT: break;
                        default: throw reader.unexpectedElement();
                    }
                    switch (reader.getLocalName()) {
                        case "connections": {
                            parseConnectionsElement(reader, builder);
                            break;
                        }
                        case "default-bind": {
                            builder.setDefaultBindAddress(parseBind(reader));
                            break;
                        }
                        default: throw reader.unexpectedElement();
                    }
                    break;
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
    }

    private static InetSocketAddress parseBind(final ConfigurationXMLStreamReader reader) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        String address = null;
        int port = 0;
        for (int i = 0; i < attributeCount; i++) {
            switch (reader.getAttributeLocalName(i)) {
                case "address": {
                    address = reader.getAttributeValue(i);
                    break;
                }
                case "port": {
                    port = reader.getIntAttributeValue(i);
                    if (port < 0 || port > 65535) {
                        throw EjbHttpClientMessages.MESSAGES.portValueOutOfRange(port);
                    }
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (address == null) {
            throw reader.missingRequiredAttribute(null, "address");
        }
        final InetSocketAddress bindAddress = InetSocketAddress.createUnresolved(address, port);
        switch (reader.nextTag()) {
            case END_ELEMENT: {
                return bindAddress;
            }
            default: {
                throw reader.unexpectedElement();
            }
        }
    }
    private static void parseConnectionsElement(final ConfigurationXMLStreamReader reader, final HttpContextBuilder builder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        if (attributeCount > 0) {
            throw reader.unexpectedAttribute(0);
        }
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    switch (reader.getNamespaceURI()) {
                        case NS_EJB_HTTP_CLIENT: break;
                        default: throw reader.unexpectedElement();
                    }
                    switch (reader.getLocalName()) {
                        case "connection": {
                            parseConnectionElement(reader, builder);
                            break;
                        }
                        default: throw reader.unexpectedElement();
                    }
                    break;
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
    }

    private static void parseConnectionElement(final ConfigurationXMLStreamReader reader, final HttpContextBuilder builder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        long idleTimeout = 60000;
        int maxConnections = 10;
        int maxStreamsPerConnection = 10;
        for (int i = 0; i < attributeCount; i ++) {
            final String attributeNamespace = reader.getAttributeNamespace(i);
            if (attributeNamespace != null && ! attributeNamespace.isEmpty()) {
                throw reader.unexpectedAttribute(i);
            }
            switch (reader.getAttributeLocalName(i)) {
                case "max-connections": {
                    maxConnections = reader.getIntAttributeValue(i);
                    break;
                }
                case "max-streams-per-connection": {
                    maxStreamsPerConnection = reader.getIntAttributeValue(i);
                    break;
                }
                case "idle-timeout": {
                    idleTimeout = reader.getLongAttributeValue(i);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        final HttpContextBuilder.ConnectionBuilder connectionBuilder = builder.addConnection();
        connectionBuilder.setIdleTimeout(idleTimeout);
        connectionBuilder.setMaxConnections(maxConnections);
        connectionBuilder.setMaxStreamsPerConnection(maxStreamsPerConnection);

        connectionBuilder.setAuthenticationContext(getGlobalDefaultAuthCtxt());
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    switch (reader.getNamespaceURI()) {
                        case NS_EJB_HTTP_CLIENT: break;
                        default: throw reader.unexpectedElement();
                    }
                    switch (reader.getLocalName()) {
                        case "bind": {
                            connectionBuilder.setBindAddress(parseBind(reader));
                            break;
                        }
                        case "modules": {
                            parseModules(reader, connectionBuilder);
                            break;
                        }
                        case "hosts": {
                            parseHosts(reader, connectionBuilder);
                            break;
                        }
                        default: throw reader.unexpectedElement();
                    }
                    break;
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
    }

    private static void parseModules(final ConfigurationXMLStreamReader reader, final HttpContextBuilder.ConnectionBuilder builder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        if (attributeCount > 0) {
            throw reader.unexpectedAttribute(0);
        }
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    switch (reader.getNamespaceURI()) {
                        case NS_EJB_HTTP_CLIENT: break;
                        default: throw reader.unexpectedElement();
                    }
                    switch (reader.getLocalName()) {
                        case "module": {
                            parseModule(reader, builder);
                            break;
                        }
                        default: throw reader.unexpectedElement();
                    }
                    break;
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
    }

    private static void parseHosts(final ConfigurationXMLStreamReader reader, final HttpContextBuilder.ConnectionBuilder builder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        if (attributeCount > 0) {
            throw reader.unexpectedAttribute(0);
        }
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    switch (reader.getNamespaceURI()) {
                        case NS_EJB_HTTP_CLIENT: break;
                        default: throw reader.unexpectedElement();
                    }
                    switch (reader.getLocalName()) {
                        case "host": {
                            parseHost(reader, builder);
                            break;
                        }
                        default: throw reader.unexpectedElement();
                    }
                    break;
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
    }

    private static void parseModule(final ConfigurationXMLStreamReader reader, final HttpContextBuilder.ConnectionBuilder connectionBuilder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        String app = null;
        String module = null;
        String distinct = null;
        for (int i = 0; i < attributeCount; i ++) {
            final String attributeNamespace = reader.getAttributeNamespace(i);
            if (attributeNamespace != null && ! attributeNamespace.isEmpty()) {
                throw reader.unexpectedAttribute(i);
            }
            switch (reader.getAttributeLocalName(i)) {
                case "app": {
                    app = reader.getAttributeValue(i);
                    break;
                }
                case "module": {
                    module = reader.getAttributeValue(i);
                    break;
                }
                case "distinct": {
                    distinct = reader.getAttributeValue(i);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        connectionBuilder.addModule(app, module, distinct);
    }


    private static void parseHost(final ConfigurationXMLStreamReader reader, final HttpContextBuilder.ConnectionBuilder connectionBuilder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        URI host = null;
        for (int i = 0; i < attributeCount; i ++) {
            final String attributeNamespace = reader.getAttributeNamespace(i);
            if (attributeNamespace != null && ! attributeNamespace.isEmpty()) {
                throw reader.unexpectedAttribute(i);
            }
            switch (reader.getAttributeLocalName(i)) {
                case "host": {
                    host = reader.getURIAttributeValue(i);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (! reader.hasNext()) throw reader.unexpectedDocumentEnd();
        if (reader.nextTag() != END_ELEMENT) throw reader.unexpectedElement();
        connectionBuilder.addUri(host);
    }

    static AuthenticationContext getGlobalDefaultAuthCtxt() {
        return AccessController.doPrivileged((PrivilegedAction<AuthenticationContext>) AuthenticationContext.getContextManager()::getGlobalDefault);
    }
}