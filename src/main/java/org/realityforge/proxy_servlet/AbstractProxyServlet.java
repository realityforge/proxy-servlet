/*
 * Copyright MITRE
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
package org.realityforge.proxy_servlet;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;

/**
 * Abstract proxy servlet.
 * This is a heavily customized version of a similar servlet under Apache2 license by David Smiley.
 */
public abstract class AbstractProxyServlet
  extends HttpServlet
{
  @Nonnull
  public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
  /**
   * These are the "hop-by-hop" headers that should not be copied.
   * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
   * I use an HttpClient HeaderGroup class instead of Set<String> because this
   * approach does case insensitive lookup faster.
   */
  @Nonnull
  private static final HeaderGroup HOP_BY_HOP_HEADERS;
  @Nonnull
  private static final BitSet ASCII_QUERY_CHARS;
  @Nonnull
  private static final Logger LOG = Logger.getLogger( AbstractProxyServlet.class.getName() );
  public static final int MAX_ASCII_VALUE = 128;
  private URI _targetUri;
  private String _target;
  private CloseableHttpClient _client;

  @Override
  public void init( @Nonnull final ServletConfig servletConfig )
    throws ServletException
  {
    super.init( servletConfig );

    final String proxyURL = getProxyURL();
    try
    {
      _targetUri = new URI( proxyURL );
    }
    catch ( final Exception e )
    {
      final String message = "Error constructing uri: " + proxyURL;
      LOG.log( Level.SEVERE, message, e );
      throw new ServletException( message, e );
    }
    _target = _targetUri.toString();

    _client = HttpClientBuilder.create().disableRedirectHandling().build();
  }

  @Nonnull
  protected abstract String getProxyURL();

  @Override
  public void destroy()
  {
    if ( null != _client )
    {
      try
      {
        _client.close();
      }
      catch ( final IOException ioe )
      {
        log( "While destroying servlet, shutting down httpclient: " + ioe, ioe );
      }
    }
    super.destroy();
  }

  @SuppressWarnings( "deprecation" )
  @Override
  protected void service( @Nonnull final HttpServletRequest servletRequest,
                          @Nonnull final HttpServletResponse servletResponse )
    throws ServletException, IOException
  {
    final String proxyRequestUri = rewriteUrlFromRequest( servletRequest );
    final HttpRequest proxyRequest = newProxyRequest( servletRequest, proxyRequestUri );

    copyRequestHeaders( servletRequest, proxyRequest );
    setXForwardedForHeader( servletRequest, proxyRequest );

    proxyPrepared( proxyRequest );

    try
    {
      final HttpResponse proxyResponse = _client.execute( URIUtils.extractHost( _targetUri ), proxyRequest );

      // Process the response
      final int statusCode = proxyResponse.getStatusLine().getStatusCode();

      if ( doResponseRedirectOrNotModifiedLogic( servletRequest, servletResponse, proxyResponse, statusCode ) )
      {
        //just to be sure, but is probably a no-op
        EntityUtils.consume( proxyResponse.getEntity() );
        return;
      }

      // Pass the response code. This method with the "reason phrase" is deprecated but it's the only way to pass the
      //  reason along too.
      servletResponse.setStatus( statusCode, proxyResponse.getStatusLine().getReasonPhrase() );

      copyResponseHeaders( proxyResponse, servletResponse );

      // Send the content to the client
      copyResponseEntity( proxyResponse, servletResponse );
    }
    catch ( final Exception e )
    {
      //abort request, according to best practice with HttpClient
      if ( proxyRequest instanceof Cancellable )
      {
        final Cancellable cancellable = (Cancellable) proxyRequest;
        cancellable.cancel();
      }
      handleError( e );
    }
  }

  private void handleError( @Nonnull final Exception e )
    throws IOException, ServletException
  {
    if ( e instanceof IOException )
    {
      throw (IOException) e;
    }
    else if ( e instanceof ServletException )
    {
      throw (ServletException) e;
    }
    else if ( e instanceof RuntimeException )
    {
      throw (RuntimeException) e;
    }
    else
    {
      throw new RuntimeException( e );
    }
  }

  /**
   * Override this to customize the proxied request.
   */
  @SuppressWarnings( "UnusedParameters" )
  protected void proxyPrepared( @Nonnull final HttpRequest request )
  {
  }

  /**
   * Override this method to control whether ip forwarded header is set.
   *
   * @return true to set header
   */
  protected boolean shouldForwardIP()
  {
    return true;
  }

  @Nonnull
  private HttpRequest newProxyRequest( @Nonnull final HttpServletRequest servletRequest,
                                       @Nonnull final String proxyRequestUri )
    throws IOException
  {
    final String method = servletRequest.getMethod();
    if ( null != servletRequest.getHeader( HttpHeaders.CONTENT_LENGTH ) ||
         null != servletRequest.getHeader( HttpHeaders.TRANSFER_ENCODING ) )
    {
      //spec: RFC 2616, sec 4.3: either of these two headers signal that there is a message body.
      final HttpEntityEnclosingRequest r = new BasicHttpEntityEnclosingRequest( method, proxyRequestUri );
      r.setEntity( new InputStreamEntity( servletRequest.getInputStream(), servletRequest.getContentLength() ) );
      return r;
    }
    else
    {
      return new BasicHttpRequest( method, proxyRequestUri );
    }
  }

  private boolean doResponseRedirectOrNotModifiedLogic( @Nonnull final HttpServletRequest servletRequest,
                                                        @Nonnull final HttpServletResponse servletResponse,
                                                        @Nonnull final HttpResponse proxyResponse,
                                                        final int statusCode )
    throws ServletException, IOException
  {
    // Check if the proxy response is a redirect
    // The following code is adapted from org.tigris.noodle.filters.CheckForRedirect
    if ( statusCode >= HttpServletResponse.SC_MULTIPLE_CHOICES &&  /* 300 */
         statusCode < HttpServletResponse.SC_NOT_MODIFIED /* 304 */ )
    {
      final Header locationHeader = proxyResponse.getLastHeader( HttpHeaders.LOCATION );
      if ( null == locationHeader )
      {
        final String message =
          "Received status code: " + statusCode + " but no " + HttpHeaders.LOCATION +
          " header was found in the response";
        throw new ServletException( message );
      }
      // Modify the redirect to go to this proxy servlet rather that the proxied host
      final String locStr = rewriteUrlFromResponse( servletRequest, locationHeader.getValue() );

      servletResponse.sendRedirect( locStr );
      return true;
    }

    // 304 needs special handling.  See:
    // http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
    // We get a 304 whenever passed an 'If-Modified-Since'
    // header and the data on disk has not changed; server
    // responds w/ a 304 saying I'm not going to send the
    // body because the file has not changed.
    if ( statusCode == HttpServletResponse.SC_NOT_MODIFIED )
    {
      servletResponse.setIntHeader( HttpHeaders.CONTENT_LENGTH, 0 );
      servletResponse.setStatus( HttpServletResponse.SC_NOT_MODIFIED );
      return true;
    }
    return false;
  }

  private void closeQuietly( @Nonnull final Closeable closeable )
  {
    try
    {
      closeable.close();
    }
    catch ( final IOException ioe )
    {
      log( ioe.getMessage(), ioe );
    }
  }

  /**
   * Copy request headers from the servlet client to the proxy request.
   */
  private void copyRequestHeaders( @Nonnull final HttpServletRequest servletRequest,
                                   @Nonnull final HttpRequest proxyRequest )
  {
    // Get an Enumeration of all of the header names sent by the client
    final Enumeration<String> enumerationOfHeaderNames = servletRequest.getHeaderNames();
    while ( enumerationOfHeaderNames.hasMoreElements() )
    {
      final String headerName = enumerationOfHeaderNames.nextElement();
      //Instead the content-length is effectively set via InputStreamEntity
      if ( headerName.equalsIgnoreCase( HttpHeaders.CONTENT_LENGTH ) )
      {
        continue;
      }
      else if ( HOP_BY_HOP_HEADERS.containsHeader( headerName ) )
      {
        continue;
      }

      final Enumeration<String> headers = servletRequest.getHeaders( headerName );
      while ( headers.hasMoreElements() )
      {
        //sometimes more than one value
        String headerValue = headers.nextElement();
        // In case the proxy host is running multiple virtual servers,
        // rewrite the Host header to ensure that we get content from
        // the correct virtual server
        if ( headerName.equalsIgnoreCase( HttpHeaders.HOST ) )
        {
          final HttpHost host = URIUtils.extractHost( _targetUri );
          headerValue = host.getHostName();
          if ( -1 != host.getPort() )
          {
            headerValue += ":" + host.getPort();
          }
        }
        proxyRequest.addHeader( headerName, headerValue );
      }
    }
  }

  private void setXForwardedForHeader( @Nonnull final HttpServletRequest servletRequest,
                                       @Nonnull final HttpRequest proxyRequest )
  {
    if ( shouldForwardIP() )
    {
      String newHeader = servletRequest.getRemoteAddr();
      String existingHeader = servletRequest.getHeader( X_FORWARDED_FOR_HEADER );
      if ( existingHeader != null )
      {
        newHeader = existingHeader + ", " + newHeader;
      }
      proxyRequest.setHeader( X_FORWARDED_FOR_HEADER, newHeader );
    }
  }

  /**
   * Copy proxied response headers back to the servlet client.
   */
  private void copyResponseHeaders( @Nonnull final HttpResponse proxyResponse,
                                    @Nonnull final HttpServletResponse servletResponse )
  {
    for ( final Header header : proxyResponse.getAllHeaders() )
    {
      if ( HOP_BY_HOP_HEADERS.containsHeader( header.getName() ) )
      {
        continue;
      }
      servletResponse.addHeader( header.getName(), header.getValue() );
    }
  }

  /**
   * Copy response body data (the entity) from the proxy to the servlet client.
   */
  private void copyResponseEntity( @Nonnull final HttpResponse proxyResponse,
                                   @Nonnull final HttpServletResponse servletResponse )
    throws IOException
  {
    final HttpEntity entity = proxyResponse.getEntity();
    if ( entity != null )
    {
      OutputStream servletOutputStream = servletResponse.getOutputStream();
      try
      {
        entity.writeTo( servletOutputStream );
      }
      finally
      {
        closeQuietly( servletOutputStream );
      }
    }
  }

  /**
   * Reads the request URI from {@code servletRequest} and rewrites it, considering {@link
   * #_targetUri}. It's used to make the new request.
   */
  protected String rewriteUrlFromRequest( @Nonnull final HttpServletRequest servletRequest )
  {
    final StringBuilder sb = new StringBuilder( 500 );
    sb.append( _target );
    // Handle the path given to the servlet
    if ( null != servletRequest.getPathInfo() )
    {
      //ex: /my/path.html
      sb.append( encodeUriQuery( servletRequest.getPathInfo() ) );
    }
    // Handle the query string
    //ex:(following '?'): name=value&foo=bar#fragment
    final String queryString = servletRequest.getQueryString();
    if ( null != queryString && queryString.length() > 0 )
    {
      sb.append( '?' );
      final int fragIdx = queryString.indexOf( '#' );
      final String queryNoFrag = ( fragIdx < 0 ? queryString : queryString.substring( 0, fragIdx ) );
      sb.append( encodeUriQuery( queryNoFrag ) );
      //Fragments should never be sent so we don't....
    }
    return sb.toString();
  }

  /**
   * For a redirect response from the target server, this translates {@code theUrl} to redirect to
   * and translates it to one the original client can use.
   */
  private String rewriteUrlFromResponse( @Nonnull final HttpServletRequest servletRequest, @Nonnull final String url )
  {
    if ( url.startsWith( _target ) )
    {
      String curUrl = servletRequest.getRequestURL().toString(); //no query
      final String pathInfo = servletRequest.getPathInfo();
      if ( null != pathInfo )
      {
        assert curUrl.endsWith( pathInfo );
        //take pathInfo off
        curUrl = curUrl.substring( 0, curUrl.length() - pathInfo.length() );
      }
      return curUrl + url.substring( _target.length() );
    }
    else
    {
      return url;
    }
  }

  /**
   * Encodes characters in the query or fragment part of the URI.
   * <p/>
   * <p>Unfortunately, an incoming URI sometimes has characters disallowed by the spec.  HttpClient
   * insists that the outgoing proxied request has a valid URI because it uses Java's {@link java.net.URI}.
   * To be more forgiving, we must escape the problematic characters.  See the URI class for the
   * spec.
   *
   * @param in example: name=value&foo=bar#fragment
   */
  private static CharSequence encodeUriQuery( @Nonnull final CharSequence in )
  {
    //Note that I can't simply use URI.java to encode because it will escape pre-existing escaped things.
    StringBuilder sb = null;
    Formatter formatter = null;
    for ( int i = 0; i < in.length(); i++ )
    {
      char c = in.charAt( i );
      boolean escape = true;
      if ( c < MAX_ASCII_VALUE )
      {
        if ( ASCII_QUERY_CHARS.get( c ) )
        {
          escape = false;
        }
      }
      else if ( !Character.isISOControl( c ) && !Character.isSpaceChar( c ) )
      {
        //not-ascii
        escape = false;
      }
      if ( !escape )
      {
        if ( null != sb )
        {
          sb.append( c );
        }
      }
      else
      {
        //escape
        if ( null == sb )
        {
          final int formatLength = 5 * 3;
          sb = new StringBuilder( in.length() + formatLength );
          sb.append( in, 0, i );
          formatter = new Formatter( sb );
        }
        //leading %, 0 padded, width 2, capital hex
        formatter.format( "%%%02X", (int) c );
      }
    }
    return sb != null ? sb : in;
  }

  static
  {
    HOP_BY_HOP_HEADERS = new HeaderGroup();
    final String[] headers = new String[]
      {
        "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
        "TE", "Trailers", "Transfer-Encoding", "Upgrade"
      };
    for ( final String header : headers )
    {
      HOP_BY_HOP_HEADERS.addHeader( new BasicHeader( header, null ) );
    }
  }

  static
  {
    //plus alphanum
    final char[] unreserved = "_-!.~'()*".toCharArray();
    final char[] punct = ",;:$&+=".toCharArray();
    //plus punct
    final char[] reserved = "?/[]@".toCharArray();

    ASCII_QUERY_CHARS = new BitSet( MAX_ASCII_VALUE );
    for ( char c = 'a'; c <= 'z'; c++ )
    {
      ASCII_QUERY_CHARS.set( c );
    }
    for ( char c = 'A'; c <= 'Z'; c++ )
    {
      ASCII_QUERY_CHARS.set( c );
    }
    for ( char c = '0'; c <= '9'; c++ )
    {
      ASCII_QUERY_CHARS.set( c );
    }
    for ( final char c : unreserved )
    {
      ASCII_QUERY_CHARS.set( c );
    }
    for ( char c : punct )
    {
      ASCII_QUERY_CHARS.set( c );
    }
    for ( char c : reserved )
    {
      ASCII_QUERY_CHARS.set( c );
    }

    //leave existing percent escapes in place
    ASCII_QUERY_CHARS.set( '%' );
  }
}
