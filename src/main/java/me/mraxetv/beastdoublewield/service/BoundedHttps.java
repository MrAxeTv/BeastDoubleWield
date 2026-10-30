package me.mraxetv.beastdoublewield.service;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Small bounded HTTPS transport. No cookies, redirects, authentication or automatic downloads. */
final class BoundedHttps implements AutoCloseable {
    private final Set<HttpsURLConnection> active = Collections.newSetFromMap(new ConcurrentHashMap<HttpsURLConnection, Boolean>());
    private volatile boolean closed;

    byte[] get(URI uri) throws IOException {
        HttpsURLConnection connection = open(uri);
        try {
            connection.setRequestProperty("Accept", "application/yaml, text/yaml, text/plain");
            int response = connection.getResponseCode();
            if (response != 200) throw new IOException("Update endpoint returned HTTP " + response);
            long length = connection.getContentLengthLong();
            if (length > UpdateManifest.MAX_BYTES) throw new IOException("Update manifest exceeds size limit");
            try (InputStream input = connection.getInputStream()) { return read(input, UpdateManifest.MAX_BYTES); }
        } finally { active.remove(connection); connection.disconnect(); }
    }

    void post(URI uri, byte[] payload) throws IOException {
        if (payload.length > 8192) throw new IOException("Metrics payload exceeds size limit");
        HttpsURLConnection connection = open(uri);
        try {
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream stream = connection.getOutputStream()) { stream.write(payload); }
            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) throw new IOException("Metrics endpoint returned HTTP " + response);
            // Response bodies are intentionally ignored; telemetry has no remotely supplied commands.
        } finally { active.remove(connection); connection.disconnect(); }
    }

    private HttpsURLConnection open(URI uri) throws IOException {
        if (closed) throw new IOException("Service closed");
        HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "BeastDoubleWield/updates-metrics");
        active.add(connection);
        if (closed) { active.remove(connection); connection.disconnect(); throw new IOException("Service closed"); }
        return connection;
    }

    static byte[] read(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (System.nanoTime() >= deadline) throw new IOException("Response exceeded total read deadline");
            if (output.size() + count > maximum) throw new IOException("Response exceeds size limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    @Override public void close() {
        closed = true;
        for (HttpsURLConnection connection : active) connection.disconnect();
        active.clear();
    }
}
