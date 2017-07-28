package org.jaeger.tracing.addon.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.ServletContextListener;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.facets.FacetFactory;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.DependencyFacet;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.projects.ui.AbstractProjectCommand;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

@SuppressWarnings("unused")
public class JaegerSetupCommand extends AbstractProjectCommand {

  private static final String IO_OPENTRACING_CONTRIB_GROUP_ID = "io.opentracing.contrib";
  private String [] technologies = {"jax-rs","spring-boot","vert.x","ejb"};

	@Inject
  private ProjectFactory projectFactory;

  @Inject
  private FacetFactory facetFactory;

	@Inject
	private DependencyInstaller dependencyInstaller;

	@Inject
	@WithAttributes(label = "Technology", required = true)
	private UISelectOne<String> techInput;

	@Override
	public UICommandMetadata getMetadata(UIContext context) {
		return Metadata.forCommand(JaegerSetupCommand.class)
				.name("Jaeger Tracing: Setup")
				.category(Categories.create("Tracing"));
	}

	@Override
	public void initializeUI(UIBuilder builder) throws Exception {

    List<String> techList = Arrays.asList(technologies);
    UIContext uiContext = builder.getUIContext();
    if (detectWildFlySwarm(uiContext)) {
      uiContext.getProvider().getOutput().out().println("** Detected WildFly Swarm **");
      techList = new ArrayList<>(techList); // the original one is *fixed* size
      techList.add("WF Swarm");
    }

    techInput.setValueChoices(techList);
		builder.add(techInput);
	}

  private boolean detectWildFlySwarm(UIContext context) {
	  DependencyFacet dependencyFacet = getSelectedProject(context).getFacet(DependencyFacet.class);
    List<Dependency> dependencies = dependencyFacet.getDependencies();
    boolean found = dependencies.stream()
        .map(dependency -> dependency.getCoordinate())
        .anyMatch(coordinate ->
                      "org.wildfly.swarm".equals(coordinate.getGroupId()));
    return found;
  }

  @Override
	protected boolean isProjectRequired() {
		return true;
	}

	@Override
	protected ProjectFactory getProjectFactory() {
		return projectFactory;
	}

	@Override
	public Result execute(UIExecutionContext context) throws Exception
	{

    facetFactory.install(getSelectedProject(context),ResourcesFacet.class);

		Map map = context.getUIContext().getAttributeMap();


    installJaegerDependency(context);

		switch (techInput.getValue()) {
			case "jax-rs":
				installJaxRs(context);
				break;
			case "spring-boot":
				installSpringBoot(context);
				break;
			case "vert.x":
			  installVertX(context);
				break;
      case "WF Swarm":
        installSwarmJaegerFraction(context);
        break;
      case "ejb":
        installEJB(context);
        break;
			default:
				return Results.fail("Unknown selection " + techInput.getValue());
		}


		return Results.success("Jaeger Tracing was successfully setup for the current project!");
	}

  private void installSwarmJaegerFraction(UIExecutionContext context) {
    Dependency dependency;
    dependency = DependencyBuilder.create("org.wildfly.swarm")
        .setArtifactId("jaeger");
    installDependencyIfNeeded(context, dependency);
  }

  private void installJaegerDependency(UIExecutionContext context) {
    Dependency dependency;
    dependency = DependencyBuilder.create("com.uber.jaeger")
        .setArtifactId("jaeger-core")
        .setVersion("0.20.5");
    installDependencyIfNeeded(context, dependency);

  }

  private void installJaxRs(UIExecutionContext context) {
    Dependency dependency;
    dependency = DependencyBuilder.create(IO_OPENTRACING_CONTRIB_GROUP_ID)
    				        .setArtifactId("opentracing-jaxrs2");
    installDependencyIfNeeded(context,dependency);

    dependency = DependencyBuilder.create("javax")
          .setArtifactId("javaee-api")
        .setVersion("7.0")
        .setScopeType("provided");
    installDependencyIfNeeded(context,dependency);

    dependency = DependencyBuilder.create("org.wildfly.swarm")
        .setArtifactId("cdi");
    installDependencyIfNeeded(context,dependency);

    createBeansXmlIfNeeded(context);

  		// Now add the setup
    // TODO check if this already exists
    Project project = getSelectedProject(context);
    JavaClassSource source = Roaster.create(JavaClassSource.class)
                  .setPackage(project.getFacet(JavaSourceFacet.class).getBasePackage())
                  .setName("TracerSetupListener");

    source.addImport("com.uber.jaeger.Configuration");
    source.addImport("com.uber.jaeger.samplers.ProbabilisticSampler");
		source.addImport("io.opentracing.util.GlobalTracer");
    source.addImport("javax.enterprise.inject.Produces");
    source.addImport("javax.inject.Inject");
    source.addImport("javax.inject.Singleton");
		source.addImport("javax.servlet.annotation.WebListener");
		source.addImport("javax.servlet.ServletContextListener");

		source.implementInterface(ServletContextListener.class);

		 source.addField().setName("tracer").setType("io.opentracing.Tracer")
		 .addAnnotation("Inject");
		 source.addAnnotation("WebListener");


    MethodSource<JavaClassSource> jtMethod = source.addMethod();
    jtMethod.setBody("return new Configuration(\"wildfly-swarm\", new Configuration.SamplerConfiguration(\n" +
                         "        ProbabilisticSampler.TYPE, 1),\n" +
                         "        new Configuration.ReporterConfiguration())\n" +
                         "        .getTracer();");
    jtMethod.setName("jaegerTracer");
    jtMethod.setStatic(true);
    jtMethod.setReturnType("io.opentracing.Tracer");
    jtMethod.addAnnotation("Produces");
    jtMethod.addAnnotation("Singleton");

    MethodSource<JavaClassSource> ciMethod = source.getMethod("contextInitialized", "ServletContextEvent");
    ciMethod.setBody("GlobalTracer.register(tracer);");

    JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);
    facet.saveJavaSource(source);


  }

  private void createBeansXmlIfNeeded(UIExecutionContext context) {

	  Project project = getSelectedProject(context);
    ResourcesFacet facet = getSelectedProject(context).getFacet(ResourcesFacet.class);
    FileResource fr = facet.getResource("META-INF/beans.xml");
    if (!fr.exists()) {
      fr.createNewFile();
      fr.setContents("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                         "<beans xmlns=\"http://java.sun.com/xml/ns/javaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                         "  xsi:schemaLocation=\"\n" +
                         "http://java.sun.com/xml/ns/javaee\n" +
                         "http://java.sun.com/xml/ns/javaee/beans_1_1.xsd\" bean-discovery-mode=\"all\">\n" +
                         "</beans>");
    }

  }

  private void installSpringBoot(UIExecutionContext context) {
	  Dependency dependency = DependencyBuilder.create(IO_OPENTRACING_CONTRIB_GROUP_ID)
	  				        .setArtifactId("opentracing-spring-web-autoconfigure")
	  				        .setScopeType("compile");

    installDependencyIfNeeded(context, dependency);

    Project project = getSelectedProject(context);
    JavaClassSource source = Roaster.create(JavaClassSource.class)
                  .setPackage(project.getFacet(JavaSourceFacet.class).getBasePackage())
                  .setName("TracerSetup");

    source.addImport("com.uber.jaeger.Configuration");
    source.addImport("com.uber.jaeger.samplers.ProbabilisticSampler");
		source.addImport("io.opentracing.util.GlobalTracer");
    source.addImport("org.springframework.context.annotation.Bean");
    source.addImport("org.springframework.context.annotation.Configuration");

    source.addAnnotation("Configuration");

    MethodSource<JavaClassSource> jtMethod = source.addMethod();
    jtMethod.setBody("return new Configuration(\"wildfly-swarm\", new Configuration.SamplerConfiguration(\n" +
                         "        ProbabilisticSampler.TYPE, 1),\n" +
                         "        new Configuration.ReporterConfiguration())\n" +
                         "        .getTracer();");
    jtMethod.setName("jaegerTracer");
    jtMethod.setPublic();
    jtMethod.setReturnType("io.opentracing.Tracer");
    jtMethod.addAnnotation("Bean");

    JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);
    facet.saveJavaSource(source);


  }

  private void installEJB(UIExecutionContext context) {
    Dependency dependency = DependencyBuilder.create(IO_OPENTRACING_CONTRIB_GROUP_ID)
    		        .setArtifactId("opentracing-ejb")
                .setVersion("0.0.2")
    		        .setScopeType("compile");
    installDependencyIfNeeded(context, dependency);

    if (detectWildFlySwarm(context.getUIContext())) {
      dependency = DependencyBuilder.create("org.wildfly.swarm")
          .setArtifactId("ejb");
      installDependencyIfNeeded(context, dependency);
    }

    installSwarmJaegerFraction(context);

  }


  private void installVertX(UIExecutionContext context) {
    Dependency dependency = DependencyBuilder.create(IO_OPENTRACING_CONTRIB_GROUP_ID)
    		        .setArtifactId("tracing-vert-x TODO") // TODO
    		        .setScopeType("compile");

    installDependencyIfNeeded(context, dependency);

  }

  private void installDependencyIfNeeded(UIExecutionContext context, Dependency dependency) {
    if (!getSelectedProject(context).getFacet(DependencyFacet.class)
  				.hasDirectDependency(dependency))
  		{
  			dependencyInstaller.install(getSelectedProject(context), dependency);
  		}
  }
}