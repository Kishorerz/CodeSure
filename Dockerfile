# ==========================================
# Stage 1: Build React Frontend
# ==========================================
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
# Ensure production API calls use relative paths
ENV VITE_API_BASE_URL=""
RUN npm run build

# ==========================================
# Stage 2: Build Spring Boot Backend
# ==========================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /app/backend

# Cache Maven dependencies
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -B

# Copy backend source code
COPY backend/src ./src

# Copy built frontend into Spring Boot static resources directory
COPY --from=frontend-builder /app/frontend/dist ./src/main/resources/static/

# Build production jar
RUN mvn clean package -DskipTests

# ==========================================
# Stage 3: Minimal Production JRE Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S codesure && adduser -S codesure -G codesure
USER codesure

# Copy compiled jar from backend builder stage
COPY --from=backend-builder /app/backend/target/*.jar /app/app.jar

# Render assigns dynamic port via $PORT (defaults to 10000 or 8080)
ENV PORT=10000
EXPOSE 8080 10000

# Health check compatible with Render
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT}/api/health || exit 1

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-10000} -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]
