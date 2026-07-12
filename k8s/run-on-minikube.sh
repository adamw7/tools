#!/usr/bin/env bash
#
# End-to-end: build the app, build the Docker image, start minikube, load the
# image, run the uniqueness-check Job, and print its result.
#
# Requirements on the host: docker, minikube, kubectl, a JDK 25 + Maven (or use
# the Maven wrapper). Run from the repository root or from k8s/.
#
# Usage:
#   ./k8s/run-on-minikube.sh
#   COLUMN=id ./k8s/run-on-minikube.sh      # check a different column
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
K8S_DIR="${ROOT_DIR}/k8s"
IMAGE="${IMAGE:-tools-k8s:local}"
COLUMN="${COLUMN:-country}"

echo "==> Building the application (mvn -DskipTests package)"
(cd "${ROOT_DIR}" && mvn -B -ntp -DskipTests package)

echo "==> Building the Docker image: ${IMAGE}"
docker build -f "${K8S_DIR}/Dockerfile" -t "${IMAGE}" "${ROOT_DIR}"

echo "==> Ensuring minikube is running"
if ! minikube status >/dev/null 2>&1; then
  minikube start --driver=docker
fi

echo "==> Loading ${IMAGE} into minikube"
minikube image load "${IMAGE}"

echo "==> Applying manifests"
kubectl apply -f "${K8S_DIR}/configmap-sample-data.yaml"
# Recreate the Job so repeated runs pick up new data/image.
kubectl delete job tools-uniqueness-check --ignore-not-found
# Substitute the target column (default 'country') into the manifest on the fly.
sed "s|\"country\"|\"${COLUMN}\"|" "${K8S_DIR}/job-uniqueness-check.yaml" | kubectl apply -f -

echo "==> Waiting for the Job to complete"
kubectl wait --for=condition=complete --timeout=120s job/tools-uniqueness-check \
  || kubectl wait --for=condition=failed --timeout=10s job/tools-uniqueness-check || true

echo "==> Job logs"
kubectl logs -l app.kubernetes.io/component=uniqueness-check --tail=-1
