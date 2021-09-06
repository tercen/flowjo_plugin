General notes 

- Use JDK9 for developing plugins and debugging.

Run plugin:
* Import project from github in IDE
* Install FlowJo plugin files. See below, check the path of your FlowJo installation
* Create JAR file with dependencies: mvn clean compile assembly:single
* Put the JAR file in the FlowJo plugin directory
* Restart FlowJo
* Plugin should be available under 'Workspace' -> 'Plugins'

```
mvn install:install-file -Dfile="bin\FlowJo.jar" -DgroupId=com.flowjo -DartifactId=flowjo -Dversion=10.8.0 -Dpackaging=jar -DgeneratePom=true

mvn install:install-file -Dfile="bin\fjlib-2.4.0.jar" -DgroupId=com.flowjo -DartifactId=flowlib -Dversion=2.4.0 -Dpackaging=jar -DgeneratePom=true

mvn install:install-file -Dfile="bin\fjengine-2.5.0.jar" -DgroupId=com.flowjo -DartifactId=fjengine -Dversion=2.5.0 -Dpackaging=jar -DgeneratePom=true

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
