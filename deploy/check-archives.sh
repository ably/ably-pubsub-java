#!/bin/bash

# Usage: CheckFileListExistance dir prefix filelist
CheckFileListExistance() {
	for file in $3; do
		local fileName=${file%.*}
		local ext=${file##*.}
		local filePath="${1}/${2}-${ABLY_VERSION}"
		if [ "$fileName" != "" ]; then
			filePath+="-${fileName}"
		fi
		filePath+=".${ext}"
		if [ ! -f "${filePath}" -o ! -f "${filePath}.md5" -o ! -f "${filePath}.sha1" ]; then
			echo "Required file or its hash not found: ${filePath}"
			exit 1
		fi
		if [ "${ext}" = "jar" -o "${ext}" = "aar" ]; then
			if ! unzip -t ${filePath} >/dev/null 2>&1; then
			       	echo "Corrupted archive: ${filePath}"
				exit 1
			fi
		fi
	done
}


ABLY_HOME="${0%/*}/.."
ABLY_VERSION=`grep version "${ABLY_HOME}/build.gradle" | sed $'s/.*\'\(.*\)\'.*/\\\1/'`

case "${ABLY_VERSION}" in
	[0-9][0-9.]*|[0-9][0-9.]*-beta|[0-9][0-9.]*-pre)
		;;
	*)
		echo "Incorrect version: ${ABLY_VERSION}"
		exit 1
		;;
esac

ABLY_JAVA_RELEASE_DIR="${ABLY_HOME}/core/build/release/${ABLY_VERSION}/io/ably/ably-java/${ABLY_VERSION}"
ABLY_ANDROID_RELEASE_DIR="${ABLY_HOME}/core-android/build/release/${ABLY_VERSION}/io/ably/ably-android/${ABLY_VERSION}"

if [ ! -d "${ABLY_JAVA_RELEASE_DIR}" ]; then
	echo "Directory for Java release ${ABLY_JAVA_RELEASE_DIR} doesn't exist"
	exit 1
fi

if [ ! -d "${ABLY_ANDROID_RELEASE_DIR}" ]; then
	echo "Directory for Android release ${ABLY_ANDROID_RELEASE_DIR} doesn't exist"
	exit 1
fi

JAVA_FILES=".jar .pom sources.jar javadoc.jar"
ANDROID_FILES=".aar .pom sources.jar javadoc.jar"

(CheckFileListExistance "${ABLY_JAVA_RELEASE_DIR}" "ably-java" "${JAVA_FILES}" &&
	CheckFileListExistance "${ABLY_ANDROID_RELEASE_DIR}" "ably-android" "${ANDROID_FILES}") || exit 1

ABLY_JAVA_JAR="${ABLY_JAVA_RELEASE_DIR}/ably-java-${ABLY_VERSION}.jar"
if [ `unzip -v ${ABLY_JAVA_JAR} | grep BuildConfig | wc -l` -ne 1 ]; then
	echo "Java release failed BuildConfig test"
	exit 1
fi

ABLY_ANDROID_AAR="${ABLY_ANDROID_RELEASE_DIR}/ably-android-${ABLY_VERSION}.aar"
if ! unzip -p "${ABLY_ANDROID_AAR}" classes.jar >/tmp/android-classes.jar 2>/dev/null; then
	echo "There is no classes.jar in ${ABLY_ANDROID_AAR} file"
	exit 1
fi

if [ `unzip -v /tmp/android-classes.jar | grep BuildConfig | wc -l` -ne 1 ]; then
	echo "Android release failed BuildConfig test"
	rm /tmp/android-classes.jar
	exit 1
fi
rm /tmp/android-classes.jar

echo "All tests passed"

