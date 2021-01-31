Proxy-Servlet
=============

[![Build Status](https://api.travis-ci.com/realityforge/proxy-servlet.svg?branch=master)](http://travis-ci.com/realityforge/proxy-servlet)

The proxy-servlet provides a base class to make it easy to create proxy servlets.

Usage
-----

Download the jar from maven central. This can be done by adding a Maven dependency such as;

```xml
<dependency>
   <groupId>org.realityforge.proxy-servlet</groupId>
   <artifactId>proxy-servlet</artifactId>
   <version>0.03.0</version>
</dependency>
```

A simple proxy that forwards to a url configured in JNDI.

```java
@WebServlet( urlPatterns = { "/myservice/*" }, loadOnStartup = 1 )
public class MyServiceProxyServlet
  extends AbstractProxyServlet
{
  @Resource( lookup = "myservice/endpoint" )
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
