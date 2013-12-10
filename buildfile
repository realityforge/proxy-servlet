require 'buildr/git_auto_version'

HTTP_CLIENT_JARS = [:httpclient, :httpcore, :commons_codec, :commons_logging]

desc "ProxyServlet: Servlet providing proxy capabilities"
define 'proxy-servlet' do
  project.group = 'org.realityforge.proxy-servlet'
  compile.options.source = '1.6'
  compile.options.target = '1.6'
  compile.options.lint = 'all'

  project.version = ENV['PRODUCT_VERSION'] if ENV['PRODUCT_VERSION']

  compile.with HTTP_CLIENT_JARS, :javax_servlet

  package(:jar)
  package(:sources)
end
