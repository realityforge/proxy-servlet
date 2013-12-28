Proxy-Servlet
=============

[![Build Status](https://secure.travis-ci.org/realityforge/proxy-servlet.png?branch=master)](http://travis-ci.org/realityforge/proxy-servlet)

The proxy-servlet provides a base class to make it easy to create proxy servlets.

Usage
-----

A simple proxy that forwards to a url configured in JNDI.

```java
@WebServlet( urlPatterns = { "/myservice/*" }, loadOnStartup = 1 )
public class MyServiceProxyServlet
  extends AbstractProxyServlet
{
  @Resource( name = "myservice/endpoint", mappedName = "myservice/endpoint" )
  private String _proxyURL;

  @Override
  protected String getProxyURL()
  {
    return _proxyURL;
  }
}
```


Credit
------

This a customization of work done by David Smiley and he takes all the credit for the good parts of the library.
All bugs are certainly Peter Donald's.
