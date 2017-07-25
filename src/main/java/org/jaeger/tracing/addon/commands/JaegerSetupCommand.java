package org.jaeger.tracing.addon.commands;

import java.util.Arrays;
import java.util.Map;
import javax.inject.Inject;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.projects.ProjectFactory;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.DependencyFacet;
import org.jboss.forge.addon.projects.ui.AbstractProjectCommand;
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

@SuppressWarnings("unused")
public class JaegerSetupCommand extends AbstractProjectCommand {

	private String [] technologies = {"jax-rs","spring-boot","vert.x"};

	@Inject
  private ProjectFactory projectFactory;

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

		techInput.setValueChoices(Arrays.asList(technologies));
		builder.add(techInput);
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

		Map map = context.getUIContext().getAttributeMap();
		System.out.println(map);

		Dependency dependency;

		switch (techInput.getValue()) {
			case "jax-rs":
				dependency = DependencyBuilder.create("io.opentracing.contrib")
				        .setArtifactId("opentracing-jaxrs2")
				        .setScopeType("provided");
				break;
			case "spring-boot":
				dependency = DependencyBuilder.create("io.opentracing.contrib")
				        .setArtifactId("opentracing-spring-web-autoconfigure")
				        .setScopeType("provided");
				break;
			case "vert.x":
				dependency = DependencyBuilder.create("io.opentracing.contrib")
		        .setArtifactId("tracing-vert-x TODO") // TODO
		        .setScopeType("provided");
				break;
			default:
				return Results.fail("Unknown selection " + techInput.getValue());
		}



		if (!getSelectedProject(context).getFacet(DependencyFacet.class)
				.hasDirectDependency(dependency))
		{
			dependencyInstaller.install(getSelectedProject(context), dependency);
		}

		dependency = DependencyBuilder.create("com.uber.jaeger")
        .setArtifactId("jaeger-core")
        .setVersion("0.20.5")
        .setScopeType("provided");

    if (!getSelectedProject(context).getFacet(DependencyFacet.class)
 				.hasDirectDependency(dependency))
 		{
 			dependencyInstaller.install(getSelectedProject(context), dependency);
 		}

		return Results.success("Jaeger Tracing was successfully setup for the current project!");
	}
}