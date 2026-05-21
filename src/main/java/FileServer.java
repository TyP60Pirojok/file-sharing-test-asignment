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

        // Статические файлы
        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if (!"GET".equals(method)) {
                exchange.sendResponseHeaders(405, 0);
                exchange.close();
                return;
            }
            if (path.equals("/")) path = "/index.html";
            Path file = Paths.get("src/main/resources" + path);
            if (Files.exists(file) && !Files.isDirectory(file)) {
                exchange.sendResponseHeaders(200, Files.size(file));
                Files.copy(file, exchange.getResponseBody());
            } else {
                exchange.sendResponseHeaders(404, 0);
            }
            exchange.close();
        });

        // Загрузка
        server.createContext("/upload", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String contentType = exchange.getRequestHeaders().getFirst("Content-type");
                    if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                        exchange.sendResponseHeaders(400, 0);
                        return;
                    }
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
                            originalFileName = extractFileName(part.headers.get("Content-Disposition"));
                            String extension = "";
                            if (originalFileName != null && originalFileName.contains(".")) {
                                extension = originalFileName.substring(originalFileName.lastIndexOf("."));
                            }
                            filePath = Paths.get(UPLOAD_DIR, fileId + extension);
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

        // Скачивание
        server.createContext("/download", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String fileId = path.substring(path.lastIndexOf("/") + 1);
            Path uploadDir = Paths.get(UPLOAD_DIR);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(uploadDir)) {
                Path found = null;
                for (Path entry : stream) {
                    if (entry.getFileName().toString().startsWith(fileId)) {
                        found = entry;
                        break;
                    }
                }
                if (found != null && Files.exists(found)) {
                    FileInfo info = files.get(fileId);
                    if (info != null && info.originalName != null) {
                        exchange.getResponseHeaders().set("Content-Disposition",
                                "attachment; filename=\"" + info.originalName + "\"");
                    }
                    exchange.sendResponseHeaders(200, Files.size(found));
                    Files.copy(found, exchange.getResponseBody());
                } else {
                    exchange.sendResponseHeaders(404, 0);
                }
            } catch (IOException e) {
                exchange.sendResponseHeaders(500, 0);
            }
            exchange.close();
        });

        // Статистика (без изменений)
        server.createContext("/stats", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                long totalSize = files.values().stream().mapToLong(f -> f.size).sum();
                int totalFiles = files.size();
                StringBuilder sb = new StringBuilder();
                sb.append("{\"totalFiles\":").append(totalFiles)
                        .append(",\"totalSize\":").append(totalSize)
                        .append(",\"files\":[");
                boolean first = true;
                for (FileInfo f : files.values()) {
                    if (!first) sb.append(",");
                    sb.append("{\"id\":\"").append(escapeJson(f.id))
                            .append("\",\"originalName\":\"").append(escapeJson(f.originalName))
                            .append("\",\"size\":").append(f.size)
                            .append(",\"lastAccessed\":").append(f.lastAccessed).append("}");
                    first = false;
                }
                sb.append("]}");
                String json = sb.toString();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length());
                exchange.getResponseBody().write(json.getBytes());
            }
            exchange.close();
        });

        server.start();
        System.out.println("Server started on http://localhost:8080");

        new Timer().schedule(new TimerTask() {
            public void run() { cleanOldFiles(); }
        }, 0, 24 * 60 * 60 * 1000);
    }

    static String extractBoundary(String contentType) {
        Pattern pattern = Pattern.compile("boundary=(.*)");
        Matcher matcher = pattern.matcher(contentType);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    static String extractFileName(String contentDisposition) {
        if (contentDisposition == null) return null;
        Pattern pattern = Pattern.compile("filename=\"(.*?)\"");
        Matcher matcher = pattern.matcher(contentDisposition);
        return matcher.find() ? URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8) : null;
    }

    static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static void cleanOldFiles() {
        long now = System.currentTimeMillis();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        files.entrySet().removeIf(entry -> {
            FileInfo info = entry.getValue();
            if (now - info.lastAccessed > thirtyDaysMs) {
                try {
                    Path uploadDir = Paths.get(UPLOAD_DIR);
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(uploadDir, p -> p.getFileName().toString().startsWith(info.id))) {
                        for (Path p : stream) Files.deleteIfExists(p);
                    }
                    System.out.println("Deleted old file: " + info.id);
                    return true;
                } catch (IOException e) { e.printStackTrace(); }
            }
            return false;
        });
    }

    static class FileInfo {
        String id, originalName;
        long lastAccessed, size;
        FileInfo(String id, String originalName, long lastAccessed, long size) {
            this.id = id; this.originalName = originalName; this.lastAccessed = lastAccessed; this.size = size;
        }
    }

    static class MultipartParser {
        private final byte[] boundaryBytes;
        public MultipartParser(String boundary) { this.boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.US_ASCII); }
        public List<MultipartPart> parse(InputStream is) throws IOException {
            List<MultipartPart> parts = new ArrayList<>();
            byte[] data = is.readAllBytes();
            int start = 0;
            while (true) {
                int idx = indexOf(data, boundaryBytes, start);
                if (idx == -1) break;
                int partStart = idx + boundaryBytes.length;
                if (partStart >= data.length) break;
                int nextBoundary = indexOf(data, boundaryBytes, partStart);
                if (nextBoundary == -1) break;
                int partEnd = nextBoundary - 2; // убираем \r\n перед следующим boundary
                if (partEnd <= partStart) { start = nextBoundary; continue; }
                byte[] partData = Arrays.copyOfRange(data, partStart, partEnd);
                int headerEndPos = indexOf(partData, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
                if (headerEndPos > 0) {
                    byte[] headersRaw = Arrays.copyOfRange(partData, 0, headerEndPos);
                    byte[] content = Arrays.copyOfRange(partData, headerEndPos + 4, partData.length);
                    Map<String, String> headers = new HashMap<>();
                    for (String line : new String(headersRaw, StandardCharsets.US_ASCII).split("\r\n")) {
                        int colon = line.indexOf(':');
                        if (colon > 0) headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                    }
                    MultipartPart part = new MultipartPart();
                    part.headers = headers;
                    part.content = content;
                    parts.add(part);
                }
                start = nextBoundary;
            }
            return parts;
        }
        private int indexOf(byte[] array, byte[] target, int start) {
            outer: for (int i = start; i <= array.length - target.length; i++) {
                for (int j = 0; j < target.length; j++) if (array[i + j] != target[j]) continue outer;
                return i;
            }
            return -1;
        }
    }

    static class MultipartPart {
        Map<String, String> headers;
        byte[] content;
        boolean isFile() {
            String cd = headers.get("Content-Disposition");
            return cd != null && cd.contains("filename=");
        }
    }
}