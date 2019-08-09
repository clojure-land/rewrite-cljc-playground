#!/usr/bin/env bash
# This script is run by continuous integration server

set -eou pipefail

./script/lint.sh
./script/clj-tests.sh 1.9
./script/clj-tests.sh 1.10
./script/cljs-tests.sh node
./script/cljs-tests.sh chrome-headless
