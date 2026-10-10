# Copy is created automatically at .runtime/env.ps1 by Setup.ps1.
# Keep real credentials out of git.
$CloudEnvironment = @{
    ENVIRONMENT_ID = "developer-name"
    # Change this if another application already owns port 8080.
    GATEWAY_PORT = "8080"

    # Copy the exact host from Supabase Dashboard > Connect > Session pooler.
    # The pooler cluster index cannot be inferred safely from the project region.
    # Keep sslmode=require. The launcher adds each service's currentSchema.
    SUPABASE_JDBC_BASE = "jdbc:postgresql://<copy-session-pooler-host-from-dashboard>:5432/postgres?sslmode=require"
    SUPABASE_DB_USER = "postgres.PROJECT_REF"
    SUPABASE_DB_PASSWORD = "<supabase-database-password>"

    # Optional per-service users. Leave blank to use SUPABASE_DB_USER/PASSWORD.
    # Roles are created by config/supabase/init-schemas.sql and can be given
    # passwords later when strict database-level isolation is needed.
    SUPABASE_AUTH_USER = ""
    SUPABASE_AUTH_PASSWORD = ""
    SUPABASE_USER_USER = ""
    SUPABASE_USER_PASSWORD = ""
    SUPABASE_MEDIA_USER = ""
    SUPABASE_MEDIA_PASSWORD = ""
    SUPABASE_POST_USER = ""
    SUPABASE_POST_PASSWORD = ""
    SUPABASE_COMMENT_USER = ""
    SUPABASE_COMMENT_PASSWORD = ""
    SUPABASE_REACTION_USER = ""
    SUPABASE_REACTION_PASSWORD = ""
    SUPABASE_GROUP_USER = ""
    SUPABASE_GROUP_PASSWORD = ""
    SUPABASE_FANPAGE_USER = ""
    SUPABASE_FANPAGE_PASSWORD = ""
    SUPABASE_DATING_USER = ""
    SUPABASE_DATING_PASSWORD = ""
    SUPABASE_MODERATION_USER = ""
    SUPABASE_MODERATION_PASSWORD = ""

    # One Atlas free cluster can host all four logical databases.
    MONGODB_STORY_URI = "mongodb+srv://<user>:<password>@<cluster>/story_db?retryWrites=true&w=majority"
    MONGODB_REELS_URI = "mongodb+srv://<user>:<password>@<cluster>/reels_db?retryWrites=true&w=majority"
    MONGODB_CHAT_URI = "mongodb+srv://<user>:<password>@<cluster>/chat_db?retryWrites=true&w=majority"
    MONGODB_NOTIFICATION_URI = "mongodb+srv://<user>:<password>@<cluster>/notification_db?retryWrites=true&w=majority"

    # Aiven Valkey free service details.
    REDIS_HOST = "<aiven-valkey-host>"
    REDIS_PORT = "<aiven-valkey-port>"
    REDIS_USERNAME = "default"
    REDIS_PASSWORD = "<aiven-valkey-password>"
    REDIS_SSL_ENABLED = "true"

    # Supabase Storage S3 endpoint and public object prefix.
    STORAGE_S3_ENDPOINT = "https://PROJECT_REF.storage.supabase.co/storage/v1/s3"
    STORAGE_PUBLIC_ENDPOINT = "https://PROJECT_REF.supabase.co/storage/v1/object/public"
    STORAGE_ACCESS_KEY = "<supabase-s3-access-key>"
    STORAGE_SECRET_KEY = "<supabase-s3-secret-key>"
    STORAGE_REGION = "<supabase-project-region>"
    STORAGE_BUCKET = "social-media"

    JWT_SECRET = "__GENERATED_JWT_SECRET__"
    # Exact browser origin(s), comma separated. Set to the public HTTPS origin for deployment.
    CORS_ALLOWED_ORIGINS = "http://localhost:3001"
    # Leave empty for normal use. For first-admin setup only, supply at least
    # 32 random bytes and remove the value after bootstrapping over SSH/loopback.
    ADMIN_BOOTSTRAP_TOKEN = ""
    RATE_LIMIT_REPLENISH = "50"
    RATE_LIMIT_BURST = "100"
    RATE_LIMIT_AUTH_REPLENISH = "1"
    RATE_LIMIT_AUTH_BURST = "5"
    RATE_LIMIT_WS_REPLENISH = "5"
    RATE_LIMIT_WS_BURST = "10"
    JAVA_OPTS = "-Xms32m -Xmx384m -XX:+UseG1GC"

    # Values shown in the Grafana Cloud connection pages. Disable only while
    # initially diagnosing credentials; business services remain unchanged.
    GRAFANA_ENABLED = "true"
    GRAFANA_PROMETHEUS_URL = "<grafana-prometheus-remote-write-url>"
    GRAFANA_PROMETHEUS_USER = "<grafana-prometheus-instance-id>"
    GRAFANA_LOKI_URL = "<grafana-loki-push-url>"
    GRAFANA_LOKI_USER = "<grafana-loki-instance-id>"
    GRAFANA_OTLP_URL = "<grafana-otlp-endpoint>"
    GRAFANA_OTLP_USER = "<grafana-stack-instance-id>"
    GRAFANA_API_TOKEN = "<grafana-cloud-api-token>"
}
