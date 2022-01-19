### FlowJo Tercen Plugin 

#### Run Plugin by using a released version

- Download the latest release from [github](https://github.com/tercen/flowjo_plugin/actions/workflows/main.yml). Click on the latest success workflow (colored green). Then click on "tercen_flowjo_plugin" below Artifacts at the bottom. The downloaded file is a zip file that contains the JAR file.
* Put the JAR file in the FlowJo plugin directory
* Put the tercen.properties file in the same directory and adjust properties if needed
* Restart FlowJo
* Plugin should be available under 'Workspace' -> 'Plugins'

#### Run/ Debug plugin (Developer)

General note:

- Use JDK9 for developing plugins and debugging.

Run plugin:
* Import project from github in IDE
* Install FlowJo plugin files. See below, check the path of your FlowJo installation
* Create JAR file with dependencies: mvn clean compile assembly:single
* Put the JAR file in the FlowJo plugin directory
* Put the tercen.properties file in the same directory and adjust properties if needed
* Restart FlowJo
* Plugin should be available under 'Workspace' -> 'Plugins'

```
mvn install:install-file -Dfile="third\FlowJo.jar" -DgroupId=com.flowjo -DartifactId=flowjo -Dversion=10.8.0 -Dpackaging=jar -DgeneratePom=true

mvn install:install-file -Dfile="third\fjlib-2.4.0.jar" -DgroupId=com.flowjo -DartifactId=flowlib -Dversion=2.4.0 -Dpackaging=jar -DgeneratePom=true

mvn install:install-file -Dfile="third\fjengine-2.5.0.jar" -DgroupId=com.flowjo -DartifactId=fjengine -Dversion=2.5.0 -Dpackaging=jar -DgeneratePom=true

```

Debug plugin:

* Make sure you have exported plugin to FlowJo (see previous section)
* Add all the JAR's fom FlowJo
* Create debug configuration
* Main class: com.treestar.flowjo.main.Main
* VM arguments:

	```
	-Djava.library.path="C:\Program Files\FlowJo 10.8.0"
	-Xms2048M
	-Xmx4096M
	```
* Copy release.properties from FlowJo dir to your Java project (main directory)
