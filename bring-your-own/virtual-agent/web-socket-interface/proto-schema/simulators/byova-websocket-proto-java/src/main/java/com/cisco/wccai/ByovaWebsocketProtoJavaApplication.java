package com.cisco.wccai;

import com.cisco.wccai.auth.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class ByovaWebsocketProtoJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ByovaWebsocketProtoJavaApplication.class, args);
    }

}
