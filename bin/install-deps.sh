#!/usr/bin/env bash

set -e

echo "installing sdkman"
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

echo "sdkman version"
sdk version

java_version="8.0.212.hs-adpt"
scala_version="2.12.8"
sbt_version="1.2.8"
node_version="10.16.0"
yarn_version=""

sdk install java ${java_version}

sdk install scala ${scala_version}

sdk install sbt ${sbt_version}

echo "installing nvm"
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.34.0/install.sh | bash

nvm install ${node_version}
nvm current
