require 'buildr/gpg'
require 'buildr/git_auto_version'

HTTP_CLIENT_JARS = [:httpclient, :httpcore, :commons_codec, :commons_logging]

desc "ProxyServlet: Servlet providing proxy capabilities"
define 'proxy-servlet' do
  project.group = 'org.realityforge.proxy-servlet'
  compile.options.source = '1.8'
  compile.options.target = '1.8'
  compile.options.lint = 'all'

  project.version = ENV['PRODUCT_VERSION'] if ENV['PRODUCT_VERSION']

  pom.add_apache_v2_license
  pom.add_github_project('realityforge/proxy-servlet')
  pom.add_developer('realityforge', 'Peter Donald')
  pom.provided_dependencies.concat [:javax_servlet]

  compile.with HTTP_CLIENT_JARS, :javax_servlet

  package(:jar)
  package(:sources)
  package(:javadoc)
  iml.excluded_directories << project._('tmp')

  ipr.add_component_from_artifact(:idea_codestyle)
end
