# KB stand alone java template 

The repository is a template for a stand alone java application on KB. 
Clone the repository and fix the names and namespacing to get the basic structure of your new application. 
Change upstream and push your code to the new repository.

Repository includes:
* Maven pom.xml 
* Default java project structure
* Maven assembly plugin setup to produce default tar-ball structure
* Simple hello world unit test

## Howto get going
To get started on your new application start by the following steps:
* Clone the repository
* Create a new git repository
* Update the cloned git repository to reflect the new upstream git repository (git remote set-url origin new.git.url/here)
* In pom.xml:
    * Update groupId and artitifactId to reflect your new project
    * Update scm section to reflect new git repository
* Update the java code to reflect your new applications namespace
* Update application env (src/main/conf/appEnv.sh) to mention the applications main-class, and name of config-file (if changed) 
* Update standard logback configuration (src/main/conf/logback.xml) to create a logfile with a suitable name for the new application
* Commit changes to the repository
