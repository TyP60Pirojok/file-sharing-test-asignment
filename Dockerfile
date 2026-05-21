FROM openjdk:17-jdk-slim
LABEL authors="Pakhomov"

WORKDIR /app

# Копируем исходники и статику
COPY FileServer.java .
COPY index.html script.js ./
RUN mkdir -p src/main/resources && cp index.html script.js src/main/resources/

# Компилируем
RUN javac FileServer.java

# Создаём папку для загрузок
RUN mkdir -p uploads

EXPOSE 8080

CMD ["java", "FileServer"]