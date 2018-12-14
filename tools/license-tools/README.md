# License Check Tools

## Introduction

This module helps developers to check license compatibility of dependency software against legal requirements
for the project. Currently, it only handles Scala dependencies in the DataPlane project. But it can be
extended for npm dependencies and also dependencies for other plugin apps.

## How to use

* cd `$PROJECT_HOME/tools/license-tools/bin`
* run `check_licenses.sh`
* The output will contain the following lines of importance, as illustrated in examples below:
   * `Unknown: LicenseDefinition(public domain,public domain,aopalliance # aopalliance # 1.0)`: 
A line starting with 'Unknown' means that the dependency (in this case `aopalliance`) has an unknown license and 
should be checked and resolved manually.
   * `Prohibited: LicenseDefinition(unrecognized,eclipse public license 1.0 (http://www.eclipse.org/legal/epl-v10.html),junit # junit # 4.12)`: 
A line starting with 'Prohibited' means that the dependency (in this case `junit`) is not allowed for purposes 
of legal compliance.