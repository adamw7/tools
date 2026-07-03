#!/usr/bin/env bash
#
# Guards against the Sonatype Central Portal "Failed to get coordinates from
# pom file" rejection (and its metadata-completeness siblings) by validating
# the *flattened* pom that each published module actually deploys.
#
# The build uses CI-friendly ${revision} versioning, so the source poms inherit
# groupId/version from the parent and carry a literal ${revision}. The Central
# Portal reads coordinates only from the direct groupId/artifactId/version of
# the uploaded pom; it resolves neither properties nor parent inheritance, so an
# unflattened pom is rejected. The flatten-maven-plugin (release build,
# process-resources phase) inlines resolved coordinates plus the required
# project metadata. This script proves that inlining happened for every module
# in the Central reactor, so a broken release is caught in CI instead of by an
# opaque Portal error during deploy.
#
# Exit status is non-zero if any published module would be rejected by Central.
set -euo pipefail

# Metadata the Central Portal requires on every published artifact.
readonly REQUIRED_METADATA=(name description url licenses developers scm)

cd "$(git rev-parse --show-toplevel)"

# Reads a direct child of the flattened pom's <project> element (not nested
# elements such as dependency coordinates), namespace-agnostically.
project_value() {
  xmllint --xpath "string(//*[local-name()='project']/*[local-name()='$2'])" "$1" 2>/dev/null
}

project_has() {
  xmllint --xpath "boolean(//*[local-name()='project']/*[local-name()='$2'])" "$1" 2>/dev/null
}

generate_flattened_poms() {
  echo "Generating flattened poms for the Central reactor (release profile)..."
  mvn -B -ntp -q -Prelease -pl '!assembly,!grpc-example' \
    -Dshellcheck.skip=true -DskipTests process-resources
}

# Validates one flattened pom. Prints every problem found and returns non-zero
# when the module would be rejected by the Central Portal.
check_pom() {
  local pom_file="$1"
  local status=0
  local coord value meta

  for coord in groupId artifactId version; do
    value="$(project_value "$pom_file" "$coord")"
    if [ -z "$value" ]; then
      echo "  MISSING coordinate <$coord>"
      status=1
    elif printf '%s' "$value" | grep -q '[$]{'; then
      echo "  UNRESOLVED coordinate <$coord>=$value"
      status=1
    fi
  done

  for meta in "${REQUIRED_METADATA[@]}"; do
    if [ "$(project_has "$pom_file" "$meta")" != "true" ]; then
      echo "  MISSING metadata <$meta>"
      status=1
    fi
  done

  return "$status"
}

main() {
  generate_flattened_poms

  echo "Validating flattened poms..."
  local overall_status=0
  local checked=0
  local pom_file gav

  while IFS= read -r pom_file; do
    checked=$((checked + 1))
    gav="$(project_value "$pom_file" groupId):$(project_value "$pom_file" artifactId):$(project_value "$pom_file" version)"
    echo "Checking $pom_file ($gav)"
    if ! check_pom "$pom_file"; then
      overall_status=1
    fi
  done < <(find . -name .flattened-pom.xml | sort)

  mvn -B -ntp -q flatten:clean >/dev/null 2>&1 || true

  if [ "$checked" -eq 0 ]; then
    echo "::error::No flattened poms were generated; the release flatten step did not run."
    exit 1
  fi

  if [ "$overall_status" -ne 0 ]; then
    echo "::error::Release pom validation failed: one or more modules would be rejected by the Central Portal."
    exit 1
  fi

  echo "OK: all $checked Central-reactor poms have resolved coordinates and required metadata."
}

main "$@"
