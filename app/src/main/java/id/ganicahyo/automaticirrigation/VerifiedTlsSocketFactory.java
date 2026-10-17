package id.ganicahyo.automaticirrigation;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLParameters;

/** Enables certificate-chain AND hostname verification for MQTT TLS sockets. */
final class VerifiedTlsSocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    VerifiedTlsSocketFactory(SSLSocketFactory delegate) { this.delegate = delegate; }

    private Socket verify(Socket socket) throws IOException {
        if (!(socket instanceof SSLSocket)) throw new IOException("TLS factory did not create an SSLSocket");
        SSLSocket ssl = (SSLSocket) socket;
        SSLParameters params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setSSLParameters(params);
        return ssl;
    }

    @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
    @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException { return verify(delegate.createSocket(s, host, port, autoClose)); }
    @Override public Socket createSocket(String host, int port) throws IOException { return verify(delegate.createSocket(host, port)); }
    @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException { return verify(delegate.createSocket(host, port, local, localPort)); }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException { return verify(delegate.createSocket(host, port)); }
    @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException { return verify(delegate.createSocket(host, port, local, localPort)); }
    @Override public Socket createSocket() throws IOException { return verify(delegate.createSocket()); }
}
