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
| `configmap-sample-data.yaml` | Sample CSV (`people.csv`) mounted at `/data`.                       |
| `job-uniqueness-check.yaml`  | Job that runs `SampleApp` against the CSV and prints the result.    |
| `kustomization.yaml`         | Bundles the ConfigMap + Job for `kubectl apply -k k8s/`.            |
| `run-on-minikube.sh`         | One-shot (Linux/macOS): build → image → minikube → load → apply → logs. |
| `run-on-minikube.ps1`        | One-shot (Windows): installs minikube/kubectl if missing, then the same. |

The Job runs under the **restricted** [Pod Security Standard](https://kubernetes.io/docs/concepts/security/pod-security-standards/):
non-root numeric UID, read-only root filesystem, all Linux capabilities dropped,
no privilege escalation, the `RuntimeDefault` seccomp profile, and no mounted
service-account token. It writes only to an `emptyDir` `/tmp` and is bounded by
`activeDeadlineSeconds`.

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
# 1. Build the distribution, then the image
mvn -B -DskipTests package
docker build -f assembly/Dockerfile -t tools-k8s:local .

# 2. Start minikube and load the locally built image
minikube start --driver=docker
minikube image load tools-k8s:local

# 3. Deploy and run (or: kubectl apply -k k8s/ to apply both at once)
kubectl apply -f k8s/configmap-sample-data.yaml
kubectl apply -f k8s/job-uniqueness-check.yaml

# 4. Watch it complete and read the result
kubectl wait --for=condition=complete --timeout=120s job/tools-uniqueness-check
kubectl logs -l app.kubernetes.io/component=uniqueness-check
```

Clean up with `kubectl delete -f k8s/configmap-sample-data.yaml -f k8s/job-uniqueness-check.yaml`
(the Job also self-deletes 10 minutes after finishing via `ttlSecondsAfterFinished`).

## Why there is no separate `k8s/Dockerfile`

There used to be one. `assembly/Dockerfile` built a `jar-with-dependencies` that
could not launch `SampleApp` at all, so `k8s/Dockerfile` unpacked that jar into a
flat classpath and ran the main class directly. Both faults now live in the build
instead of the image:

- `data` attaches its `spring-boot:repackage` output under the `boot` classifier,
  so the library jar keeps a flat layout and the distribution's `Main-Class`
  resolves.
- The distribution is a launcher jar plus a `lib/` of intact dependency jars
  rather than one merged archive. Merging collapsed the two
  `Log4j2Plugins.dat` files (log4j-core's and spring-boot's) into one, which cost
  log4j2 its plugin registry and silently dropped it to `DefaultConfiguration`
  at level `ERROR` — the app ran and logged nothing.

With that fixed, the only thing the Kubernetes image still needed over the
ordinary one was a numeric `USER` for the `runAsNonRoot` admission check, so
`assembly/Dockerfile` simply declares UID/GID `10001` and both use cases share
it. The console log config it copies from `docker/log4j2-console.properties`
keeps log4j2 off the RollingFile appender, which could not create its `logs/`
directory under the Job's `readOnlyRootFilesystem` anyway.

## Note on this repository's automated environment

These manifests were authored in the Claude Code sandbox. The workload itself is
verified there by running the assembled distribution directly
(`java -jar tools.assembly-<version>.jar people.csv country`), which exercises
the same launcher jar, `lib/` classpath, and console log config the image ships;
`docker.yml` covers the containerised path on every release. The sandbox
**cannot host a minikube control plane**, so the `kubectl` steps above were not
executed there. Three independent constraints prevent it:

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
   allowed.)

On a normal workstation none of these apply and `run-on-minikube.sh` runs the Job
end to end.
