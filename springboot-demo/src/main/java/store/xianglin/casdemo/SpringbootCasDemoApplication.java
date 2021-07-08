package store.xianglin.casdemo;

import org.jasig.cas.client.boot.configuration.EnableCasClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableCasClient
public class SpringbootCasDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootCasDemoApplication.class, args);
    }

}
