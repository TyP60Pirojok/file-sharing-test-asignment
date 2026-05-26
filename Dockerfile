# Используем актуальный JDK 17 от Eclipse Temurin
FROM eclipse-temurin:17-jdk-alpine

# Создаёт и переходит в указанную папку
WORKDIR /app

# Создаём структуру, которую ожидает FileServer.java
RUN mkdir -p src/main/resources

# Копируем статические файлы из корня проекта в нужное место
COPY src ./src

# Копируем Java-сервер
COPY src/main/java/FileServer.java .

# Компилируем
RUN javac FileServer.java

# Папка для загруженных файлов
RUN mkdir uploads

# Слушаем порт 8080
EXPOSE 8080

# Команда по умолчанию при старте контейнера
CMD ["java", "FileServer"]