# Используем актуальный JDK 17 от Eclipse Temurin
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Создаём структуру, которую ожидает FileServer.java
RUN mkdir -p src/main/resources

# Копируем статические файлы из корня проекта в нужное место
COPY index.html script.js src/main/resources/

# Копируем Java-сервер
COPY FileServer.java .

# Компилируем
RUN javac FileServer.java

# Папка для загруженных файлов
RUN mkdir uploads

EXPOSE 8080

CMD ["java", "FileServer"]