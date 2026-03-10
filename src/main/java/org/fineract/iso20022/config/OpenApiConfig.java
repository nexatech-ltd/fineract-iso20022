package org.fineract.iso20022.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI iso20022OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fineract ISO 20022 Adapter API")
                        .description("Full-featured ISO 20022 message adapter for Apache Fineract. "
                                + "Supports pain.001, pain.002, pacs.008, pacs.002, camt.053, camt.054 messages.")
                        .version("1.0.0")
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0"))
                        .contact(new Contact().name("Fineract ISO 20022 Adapter").url("https://github.com/fineract")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local"),
                        new Server().url("http://adapter:8081").description("Docker")));
    }
}
