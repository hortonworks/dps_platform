#!/usr/bin/env bash
#
# HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
# (c) 2016-2018 Hortonworks, Inc. All rights reserved.
# This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
# Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
# to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
# properly licensed third party, you do not have any rights to this code.
# If this code is provided to you under the terms of the AGPLv3:
# (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
# (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
# LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
# (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
# FROM OR RELATED TO THE CODE; AND
# (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
# DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
# DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
# OR LOSS OR CORRUPTION OF DATA.
#

set -e

LICENSE_CHECK_SCRIPT_DIR=`dirname ${BASH_SOURCE[0]}`
PROJECT_HOME=${LICENSE_CHECK_SCRIPT_DIR}/../../../

log() {
    echo "$@"
}

generate_scala_licenses() {
    log "Generating licenses from scala sources"
    pushd ${PROJECT_HOME}
    sbt dumpLicenseReport
    log "Licenses generated from scala sources"
    popd
}

collect_scala_licenses() {
    pushd ${PROJECT_HOME}
    local LICENSES_DIR=dp-build/build/licenses
    # Name the file with .txt so that it doesn't recursively find itself causing an infinite loop.
    local LICENSES_FILE=${LICENSES_DIR}/scala_licenses.txt
    mkdir -p ${LICENSES_DIR}
    rm -rf ${LICENSES_FILE}
    log "Collecting all scala licenses to ${LICENSES_FILE}"
    find . -iname *licenses.csv | xargs cat >> ${LICENSES_FILE}
    log "All scala licenses available at ${LICENSES_FILE}"
    popd
}

generate_java_licenses() {
    pushd ${PROJECT_HOME}
    local GATEWAY_DIR=services/gateway
    cd ${GATEWAY_DIR}
    local GATEWAY_LICENSES_FILE_PATH=../../dp-build/build/licenses/gradle_licenses.txt
    log "Generating licenses from java sources"
    rm -rf ${GATEWAY_DIR}/build/reports/dependency-license
    gradle clean generateLicenseReport
    mv build/reports/dependency-license/licenses.csv ${GATEWAY_LICENSES_FILE_PATH}
    log "Licenses generated from java sources"
    popd
}

generate_npm_licenses() {
    pushd ${PROJECT_HOME}
    cd dp-web
    local NPM_LICENSES_FILE_PATH=../dp-build/build/licenses/npm_licenses.txt
    log "Generating licenses from npm sources"
    license-checker --csv --out ${NPM_LICENSES_FILE_PATH}
    log "Licenses generated from npm sources"
    popd
}

run_license_checks() {
    local LICENSE_TYPE=$1
    pushd ${PROJECT_HOME}
    cd tools/license-tools
    sbt package
    sbt "run check_licenses \
            ../../dp-build/build/licenses/${LICENSE_TYPE}_licenses.txt \
            src/main/resources/approved_licenses.txt \
            src/main/resources/prohibited_licenses.txt \
            src/main/resources/license_mapping.txt"
    popd
}

generate_scala_licenses
collect_scala_licenses
generate_java_licenses
generate_npm_licenses
run_license_checks scala
run_license_checks npm
run_license_checks gradle