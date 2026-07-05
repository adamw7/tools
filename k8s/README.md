# Running the tools app on minikube

This directory contains everything needed to run the project's `SampleApp`
(the CSV column-uniqueness checker, `io.github.adamw7.tools.data.SampleApp`) on a
local [minikube](https://minikube.sigs.k8s.io/) cluster.

`SampleApp` is a **batch** program: it reads a CSV file, checks whether a chosen
column is unique, logs the result, and exits. The correct Kubernetes primitive
for a run-to-completion workload is a **Job**, not a Deployment.

## Contents

| File                         | Purpose                                                             |
| ---------------------------- | ------------------------------------------------------------------- |
| `Dockerfile`                 | Runnable deployment image (flat classpath + console logging).       |
| `log4j2.properties`          | Console log config baked into the image so results reach stdout.    |
| `configmap-sample-data.yaml` | Sample CSV (`people.csv`) mounted at `/data`.                       |
| `job-uniqueness-check.yaml`  | Job that runs `SampleApp` against the CSV and prints the result.    |
| `run-on-minikube.sh`         | One-shot (Linux/macOS): build → image → minikube → load → apply → logs. |
| `run-on-minikube.ps1`        | One-shot (Windows): installs minikube/kubectl if missing, then the same. |

## Quick start

### Linux / macOS

Prerequisites on your machine: `docker`, `minikube`, `kubectl`, JDK 25 + Maven.

```bash
./k8s/run-on-minikube.sh
# check a different column:
COLUMN=id ./k8s/run-on-minikube.sh
```

### Windows

Prerequisites: Docker Desktop and a JDK 25 + Maven (or the `mvnw.cmd` wrapper).
The script downloads `minikube` and `kubectl` automatically when they are not
already on `PATH`.

```powershell
.\k8s\run-on-minikube.ps1
# check a different column:
.\k8s\run-on-minikube.ps1 -Column id
```

Expected output (default column `country`, which repeats):

```
... country is NOT unique
```

With `COLUMN=id` (unique):

```
... id is unique
```

## Manual steps

```bash
# 1. Build the fat jar, then the deployment image
mvn -B -DskipTests package
docker build -f k8s/Dockerfile -t tools-k8s:local .

# 2. Start minikube and load the locally built image
minikube start --driver=docker
minikube image load tools-k8s:local

# 3. Deploy and run
kubectl apply -f k8s/configmap-sample-data.yaml
kubectl apply -f k8s/job-uniqueness-check.yaml

# 4. Watch it complete and read the result
kubectl wait --for=condition=complete --timeout=120s job/tools-uniqueness-check
kubectl logs -l app.kubernetes.io/component=uniqueness-check
```

Clean up with `kubectl delete -f k8s/configmap-sample-data.yaml -f k8s/job-uniqueness-check.yaml`
(the Job also self-deletes 10 minutes after finishing via `ttlSecondsAfterFinished`).

## Why a dedicated deployment image (not `assembly/Dockerfile`)

`../assembly/Dockerfile` builds a fat jar that **cannot launch `SampleApp` with
`java -jar`**. The `data` module is built with `spring-boot:repackage`, so its jar
has a nested Spring Boot layout (`BOOT-INF/classes` + `BOOT-INF/lib`); the assembly
`jar-with-dependencies` inherits that layout, yet its manifest `Main-Class` points
straight at `SampleApp`. Running it fails with
`ClassNotFoundException: io.github.adamw7.tools.data.SampleApp`, because the class
lives under `BOOT-INF/classes`, not on the flat classpath. (The project's CI builds
that image but never runs it, so the problem is latent.)

`k8s/Dockerfile` sidesteps this without touching the project build: a JDK build
stage expands the fat jar into a flat `classes/` + `lib/` classpath, and the
runtime stage runs `SampleApp` directly against it. It also adds a console
`log4j2.properties` first on the classpath, because `SampleApp` otherwise logs only
to a rolling file (`logs/app.log`, no console appender) — with the console config
the result appears directly in `kubectl logs`.

A cleaner long-term fix belongs in the project build: give the assembly a directly
runnable main class (or a proper Spring Boot `Start-Class`) and add a Console
appender to `data/src/main/resources/log4j2.properties`.

## Note on this repository's automated environment

These manifests were authored and the container workload verified with plain
`docker run` in the Claude Code sandbox, but the sandbox itself **cannot host a
minikube control plane**, so the `kubectl` steps above were not executed there.
Three independent constraints prevented it:

1. **cgroup v1 host + nested containers.** The `docker` (and `kind`/`k3d`) drivers
   run the control plane in a nested container; on this cgroup-v1 host the inner
   `runc` fails every sandbox with
   `runc create failed: unable to start container process: can't get final child's PID from pipe: EOF`.
   Both the `cri-dockerd` and `containerd` runtimes fail identically.
2. **No systemd.** PID 1 is not systemd and `systemctl` is offline, so minikube's
   `none` driver (which supervises `kubelet` via systemd) cannot be used either.
3. **Restricted egress.** The egress policy 403-blocks Docker Hub,
   `registry.k8s.io`, the Kubernetes package CDN, and GitHub release assets, so the
   `none` driver's `crictl`/`cri-dockerd` prerequisites can't be fetched. (The
   docker driver still reaches the point above because minikube's `kicbase` image
   and preloaded component images come from Google-hosted registries, which are
   allowed. The images above are built with the base pulled through the allowed
   `mirror.gcr.io` Docker Hub mirror.)

On a normal workstation none of these apply and `run-on-minikube.sh` runs the Job
end to end.
