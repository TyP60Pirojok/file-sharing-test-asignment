FROM openjdk:17-jdk-alpine

WORKDIR /app

# Копируем все файлы приложения
COPY index.html script.js FileServer.java ./

# Создаём директорию для загруженных файлов
RUN mkdir -p uploads

# Компилируем Java-код
RUN javac FileServer.java

# Открываем порт
EXPOSE 8080

# Запускаем сервер
CMD ["java", "FileServer"]