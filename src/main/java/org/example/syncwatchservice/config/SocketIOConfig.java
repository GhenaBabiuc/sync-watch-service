package org.example.syncwatchservice.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "socketio")
@Data
public class SocketIOConfig {

    private String hostname = "0.0.0.0";
    private Integer port = 3001;
    private int bossThreads = 1;
    private int workerThreads = 100;
    private boolean allowCustomRequests = true;
    private int upgradeTimeout = 10000;
    private int pingTimeout = 60000;
    private int pingInterval = 25000;

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(this.hostname);
        config.setPort(this.port);
        config.setBossThreads(this.bossThreads);
        config.setWorkerThreads(this.workerThreads);
        config.setAllowCustomRequests(this.allowCustomRequests);
        config.setUpgradeTimeout(this.upgradeTimeout);
        config.setPingTimeout(this.pingTimeout);
        config.setPingInterval(this.pingInterval);

        config.setOrigin("*");

        return new SocketIOServer(config);
    }
}
