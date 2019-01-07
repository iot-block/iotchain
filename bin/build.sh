#!/usr/bin/env bash

set -e

echo "building jbok app"
sbt "project appJVM" stage
