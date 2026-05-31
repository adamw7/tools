#!/bin/bash
mvn versions:plugin-updates-aggregate-report &
mvn versions:dependency-updates-aggregate-report &
