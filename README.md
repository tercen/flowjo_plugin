General notes 

- Use JDK9 for developing plugins and debugging.

Run plugin:
* Import project from github in IDE
* Add all the JAR's from FlowJo
* Export to JAR file: Put JAR file in FlowJo directory
* Restart FlowJo
* Plugin should be available under 'Workspace' -> 'Plugins'

Debug plugin:

* Make sure you have exported plugin to FlowJo (see previous section)
* Create debug configuration
* Main class: com.treestar.flowjo.main.Main
* VM arguments:

	```
	-Djava.library.path="C:\Program Files\FlowJo 10.8.0"
	-Xms2048M
	-Xmx4096M
	```
* Copy release.properties from FlowJo dir to your Java project (main directory)
