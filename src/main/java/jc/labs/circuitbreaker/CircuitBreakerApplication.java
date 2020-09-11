package jc.labs.circuitbreaker;

import java.time.Duration;
import java.util.Optional;

import org.reactivestreams.Publisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreaker;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class CircuitBreakerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CircuitBreakerApplication.class, args);
	}

	@Bean
	ReactiveCircuitBreakerFactory circuitBreakerFactory() {
		var factory = new ReactiveResilience4JCircuitBreakerFactory();
		factory.configureDefault( s -> new Resilience4JConfigBuilder(s)
		.timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build())
		.circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
		.build());
		return factory;
	}


}

@RestController
class FailingRestController {
	private final FailingService failingService;
	private final ReactiveCircuitBreaker circuitBreaker;

	FailingRestController(FailingService fs, ReactiveCircuitBreakerFactory cbf) {
		
		this.failingService = fs;
		this.circuitBreaker = cbf.create("greet"); //arbitrary string. configure cb using this string.
	}


	@GetMapping("/greet")
	Publisher<String> greet(@RequestParam Optional<String> name) {
		var results = this.failingService.greet(name);
		return this.circuitBreaker.run(results, throwable -> Mono.just("Hello world!"));
	}
}

@Service
class FailingService {

	Mono<String> greet(Optional<String> name) {
		var seconds = (long) (Math.random() * 10);
		return name
		.map( str -> Mono.just("Hello " + str + "! (in " + seconds + " seconds)"))
		.orElse(Mono.error(new NullPointerException()))
		.delayElement(Duration.ofSeconds(seconds));
	}

}
