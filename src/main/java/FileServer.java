import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

public class FileServer {
    private static final String UPLOAD_DIR = "uploads"; // Папка с загруженными на сервер файлами внутри проекта
    private static final Map<String, FileInfo> files = new HashMap<>();

    public static void main(String[] args) throws IOException {
        Files.createDirectories(Paths.get(UPLOAD_DIR));

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/")) path = "/index.html";

                Path file = Paths.get("src/main/resources/" + path);
                if (Files.exists(file)) {
                    exchange.sendResponseHeaders(200, Files.size(file));
                    Files.copy(file, exchange.getResponseBody());
                } else {
                    exchange.sendResponseHeaders(404, 0);
                }
            }
            exchange.close();
        });

        // Загрузка файла
        server.createContext("/upload", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String fileId = UUID.randomUUID().toString();
                Path filePath = Paths.get(UPLOAD_DIR, fileId);

                // Сохраняем файл
                exchange.getRequestBody().transferTo(Files.newOutputStream(filePath));

                // Сохраняем метаданные
                files.put(fileId, new FileInfo(fileId, System.currentTimeMillis()));

                // Создание временной ссылки на скачивание
                String response = "http://localhost:8080/download/" + fileId;
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
            }
            exchange.close();
        });

        // Скачивание файла
        server.createContext("/download", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String fileId = path.substring(path.lastIndexOf("/") + 1);

            Path filePath = Paths.get(UPLOAD_DIR, fileId);
            if (Files.exists(filePath)) {
                // Обновляем время доступа
                files.get(fileId).lastAccessed = System.currentTimeMillis();

                exchange.sendResponseHeaders(200, Files.size(filePath));
                Files.copy(filePath, exchange.getResponseBody());
            } else {
                exchange.sendResponseHeaders(404, 0);
            }
            exchange.close();
        });

        server.start();
        System.out.println("Server started on http://localhost:8080");

        // Очистка старых файлов (например, 10 секунд для демонстрации)
        new Timer().schedule(new TimerTask() {
            public void run() {
                cleanOldFiles();
            }
        }, 10000, 10000);
    }

    static void cleanOldFiles() {
        long now = System.currentTimeMillis();
        files.entrySet().removeIf(entry -> {
            FileInfo info = entry.getValue();
            if (now - info.lastAccessed > 30000) {
                try {
                    Files.deleteIfExists(Paths.get(UPLOAD_DIR, info.id));
                    System.out.println("Deleted old file: " + info.id);
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return false;
        });
    }

    static class FileInfo {
        String id;
        long lastAccessed;

        FileInfo(String id, long lastAccessed) {
            this.id = id;
            this.lastAccessed = lastAccessed;
        }
    }
}