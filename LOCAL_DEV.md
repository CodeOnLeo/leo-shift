# Local Development

## Prerequisites

- **JDK 21** — Gradle 8.10.2가 Java 25 이상에서 빌드 스크립트를 컴파일하지 못한다.
  더 최신 JDK가 기본이라면 실행 시 지정해야 한다.

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
  ```

- Docker 런타임 (Docker Desktop, Colima, Podman 등)

  테스트가 Testcontainers로 진짜 PostgreSQL을 띄운다. 소켓 경로는 `build.gradle`이
  활성 docker context에서 자동으로 읽으므로 별도 설정이 필요 없다.

## Run Locally

```bash
./scripts/run-local.sh
```

Then open:

```text
http://localhost:8080
```

The script starts a local PostgreSQL container and runs Spring Boot with the `local` profile.
Gradle caches are written to the project-local `.gradle-home` directory unless `GRADLE_USER_HOME` is already set.

## Login

Use email signup/login on `/login.html`. Google OAuth is not configured for local by default.

## Optional Local Overrides

Create `.env.local` if you need to override the defaults:

```bash
LOCAL_DATABASE_URL=jdbc:postgresql://localhost:5432/leo_shift
LOCAL_DATABASE_USERNAME=leo_shift
LOCAL_DATABASE_PASSWORD=leo_shift
LOCAL_JWT_SECRET=replace-with-a-local-secret
LOCAL_GOOGLE_CLIENT_ID=your-local-google-client-id
LOCAL_GOOGLE_CLIENT_SECRET=your-local-google-client-secret
```

## Database

Start only the database:

```bash
docker compose up -d postgres
```

Stop it:

```bash
docker compose down
```

Reset local data:

```bash
docker compose down -v
```

If your Docker installation uses the older standalone binary, replace `docker compose` with `docker-compose`.

The app runs Flyway migrations on startup in the `local` profile.
