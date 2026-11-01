import java.net.*;
import java.sql.*;
import java.util.*;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import com.mongodb.client.*;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import org.bson.Document;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/** Read-only runtime probes. Never print exception messages or connection strings. */
public class CloudConnectionCheck {
    static String env(String name) { return System.getenv(name); }
    static boolean failed;
    static void result(String name, boolean ok, String detail) {
        System.out.println(name + "=" + (ok ? "PASS" : "FAIL") + " " + detail);
        failed |= !ok;
    }
    public static void main(String[] args) {
        DriverManager.setLoginTimeout(8);
        Map<String, Connection> connections = new HashMap<>();
        Set<String> rejected = new HashSet<>();
        try {
            for (int i = 0; i < Integer.parseInt(env("CHECK_SQL_COUNT")); i++) {
                String prefix = "CHECK_SQL_" + i + "_";
                String key = env(prefix + "URL") + "\n" + env(prefix + "USER") + "\n" + env(prefix + "PASSWORD");
                if (rejected.contains(key)) {
                    result(env(prefix + "NAME"), false, "shared credential already failed");
                    continue;
                }
                try {
                    Connection c = connections.get(key);
                    if (c == null) {
                        Properties p = new Properties();
                        p.setProperty("user", env(prefix + "USER"));
                        p.setProperty("password", env(prefix + "PASSWORD"));
                        p.setProperty("connectTimeout", "8");
                        p.setProperty("socketTimeout", "10");
                        c = DriverManager.getConnection(env(prefix + "URL"), p);
                        connections.put(key, c);
                    }
                    try (PreparedStatement s = c.prepareStatement("select exists(select 1 from pg_namespace where nspname = ?), has_schema_privilege(current_user, ?, 'USAGE'), has_schema_privilege(current_user, ?, 'CREATE')")) {
                        for (int n = 1; n <= 3; n++) s.setString(n, env(prefix + "SCHEMA"));
                        try (ResultSet r = s.executeQuery()) {
                            boolean ok = r.next() && r.getBoolean(1) && r.getBoolean(2) && r.getBoolean(3);
                            result(env(prefix + "NAME"), ok, "login/schema USAGE/CREATE");
                        }
                    }
                } catch (SQLException e) {
                    if (!connections.containsKey(key)) rejected.add(key);
                    result(env(prefix + "NAME"), false, "SQLSTATE=" + e.getSQLState());
                }
            }
        } finally {
            for (Connection c : connections.values()) try { c.close(); } catch (SQLException ignored) { }
        }
        if (Boolean.parseBoolean(env("CHECK_SUPABASE_ONLY"))) {
            if (failed) System.exit(1);
            return;
        }
        for (int i = 0; i < Integer.parseInt(env("CHECK_MONGO_COUNT")); i++) {
            String prefix = "CHECK_MONGO_" + i + "_";
            try {
                ConnectionString uri = new ConnectionString(env(prefix + "URI"));
                MongoClientSettings settings = MongoClientSettings.builder().applyConnectionString(uri)
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(8, java.util.concurrent.TimeUnit.SECONDS))
                    .applyToSocketSettings(b -> b.connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS).readTimeout(8, java.util.concurrent.TimeUnit.SECONDS))
                    .build();
                try (MongoClient c = MongoClients.create(settings)) {
                    try {
                        String db = uri.getDatabase();
                        if (db == null || !db.equals(env(prefix + "DB"))) throw new IllegalArgumentException();
                        c.getDatabase(db).runCommand(new Document("ping", 1));
                        c.getDatabase(db).listCollectionNames().first();
                        result(env(prefix + "NAME"), true, "login/read");
                    } catch (Exception e) {
                        Set<String> errors = new TreeSet<>();
                        for (var server : c.getClusterDescription().getServerDescriptions()) {
                            if (server.getException() != null) errors.add(exceptionTypes(server.getException()));
                        }
                        result(env(prefix + "NAME"), false, exceptionTypes(e)
                            + (errors.isEmpty() ? "" : " server-errors=" + String.join(",", errors)));
                    }
                }
            } catch (Exception e) { result(env(prefix + "NAME"), false, e.getClass().getSimpleName()); }
        }
        try {
            boolean tls = Boolean.parseBoolean(env("CHECK_REDIS_TLS"));
            Socket socket = tls ? SSLSocketFactory.getDefault().createSocket() : new Socket();
            try (Socket s = socket) {
                if (s instanceof javax.net.ssl.SSLSocket ssl) {
                    javax.net.ssl.SSLParameters parameters = ssl.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    ssl.setSSLParameters(parameters);
                }
                s.connect(new InetSocketAddress(env("CHECK_REDIS_HOST"), Integer.parseInt(env("CHECK_REDIS_PORT"))), 8000);
                s.setSoTimeout(8000);
                String user = env("CHECK_REDIS_USER");
                String password = env("CHECK_REDIS_PASSWORD");
                send(s, "AUTH", user == null || user.isBlank() ? "default" : user, password);
                BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                if (!"+OK".equals(reader.readLine())) throw new IOException();
                send(s, "PING");
                result("Valkey", "+PONG".equals(reader.readLine()), "AUTH/PING");
            }
        } catch (Exception e) { result("Valkey", false, e.getClass().getSimpleName()); }
        try (S3Client s3 = S3Client.builder().endpointOverride(URI.create(env("CHECK_S3_ENDPOINT")))
                .region(Region.of(env("CHECK_S3_REGION"))).forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(env("CHECK_S3_KEY"), env("CHECK_S3_SECRET"))))
                .overrideConfiguration(b -> b.apiCallTimeout(java.time.Duration.ofSeconds(15)))
                .build()) {
            s3.headBucket(HeadBucketRequest.builder().bucket(env("CHECK_S3_BUCKET")).build());
            result("Storage", true, "authenticated HeadBucket");
        } catch (Exception e) { result("Storage", false, e.getClass().getSimpleName()); }
        if (failed) System.exit(1);
    }
    static String exceptionTypes(Throwable error) {
        List<String> types = new ArrayList<>();
        for (int i = 0; error != null && i < 6; i++, error = error.getCause()) {
            types.add(error.getClass().getSimpleName());
            // Only emit fixed TLS categories; never expose raw exception messages.
            if (error instanceof javax.net.ssl.SSLException && error.getMessage() != null) {
                for (String category : List.of("internal_error", "handshake_failure", "protocol_version", "PKIX")) {
                    if (error.getMessage().contains(category)) { types.add(category); break; }
                }
            }
        }
        return String.join("/", types);
    }
    static void send(Socket s, String... values) throws IOException {
        OutputStream out = s.getOutputStream();
        out.write(("*" + values.length + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String value : values) {
            byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.write(("$" + bytes.length + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.write(bytes); out.write(new byte[]{13, 10});
        }
        out.flush();
    }
}
