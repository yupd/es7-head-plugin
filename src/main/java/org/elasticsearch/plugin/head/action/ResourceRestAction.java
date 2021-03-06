package org.elasticsearch.plugin.head.action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.plugin.head.utils.Utils;
import org.elasticsearch.rest.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.*;

import static java.util.Collections.singletonList;
import static org.elasticsearch.rest.RestRequest.Method.GET;

/**
 * head插件静态资源请求处理实现
 * @author yupd
 */
public class ResourceRestAction implements RestHandler {

    private static Logger logger = LogManager.getLogger(ResourceRestAction.class);

    static {
        mimeTypes();
    }

    private static Map<String, String> MIME_TYPES;
    private static final String resourcePath = "support/http/resources";
    private static final String REQ_PREFIX = "/_plugin/head";

    public List<Route> routes() {
        return Arrays.asList(
                new Route(GET, REQ_PREFIX),
                new Route(GET, REQ_PREFIX + "/*"),
                new Route(GET, REQ_PREFIX + "/_site/*"),
                new Route(GET, REQ_PREFIX + "/_site/base/*"),
                new Route(GET, REQ_PREFIX + "/_site/fonts/*"),
                new Route(GET, REQ_PREFIX + "/_site/lang/*"));
    }

    public void handleRequest(RestRequest restRequest, RestChannel restChannel, NodeClient nodeClient) throws Exception {
        restChannel.sendResponse(returnResourceFile(restRequest.getHttpRequest().uri()));
    }

    protected BytesRestResponse returnResourceFile(String uri) throws IOException {
        String mimeType = getMimeTypeForFile(uri);
        String fileName = uri.substring(uri.indexOf(REQ_PREFIX) + REQ_PREFIX.length());
        if(fileName.equals("") || fileName.equals("/")){
            fileName = "/index.html";
            mimeType = "text/html";
        }
        String resource = resourcePath + fileName;
        logger.info("fileName:[{}], resource:[{}]", fileName, resource);
        byte[] bytes = Utils.readByteArrayFromResource(resource);
        if(bytes == null){
            return new BytesRestResponse(RestStatus.OK, mimeType, new byte[0]);
        }
        return new BytesRestResponse(RestStatus.OK, mimeType, bytes);
    }

    public static String getMimeTypeForFile(String uri) {
        int dot = uri.lastIndexOf('.');
        String mime = null;
        if (dot >= 0) {
            mime = mimeTypes().get(uri.substring(dot + 1).toLowerCase());
        }
        return mime == null ? "application/octet-stream" : mime;
    }

    public static Map<String, String> mimeTypes() {
        if (MIME_TYPES == null) {
            MIME_TYPES = new HashMap<String, String>();
            loadMimeTypes(MIME_TYPES, "META-INF/nanohttpd/default-mimetypes.properties");
            loadMimeTypes(MIME_TYPES, "META-INF/nanohttpd/mimetypes.properties");
            if (MIME_TYPES.isEmpty()) {
                logger.warn("no mime types found in the classpath! please provide mimetypes.properties");
            }
        }
        return MIME_TYPES;
    }

    private static void loadMimeTypes(Map<String, String> result, String resourceName) {
        try {
            Enumeration<URL> resources = ResourceRestAction.class.getClassLoader().getResources(resourceName);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                Properties properties = new Properties();
                InputStream stream = null;
                try {
                    stream = url.openStream();
                    properties.load(stream);
                } catch (IOException e) {
                    logger.error("could not load mimetypes from " + url, e);
                } finally {
                    safeClose(stream);
                }
                result.putAll((Map) properties);
            }
        } catch (IOException e) {
            logger.info("no mime types available at " + resourceName);
        }
    };

    public static final void safeClose(Object closeable) {
        try {
            if (closeable != null) {
                if (closeable instanceof Closeable) {
                    ((Closeable) closeable).close();
                } else if (closeable instanceof Socket) {
                    ((Socket) closeable).close();
                } else if (closeable instanceof ServerSocket) {
                    ((ServerSocket) closeable).close();
                } else {
                    throw new IllegalArgumentException("Unknown object to close");
                }
            }
        } catch (IOException e) {
            logger.error("Could not close", e);
        }
    }
}
