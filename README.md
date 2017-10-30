# Ocopea Cloud Foundry Extension

Ocopea is a [Cloud Foundry](https://github.com/cloudfoundry) extension allowing developers to take application copies of 
their deployments. 

**Note**

The initial data service and copy service broker components are targetting [Pivotal Cloud Foundry](https://pivotal.io/platform) implementation, but can be easily extended to other Cloud Foundry implementations by minor tweaks.


## Description
Ocopea Cloud Foundry (CF) extension simplifies the development process of complex multi-microservice apps in multi-site multi-space 
environments. Untangling the complexity of orchestrating the restoration of production copies for debugging and 
automated tests are two of the most common tasks our rich API and UI offers. 

**Learn More**

* [Ocopea use cases](https://ocopea.github.io).

## How to build

### Pre-requisites

* mvn : 3.2.5 or greater
* jdk : 1.8 or greater
* Dependencies listed in master POM have been built and exist either in artifactory or local maven repository

Once the pre-requisites have been met, simply run the following command:

```
mvn clean install
```

This will build the artifacts after validating all checks including unit tests and place it in the `deployer/target` directory.


## Installation

* Build the project.
* Issue "cf login" and select the space that Ocopea will deploy to.
* Ensure a Postgres service exists in the selected space. Run
  ```
  cf s
  ```
  Here is a sample output:
  ```
  name        service         plan                bound apps   last operation
  postgres    user-provided
  ```

* From the deployer/target/classes folder, execute deploy.sh script:
  ```
  cd deployer/target/classes
  ./deploy.sh
  ```

  If you want to tag the deployment with an identifier, pass it as an argument:
  ```
  ./deploy.sh <identifier>
  ```
  By default the username is used as an identifier to tag the deployment.

  **Note**: You may need to change the permissions on deploy.sh file so as to execute it.

* Log into the Ocopea GUI using the link printed out at the end of the deploy.sh script execution. For example,
  ```
  http://ocopea-orcs.cf.isus.emc.com/hub-web-api/html/nui/index.html

  ```
  The login credential is admin/nazgul.

## Usage Instructions

[How-to-use](https://github.com/ocopea/documentation/blob/master/docs/how_to_use.md)

## Contribution

* [Contributing to Ocopea](https://github.com/ocopea/documentation/blob/master/docs/contributing.md)
* [Ocopea Developer Guidelines](https://github.com/ocopea/documentation/blob/master/docs/guidelines.md)

