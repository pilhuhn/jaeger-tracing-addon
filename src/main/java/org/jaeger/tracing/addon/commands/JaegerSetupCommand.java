package org.jaeger.tracing.addon.commands;

import static org.jaeger.tracing.addon.util.WriteClassHelper.writeClassFromTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import me.gastaldi.forge.reflections.facet.ReflectionsFacet;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.facets.FacetFactory;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.parser.yaml.resource.YamlResource;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.DependencyFacet;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.projects.ui.AbstractProjectCommand;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.ResourceFactory;
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
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.reflections.Reflections;

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
  private ResourceFactory resourceFactory;

	@Inject
	@WithAttributes(label = "Technology", required = true)
	private UISelectOne<String> techInput;

	@Override
	public UICommandMetadata getMetadata(UIContext context) {
		return Metadata.forCommand(JaegerSetupCommand.class)
				.name("Jaeger Setup Tracing")
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
      techInput.setDefaultValue("WF Swarm");
    }
    if (detectSpringBoot(uiContext)) {
      uiContext.getProvider().getOutput().out().println("** Detected SpringBoot **");
      techInput.setDefaultValue("spring-boot");
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

  private boolean detectSpringBoot(UIContext context) {
    DependencyFacet dependencyFacet = getSelectedProject(context).getFacet(DependencyFacet.class);
     List<Dependency> dependencies = dependencyFacet.getDependencies();
     boolean found = dependencies.stream()
         .map(dependency -> dependency.getCoordinate())
         .anyMatch(coordinate ->
                       "org.springframework.boot".equals(coordinate.getGroupId())
         && coordinate.getArtifactId().startsWith("spring-boot-starter"));
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
    facetFactory.install(getSelectedProject(context),ReflectionsFacet.class);

		Map map = context.getUIContext().getAttributeMap();


    installJaegerDependency(context);
    installJaegerEnvironment(context);

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
        .setVersion("0.20.6");
    installDependencyIfNeeded(context, dependency);

  }

  // assumes post-processing by Fabric8 docker maven plugin
  private void installJaegerEnvironment(UIExecutionContext context) {

    Map<String,Object> model;
    YamlResource resource;

    Path f8path = Paths.get("src/main/fabric8");
    Path p = null;
    try {
      p = Files.createDirectories(f8path);
    } catch (IOException e) {
      e.printStackTrace();  // TODO: Customise this generated block
    }

    File underlyingResource = new File(p.toFile(),"deployment.yml");
    if (!underlyingResource.exists()) {
      try {
        underlyingResource.createNewFile();
      } catch (IOException e) {
        e.printStackTrace();  // TODO: Customise this generated block
      }
    }

    Resource<File> fileResource = resourceFactory.create(underlyingResource);
    resource = fileResource.reify(YamlResource.class);
    if ( resource.getModel().isPresent()  ) {
      model = resource.getModel().get();
    } else {
      model = new HashMap<>();
    }


    Map<String,Object> t;

    t = putMapIfAbsent(model, "spec");
    t = putMapIfAbsent(t, "template");
    t = putMapIfAbsent(t, "spec");

    if (!t.keySet().contains("containers")) {
      t.put("containers", new ArrayList<>());
    }

    List l = (List) t.get("containers");
    if (!hasMap(l,"env")) {
      Map<String, List> env = new HashMap<>();
      env.put("env", new ArrayList());
      l.add(env);
    }

    List<Map> envEntries = (List<Map>) findMap(l,"env").get("env");
    if (envEntries==null) {
      // -env exists, but is empty - is that realistic?
      envEntries=new ArrayList<>();
      findMap(l,"env").put("env",envEntries);
    }

    Map<String,String> env;
    addIfNotExists(envEntries,"JAEGER_AGENT_HOST","localhost");
    addIfNotExists(envEntries,"JAEGER_AGENT_PORT",6831);
    addIfNotExists(envEntries,"JAEGER_SERVICE_NAME","XXX-TODO"); // TODO
    addIfNotExists(envEntries,"JAEGER_HTTP_QUERY_URL","http://jaeger-agent-EDIT_ME.starter-us-east-2.openshiftapps.com/api/traces");

    Map resourcesMap = findMap(l,"resources");
    if (resourcesMap==null) {
      resourcesMap = new HashMap<>();
      resourcesMap.put("resources", new HashMap<>());
      l.add(resourcesMap);
    }
    t = putMapIfAbsent(resourcesMap,"resources");
    t = putMapIfAbsent(t,"limits");
    t.put("memory","250Mi");

    // save it
    resource.setContents(model);
  }

  private Map<String, Object> putMapIfAbsent(Map<String, Object> model, String key) {
    Map<String, Object> out = (Map<String, Object>) model.get(key);
    if (out==null) {
      out = new HashMap<>();
      model.put(key,out);
    }
    return out;
  }

  private void addIfNotExists(List<Map> envEntries, String name, Object value) {
    boolean found = envEntries.stream().anyMatch(m -> m.values().contains(name));
    if (!found) {
      Map<String,Object> map = new HashMap<>(2);
      map.put("name",name);
      map.put("value",value);
      envEntries.add(map);
    }
  }

  private boolean hasMap(List<Map> l, String keyToLookUp) {
    return l.stream().anyMatch(m -> m.keySet().contains(keyToLookUp));
  }

  private Map findMap(List<Map> l, String keyToLookUp) {
    return l.stream().filter(m -> m.keySet().contains(keyToLookUp)).findFirst().orElse(null);
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

    // Create the setup listener
    Project project = getSelectedProject(context);
    String basePackage = project.getFacet(JavaSourceFacet.class).getBasePackage();
    Map map = new HashMap();
    JavaClassSource source = writeClassFromTemplate(basePackage,"JaXRSTracerSetupListener.java.ftl", map,
                                                    context.getUIContext().getProvider().getOutput().out());

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

    Project project = getSelectedProject(context);

    installDependencyIfNeeded(context, dependency);
    String sbaPackage = findSpringBootApplicationPackage(context, project);
    if (sbaPackage==null) {
      sbaPackage = project.getFacet(JavaSourceFacet.class).getBasePackage();
      context.getUIContext().getProvider().getOutput().err().println("** No @SpringBootApplication found **");
    }

    Map root = new HashMap();
    root.put("package",sbaPackage);
    JavaClassSource source = writeClassFromTemplate(sbaPackage, "SBTracerSetup.java.ftl", root,
                                                    context.getUIContext().getProvider().getOutput().out());

    JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);
    facet.saveJavaSource(source);


  }


  private String findSpringBootApplicationPackage(UIExecutionContext context, Project project) {
    ReflectionsFacet facet = project.getFacet(ReflectionsFacet.class);
    Reflections reflections = facet.getReflections();

    //Returns all classes in the project (including its dependencies) annotated with @Entity
    Set<Class<?>> sbaClasses = reflections.getTypesAnnotatedWith(org.springframework.boot.autoconfigure
                                                                   .SpringBootApplication.class);

    if (sbaClasses==null || sbaClasses.isEmpty()) {
      return null;
    }
    return  sbaClasses.iterator().next().getPackage().getName();
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