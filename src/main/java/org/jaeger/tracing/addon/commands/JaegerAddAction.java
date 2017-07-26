package org.jaeger.tracing.addon.commands;

import java.io.File;
import javax.inject.Inject;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.parser.java.resources.JavaResource;
import org.jboss.forge.addon.parser.java.ui.AbstractJavaSourceCommand;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.ui.command.AbstractUICommand;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.facets.HintsFacet;
import org.jboss.forge.addon.ui.hints.InputType;
import org.jboss.forge.addon.ui.input.InputComponentFactory;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.util.Types;

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

    source.addImport("io.opentracing.ActiveSpan");
    source.addImport("javax.inject.Inject");

    source.addField().setName("tracer").setType("io.opentracing.Tracer")
    		 .addAnnotation("Inject");

    MethodSource<JavaClassSource> action = source.addMethod();
    action.setName("action")
          .setPublic()
          .setReturnTypeVoid()
          .setBody("try (ActiveSpan span = tracer.buildSpan(\"action\").startActive()) {\n" +
                   "\n" +
                   "   span.setTag(\"myTag\",\"myVal\");\n" +
                   "\n" +
                   "  System.out.println(\" TODO your code goes here.\");\n" +
                   "\n" +
                   "    }");


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