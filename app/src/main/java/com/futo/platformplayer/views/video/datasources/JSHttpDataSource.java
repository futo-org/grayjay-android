package com.futo.platformplayer.views.video.datasources;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.datasource.HttpUtil.buildRangeRequestHeader;
import static java.lang.Math.min;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.futo.platformplayer.api.media.models.modifier.IRequest;
import com.futo.platformplayer.api.media.models.modifier.IRequestModifier;
import com.futo.platformplayer.api.media.platforms.js.models.JSRequest;
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor;
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestModifier;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSourceException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.HttpUtil;
import androidx.media3.datasource.TransferListener;

import com.futo.platformplayer.engine.dev.V8RemoteObject;
import com.futo.platformplayer.engine.exceptions.PluginException;
import com.futo.platformplayer.engine.exceptions.ScriptException;
import com.futo.platformplayer.logging.Logger;
import com.google.common.base.Predicate;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import kotlinx.serialization.json.Json;

/*
 * Based on the default ExoPlayer DefaultHttpDataSource
 */

@UnstableApi
public class JSHttpDataSource extends BaseDataSource implements HttpDataSource {
    public static final class Factory implements HttpDataSource.Factory {

        private final RequestProperties defaultRequestProperties;

        @Nullable private TransferListener transferListener;
        @Nullable private Predicate<String> contentTypePredicate;
        @Nullable private String userAgent;
        private int connectTimeoutMs;
        private int readTimeoutMs;
        private boolean allowCrossProtocolRedirects;
        private boolean keepPostFor302Redirects;
        @Nullable private IRequestModifier requestModifier = null;
        @Nullable public JSRequestExecutor requestExecutor = null;
        @Nullable public JSRequestExecutor requestExecutor2 = null;


        /** Creates an instance. */
        public Factory() {
            defaultRequestProperties = new RequestProperties();
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLIS;
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLIS;
        }

        @Override
        public Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
            this.defaultRequestProperties.clearAndSet(defaultRequestProperties);
            return this;
        }

        /**
         * Sets the request modifier that will be used.
         *
         * <p>The default is {@code null}, which results in no request modification
         *
         * @param requestModifier The request modifier that will be used, or {@code null} to use no request modifier
         * @return This factory.
         */
        public Factory setRequestModifier(@Nullable IRequestModifier requestModifier) {
            this.requestModifier = requestModifier;
            return this;
        }
        /**
         * Sets the request executor that will be used.
         *
         * <p>The default is {@code null}, which results in no request modification
         *
         * @param requestExecutor The request modifier that will be used, or {@code null} to use no request modifier
         * @return This factory.
         */
        public Factory setRequestExecutor(@Nullable JSRequestExecutor requestExecutor) {
            this.requestExecutor = requestExecutor;
            return this;
        }
        /**
         * Sets the secondary request executor that will be used.
         *
         * <p>The default is {@code null}, which results in no request modification
         *
         * @param requestExecutor The request modifier that will be used, or {@code null} to use no request modifier
         * @return This factory.
         */
        public Factory setRequestExecutor2(@Nullable JSRequestExecutor requestExecutor) {
            this.requestExecutor2 = requestExecutor;
            return this;
        }

        /**
         * Sets the user agent that will be used.
         *
         * <p>The default is {@code null}, which causes the default user agent of the underlying
         * platform to be used.
         *
         * @param userAgent The user agent that will be used, or {@code null} to use the default user
         *     agent of the underlying platform.
         * @return This factory.
         */
        public Factory setUserAgent(@Nullable String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Sets the connect timeout, in milliseconds.
         *
         * <p>The default is {@link JSHttpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS}.
         *
         * @param connectTimeoutMs The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        public Factory setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        /**
         * Sets the read timeout, in milliseconds.
         *
         * <p>The default is {@link JSHttpDataSource#DEFAULT_READ_TIMEOUT_MILLIS}.
         *
         * @param readTimeoutMs The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        public Factory setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        /**
         * Sets whether to allow cross protocol redirects.
         *
         * <p>The default is {@code false}.
         *
         * @param allowCrossProtocolRedirects Whether to allow cross protocol redirects.
         * @return This factory.
         */
        public Factory setAllowCrossProtocolRedirects(boolean allowCrossProtocolRedirects) {
            this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
            return this;
        }

        /**
         * Sets a content type {@link Predicate}. If a content type is rejected by the predicate then a
         * {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link
         * JSHttpDataSource#open(androidx.media3.datasource.DataSpec)}.
         *
         * <p>The default is {@code null}.
         *
         * @param contentTypePredicate The content type {@link Predicate}, or {@code null} to clear a
         *     predicate that was previously set.
         * @return This factory.
         */
        public Factory setContentTypePredicate(@Nullable Predicate<String> contentTypePredicate) {
            this.contentTypePredicate = contentTypePredicate;
            return this;
        }

        /**
         * Sets the {@link TransferListener} that will be used.
         *
         * <p>The default is {@code null}.
         *
         * <p>See {@link androidx.media3.datasource.DataSource#addTransferListener(TransferListener)}.
         *
         * @param transferListener The listener that will be used.
         * @return This factory.
         */
        public Factory setTransferListener(@Nullable TransferListener transferListener) {
            this.transferListener = transferListener;
            return this;
        }

        /**
         * Sets whether we should keep the POST method and body when we have HTTP 302 redirects for a
         * POST request.
         */
        public Factory setKeepPostFor302Redirects(boolean keepPostFor302Redirects) {
            this.keepPostFor302Redirects = keepPostFor302Redirects;
            return this;
        }

        @Override
        public JSHttpDataSource createDataSource() {
            JSHttpDataSource dataSource =
                    new JSHttpDataSource(
                            userAgent,
                            connectTimeoutMs,
                            readTimeoutMs,
                            allowCrossProtocolRedirects,
                            defaultRequestProperties,
                            contentTypePredicate,
                            keepPostFor302Redirects,
                            requestModifier,
                            requestExecutor,
                            requestExecutor2);
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener);
            }
            return dataSource;
        }
    }

    /** The default connection timeout, in milliseconds. */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8 * 1000;
    /** The default read timeout, in milliseconds. */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 8 * 1000;

    private static final String TAG = "JSHttpDataSource";
    private static final int MAX_REDIRECTS = 20; // Same limit as okhttp.
    private static final int HTTP_STATUS_TEMPORARY_REDIRECT = 307;
    private static final int HTTP_STATUS_PERMANENT_REDIRECT = 308;
    private static final long MAX_BYTES_TO_DRAIN = 2048;

    private final boolean allowCrossProtocolRedirects;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    @Nullable private final String userAgent;
    @Nullable private final RequestProperties defaultRequestProperties;
    private final RequestProperties requestProperties;
    private final boolean keepPostFor302Redirects;

    @Nullable private Predicate<String> contentTypePredicate;
    @Nullable private DataSpec dataSpec;
    @Nullable private HttpURLConnection connection;
    @Nullable private InputStream inputStream;
    private boolean opened;
    private int responseCode;
    private long bytesToRead;
    private long bytesRead;
    @Nullable private IRequestModifier requestModifier;
    @Nullable public JSRequestExecutor requestExecutor;
    @Nullable public JSRequestExecutor requestExecutor2; //Not ideal, but required for now to have 2 executors under 1 datasource

    private Uri fallbackUri = null;

    private JSHttpDataSource(
            @Nullable String userAgent,
            int connectTimeoutMillis,
            int readTimeoutMillis,
            boolean allowCrossProtocolRedirects,
            @Nullable RequestProperties defaultRequestProperties,
            @Nullable Predicate<String> contentTypePredicate,
            boolean keepPostFor302Redirects,
            @Nullable IRequestModifier requestModifier,
            @Nullable JSRequestExecutor requestExecutor,
            @Nullable JSRequestExecutor requestExecutor2) {
        super(/* isNetwork= */ true);
        this.userAgent = userAgent;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
        this.defaultRequestProperties = defaultRequestProperties;
        this.contentTypePredicate = contentTypePredicate;
        this.requestProperties = new RequestProperties();
        this.keepPostFor302Redirects = keepPostFor302Redirects;
        this.requestModifier = requestModifier;
        this.requestExecutor = requestExecutor;
        this.requestExecutor2 = requestExecutor2;
    }

    @Override
    @Nullable
    public Uri getUri() {
        return connection == null ? fallbackUri : Uri.parse(connection.getURL().toString());
    }

    @Override
    public int getResponseCode() {
        return connection == null || responseCode <= 0 ? -1 : responseCode;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        if (connection == null) {
            return ImmutableMap.of();
        }
        // connection.getHeaderFields() always contains a null key with a value like
        // ["HTTP/1.1 200 OK"]. The response code is available from HttpURLConnection#getResponseCode()
        // and the HTTP version is fixed when establishing the connection.
        // DataSource#getResponseHeaders() doesn't allow null keys in the returned map, so we need to
        // remove it.
        // connection.getHeaderFields() returns a special unmodifiable case-insensitive Map
        // so we can't just remove the null key or make a copy without the null key. Instead we wrap it
        // in a ForwardingMap subclass that ignores and filters out null keys in the read methods.
        return new NullFilteringHeadersMap(connection.getHeaderFields());
    }

    @Override
    public void setRequestProperty(String name, String value) {
        checkNotNull(name);
        checkNotNull(value);
        requestProperties.set(name, value);
    }

    @Override
    public void clearRequestProperty(String name) {
        checkNotNull(name);
        requestProperties.remove(name);
    }

    @Override
    public void clearAllRequestProperties() {
        requestProperties.clear();
    }

    /** Opens the source to read the specified data. */
    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {
        this.dataSpec = dataSpec;
        bytesRead = 0;
        bytesToRead = 0;
        transferInitializing(dataSpec);

        //Use executor 2 if it matches the urlPrefix
        JSRequestExecutor executor = (requestExecutor2 != null && requestExecutor2.getUrlPrefix() != null && dataSpec.uri.toString().startsWith(requestExecutor2.getUrlPrefix())) ?
                requestExecutor2 : requestExecutor;

        if(executor != null) {
            try {
                Logger.Companion.i(TAG, "Executor for " + dataSpec.uri.toString(), null);
                byte[] data = executor.executeRequest("GET", dataSpec.uri.toString(), null, dataSpec.httpRequestHeaders);
                Logger.Companion.i(TAG, "Executor result for " + dataSpec.uri.toString() + " : " + data.length, null);
                if (data == null)
                    throw new HttpDataSourceException(
                            "No response",
                            dataSpec,
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                            HttpDataSourceException.TYPE_OPEN);
                inputStream = new ByteArrayInputStream(data);
                fallbackUri = dataSpec.uri;
                bytesToRead = data.length;

                transferStarted(dataSpec);
                return data.length;
            }
            catch(PluginException ex) {
                throw HttpDataSourceException.createForIOException(new IOException("Executor failed: " + ex.getMessage(), ex), dataSpec, HttpDataSourceException.TYPE_OPEN);
            }
        }
        else {
            String responseMessage;
            HttpURLConnection connection;
            try {
                this.connection = makeConnection(dataSpec);
                connection = this.connection;
                responseCode = connection.getResponseCode();
                responseMessage = connection.getResponseMessage();
            } catch (IOException e) {
                closeConnectionQuietly();
                throw HttpDataSourceException.createForIOException(
                        e, dataSpec, HttpDataSourceException.TYPE_OPEN);
            }

            // Check for a valid response code.
            if (responseCode < 200 || responseCode > 299) {
                Map<String, List<String>> headers = connection.getHeaderFields();
                if (responseCode == 416) {
                    long documentSize = HttpUtil.getDocumentSize(connection.getHeaderField(HttpHeaders.CONTENT_RANGE));
                    if (dataSpec.position == documentSize) {
                        opened = true;
                        transferStarted(dataSpec);
                        return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : 0;
                    }
                }

                @Nullable InputStream errorStream = connection.getErrorStream();
                byte[] errorResponseBody;
                try {
                    errorResponseBody =
                            errorStream != null ? Util.toByteArray(errorStream) : Util.EMPTY_BYTE_ARRAY;
                } catch (IOException e) {
                    errorResponseBody = Util.EMPTY_BYTE_ARRAY;
                }
                closeConnectionQuietly();
                @Nullable
                IOException cause = responseCode == 416
                        ? new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
                        : null;

                throw new InvalidResponseCodeException(
                        responseCode, responseMessage, cause, headers, dataSpec, errorResponseBody);
            }

            // Check for a valid content type.
            String contentType = connection.getContentType();
            if (contentTypePredicate != null && !contentTypePredicate.apply(contentType)) {
                closeConnectionQuietly();
                throw new InvalidContentTypeException(contentType, dataSpec);
            }

            // If we requested a range starting from a non-zero position and received a 200 rather than a
            // 206, then the server does not support partial requests. We'll need to manually skip to the
            // requested position.
            long bytesToSkip;
            if (requestModifier != null && !requestModifier.getAllowByteSkip()) {
                bytesToSkip = 0;
            } else {
                bytesToSkip = responseCode == 200 && dataSpec.position != 0 ? dataSpec.position : 0;
            }

            // Determine the length of the data to be read, after skipping.
            boolean isCompressed = isCompressed(connection);
            if (!isCompressed) {
                if (dataSpec.length != C.LENGTH_UNSET) {
                    bytesToRead = dataSpec.length;
                } else {
                    long contentLength = HttpUtil.getContentLength(
                            connection.getHeaderField(HttpHeaders.CONTENT_LENGTH),
                            connection.getHeaderField(HttpHeaders.CONTENT_RANGE)
                    );

                    bytesToRead = contentLength != C.LENGTH_UNSET ? (contentLength - bytesToSkip) : C.LENGTH_UNSET;
                }
            } else {
                // Gzip is enabled. If the server opts to use gzip then the content length in the response
                // will be that of the compressed data, which isn't what we want. Always use the dataSpec
                // length in this case.
                bytesToRead = dataSpec.length;
            }

            try {
                inputStream = connection.getInputStream();
                if (isCompressed) {
                    inputStream = new GZIPInputStream(inputStream);
                }
            } catch (IOException e) {
                closeConnectionQuietly();
                throw new HttpDataSourceException(
                        e,
                        dataSpec,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        HttpDataSourceException.TYPE_OPEN);
            }

            opened = true;
            transferStarted(dataSpec);

            try {
                skipFully(bytesToSkip, dataSpec);
            } catch (IOException e) {
                closeConnectionQuietly();

                if (e instanceof HttpDataSourceException) {
                    throw (HttpDataSourceException) e;
                }
                throw new HttpDataSourceException(
                        e,
                        dataSpec,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        HttpDataSourceException.TYPE_OPEN);
            }

            return bytesToRead;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws HttpDataSourceException {
        try {
            return readInternal(buffer, offset, length);
        } catch (IOException e) {
            throw HttpDataSourceException.createForIOException(
                    e, castNonNull(dataSpec), HttpDataSourceException.TYPE_READ);
        }
    }

    @Override
    public void close() throws HttpDataSourceException {
        try {
            @Nullable InputStream inputStream = this.inputStream;
            if (inputStream != null) {
                long bytesRemaining =
                        bytesToRead == C.LENGTH_UNSET ? C.LENGTH_UNSET : bytesToRead - bytesRead;
                maybeTerminateInputStream(connection, bytesRemaining);
                try {
                    inputStream.close();
                } catch (IOException e) {
                    throw new HttpDataSourceException(
                            e,
                            castNonNull(dataSpec),
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                            HttpDataSourceException.TYPE_CLOSE);
                }
            }
        } finally {
            inputStream = null;
            closeConnectionQuietly();
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }

    /** Establishes a connection, following redirects to do so where permitted. */
    private HttpURLConnection makeConnection(DataSpec dataSpec) throws IOException {
        URL url = new URL(dataSpec.uri.toString());
        @DataSpec.HttpMethod int httpMethod = dataSpec.httpMethod;
        @Nullable byte[] httpBody = dataSpec.httpBody;
        long position = dataSpec.position;
        long length = dataSpec.length;
        boolean allowGzip = dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP);

        if (!allowCrossProtocolRedirects && !keepPostFor302Redirects) {
            // HttpURLConnection disallows cross-protocol redirects, but otherwise performs redirection
            // automatically. This is the behavior we want, so use it.
            return makeConnection(
                    url,
                    httpMethod,
                    httpBody,
                    position,
                    length,
                    allowGzip,
                    /* followRedirects= */ true,
                    dataSpec.httpRequestHeaders);
        }

        // We need to handle redirects ourselves to allow cross-protocol redirects or to keep the POST
        // request method for 302.
        int redirectCount = 0;
        while (redirectCount++ <= MAX_REDIRECTS) {
            HttpURLConnection connection =
                    makeConnection(
                            url,
                            httpMethod,
                            httpBody,
                            position,
                            length,
                            allowGzip,
                            /* followRedirects= */ false,
                            dataSpec.httpRequestHeaders);
            int responseCode = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            if ((httpMethod == DataSpec.HTTP_METHOD_GET || httpMethod == DataSpec.HTTP_METHOD_HEAD)
                    && (responseCode == HttpURLConnection.HTTP_MULT_CHOICE
                    || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                    || responseCode == HTTP_STATUS_TEMPORARY_REDIRECT
                    || responseCode == HTTP_STATUS_PERMANENT_REDIRECT)) {
                connection.disconnect();
                url = handleRedirect(url, location, dataSpec);
            } else if (httpMethod == DataSpec.HTTP_METHOD_POST
                    && (responseCode == HttpURLConnection.HTTP_MULT_CHOICE
                    || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_SEE_OTHER)) {
                connection.disconnect();
                boolean shouldKeepPost =
                        keepPostFor302Redirects && responseCode == HttpURLConnection.HTTP_MOVED_TEMP;
                if (!shouldKeepPost) {
                    // POST request follows the redirect and is transformed into a GET request.
                    httpMethod = DataSpec.HTTP_METHOD_GET;
                    httpBody = null;
                }
                url = handleRedirect(url, location, dataSpec);
            } else {
                return connection;
            }
        }

        // If we get here we've been redirected more times than are permitted.
        throw new HttpDataSourceException(
                new NoRouteToHostException("Too many redirects: " + redirectCount),
                dataSpec,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSourceException.TYPE_OPEN);
    }

    /**
     * Configures a connection and opens it.
     *
     * @param url The url to connect to.
     * @param httpMethod The http method.
     * @param httpBody The body data, or {@code null} if not required.
     * @param position The byte offset of the requested data.
     * @param length The length of the requested data, or {@link C#LENGTH_UNSET}.
     * @param allowGzip Whether to allow the use of gzip.
     * @param followRedirects Whether to follow redirects.
     * @param requestParameters parameters (HTTP headers) to include in request.
     */
    private HttpURLConnection makeConnection(
            URL url,
            @DataSpec.HttpMethod int httpMethod,
            @Nullable byte[] httpBody,
            long position,
            long length,
            boolean allowGzip,
            boolean followRedirects,
            Map<String, String> requestParameters)
            throws IOException {
        Map<String, String> requestHeaders = new HashMap<>();
        if (defaultRequestProperties != null) {
            requestHeaders.putAll(defaultRequestProperties.getSnapshot());
        }
        requestHeaders.putAll(requestProperties.getSnapshot());
        requestHeaders.putAll(requestParameters);

        @Nullable String rangeHeader = buildRangeRequestHeader(position, length);
        if (rangeHeader != null) {
            requestHeaders.put(HttpHeaders.RANGE, rangeHeader);
        }

        if (userAgent != null) {
            requestHeaders.put(HttpHeaders.USER_AGENT, userAgent);
        }

        requestHeaders.put(HttpHeaders.ACCEPT_ENCODING, allowGzip ? "gzip" : "identity");

        String requestUrl = url.toString();
        if (requestModifier != null) {
            IRequest result = requestModifier.modifyRequest(requestUrl, requestHeaders);
            String modifiedUrl = result.getUrl();
            requestUrl = (modifiedUrl != null) ? modifiedUrl : requestUrl;
            requestHeaders = result.getHeaders();
        }

        Logger.Companion.v("JSHttpDataSource", "DataSource REQ: " + requestUrl + "\nHEADERS: [" + V8RemoteObject.Companion.getGsonStandard().toJson(requestHeaders)+ "]", null);

        HttpURLConnection connection = openConnection(new URL(requestUrl));
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);

        for (Map.Entry<String, String> property : requestHeaders.entrySet()) {
            connection.setRequestProperty(property.getKey(), property.getValue());
        }

        connection.setInstanceFollowRedirects(followRedirects);
        connection.setDoOutput(httpBody != null);
        connection.setRequestMethod(DataSpec.getStringForHttpMethod(httpMethod));

        if (httpBody != null) {
            connection.setFixedLengthStreamingMode(httpBody.length);
            connection.connect();
            OutputStream os = connection.getOutputStream();
            os.write(httpBody);
            os.close();
        } else {
            connection.connect();
        }
        return connection;
    }

    /** Creates an {@link HttpURLConnection} that is connected with the {@code url}. */
    @VisibleForTesting
    /* package */ HttpURLConnection openConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    /**
     * Handles a redirect.
     *
     * @param originalUrl The original URL.
     * @param location The Location header in the response. May be {@code null}.
     * @param dataSpec The {@link DataSpec}.
     * @return The next URL.
     * @throws HttpDataSourceException If redirection isn't possible.
     */
    private URL handleRedirect(URL originalUrl, @Nullable String location, DataSpec dataSpec)
            throws HttpDataSourceException {
        if (location == null) {
            throw new HttpDataSourceException(
                    "Null location redirect",
                    dataSpec,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN);
        }
        // Form the new url.
        URL url;
        try {
            url = new URL(originalUrl, location);
        } catch (MalformedURLException e) {
            throw new HttpDataSourceException(
                    e,
                    dataSpec,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN);
        }

        // Check that the protocol of the new url is supported.
        String protocol = url.getProtocol();
        if (!"https".equals(protocol) && !"http".equals(protocol)) {
            throw new HttpDataSourceException(
                    "Unsupported protocol redirect: " + protocol,
                    dataSpec,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN);
        }
        if (!allowCrossProtocolRedirects && !protocol.equals(originalUrl.getProtocol())) {
            throw new HttpDataSourceException(
                    "Disallowed cross-protocol redirect ("
                            + originalUrl.getProtocol()
                            + " to "
                            + protocol
                            + ")",
                    dataSpec,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    HttpDataSourceException.TYPE_OPEN);
        }
        return url;
    }

    /**
     * Attempts to skip the specified number of bytes in full.
     *
     * @param bytesToSkip The number of bytes to skip.
     * @param dataSpec The {@link DataSpec}.
     * @throws IOException If the thread is interrupted during the operation, or if the data ended
     *     before skipping the specified number of bytes.
     */
    private void skipFully(long bytesToSkip, DataSpec dataSpec) throws IOException {
        if (bytesToSkip == 0) {
            return;
        }
        byte[] skipBuffer = new byte[4096];
        while (bytesToSkip > 0) {
            int readLength = (int) min(bytesToSkip, skipBuffer.length);
            int read = castNonNull(inputStream).read(skipBuffer, 0, readLength);
            if (Thread.currentThread().isInterrupted()) {
                throw new HttpDataSourceException(
                        new InterruptedIOException(),
                        dataSpec,
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        HttpDataSourceException.TYPE_OPEN);
            }
            if (read == -1) {
                throw new HttpDataSourceException(
                        dataSpec,
                        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                        HttpDataSourceException.TYPE_OPEN);
            }
            bytesToSkip -= read;
            bytesTransferred(read);
        }
    }

    /**
     * Reads up to {@code length} bytes of data and stores them into {@code buffer}, starting at index
     * {@code offset}.
     *
     * <p>This method blocks until at least one byte of data can be read, the end of the opened range
     * is detected, or an exception is thrown.
     *
     * @param buffer The buffer into which the read data should be stored.
     * @param offset The start offset into {@code buffer} at which data should be written.
     * @param readLength The maximum number of bytes to read.
     * @return The number of bytes read, or {@link C#RESULT_END_OF_INPUT} if the end of the opened
     *     range is reached.
     * @throws IOException If an error occurs reading from the source.
     */
    private int readInternal(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        }
        if (bytesToRead != C.LENGTH_UNSET) {
            long bytesRemaining = bytesToRead - bytesRead;
            if (bytesRemaining == 0) {
                return C.RESULT_END_OF_INPUT;
            }
            readLength = (int) min(readLength, bytesRemaining);
        }

        int read = castNonNull(inputStream).read(buffer, offset, readLength);
        if (read == -1) {
            return C.RESULT_END_OF_INPUT;
        }

        bytesRead += read;
        bytesTransferred(read);
        return read;
    }

    /**
     * On platform API levels 19 and 20, okhttp's implementation of {@link InputStream#close} can
     * block for a long time if the stream has a lot of data remaining. Call this method before
     * closing the input stream to make a best effort to cause the input stream to encounter an
     * unexpected end of input, working around this issue. On other platform API levels, the method
     * does nothing.
     *
     * @param connection The connection whose {@link InputStream} should be terminated.
     * @param bytesRemaining The number of bytes remaining to be read from the input stream if its
     *     length is known. {@link C#LENGTH_UNSET} otherwise.
     */
    private static void maybeTerminateInputStream(
            @Nullable HttpURLConnection connection, long bytesRemaining) {
        if (connection == null || Util.SDK_INT < 19 || Util.SDK_INT > 20) {
            return;
        }

        try {
            InputStream inputStream = connection.getInputStream();
            if (bytesRemaining == C.LENGTH_UNSET) {
                // If the input stream has already ended, do nothing. The socket may be re-used.
                if (inputStream.read() == -1) {
                    return;
                }
            } else if (bytesRemaining <= MAX_BYTES_TO_DRAIN) {
                // There isn't much data left. Prefer to allow it to drain, which may allow the socket to be
                // re-used.
                return;
            }
            String className = inputStream.getClass().getName();
            if ("com.android.okhttp.internal.http.HttpTransport$ChunkedInputStream".equals(className)
                    || "com.android.okhttp.internal.http.HttpTransport$FixedLengthInputStream"
                    .equals(className)) {
                Class<?> superclass = inputStream.getClass().getSuperclass();
                Method unexpectedEndOfInput =
                        checkNotNull(superclass).getDeclaredMethod("unexpectedEndOfInput");
                unexpectedEndOfInput.setAccessible(true);
                unexpectedEndOfInput.invoke(inputStream);
            }
        } catch (Exception e) {
            // If an IOException then the connection didn't ever have an input stream, or it was closed
            // already. If another type of exception then something went wrong, most likely the device
            // isn't using okhttp.
        }
    }

    /** Closes the current connection quietly, if there is one. */
    private void closeConnectionQuietly() {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error while disconnecting", e);
            }
            connection = null;
        }
    }

    private static boolean isCompressed(HttpURLConnection connection) {
        String contentEncoding = connection.getHeaderField("Content-Encoding");
        return "gzip".equalsIgnoreCase(contentEncoding);
    }

    private static class NullFilteringHeadersMap extends ForwardingMap<String, List<String>> {

        private final Map<String, List<String>> headers;

        public NullFilteringHeadersMap(Map<String, List<String>> headers) {
            this.headers = headers;
        }

        @Override
        protected Map<String, List<String>> delegate() {
            return headers;
        }

        @Override
        public boolean containsKey(@Nullable Object key) {
            return key != null && super.containsKey(key);
        }

        @Nullable
        @Override
        public List<String> get(@Nullable Object key) {
            return key == null ? null : super.get(key);
        }

        @Override
        public Set<String> keySet() {
            return Sets.filter(super.keySet(), key -> key != null);
        }

        @Override
        public Set<Entry<String, List<String>>> entrySet() {
            return Sets.filter(super.entrySet(), entry -> entry.getKey() != null);
        }

        @Override
        public int size() {
            return super.size() - (super.containsKey(null) ? 1 : 0);
        }

        @Override
        public boolean isEmpty() {
            return super.isEmpty() || (super.size() == 1 && super.containsKey(null));
        }

        @Override
        public boolean containsValue(@Nullable Object value) {
            return super.standardContainsValue(value);
        }

        @Override
        public boolean equals(@Nullable Object object) {
            return object != null && super.standardEquals(object);
        }

        @Override
        public int hashCode() {
            return super.standardHashCode();
        }
    }
}