# Change Log

### Unreleased

* Upgrade the `au.com.stocksoftware.idea.codestyle` artifact to version `1.17`.

### [v0.3.0](https://github.com/realityforge/proxy-servlet/tree/v0.3.0) (2021-02-01) · [Full Changelog](https://github.com/realityforge/proxy-servlet/compare/v0.2.0...v0.3.0)

* Upgrade the minimum JVM version to 1.8.
* Upgrade the servlet-api to latest version `javax.servlet:javax.servlet-api:jar:4.0.1`
* Annotate APIs with nullability annotations.
* Catch a IOException that is thrown from GlassFish due to bug in GlassFish where it attempts to write to a closed connection. This avoids spurious error messages appearing in the logs.
* Remove the `AbstractProxyServlet.rewriteUrlFromRequest(...)` method from the public API of the servlet as no downstream users override method.

### [v0.2.0](https://github.com/realityforge/proxy-servlet/tree/v0.2.0) (2013-12-28) · [Full Changelog](https://github.com/realityforge/proxy-servlet/compare/v0.1.0...v0.2.0)

* Rework the build to be Maven Central compliant and publish to Maven Central.

### [v0.1.0](https://github.com/realityforge/proxy-servlet/tree/v0.1.0) (2013-12-10) · [Full Changelog](https://github.com/realityforge/proxy-servlet/compare/8749958dee0650956c680a018e70ea516fd50fd6...v0.1.0)

* 🎉 Initial version 🎉
