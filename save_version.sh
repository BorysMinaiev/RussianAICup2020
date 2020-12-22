#!/bin/bash

version_num=$(ls versions/ | wc -l)
version_num=$(( $version_num + 1))
echo "new version num: $version_num"
mkdir "versions/$version_num"
cp -r src/main/java/* versions/$version_num/
javac versions/$version_num/*.java versions/$version_num/util/*.java versions/$version_num/model/*.java