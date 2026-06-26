# Docker & Kubernetes Guide

This guide covers Docker and Kubernetes standards for all projects. The local runtime is **OrbStack** (not Docker Desktop). Be aware of OrbStack-specific behaviors where noted.

---

## OrbStack — Runtime Context

OrbStack is the local Docker and Kubernetes runtime on this machine. It replaces Docker Desktop and is compatible with all standard `docker`, `docker compose`, and `kubectl` commands.

### Key OrbStack behaviors to know

- Docker context is automatically set to `orbstack` — no manual `docker context use` needed.
- Credentials are stored in macOS Keychain (`osxkeychain`), not Docker Desktop's credential store.
- Engine config is edited via `orb config docker` (modifies `daemon.json`).
- Built Docker images are immediately available in Kubernetes pods — no registry push needed for local development.
- Starts in ~2 seconds. Low CPU, memory, and battery usage by design.

### Troubleshooting: Docker socket unreachable / `orb start` timeout

**Symptom:** `docker run` fails with `failed to connect to the docker API at unix:///Users/<user>/.orbstack/run/docker.sock`, and running `orb start` returns `timed out waiting for VM to start`.

**Cause:** OrbStack was already running. `orb start` waits for a "freshly started" signal that never comes when the VM is already active, causing a timeout. The Docker socket failure is usually a transient connectivity hiccup (race condition during an OrbStack internal event), not a true daemon crash.

**Fix:** Always verify state before acting:

```bash
orb status    # if "Running", the daemon is up
docker info   # if this responds, Docker is accessible — wait a few seconds and retry
```

Only run `orb restart` if `orb status` shows the VM is not running or `docker info` keeps failing after ~10 seconds.

### OrbStack CLI (`orb`)

```bash
# Start / stop OrbStack or a specific machine
orb start
orb stop
orb restart

# Kubernetes cluster lifecycle
orb start k8s
orb stop k8s
orb restart k8s
orb delete k8s

# Check status
orb status

# Edit Docker engine config (daemon.json)
orb config docker

# Edit OrbStack settings
orb config set <key> <value>

# Migrate data from Docker Desktop
orb migrate

# Debug a running container with extra tools
orb debug <container>

# Copy files to/from Linux machines
orb push <local-path> <machine>:<remote-path>
orb pull <machine>:<remote-path> <local-path>
```

---

## Docker

### Dockerfile Standards

- **Always use multi-stage builds** to minimize final image size.
- **Never run as root** in the final stage. Create a non-root user.
- Pin base image versions — never use `:latest` in production Dockerfiles.
- Use `.dockerignore` to exclude `node_modules`, `.git`, build artifacts, and secrets.

```dockerfile
# --- Build stage ---
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

# --- Final stage ---
FROM node:20-alpine AS runner
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /app/node_modules ./node_modules
COPY . .
USER appuser
EXPOSE 3000
CMD ["node", "server.js"]
```

### Key Docker Commands

```bash
# Build an image
docker build -t myapp:dev .

# Build for a specific platform (Apple Silicon → x86 emulation via Rosetta)
docker build --platform linux/amd64 -t myapp:dev .

# Set default platform for x86-first projects
export DOCKER_DEFAULT_PLATFORM=linux/amd64

# Run a container
docker run -d --name myapp -p 3000:3000 myapp:dev

# Run and remove after exit
docker run --rm -it myapp:dev sh

# View logs
docker logs -f myapp

# Execute a command inside a running container
docker exec -it myapp sh

# Stop and remove
docker stop myapp && docker rm myapp

# Remove all stopped containers, unused images, networks, build cache
docker system prune -af
```

### Docker Compose

```bash
# Start all services (detached)
docker compose up -d

# Start and force rebuild
docker compose up -d --build

# Stop and remove containers (keep volumes)
docker compose down

# Stop and remove containers + volumes
docker compose down -v

# View logs for all services
docker compose logs -f

# View logs for a specific service
docker compose logs -f api

# Run a one-off command in a service
docker compose run --rm api sh

# Scale a service
docker compose up -d --scale worker=3
```

### Networking (OrbStack)

**Primary approach — always use explicit port binding:**

```bash
docker run -d --name myapp -p 8080:80 nginx:alpine
# Access at: http://localhost:8080
```

Always pass `-p <host-port>:<container-port>` and access via `localhost:<host-port>`. This is reliable and works consistently.

**`.orb.local` domains — secondary, unreliable:**

OrbStack advertises automatic domains (`<container-name>.orb.local`) via mDNS, but in practice the DNS may resolve to a stale or incorrect IP that has no route on the host. Do not rely on `.orb.local` as the primary access method.

Symptoms of broken `.orb.local`: DNS resolves but `ping` shows "No route to host". Root cause: OrbStack mDNS advertises an IP from a different subnet than the container's actual network interface.

If you need to investigate: `dns-sd -G v4 <name>.orb.local` shows what IP mDNS is advertising; `docker inspect <name>` shows the actual container IP. A mismatch means `.orb.local` won't work.

**Other networking:**

- Host networking: use `host.docker.internal` to reach the Mac host from inside a container.
- SSH agent forwarding: mount `/run/host-services/ssh-auth.sock` as `SSH_AUTH_SOCK` inside the container.

```yaml
# docker-compose.yml — SSH agent forwarding example
services:
  app:
    volumes:
      - /run/host-services/ssh-auth.sock:/ssh-auth.sock
    environment:
      - SSH_AUTH_SOCK=/ssh-auth.sock
```

### Volumes and Bind Mounts

```bash
# Create a named volume
docker volume create mydata

# List volumes
docker volume ls

# Inspect volume (find mount path on Mac via OrbStack)
docker volume inspect mydata

# Remove unused volumes
docker volume prune
```

- Bind mounts map Mac directories into containers. Use relative paths in `docker-compose.yml`.
- Named volumes persist across container restarts. Prefer them over bind mounts for database data.

### Security Rules

- Never run containers as root in production.
- Never bake secrets into images. Use environment variables or secret managers.
- Avoid exposing the Docker socket (`/var/run/docker.sock`) unless strictly necessary.
- Use `--read-only` flag where possible to prevent filesystem writes.
- Scan images before pushing: `docker scout cves myapp:dev`

---

## Kubernetes

### OrbStack Kubernetes Context

OrbStack provides a single-node local Kubernetes cluster. Enable it in OrbStack settings or via CLI.

```bash
# Start the cluster
orb start k8s

# OrbStack sets the kubectl context automatically
kubectl config current-context   # → orbstack

# Switch back to orbstack context if changed
kubectl config use-context orbstack
```

### Image Pull Policy for Local Development

Built Docker images are immediately available in pods — no registry push needed.

```yaml
# Always set this for local dev to avoid pull attempts
spec:
  containers:
    - name: myapp
      image: myapp:dev        # use a non-:latest tag
      imagePullPolicy: IfNotPresent
```

### Key kubectl Commands

```bash
# Cluster info
kubectl cluster-info
kubectl get nodes

# Namespaces
kubectl get namespaces
kubectl create namespace myapp
kubectl config set-context --current --namespace=myapp

# Pods
kubectl get pods -A                        # all namespaces
kubectl get pods -n myapp -w               # watch
kubectl describe pod <pod-name> -n myapp
kubectl logs -f <pod-name> -n myapp
kubectl exec -it <pod-name> -n myapp -- sh

# Deployments
kubectl get deployments -n myapp
kubectl rollout status deployment/<name> -n myapp
kubectl rollout restart deployment/<name> -n myapp
kubectl rollout undo deployment/<name> -n myapp    # rollback

# Services
kubectl get svc -n myapp
kubectl port-forward svc/<name> 8080:80 -n myapp

# ConfigMaps and Secrets
kubectl get configmaps -n myapp
kubectl get secrets -n myapp
kubectl describe secret <name> -n myapp

# Apply and delete manifests
kubectl apply -f manifests/
kubectl delete -f manifests/

# Scale
kubectl scale deployment/<name> --replicas=3 -n myapp
```

### Networking (OrbStack Kubernetes)

OrbStack makes Kubernetes services directly accessible from Mac without port forwarding:

| Service type | Access from Mac |
|---|---|
| `ClusterIP` | Direct via cluster IP |
| `NodePort` | `localhost:<port>` |
| `LoadBalancer` | `<service>.k8s.orb.local` |
| `Ingress` | `*.k8s.orb.local` wildcard |
| Internal DNS | `service.namespace.svc.cluster.local` |

### Ingress Controllers

OrbStack does not install an ingress controller by default. Install one before using Ingress resources.

```bash
# Ingress-NGINX (recommended)
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.1/deploy/static/provider/cloud/deploy.yaml

# Verify
kubectl get pods -n ingress-nginx -w
```

### Manifest Standards

- Use `Deployment` for stateless workloads, `StatefulSet` for stateful ones.
- Always define `resources.requests` and `resources.limits` for every container.
- Always define `readinessProbe` and `livenessProbe`.
- Never hardcode secrets in manifests. Use `Secret` resources or an external secrets manager.
- Use `namespaces` to isolate environments (e.g., `dev`, `staging`).

```yaml
# Minimal production-ready Deployment template
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
  namespace: myapp
spec:
  replicas: 2
  selector:
    matchLabels:
      app: myapp
  template:
    metadata:
      labels:
        app: myapp
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
      containers:
        - name: myapp
          image: myapp:dev
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 3000
          resources:
            requests:
              cpu: "100m"
              memory: "128Mi"
            limits:
              cpu: "500m"
              memory: "512Mi"
          readinessProbe:
            httpGet:
              path: /health
              port: 3000
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /health
              port: 3000
            initialDelaySeconds: 15
            periodSeconds: 20
          env:
            - name: NODE_ENV
              value: "production"
          envFrom:
            - secretRef:
                name: myapp-secrets
```

### Multi-node and Advanced Clusters

For multi-node or custom CNI scenarios, use:

```bash
# kind (Kubernetes in Docker)
kind create cluster --config kind-config.yaml

# k3d (k3s in Docker)
k3d cluster create mycluster

# Switch context between clusters
kubectl config get-contexts
kubectl config use-context <context-name>
```

### 12-Factor Alignment

- **Config**: all environment-specific values via `ConfigMap` or `Secret`, never baked into the image.
- **Logs**: containers must write to stdout/stderr — never to files inside the container.
- **Port binding**: services expose themselves via the `Service` resource, not hardcoded IPs.
- **Processes**: pods must be stateless. Persistent state goes in volumes or external storage.
