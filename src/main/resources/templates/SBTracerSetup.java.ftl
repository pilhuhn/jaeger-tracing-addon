
import com.uber.jaeger.samplers.ProbabilisticSampler;
import com.uber.jaeger.senders.HttpSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.opentracing.Tracer;

@SuppressWarnings("unused")
@Configuration
public class TracerSetup {

	@Bean
	public Tracer jaegerTracer() {
		String jaegerHttpUrl = System.getenv("JAEGER_HTTP_QUERY_URL");
		if (jaegerHttpUrl==null || jaegerHttpUrl.isEmpty()) {
			jaegerHttpUrl="http://localhost:14268/api/traces";
		}
		HttpSender httpSender = new HttpSender(jaegerHttpUrl,0);
		return new com.uber.jaeger.Configuration("spring-boot",
				new com.uber.jaeger.Configuration.SamplerConfiguration(
						ProbabilisticSampler.TYPE, 1),
				new com.uber.jaeger.Configuration.ReporterConfiguration(httpSender))
				.getTracer();
	}
}