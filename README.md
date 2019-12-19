# KB stand alone java template 

The repository is a template for a stand alone java application on KB. 
Clone the repository and fix the names and namespacing to get the basic structure of your new application. 
Change upstream and push your code to the new repository.

Repository includes:
* Default java project structure
* Simple hello world unit test
* Maven pom.xml
* Maven assembly plugin setup to produce default tar-ball structure
* Jenkinsfile for OpenShift

## Tests
Unit tests are run using the surefire plugin (configured in the parent pom). 
If you have unit tests that takes long to run, and don't want them to run when at every invocation of mvn package, annotate the testcase with `@Tag("slow")` in the java code. 
To run all unit tests including the ones tagged as slow, enable the `allTests` maven profile: e.g. `mvn clean package -PallTests`.

## How to get going
To get started on your new application start by the following steps:
* Create a new empty git repository and clone it locally
* In the project folder, run the following command to get the template files
`git archive --format=tar --remote=ssh://git@sbprojects.statsbiblioteket.dk:7999/ark/stand-alone-java-template.git master | tar -x`
* In pom.xml:
    * Update groupId and artitifactId to reflect your new project
    * Update scm section to reflect new git repository
* Update the java code to reflect your new applications namespace
* Update application env (`src/main/conf/appEnv.sh`) to mention the applications main-class, and name of config-file (if changed) 
* Update standard logback configuration (`src/main/conf/logback.xml`) to create a logfile with a suitable name for the new application
* Commit & push changes to the repository
