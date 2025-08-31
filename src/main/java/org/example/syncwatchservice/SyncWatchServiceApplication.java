package org.example.syncwatchservice;

import org.example.syncwatchservice.service.SocketIOService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SyncWatchServiceApplication implements CommandLineRunner {

    @Autowired
    private SocketIOService socketIOService;

    public static void main(String[] args) {
        SpringApplication.run(SyncWatchServiceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        socketIOService.startServer();

        // Добавляем shutdown hook для корректного закрытия сервера
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Остановка Socket.IO сервера...");
            socketIOService.stopServer();
        }));

        System.out.println("Сервер запущен на http://localhost:3000");
        System.out.println("Socket.IO сервер запущен на порту 3001");
    }
}
