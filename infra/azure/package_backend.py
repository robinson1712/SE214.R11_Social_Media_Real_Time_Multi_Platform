"""Create a private, small source release; discover runnable Maven modules."""
import argparse
import io
import gzip
import hashlib
import json
import re
import subprocess
import tarfile
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

import yaml

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def required(cloud, key):
    value = str(cloud.get(key, ""))
    if not value or re.search(r"<[^>]+>|PROJECT_REF|__GENERATED_", value):
        raise ValueError(f"Missing credential/configuration: {key}")
    if "\n" in value or "\r" in value:
        raise ValueError(f"Multiline setting unsupported: {key}")
    return value


def jdbc_schema(base, schema):
    parts = urlsplit(base.removeprefix("jdbc:"))
    query = dict(parse_qsl(parts.query))
    query["currentSchema"] = schema
    return "jdbc:" + urlunsplit(parts._replace(query=urlencode(query)))


def discover(root, cloud, pages_origin):
    pom = ET.parse(root / "pom.xml").getroot()
    overrides_path = root / "infra/azure/service-overrides.json"
    overrides = json.loads(overrides_path.read_text()) if overrides_path.exists() else {}
    common = {
        "SPRING_PROFILES_ACTIVE": "cloud-free", "EUREKA_URI": "http://127.0.0.1:8761/eureka/",
        "CONFIG_SERVER_URI": "http://127.0.0.1:8888", "KAFKA_BOOTSTRAP_SERVERS": "127.0.0.1:9092",
        "ZIPKIN_ENDPOINT": "http://127.0.0.1:9411/api/v2/spans", "TRACING_SAMPLING_PROBABILITY": "0.1",
        "CORS_ALLOWED_ORIGINS": pages_origin, "TRUSTED_PROXY_IPS": "127.0.0.1,::1",
        "SERVER_ADDRESS": "127.0.0.1", "EUREKA_INSTANCE_HOSTNAME": "127.0.0.1",
        "EUREKA_INSTANCE_PREFER_IP_ADDRESS": "false", "JAVA_OPTS": required(cloud, "JAVA_OPTS"),
        "JWT_SECRET": required(cloud, "JWT_SECRET"),
    }
    for suffix in ("HOST", "PORT", "USERNAME", "PASSWORD", "SSL_ENABLED"):
        common["CLOUD_REDIS_" + suffix] = required(cloud, "REDIS_" + suffix)
    for key in cloud:
        if key.startswith("RATE_LIMIT_"):
            common[key] = str(cloud[key])
    services = []
    for entry in pom.findall("m:modules/m:module", NS):
        module = entry.text.strip()
        module_path = (root / module).resolve()
        if not module_path.is_relative_to(root.resolve()):
            raise ValueError("Maven module outside repository")
        child = ET.parse(module_path / "pom.xml").getroot()
        plugins = child.findall("m:build/m:plugins/m:plugin/m:artifactId", NS)
        if not any(node.text == "spring-boot-maven-plugin" for node in plugins):
            continue
        name = child.findtext("m:build/m:finalName", namespaces=NS) or child.findtext("m:artifactId", namespaces=NS)
        if not re.fullmatch(r"[a-z][a-z0-9-]{1,63}", name or "") or name in {"start","auto-update","kafka","alloy","cloud-check","deploy-agent"}:
            raise ValueError(f"Invalid service artifact: {module}")
        resources = module_path / "src/main/resources"
        app = yaml.safe_load((resources / "application.yml").read_text()) or {}
        profile_path = resources / "application-cloud-free.yml"
        profile = profile_path.read_text() if profile_path.exists() else ""
        extra = overrides.get(name, {})
        port = extra.get("port", app.get("server", {}).get("port"))
        if name == "api-gateway":
            port = int(cloud.get("GATEWAY_PORT", 18080))
        if not isinstance(port, int) or not 1024 <= port <= 65535:
            raise ValueError(f"Declare a numeric server.port or service override: {name}")
        short = name.removesuffix("-service").replace("-", "_")
        prefix = extra.get("env_prefix", short.upper())
        env = dict(common, SERVER_PORT=str(port))
        for key in extra.get('environment_keys', []):
            if not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
                raise ValueError(f'Invalid environment key for {name}')
            env[key] = required(cloud, key)
        store = extra.get("store", "postgres" if "CLOUD_POSTGRES_JDBC_URL" in profile else "mongo" if "CLOUD_MONGODB_URI" in profile else "")
        schema = extra.get("schema", "sma_" + short) if store == "postgres" else None
        if store == "postgres":
            if not re.fullmatch(r"[a-z][a-z0-9_]{1,62}", schema):
                raise ValueError(f"Invalid schema for {name}")
            if cloud.get(f"SUPABASE_{prefix}_USER") and not cloud.get(f"SUPABASE_{prefix}_PASSWORD"):
                raise ValueError(f"Missing credential/configuration: SUPABASE_{prefix}_PASSWORD")
            env.update({
                "CLOUD_POSTGRES_JDBC_URL": jdbc_schema(required(cloud, "SUPABASE_JDBC_BASE"), schema),
                "CLOUD_POSTGRES_USERNAME": cloud.get(f"SUPABASE_{prefix}_USER") or required(cloud, "SUPABASE_DB_USER"),
                "CLOUD_POSTGRES_PASSWORD": cloud.get(f"SUPABASE_{prefix}_PASSWORD") or required(cloud, "SUPABASE_DB_PASSWORD"),
                "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE": "0", "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE": "2",
                "SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT": "15000", "SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT": "5000",
                "SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT": "60000",
            })
        elif store == "mongo":
            env["CLOUD_MONGODB_URI"] = required(cloud, extra.get("mongo_key", f"MONGODB_{prefix}_URI"))
        elif store:
            raise ValueError(f"Unknown store for {name}")
        if name == "config-server":
            env["SPRING_PROFILES_ACTIVE"] = "native"
        if name == "auth-service":
            env["ADMIN_BOOTSTRAP_TOKEN"] = str(cloud.get("ADMIN_BOOTSTRAP_TOKEN", ""))
        if name == "media-service":
            for suffix in ("S3_ENDPOINT", "PUBLIC_ENDPOINT", "ACCESS_KEY", "SECRET_KEY", "REGION", "BUCKET"):
                env["CLOUD_STORAGE_" + suffix] = required(cloud, "STORAGE_" + suffix)
        stage = extra.get("stage", {"eureka-server": 10, "config-server": 20, "api-gateway": 40}.get(name, 30))
        if not isinstance(stage, int):
            raise ValueError(f"Invalid startup stage: {name}")
        services.append({"name": name, "module": module, "port": port, "stage": stage, "schema": schema, "env": env})
    if len({item["name"] for item in services}) != len(services) or len({item["port"] for item in services}) != len(services):
        raise ValueError("Duplicate service name or port")
    if not {"eureka-server", "config-server", "api-gateway"}.issubset({item["name"] for item in services}):
        raise ValueError("Missing core infrastructure modules")
    return sorted(services, key=lambda item: (item["stage"], item["name"]))


def backend_digest(source, manifest):
    digest = hashlib.sha256()
    with tarfile.open(fileobj=io.BytesIO(source)) as archive:
        for member in sorted(archive.getmembers(), key=lambda item: item.name):
            if member.isfile() and not member.name.endswith('.md'):
                digest.update(member.name.encode() + b'\0')
                digest.update(archive.extractfile(member).read())
    settings = {key:value for key,value in manifest.items() if key not in {'revision','backend_digest'}}
    digest.update(json.dumps(settings, sort_keys=True).encode())
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cloud-settings", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--backend-host", required=True)
    parser.add_argument("--pages-origin", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.revision):
        raise ValueError("Expected a full commit SHA")
    if not re.fullmatch(r"[a-z0-9.-]+", args.backend_host):
        raise ValueError("Invalid backend hostname")
    if not re.fullmatch(r"https://[a-z0-9.-]+", args.pages_origin):
        raise ValueError("Invalid Pages HTTPS origin")
    root = Path.cwd()
    cloud = json.loads(Path(args.cloud_settings).read_text(encoding="utf-8-sig"))
    manifest = {
        "revision": args.revision, "backend_host": args.backend_host,
        "services": discover(root, cloud, args.pages_origin),
        "database_admin": {"url": required(cloud, "SUPABASE_JDBC_BASE"), "user": required(cloud, "SUPABASE_DB_USER"), "password": required(cloud, "SUPABASE_DB_PASSWORD")},
        "alloy_env": {key: str(value) for key, value in cloud.items() if key.startswith("GRAFANA_")},
    }
    source = subprocess.check_output(["git", "archive", "--format=tar", args.revision, "pom.xml", "common", "services", "infra", "config-repo"])
    manifest['backend_digest'] = backend_digest(source, manifest)
    Path(args.output + '.digest').write_text(manifest['backend_digest'])
    with open(args.output, 'wb') as output, gzip.GzipFile(fileobj=output, mode='wb', mtime=0, filename='') as compressed:
        with tarfile.open(fileobj=compressed, mode='w') as archive:
            for filename, data in (("source.tar", source), ("deployment.json", json.dumps(manifest, sort_keys=True).encode())):
                info = tarfile.TarInfo(filename)
                info.size, info.mode = len(data), 0o600
                archive.addfile(info, io.BytesIO(data))
    print(f"Private release packaged: {len(manifest['services'])} services, revision {args.revision}")


if __name__ == "__main__":
    main()
