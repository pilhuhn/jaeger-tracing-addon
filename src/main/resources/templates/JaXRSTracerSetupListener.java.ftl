
import com.uber.jaeger.Configuration;
import com.uber.jaeger.samplers.ProbabilisticSampler;
import com.uber.jaeger.senders.HttpSender;
import io.opentracing.util.GlobalTracer;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.annotation.WebListener;
import javax.servlet.ServletContextEvent;
import io.opentracing.Tracer;

@WebListener
public class TracerSetupListener
		implements
			javax.servlet.ServletContextListener {

	@Inject
	Tracer tracer;

	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
		GlobalTracer.register(tracer);
	}

	@Override
	public void contextDestroyed(
			javax.servlet.ServletContextEvent servletContextEvent) {
	}

	@Produces
	@Singleton
	static Tracer jaegerTracer() {
        String jaegerHttpUrl = System.getenv("JAEGER_HTTP_QUERY_URL");
        if (jaegerHttpUrl == null || jaegerHttpUrl.isEmpty()) {
            jaegerHttpUrl = "http://localhost:14268/api/traces";
        }
        HttpSender httpSender = new HttpSender(jaegerHttpUrl, 0);

		return new Configuration("wildfly-swarm",
				new Configuration.SamplerConfiguration(
						ProbabilisticSampler.TYPE, 1),
				new Configuration.ReporterConfiguration(httpSender)).getTracer();
	}
}