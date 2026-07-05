#!/bin/bash
set -euo pipefail
mvn versions:plugin-updates-aggregate-report versions:dependency-updates-aggregate-report
