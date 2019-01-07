#!/usr/bin/env bash

set -e
echo "building jbok docker image"
sbt "project appJVM" docker:publishLocal

echo "docker images:"
docker images
