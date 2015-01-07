#!/bin/bash

set -e

pushd $(dirname $0) > /dev/null
basedir=$(pwd)
popd > /dev/null

bindir="$basedir/bin"
srcdir="$basedir/src"

if [ -d "$bindir" ]; then
	rm -r "$bindir"
fi

mkdir "$bindir"

javac -d "$bindir" -cp "$srcdir" "$srcdir/UDPClient.java" "$srcdir/UDPServer.java"
