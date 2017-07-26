package org.jaeger.tracing.addon.commands;

import java.io.File;
import javax.inject.Inject;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.parser.java.resources.JavaResource;
import org.jboss.forge.addon.parser.java.ui.AbstractJavaSourceCommand;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.facets.DependencyFacet;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.InputComponentFactory;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;

@SuppressWarnings("unused")
public class JaegerAddAction extends AbstractJavaSourceCommand<JavaClassSource> {

  @Inject
 	private ProjectFactory projectFactory;

  private UIInput<String> traceEntity;
  private UIInput<String> named;

	@Override
	public UICommandMetadata getMetadata(UIContext context) {
		return Metadata.forCommand(JaegerAddAction.class)
				.name("Jaeger: add traced action")
				.category(Categories.create("Tracing"));
	}



	@Override
	public void initializeUI(UIBuilder builder) throws Exception {

    InputComponentFactory inputFactory = builder.getInputComponentFactory();

    traceEntity = inputFactory.createInput("Entity to trace", String.class)
        .setLabel("Entity name").setRequired(true)
        .setDescription("The name of the entity to trace");

    builder.add(traceEntity);

    named = inputFactory.createInput("named", String.class)
        .setLabel("Span Name").setRequired(false)
        .setDescription("The name of the span to open (optional)");

    builder.add(named);

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
  protected String getType() {
    return null;  // TODO: Customise this generated block
  }

  @Override
  protected Class<JavaClassSource> getSourceType() {
    return JavaClassSource.class;
  }

  @Override
	public Result execute(UIExecutionContext context) throws Exception {

    Project project = getSelectedProject(context);

    String value = traceEntity.getValue();
    JavaClassSource source;
    File file = new File(value);

    JavaSourceFacet javaSourceFacet = project.getFacet(JavaSourceFacet.class);
    if (!value.contains(".")) {
      value = project.getFacet(JavaSourceFacet.class).getBasePackage() + "." + value;
    }
    JavaResource javaResource = javaSourceFacet.getJavaResource(value);
    if (!javaResource.exists()) {

      source = Roaster.create(JavaClassSource.class)
          .setPackage(project.getFacet(JavaSourceFacet.class).getBasePackage())
          .setName(traceEntity.getValue());
    } else {
      source = Roaster.parse(JavaClassSource.class, javaResource.getContents());
    }

    boolean isJaxRs = false;
    Dependency dependency = DependencyBuilder.create("io.opentracing.contrib")
        				        .setArtifactId("opentracing-jaxrs2");
    if (getSelectedProject(context).getFacet(DependencyFacet.class)
      				.hasDirectDependency(dependency))

    {
      isJaxRs = true;
    }

    if (isJaxRs) {
      source.addImport("javax.ws.rs.GET");
      source.addImport("javax.ws.rs.Path");
    }

    source.addImport("io.opentracing.ActiveSpan");
    source.addImport("javax.inject.Inject");

    source.addField().setName("tracer").setType("io.opentracing.Tracer")
    		 .addAnnotation("Inject");

    String spanName = named.getValue();
    if (spanName == null || spanName.equals("")) {
      spanName = "action";
    }

    MethodSource<JavaClassSource> action = source.addMethod();
    action.setName("action")
          .setPublic()
          .setReturnType("String")
          .setBody("try (ActiveSpan span = tracer.buildSpan(\"" + spanName + "\").startActive()) {\n" +
                   "\n" +
                   "   span.setTag(\"myTag\",\"myVal\");\n" +
                   "\n" +
                   "  System.out.println(\" TODO your code goes here.\");\n" +
                   "\n" +
                   "  return \"Success  \";\n" +
                   "    }");
    if (isJaxRs) {
      action.addAnnotation("GET");
      action.addAnnotation("Path").setLiteralValue("\"/" +spanName + "\"");
    }


    JavaSourceFacet facet = project.getFacet(JavaSourceFacet.class);
    facet.saveJavaSource(source);

		return Results
				.success("Command 'Jaeger: add traced action' successfully executed!");
	}

  @Override
	public void validate(UIValidationContext validator) {
		// super.validate(validator); // TODO: Customise this generated block
	}

}