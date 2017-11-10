# cucumber-runner-maven-plugin ![https://travis-ci.org/eu-evops/cucumber-runner-maven-plugin.svg?branch=master](https://travis-ci.org/eu-evops/cucumber-runner-maven-plugin.svg?branch=master)

Maven plugin for running cucumber features in parallel using cucumber's Main class. To use it, add it to your pom file as a build plugin:

```xml
<build>
  <plugins>
    <plugin>
          <groupId>eu.evops.maven.plugins</groupId>
          <artifactId>cucumber-runner-maven-plugin</artifactId>
          <version>1.15</version>
     </plugin>
    </plugins>
  </build>
```

Then execute using following command:
```bash
mvn cucumber-runner:run
```

By default, this will run your feature files from src/test/resources in parallel using 1 process per available CPU core. On most modern computers this would be 8. You can control threading by specifying number of threads manual in the plugin configuration (threadCount parameter).

You can also specify other cucumber related configurations, a list of them is below:

| Parameter | Type | Description | Default value |
| --------- | ---- | ----------- | ------------- |
| outputFolder | ```File``` | Where all the output files are created | target/cucumber/threads |
| features | ```List<String>``` | List of locations to look for feature files, can use relative location and classpath: format | src/test/resources |
| includeTags | ```List<String>``` | List of tags to include, precede with @ symbol | none |
| excludeTags | ```List<String>``` | List of tags to exclude, precede with @ symbol, a common approach is to exclude @wip and possibly @manual | none |
| gluePaths | ```List<String>``` | List of glue paths to use, java packages that contain your step definitions and hooks | none |
| plugins | ```List<String>``` | List of plugins to use, reporters, formatters etc | none |
| scenarioNames | ```List<String>``` | List of scenario regular expressions for matching scenario names | none |
| dryRun | ```boolean``` | Do not actually run scenario, just run plugins | false |
| monochrome | ```boolean``` | Don't colour output | true |
| strict | ```boolean``` | When set to true, it will fail scenarios if undefined steps are found | true |
| threadCount | ```int``` | Number of process to start | Number of available CPU cores on the system |
| enhancedJsonReporting| ```boolean``` | When set to true, reports are generated after each scenario and saved to disk and reports are updated| false |
| jvmArgs | ```String``` | JVM arguments that will be passed to cucumber process | empty string |

# Requirements
Maven 3, Java 8

# Test execution

During test execution following properties are available for you to use:

- System property: ```cucumberRunner.threadNumber```, ```cucumberRunner.threadCount```
- Environment variables: ```THREAD_NUMBER```, ```THREAD_COUNT```

Both of the above indicate 0-based thread number. Typical usage is to set up individual database
per thread so that you don't have clashes.

# Development

In order to run unit tests, you need to first install local package by executing:

```
mvn install -DskipTests
```

This deploys local version of the plugin to your ~/.m2/repository folder, and integration tests use it to validate the plugin. This is already handled during CI builds.

# Release

In order to release the plugin, you need to have credentials for ossrh and your settings.xml updated accordingly. Once this is in place, you can execute following commands:

```
mvn versions:set
mvn clean deploy
```
