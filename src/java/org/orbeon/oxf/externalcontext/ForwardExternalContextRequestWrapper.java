/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.externalcontext;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.NetUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Create an ExternalContext.Request useful for forwarding a request while simulating a
 * server-side redirect.
 */
public class ForwardExternalContextRequestWrapper extends RequestWrapper {

    private String pathQuery;
    private String method;
    private String mediaType;
    private byte[] postData;

    private Map headerMap;
    private Map headerValuesMap;

    private InputStream inputStream;
    private String path;
    private String queryString;
    private Map queryParameters;

    /**
     * This simulates a POST or a PUT.
     */
    public ForwardExternalContextRequestWrapper(ExternalContext.Request request, String pathQuery, String method, String mediaType, byte[] postData) {
        super(request);
        this.pathQuery = pathQuery;
        this.method = method;
        this.mediaType = mediaType;
        this.postData = postData;

        initializeHeaders(request);
    }

    /**
     * This simulates a GET.
     */
    public ForwardExternalContextRequestWrapper(ExternalContext.Request request, String pathQuery, String method) {
        super(request);
        this.pathQuery = pathQuery;
        this.method = method;

        initializeHeaders(request);
    }

    private void initializeHeaders(ExternalContext.Request request) {
        /**
         * We don't want to pass all the headers. For instance passing the Referer or Content-Length would be wrong. So
         * we only pass 2 headers:
         *
         * Cookie: In particular for the JSESSIONID. We want the page to be able to know who the user is.
         *
         * Authorization: If we don't pass this header, when the destination page makes a query to a service it won't be
         * able to pass the Authorization header, which in certain cases leads to a 401. Why in some cases passing just
         * the JSESSIONID cookie is enough while in other cases this leads to a 401 is unclear.
         */
        {
            this.headerMap = new HashMap();
            final Map requestHeaderMap = request.getHeaderMap();
            final Object cookie = requestHeaderMap.get("cookie");
            if (cookie != null) headerMap.put("cookie", cookie);
            final Object authorization = requestHeaderMap.get("authorization");
            if (authorization != null) headerMap.put("authorization", authorization);
        }
        {
            this.headerValuesMap = new HashMap();
            Map requestHeaderValuesMap = request.getHeaderValuesMap();
            final Object cookie = requestHeaderValuesMap.get("cookie");
            if (cookie != null) headerValuesMap.put("cookie", cookie);
            final Object authorization = requestHeaderValuesMap.get("authorization");
            if (authorization != null) headerValuesMap.put("authorization", authorization);
        }
    }

    public int getContentLength() {
        return (postData == null) ? 0 : postData.length;
    }

    public String getContentType() {
        return mediaType;
    }

    public InputStream getInputStream() throws IOException {
        if (inputStream == null && postData != null) {
            inputStream = new ByteArrayInputStream(postData);
        }

        return inputStream;
    }

    public String getMethod() {
        return method.toUpperCase();
    }

    public Map getParameterMap() {
        if (queryParameters == null) {
            queryParameters = NetUtils.decodeQueryString(getQueryString(), false);
        }

        return queryParameters;
    }

    public String getPathInfo() {
        if (path == null) {
            final int mark = pathQuery.indexOf('?');
            path = (mark == -1) ? pathQuery : pathQuery.substring(0, mark);
        }

        return path;
    }

    public String getQueryString() {
        if (queryString == null) {
            final int mark = pathQuery.indexOf('?');
            queryString = (mark == -1) ? null : pathQuery.substring(mark + 1);
        }

        return queryString;
    }

    public String getServletPath() {
        return "";
    }

    public String getCharacterEncoding() {
        return null;//TODO?
    }

    public String getRequestPath() {
        // Get servlet path and path info
        String servletPath = getServletPath();
        if (servletPath == null) servletPath = "";
        String pathInfo = getPathInfo();
        if (pathInfo == null) pathInfo = "";

        // Concatenate servlet path and path info, avoiding a double slash
        String requestPath = servletPath.endsWith("/") && pathInfo.startsWith("/")
                ? servletPath + pathInfo.substring(1)
                : servletPath + pathInfo;

        // Add starting slash if missing
        if (!requestPath.startsWith("/"))
            requestPath = "/" + requestPath;

        return requestPath;
    }
//
//    public String getRequestURI() {
//        return getRequestPath();// FIXME
//    }
//
//    public String getRequestURL() {
//        return getRequestPath();// FIXME
//    }

    public Map getHeaderMap() {
        return headerMap;
    }

    public Map getHeaderValuesMap() {
        return headerValuesMap;
    }

    public String getPathTranslated() {
        return null;
    }

    public Reader getReader() throws IOException {
        return null;//TODO?
    }
}