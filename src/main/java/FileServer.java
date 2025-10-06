import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileServer {
    private static final String UPLOAD_DIR = "uploads";
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

        // Загрузка файла с multipart обработкой
        server.createContext("/upload", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String contentType = exchange.getRequestHeaders().getFirst("Content-type");
                    if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                        exchange.sendResponseHeaders(400, 0);
                        return;
                    }

                    // Получаем boundary из Content-Type
                    String boundary = extractBoundary(contentType);
                    if (boundary == null) {
                        exchange.sendResponseHeaders(400, 0);
                        return;
                    }

                    MultipartParser parser = new MultipartParser(boundary);
                    List<MultipartPart> parts = parser.parse(exchange.getRequestBody());

                    String fileId = UUID.randomUUID().toString();
                    String originalFileName = null;
                    Path filePath = null;

                    for (MultipartPart part : parts) {
                        if (part.isFile()) {
                            String contentDisposition = part.headers.get("Content-Disposition");
                            originalFileName = extractFileName(contentDisposition);

                            // Сохраняем с оригинальным расширением
                            if (originalFileName != null && originalFileName.contains(".")) {
                                String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
                                fileId += extension;
                            }

                            filePath = Paths.get(UPLOAD_DIR, fileId);
                            Files.write(filePath, part.content);
                            break;
                        }
                    }

                    if (filePath != null && Files.exists(filePath)) {
                        long fileSize = Files.size(filePath);
                        files.put(fileId, new FileInfo(fileId, originalFileName, System.currentTimeMillis(), fileSize));

                        String response = "http://localhost:8080/download/" + fileId;
                        exchange.sendResponseHeaders(200, response.length());
                        exchange.getResponseBody().write(response.getBytes());
                    } else {
                        exchange.sendResponseHeaders(400, 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, 0);
                }
            }
            exchange.close();
        });

        // Скачивание файла
        server.createContext("/download", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String fileId = path.substring(path.lastIndexOf("/") + 1);

            Path filePath = Paths.get(UPLOAD_DIR, fileId);
            if (Files.exists(filePath)) {
                FileInfo fileInfo = files.get(fileId);
                if (fileInfo != null) {
                    fileInfo.lastAccessed = System.currentTimeMillis();

                    // Устанавливаем оригинальное имя файла в заголовках
                    if (fileInfo.originalName != null) {
                        exchange.getResponseHeaders().set("Content-Disposition",
                                "attachment; filename=\"" + fileInfo.originalName + "\"");
                    }
                }

                exchange.sendResponseHeaders(200, Files.size(filePath));
                Files.copy(filePath, exchange.getResponseBody());
            } else {
                exchange.sendResponseHeaders(404, 0);
            }
            exchange.close();
        });

        // Статистика
        server.createContext("/stats", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                long totalSize = files.values().stream().mapToLong(f -> f.size).sum();
                int totalFiles = files.size();

                String json = String.format(
                        "{\"totalFiles\": %d, \"totalSize\": %d, \"files\": %s}",
                        totalFiles,
                        totalSize,
                        files.values().stream()
                                .map(f -> String.format(
                                        "{\"id\": \"%s\", \"originalName\": \"%s\", \"size\": %d, \"lastAccessed\": %d}",
                                        f.id, escapeJson(f.originalName != null ? f.originalName : "unknown"), f.size, f.lastAccessed))
                                .reduce("[", (a, b) -> a.equals("[") ? a + b : a + "," + b) + "]"
                );

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length());
                exchange.getResponseBody().write(json.getBytes());
            }
            exchange.close();
        });

        server.start();
        System.out.println("Server started on http://localhost:8080");

        // Очистка старых файлов (30 дней)
        new Timer().schedule(new TimerTask() {
            public void run() {
                cleanOldFiles();
            }
        }, 0, 24 * 60 * 60 * 1000);
    }

    static String extractBoundary(String contentType) {
        Pattern pattern = Pattern.compile("boundary=(.*)");
        Matcher matcher = pattern.matcher(contentType);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    static String extractFileName(String contentDisposition) {
        if (contentDisposition == null) return null;
        Pattern pattern = Pattern.compile("filename=\"(.*?)\"");
        Matcher matcher = pattern.matcher(contentDisposition);
        if (matcher.find()) {
            return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
        }
        return null;
    }

    static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    static void cleanOldFiles() {
        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;

        files.entrySet().removeIf(entry -> {
            FileInfo info = entry.getValue();
            if (now - info.lastAccessed > thirtyDaysMs) {
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
        String originalName;
        long lastAccessed;
        long size;

        FileInfo(String id, String originalName, long lastAccessed, long size) {
            this.id = id;
            this.originalName = originalName;
            this.lastAccessed = lastAccessed;
            this.size = size;
        }
    }

    // Multipart парсер
    static class MultipartParser {
        private final String boundary;
        private static final String BOUNDARY_PREFIX = "--";

        public MultipartParser(String boundary) {
            this.boundary = boundary;
        }

        public List<MultipartPart> parse(InputStream inputStream) throws IOException {
            List<MultipartPart> parts = new ArrayList<>();
            String currentBoundary = BOUNDARY_PREFIX + boundary;
            String endBoundary = currentBoundary + BOUNDARY_PREFIX;

            byte[] data = inputStream.readAllBytes();
            String text = new String(data);

            String[] partsData = text.split(Pattern.quote(currentBoundary));

            for (String partData : partsData) {
                if (partData.trim().isEmpty() || partData.contains(endBoundary)) {
                    continue;
                }

                int headerEnd = partData.indexOf("\r\n\r\n");
                if (headerEnd == -1) continue;

                String headersSection = partData.substring(0, headerEnd);
                byte[] content = partData.substring(headerEnd + 4).getBytes();

                if (content.length >= 2) {
                    content = Arrays.copyOf(content, content.length - 2);
                }

                MultipartPart part = new MultipartPart();
                part.headers = parseHeaders(headersSection);
                part.content = content;
                parts.add(part);
            }

            return parts;
        }

        private Map<String, String> parseHeaders(String headersSection) {
            Map<String, String> headers = new HashMap<>();
            String[] headerLines = headersSection.split("\r\n");

            for (String line : headerLines) {
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String key = line.substring(0, colonIndex).trim();
                    String value = line.substring(colonIndex + 1).trim();
                    headers.put(key, value);
                }
            }
            return headers;
        }
    }

    static class MultipartPart {
        Map<String, String> headers;
        byte[] content;

        boolean isFile() {
            String contentDisposition = headers.get("Content-Disposition");
            return contentDisposition != null && contentDisposition.contains("filename=");
        }
    }
}