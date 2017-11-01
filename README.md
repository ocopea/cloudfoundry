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

## Installation

TODO: Add instructions on how to download triple apps and deploy

## Usage Instructions

[How-to-use](https://github.com/ocopea/documentation/blob/master/docs/how_to_use.md)

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

## Contribution

* [Contributing to Ocopea](https://github.com/ocopea/documentation/blob/master/docs/contributing.md)
* [Ocopea Developer Guidelines](https://github.com/ocopea/documentation/blob/master/docs/guidelines.md)

