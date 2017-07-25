package org.jaeger.tracing.addon.commands;

import javax.inject.Inject;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.jboss.forge.addon.parser.java.facets.JavaSourceFacet;
import org.jboss.forge.addon.parser.java.resources.JavaResource;
import org.jboss.forge.addon.parser.java.ui.AbstractJavaSourceCommand;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.input.InputComponentFactory;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaInterface;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MemberSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.util.Types;

@SuppressWarnings("unused")
public class JaegerAddConfigCommand
		extends
			AbstractJavaSourceCommand<JavaClassSource> {

	@Inject
	private ProjectFactory projectFactory;

	@Inject
	@WithAttributes(label = "Class for Config", required = true)
	private UIInput<JavaResource> jaegerConfigClass;

  // Mandatory fields
  private UIInput<String> targetPackage;
  private UIInput<String> named;

	@Override
	public UICommandMetadata getMetadata(UIContext context) {
		return Metadata.forCommand(JaegerAddConfigCommand.class).name(
				"Jaeger: add config");
	}

	@Override
	protected String getType() {
		return "Tracer config class";
	}

	@Override
	protected Class<JavaClassSource> getSourceType() {
		return JavaClassSource.class;
	}

	@Override
	protected boolean isProjectRequired() {
		return true;
	}

	@Override
	public boolean isEnabled(UIContext context) {
		// TODO check for dependency in POM -- but which?
		// Perhaps we write a configuration in setup and check on this?
		return super.isEnabled(context); // TODO: Customise this generated block
	}

	@Override
	public void initializeUI(UIBuilder builder) throws Exception {

    InputComponentFactory inputFactory = builder.getInputComponentFactory();

    named = inputFactory.createInput("named", String.class)
        .setLabel("Type Name").setRequired(true)
        .setDescription("The type name");

    named.addValidator((context) -> {
      if (!Types.isSimpleName(named.getValue()))
        context.addValidationError(named, "Invalid java type name.");
    });


    builder.add(named);

  }

	@Override
	public Result execute(UIExecutionContext context) throws Exception {

    Project project = getSelectedProject(context);
    JavaClassSource source = Roaster.create(JavaClassSource.class)
                  .setPackage(project.getFacet(JavaSourceFacet.class).getBasePackage())
                  .setName(named.getValue());

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


		return Results
				.success("Command 'Jaeger: add config' successfully executed!");
	}

  @Override
	public void validate(UIValidationContext validator) {
	  // We override this as we would get a NPE otherwise
		// super.validate(validator); // TODO: Customise this generated block
	}


	@Override
	protected ProjectFactory getProjectFactory() {
		return projectFactory;
	}
}